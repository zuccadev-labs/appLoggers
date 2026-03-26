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
private const val BYTES_PER_MB = 1_048_576L
private const val MAX_INSTALL_PACKAGE_LENGTH = 64
private const val REMOTE_CONFIG_TIMEOUT_MS = 10_000

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

    @Volatile
    private var consentLevel: ConsentLevel = ConsentLevel.MARKETING

    @Volatile
    private var appContextRef: android.content.Context? = null

    @Volatile
    private var deviceFingerprint: String = ""

    @Volatile
    private var resolvedConfigRef: AppLoggerConfig? = null

    @Volatile
    private var remoteConfigTimer: java.util.Timer? = null

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
        appContextRef = appContext
        val resolvedConfig = resolveConfig(appContext, config)
        val platform = PlatformDetector.detect(appContext)

        // Restore persisted consent level (or use default from config)
        val prefs = appContext.getSharedPreferences("applogger_prefs", Context.MODE_PRIVATE)
        val savedConsent = prefs.getString("consent_level", null)
        consentLevel = if (savedConsent != null) {
            runCatching { ConsentLevel.valueOf(savedConsent) }.getOrElse { resolvedConfig.defaultConsentLevel }
        } else resolvedConfig.defaultConsentLevel

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
        val budgetPersistence = SharedPrefsDataBudgetPersistence(prefs)
        val networkTypeProvider: () -> String = { captureNetworkType(appContext) ?: "unknown" }
        val dataBudget = if (resolvedConfig.dailyDataLimitMb > 0)
            DataBudgetManager(resolvedConfig.dailyDataLimitMb * BYTES_PER_MB, budgetPersistence, networkTypeProvider)
        else DataBudgetManager(DataBudgetManager.DISABLED)
        val integrityManager = if (resolvedConfig.integritySecret.isNotBlank())
            BatchIntegrityManager(resolvedConfig.integritySecret) else null
        val processor = BatchProcessor(
            buffer = buffer,
            transport = transport ?: NoOpTransport(),
            formatter = JsonLogFormatter(prettyPrint = resolvedConfig.isDebugMode),
            config = resolvedConfig,
            offlineStorage = buildOfflineStorage(appContext, resolvedConfig),
            dataBudget = dataBudget,
            integrityManager = integrityManager
        )
        val impl = AppLoggerImpl(
            deviceInfo = deviceInfo,
            sessionManager = sessionManager,
            filter = filter,
            processor = processor,
            config = resolvedConfig,
            systemSnapshotProvider = buildSystemSnapshotProvider(appContext),
            consentProvider = { consentLevel },
            deviceIdAnonymizer = { id -> sha256Hex(id) }
        )
        wireInstance(appContext, resolvedConfig, impl, sessionManager, processor, transport, buffer, bufferCapacity)
    }

    @Suppress("LongParameterList")
    private fun wireInstance(
        appContext: Context,
        config: AppLoggerConfig,
        impl: AppLoggerImpl,
        sessionManager: SessionManager,
        processor: BatchProcessor,
        transport: LogTransport?,
        buffer: InMemoryBuffer,
        bufferCapacity: Int
    ) {
        deviceFingerprint = captureDeviceFingerprint(appContext)
        impl.addGlobalExtra("device_fingerprint", deviceFingerprint)
        impl.addGlobalExtra("app_package", appContext.packageName)
        impl.addGlobalExtra("install_source", captureInstallSource(appContext))

        instance = impl
        implRef = impl
        sessionManagerRef = sessionManager
        resolvedConfigRef = config
        updateHealthReferences(processor, transport ?: NoOpTransport(), buffer, bufferCapacity)

        if (!config.isDebugMode) AndroidCrashHandler(impl).install()
        registerLifecycleObservers(impl, sessionManager)

        if (config.remoteConfigEnabled) {
            startRemoteConfigPolling(config, impl)
        }
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
     * Updates the active consent level and persists it across app restarts.
     * Events requiring a higher consent level than [level] are silently dropped.
     */
    fun setConsent(level: ConsentLevel) {
        consentLevel = level
        appContextRef?.getSharedPreferences("applogger_prefs", Context.MODE_PRIVATE)
            ?.edit()?.putString("consent_level", level.name)?.apply()
    }

    /** Returns the current active consent level. */
    fun getConsent(): ConsentLevel = consentLevel

    /**
     * Marks the current user as a beta tester with their email for identification.
     *
     * The email must come from the developer's own auth flow (Google Sign-In,
     * Firebase Auth, custom login, etc.) — the SDK does not capture it.
     * `APPLOGGER_BETA_TESTER=true` in `local.properties` activates the mode;
     * the developer calls this method with the email obtained at runtime.
     *
     * Injects `is_beta_tester = "true"` and `beta_tester_email` into every
     * subsequent event's `extra` JSONB field.
     *
     * ## Usage
     * ```kotlin
     * // In your login/auth callback — NOT from a config variable
     * if (BuildConfig.IS_BETA_TESTER) {
     *     val email = googleAccount.email  // from YOUR auth flow
     *     AppLoggerSDK.setBetaTester(email)
     * }
     * ```
     *
     * ## Query in Supabase
     * ```sql
     * SELECT * FROM app_logs WHERE extra->>'is_beta_tester' = 'true';
     * SELECT * FROM app_logs WHERE extra->>'beta_tester_email' = 'tester@example.com';
     * ```
     *
     * @param email The beta tester's email captured from the developer's auth flow.
     *              Blank values are ignored.
     */
    fun setBetaTester(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return
        implRef?.addGlobalExtra("beta_tester_email", trimmed)
        implRef?.addGlobalExtra("is_beta_tester", "true")
    }

    /**
     * Removes beta tester identification from subsequent events.
     * Previously sent events retain the beta tester data.
     */
    fun clearBetaTester() {
        implRef?.removeGlobalExtra("beta_tester_email")
        implRef?.removeGlobalExtra("is_beta_tester")
    }

    /**
     * Tags the session with an A/B test or experiment variant.
     * Stored as top-level `variant` column for efficient group queries.
     * Pass null to clear.
     */
    override fun setSessionVariant(variant: String?) {
        implRef?.setSessionVariant(variant)
    }

    /** Attaches a user-scoped property. MARKETING-level data — suppressed in STRICT/PERFORMANCE mode. */
    override fun setUserProperty(key: String, value: String) {
        implRef?.addGlobalExtra("user_prop_$key", value)
    }

    /** Removes a user property set via [setUserProperty]. */
    override fun removeUserProperty(key: String) {
        implRef?.removeGlobalExtra("user_prop_$key")
    }

    /**
     * Creates a [ScopedAppLogger] with an optional consent-level override.
     */
    fun newScope(vararg attributes: Pair<String, Any>, consentLevel: ConsentLevel? = null): ScopedAppLogger =
        instance.newScope(*attributes, consentLevel = consentLevel)

    /**
     * Resets the SDK to its uninitialized state.
     *
     * **FOR TESTING ONLY.** Allows re-initialization between test cases.
     * Never call this in production code.
     */
    @VisibleForTesting
    fun reset() {
        remoteConfigTimer?.cancel()
        remoteConfigTimer = null
        isInitialized.set(false)
        instance = NoOpLogger()
        implRef = null
        sessionManagerRef = null
        resolvedConfigRef = null
        consentLevel = ConsentLevel.MARKETING
        appContextRef = null
        deviceFingerprint = ""
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

    @Suppress("TooGenericExceptionCaught")
    private fun captureInstallSource(context: android.content.Context): String {
        return try {
            val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            sanitizeInstallPackage(installer)
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun sanitizeInstallPackage(pkg: String?): String = when (pkg?.trim()) {
        null, "" -> "sideload"
        "com.android.vending" -> "play_store"
        "com.amazon.venezia" -> "amazon_appstore"
        "com.huawei.appmarket" -> "huawei_appgallery"
        else -> pkg.take(MAX_INSTALL_PACKAGE_LENGTH)
    }

    /**
     * Returns the persistent device fingerprint captured during initialization.
     * On Android this is `Settings.Secure.ANDROID_ID` — survives app reinstalls.
     * Returns empty string if SDK is not initialized.
     */
    fun getDeviceFingerprint(): String = deviceFingerprint

    /**
     * Manually triggers a remote config refresh from the `device_remote_config` table.
     * No-op if remote config is not enabled or SDK is not initialized.
     */
    fun refreshRemoteConfig() {
        appContextRef ?: return
        val impl = implRef ?: return
        val cfg = resolvedConfigRef ?: return
        if (!cfg.remoteConfigEnabled) return
        fetchAndApplyRemoteConfig(cfg, impl)
    }

    /**
     * Captures a persistent, pseudonymized device fingerprint.
     *
     * Formula: `SHA-256(ANDROID_ID + ":" + package_name)`
     *
     * Properties:
     * - **Persistent**: survives app reinstalls (only resets on factory reset).
     * - **Unique per app**: two apps on the same device produce different hashes.
     * - **GDPR Art. 25 compliant**: pseudonymized — cannot be reversed to the raw ANDROID_ID.
     * - **Deterministic**: same device + same app = same hash, always.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun captureDeviceFingerprint(context: android.content.Context): String {
        return try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: return ""
            val packageName = context.packageName ?: return ""
            sha256Hex("$androidId:$packageName")
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Fetches remote config from the `device_remote_config` table and applies overrides.
     * Queries: device-specific rule first (by fingerprint), then global fallback.
     * Thread-safe: can be called from any thread.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun fetchAndApplyRemoteConfig(
        config: AppLoggerConfig,
        impl: AppLoggerImpl
    ) {
        val fingerprint = deviceFingerprint
        val env = config.environment
        val baseUrl = "${config.endpoint.trimEnd('/')}/rest/v1/device_remote_config"

        try {
            // Query: device-specific + global, enabled only, ordered so device-specific wins
            val query = buildString {
                append(baseUrl)
                append("?enabled=eq.true")
                append("&select=min_level,debug_enabled,tags_allow,tags_block,sampling_rate,device_fingerprint")
                append("&or=(device_fingerprint.eq.$fingerprint,device_fingerprint.is.null)")
                if (env.isNotBlank()) {
                    append("&or=(environment.eq.$env,environment.is.null)")
                }
                append("&order=device_fingerprint.desc.nullslast")
                append("&limit=2")
            }

            val url = java.net.URL(query)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", config.apiKey)
            conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = REMOTE_CONFIG_TIMEOUT_MS
            conn.readTimeout = REMOTE_CONFIG_TIMEOUT_MS

            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val overrides = parseRemoteConfigResponse(body)
            impl.applyRemoteConfig(overrides)
        } catch (_: Exception) {
            // Remote config is best-effort — never crash the app
        }
    }

    /**
     * Parses the PostgREST JSON array response into [RemoteConfigOverrides].
     * First row with non-null device_fingerprint wins (device-specific),
     * otherwise falls back to global row.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun parseRemoteConfigResponse(json: String): RemoteConfigOverrides? {
        return try {
            val arr = org.json.JSONArray(json)
            if (arr.length() == 0) return null

            // Pick the best row: device-specific (non-null fingerprint) wins
            var bestRow: org.json.JSONObject? = null
            for (i in 0 until arr.length()) {
                val row = arr.getJSONObject(i)
                if (!row.isNull("device_fingerprint")) {
                    bestRow = row
                    break
                }
                if (bestRow == null) bestRow = row
            }
            if (bestRow == null) return null

            val minLevel = if (bestRow.isNull("min_level")) null else {
                runCatching { LogMinLevel.valueOf(bestRow.getString("min_level").uppercase()) }.getOrNull()
            }
            val debugEnabled = if (bestRow.isNull("debug_enabled")) null else bestRow.getBoolean("debug_enabled")
            val samplingRate = if (bestRow.isNull("sampling_rate")) null else bestRow.getDouble("sampling_rate")

            val allowedTags = if (bestRow.isNull("tags_allow")) null else {
                val a = bestRow.getJSONArray("tags_allow")
                (0 until a.length()).map { a.getString(it) }.toSet()
            }
            val blockedTags = if (bestRow.isNull("tags_block")) null else {
                val a = bestRow.getJSONArray("tags_block")
                (0 until a.length()).map { a.getString(it) }.toSet()
            }

            RemoteConfigOverrides(
                minLevel = minLevel,
                debugEnabled = debugEnabled,
                allowedTags = allowedTags,
                blockedTags = blockedTags,
                samplingRate = samplingRate
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Starts the background polling timer for remote config.
     * Uses a simple Timer thread — lightweight, no coroutine dependency.
     */
    private fun startRemoteConfigPolling(
        config: AppLoggerConfig,
        impl: AppLoggerImpl
    ) {
        val intervalMs = config.remoteConfigIntervalSeconds * 1000L
        val timer = java.util.Timer("AppLogger-RemoteConfig", true)
        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                fetchAndApplyRemoteConfig(config, impl)
            }
        }, 0L, intervalMs)
        remoteConfigTimer = timer
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
/**
 * SharedPreferences-backed persistence for [DataBudgetManager].
 * Survives process restarts within the same UTC day.
 */
private class SharedPrefsDataBudgetPersistence(
    private val prefs: android.content.SharedPreferences
) : com.applogger.core.internal.DataBudgetPersistence {
    override fun loadBytesUsed(): Long = prefs.getLong(KEY_BYTES, 0L)
    override fun loadDayIndex(): Int = prefs.getInt(KEY_DAY, 0)
    override fun save(bytesUsed: Long, dayIndex: Int) {
        prefs.edit().putLong(KEY_BYTES, bytesUsed).putInt(KEY_DAY, dayIndex).apply()
    }
    companion object {
        private const val KEY_BYTES = "data_budget_bytes_today"
        private const val KEY_DAY = "data_budget_day_index"
    }
}

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
