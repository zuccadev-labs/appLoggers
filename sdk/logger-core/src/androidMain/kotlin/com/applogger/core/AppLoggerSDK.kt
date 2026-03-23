package com.applogger.core

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ProcessLifecycleOwner
import com.applogger.core.internal.*
import kotlin.concurrent.Volatile
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName

private const val LOW_RESOURCE_BUFFER_CAPACITY = 100
private const val DEFAULT_BUFFER_CAPACITY = 1000
private const val ADAPTIVE_RAM_PERCENTAGE = 0.001
private const val MIN_ADAPTIVE_BUFFER_CAPACITY = 50
private const val MAX_ADAPTIVE_BUFFER_CAPACITY = 5000

/**
 * Android entry point for the AppLogger SDK.
 *
 * Thread-safe singleton with idempotent initialization. Delegates all calls
 * to [com.applogger.core.internal.AppLoggerImpl] once [initialize] succeeds;
 * before that, all calls are no-ops.
 *
 * ## Quick start
 * ```kotlin
 * // In Application.onCreate()
 * val transport = SupabaseTransport(url, key)
 * AppLoggerSDK.initialize(this, config, transport)
 *
 * // Anywhere in the app
 * AppLoggerSDK.info("Cart", "Item added", mapOf("sku" to "A123"))
 * ```
 *
 * @see AppLoggerConfig for configuration options.
 * @see com.applogger.transport.supabase.SupabaseTransport for the Supabase transport.
 */
@Suppress("TooManyFunctions")
object AppLoggerSDK : AppLogger {

    @Volatile
    private var instance: AppLogger = NoOpLogger()
    private val isInitialized = AtomicBoolean(false)

    @Volatile
    private var implRef: AppLoggerImpl? = null

    @Volatile
    private var sessionManagerRef: SessionManager? = null

