package com.applogger.core

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ProcessLifecycleOwner
import com.applogger.core.internal.*
import kotlin.concurrent.Volatile
import java.util.concurrent.atomic.AtomicBoolean

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
            formatter = JsonLogFormatter(),
            config = resolvedConfig,
            offlineStorage = buildOfflineStorage(appContext, resolvedConfig)
        )
        val impl = AppLoggerImpl(
            deviceInfo = deviceInfo,
            sessionManager = sessionManager,
            filter = filter,
            processor = processor,
            config = resolvedConfig
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
        implRef?.clearDeviceId()
    }

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