    /**
     * Initializes the SDK. Must be called exactly once, in `Application.onCreate()`.
     * Subsequent calls are silently ignored (idempotent).
     *
     * ## Debug mode via APPLOGGER_DEBUG
     * If the app's `AndroidManifest.xml` (or `gradle.properties`) define
     * `APPLOGGER_DEBUG=true` as a manifest placeholder or BuildConfig field,
     * the SDK activates debug mode and logcat output automatically.
     * When absent or `false`, logcat is suppressed regardless of [AppLoggerConfig.consoleOutput].
     *
     * ```xml
     * <!-- AndroidManifest.xml -->
     * <meta-data android:name="APPLOGGER_DEBUG" android:value="${APPLOGGER_DEBUG}" />
     * ```
     *
     * @param context   Android [Context]; the application context will be extracted.
     * @param config    SDK configuration built via [AppLoggerConfig.Builder].
     * @param transport Optional [LogTransport]; defaults to no-op if omitted.
     */
    fun initialize(
        context: Context,
        config: AppLoggerConfig,
        transport: LogTransport? = null
    ) {
        if (!isInitialized.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        val resolvedConfig = resolveConfig(appContext, config)
        val platform = PlatformDetector.detect(appContext)

        val deviceInfo = AndroidDeviceInfoProvider(appContext, platform).get()
        val sessionManager = SessionManager()
        val filter = ChainedLogFilter(
            listOf(RateLimitFilter(if (platform.isLowResource) 30 else 120))
        )
        val bufferCapacity = computeBufferCapacity(appContext, platform, resolvedConfig)
        val buffer = InMemoryBuffer(
            maxCapacity = bufferCapacity,
            overflowPolicy = resolvedConfig.bufferOverflowPolicy
        )
        val processor = BatchProcessor(
            buffer = buffer,
            transport = transport ?: NoOpTransport(),
            formatter = JsonLogFormatter(prettyPrint = resolvedConfig.isDebugMode),
            config = resolvedConfig,
            offlineStorage = buildOfflineStorage(appContext, resolvedConfig)
        )
        val impl = AppLoggerImpl(
            deviceInfo = deviceInfo,
            sessionManager = sessionManager,
            filter = filter,
            processor = processor,
            config = resolvedConfig,
            systemSnapshotProvider = buildSystemSnapshotProvider(appContext)
        )

        instance = impl
        implRef = impl
        sessionManagerRef = sessionManager
        updateHealthReferences(processor, transport ?: NoOpTransport(), buffer, bufferCapacity)

        if (!resolvedConfig.isDebugMode) AndroidCrashHandler(impl).install()
        registerLifecycleObservers(impl, sessionManager)
    }

    private fun resolveConfig(appContext: Context, config: AppLoggerConfig): AppLoggerConfig {
        val debugFlag = readAppLoggerDebugFlag(appContext)
        val withDebug = if (debugFlag && !config.isDebugMode) config.copy(isDebugMode = true) else config
        val platform = PlatformDetector.detect(appContext)
        return if (platform.isLowResource) withDebug.resolveForLowResource() else withDebug
    }

    private fun registerLifecycleObservers(impl: AppLoggerImpl, sessionManager: SessionManager) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLoggerLifecycleObserver {
                impl.flush()
                sessionManager.onBackground()
            }
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLoggerForegroundObserver { sessionManager.onForeground() }
        )
    }

    override fun debug(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.debug(tag, message, throwable, extra)

    override fun info(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.info(tag, message, throwable, extra)

    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?,
        anomalyType: String?,
        extra: Map<String, Any>?
    ) = instance.warn(tag, message, throwable, anomalyType, extra)

    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.error(tag, message, throwable, extra)

    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.critical(tag, message, throwable, extra)

    override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) =
        instance.metric(name, value, unit, tags)

    override fun flush() = instance.flush()

    fun setAnonymousUserId(userId: String) {
        implRef?.setUserId(userId)
    }

    fun clearAnonymousUserId() {
        implRef?.clearUserId()
    }

    fun setDeviceId(deviceId: String) {
        implRef?.setDeviceId(deviceId)
    }

    fun clearDeviceId() {
        implRef?.setDeviceId(null)
    }

    /**
     * Sets the distributed trace ID attached to every subsequent event.
     *
     * Use this to correlate events across devices — e.g. pass the same `traceId` to
     * the TV app via gRPC when the mobile app initiates a playback session. In Supabase:
     * ```sql
     * SELECT * FROM app_logs WHERE trace_id = 'abc-123' ORDER BY timestamp;
     * -- Returns the full mobile→TV→backend event sequence in chronological order.
     * ```
     */
    fun setTraceId(id: String) {
        implRef?.setTraceId(id)
    }

    /** Clears the distributed trace ID. Call after the traced operation completes. */
    fun clearTraceId() {
        implRef?.setTraceId(null)
    }

    /**
     * Records a user interaction breadcrumb in the circular buffer.
     *
     * The last [AppLoggerConfig.breadcrumbCapacity] breadcrumbs are automatically attached
     * to ERROR and CRITICAL events as a `"breadcrumbs"` JSON array, giving the full
     * "what the user did before the crash" sequence without manual instrumentation at the
     * error call site.
     *
     * ```kotlin
     * // In your click handlers / navigation callbacks:
     * AppLoggerSDK.recordBreadcrumb("tap_play",       screen = "ContentDetail")
     * AppLoggerSDK.recordBreadcrumb("tap_settings",   screen = "HomeScreen")
     * AppLoggerSDK.recordBreadcrumb("tap_disney_plus", screen = "SettingsScreen",
     *     metadata = mapOf("content_id" to contentId))
     * ```
     */
    fun recordBreadcrumb(
        action: String,
        screen: String? = null,
        metadata: Map<String, String>? = null
    ) {
        implRef?.recordBreadcrumb(action, screen, metadata)
    }

    /**
     * Creates a [ScopedAppLogger] that automatically merges [attributes] into every event.
     *
     * Useful for isolating logs from a specific feature flow (playback session, checkout,
     * onboarding) without polluting global state via [addGlobalExtra].
     *
     * ```kotlin
     * val log = AppLoggerSDK.newScope(
     *     "content_id"  to contentId,
     *     "stream_type" to "4K_HDR"
     * )
     * log.error("Player", "Codec failed", exception)
     * // → event includes content_id and stream_type automatically
     * ```
     */
    fun newScope(vararg attributes: Pair<String, Any>): ScopedAppLogger =
        instance.newScope(*attributes)

    override fun addGlobalExtra(key: String, value: String) {
        implRef?.addGlobalExtra(key, value)
    }

    override fun removeGlobalExtra(key: String) {
        implRef?.removeGlobalExtra(key)
    }

    override fun clearGlobalExtra() {
        implRef?.clearGlobalExtra()
    }

    /**
     * [CoroutineExceptionHandler] que captura excepciones no manejadas en coroutines
     * y las registra como errores con contexto completo (nombre de coroutine + stack trace).
     *
     * ## Problema que resuelve
     * Los fallos silenciosos en coroutines de background se pierden sin dejar rastro.
     * Con este handler, el SDK los captura y los envía a Supabase automáticamente.
     *
     * ## Uso
     * ```kotlin
     * // Scope de pantalla o repositorio:
     * val scope = CoroutineScope(Dispatchers.IO + AppLoggerSDK.exceptionHandler)
     *
     * // Scope de ViewModel (con SupervisorJob para no cancelar hermanos):
     * val scope = CoroutineScope(
     *     Dispatchers.Main + SupervisorJob() + AppLoggerSDK.exceptionHandler
     * )
     * ```
     *
     * El SDK adjuntará automáticamente:
     * - `anomaly_type = "coroutine_crash"` (promovido a columna en Supabase)
     * - `coroutine_name` = nombre del CoroutineName del contexto (si aplica)
     * - stack trace completo de la excepción
     */
    val exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { context, throwable ->
        instance.error(
            tag = "CoroutineException",
            message = throwable.message ?: "Unhandled coroutine exception",
            throwable = throwable,
            extra = buildMap {
                put("anomaly_type", "coroutine_crash")
                context[CoroutineName]?.name?.let { put("coroutine_name", it) }
            }
        )
    }

    /**
     * Fuerza el inicio de una nueva sesión inmediatamente.
     *
     * Útil en eventos de login/logout, inicio de onboarding, o cuando el producto
     * necesita separar sesiones lógicas independientemente del tiempo en background.
     *
     * ```kotlin
     * // Al hacer login
     * AppLoggerSDK.setAnonymousUserId(user.id)
     * AppLoggerSDK.newSession()
     * ```
     */
    fun newSession() {
        sessionManagerRef?.rotate()
    }

    /**
     * Resets the SDK to its uninitialized state.
     *
     * **FOR TESTING ONLY.** Allows re-initialization between test cases.
     * Never call this in production code.
     */
    @VisibleForTesting
    fun reset() {
        isInitialized.set(false)
        instance = NoOpLogger()
        implRef = null
        sessionManagerRef = null
        AppLoggerHealth.initialized = false
        AppLoggerHealth.processor = null
        AppLoggerHealth.transport = null
        AppLoggerHealth.buffer = null
        // traceId, breadcrumbs, debouncer are owned by implRef and GC'd with it.
    }

    /**
     * Lee el flag APPLOGGER_DEBUG del manifest meta-data.
     * Permite activar debug mode sin cambiar código — solo con una variable de entorno
     * o manifest placeholder en el build.
     *
     * Configuración en AndroidManifest.xml:
     * ```xml
     * <meta-data android:name="APPLOGGER_DEBUG" android:value="${APPLOGGER_DEBUG}" />
     * ```
     *
     * Configuración en build.gradle:
     * ```groovy
     * manifestPlaceholders = [APPLOGGER_DEBUG: System.getenv("APPLOGGER_DEBUG") ?: "false"]
     * ```
     */
    private fun readAppLoggerDebugFlag(context: Context): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("APPLOGGER_DEBUG")?.equals("true", ignoreCase = true) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Builds the platform diagnostic snapshot provider.
     * Called on ERROR/CRITICAL events; results merged into the event's extra map.
     * Captures: memory_usage_pct, thermal_status (API 29+), network_type.
     * Each capture is independently guarded — one failure does not block the others.
     */
    private fun buildSystemSnapshotProvider(context: Context): () -> Map<String, String> {
        val appContext = context.applicationContext
        return {
            buildMap {
                captureMemoryUsage(appContext)?.let { put("memory_usage_pct", it) }
                captureThermalStatus(appContext)?.let { put("thermal_status", it) }
                captureNetworkType(appContext)?.let { put("network_type", it) }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun captureMemoryUsage(context: android.content.Context): String? = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mem = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mem)
        if (mem.totalMem > 0L) ((mem.totalMem - mem.availMem) * 100L / mem.totalMem).toString()
        else null
    } catch (_: Exception) { null }

    @Suppress("TooGenericExceptionCaught")
    private fun captureThermalStatus(context: android.content.Context): String? = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.let { resolveThermalLabel(it.currentThermalStatus) }
        } else null
    } catch (_: Exception) { null }

    private fun resolveThermalLabel(status: Int): String = when (status) {
        android.os.PowerManager.THERMAL_STATUS_NONE      -> "none"
        android.os.PowerManager.THERMAL_STATUS_LIGHT     -> "light"
        android.os.PowerManager.THERMAL_STATUS_MODERATE  -> "moderate"
        android.os.PowerManager.THERMAL_STATUS_SEVERE    -> "severe"
        android.os.PowerManager.THERMAL_STATUS_CRITICAL  -> "critical"
        android.os.PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        android.os.PowerManager.THERMAL_STATUS_SHUTDOWN  -> "shutdown"
        else -> "unknown"
    }

    @Suppress("TooGenericExceptionCaught", "DEPRECATION")
    private fun captureNetworkType(context: android.content.Context): String? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "none"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)     -> "wifi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                else -> "unknown"
            }
        } else {
            cm.activeNetworkInfo?.typeName?.lowercase() ?: "none"
        }
    } catch (_: Exception) { null }

    private fun buildOfflineStorage(context: Context, config: AppLoggerConfig): OfflineStorage =
        when (config.offlinePersistenceMode) {
            OfflinePersistenceMode.NONE -> NoOpOfflineStorage
            else -> SqliteOfflineStorage(context)
        }

    private fun computeBufferCapacity(
        appContext: Context,
        platform: Platform,
        config: AppLoggerConfig
    ): Int {
        return when (config.bufferSizeStrategy) {
            BufferSizeStrategy.FIXED -> defaultBufferCapacity(platform)
            BufferSizeStrategy.ADAPTIVE_TO_RAM -> adaptiveRamBufferCapacity(appContext)
            BufferSizeStrategy.ADAPTIVE_TO_LOG_RATE -> defaultBufferCapacity(platform)
        }
    }

    private fun defaultBufferCapacity(platform: Platform): Int {
        return if (platform.isLowResource) {
            LOW_RESOURCE_BUFFER_CAPACITY
        } else {
            DEFAULT_BUFFER_CAPACITY
        }
    }

    private fun adaptiveRamBufferCapacity(appContext: Context): Int {
        val activityManager = appContext.getSystemService(
            Context.ACTIVITY_SERVICE
        ) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem
        val target = (totalRam * ADAPTIVE_RAM_PERCENTAGE).toInt()
        return target.coerceIn(
            MIN_ADAPTIVE_BUFFER_CAPACITY,
            MAX_ADAPTIVE_BUFFER_CAPACITY
        )
    }

    private fun updateHealthReferences(
        processor: BatchProcessor,
        transport: LogTransport,
        buffer: InMemoryBuffer,
        bufferCapacity: Int
    ) {
        AppLoggerHealth.processor = processor
        AppLoggerHealth.transport = transport
        AppLoggerHealth.buffer = buffer
        AppLoggerHealth.bufferCapacity = bufferCapacity
        AppLoggerHealth.initialized = true
    }
}

/** No-op transport used when no backend is configured. */
internal class NoOpTransport : LogTransport {
    override suspend fun send(events: List<com.applogger.core.model.LogEvent>): TransportResult =
        TransportResult.Success
    override fun isAvailable(): Boolean = false
}

/**
 * Returns a network availability provider backed by Android's [ConnectivityManager].
 *
 * Pass this to [com.applogger.transport.supabase.SupabaseTransport] to avoid
 * retry loops when the device is offline:
 *
 * ```kotlin
 * val transport = SupabaseTransport(
 *     endpoint = url,
 *     apiKey = key,
 *     networkAvailabilityProvider = androidNetworkAvailabilityProvider(context)
 * )
 * AppLoggerSDK.initialize(context, config, transport)
 * ```
 *
 * The returned lambda uses cached network state — no I/O on the calling thread.
 */
fun androidNetworkAvailabilityProvider(context: Context): () -> Boolean {
    val appContext = context.applicationContext
    return {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(network)
                caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } else {
                @Suppress("DEPRECATION")
                cm?.activeNetworkInfo?.isConnected == true
            }
        } catch (_: Exception) {
            true // Fail open — let the transport attempt and handle the error
        }
    }
}
