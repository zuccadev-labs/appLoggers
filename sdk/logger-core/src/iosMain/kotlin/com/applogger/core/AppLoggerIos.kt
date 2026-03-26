package com.applogger.core

import com.applogger.core.internal.*
import com.applogger.core.model.LogEvent
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice

private const val DEFAULT_BUFFER_CAPACITY = 1000
private const val REMOTE_CONFIG_TIMEOUT_MS = 10_000
private const val PREFS_KEY_CONSENT = "applogger_consent_level"
private const val PREFS_KEY_BUDGET_BYTES = "data_budget_bytes_today"
private const val PREFS_KEY_BUDGET_DAY = "data_budget_day_index"
private const val POLLING_DELAY_MULTIPLIER = 1_000L
private const val NS_PER_MS = 1_000_000L
private const val BYTES_PER_MB = 1_048_576L

/**
 * iOS entry point for the AppLogger SDK, exported to Swift via KMP framework.
 *
 * Access via the [shared] singleton. Thread-safe after [initialize].
 *
 * ## Swift usage
 * ```swift
 * let transport = SupabaseTransport(endpoint: url, apiKey: key)
 * AppLoggerIos.shared.initialize(config: config, transport: transport)
 *
 * AppLoggerIos.shared.info(tag: "Cart", message: "Item added", extra: nil)
 * ```
 *
 * @see AppLoggerConfig for configuration options.
 */
@Suppress("TooManyFunctions")
class AppLoggerIos private constructor() : AppLogger {

    @Volatile
    private var instance: AppLogger = NoOpLogger()

    @Volatile
    private var implRef: AppLoggerImpl? = null

    @Volatile
    private var sessionManagerRef: SessionManager? = null

    @Volatile
    private var consentLevel: ConsentLevel = ConsentLevel.MARKETING

    @Volatile
    private var deviceFingerprint: String = ""

    @Volatile
    private var resolvedConfigRef: AppLoggerConfig? = null

    private var pollingJob: kotlinx.coroutines.Job? = null
    private val pollingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var initialized = false

    companion object {
        val shared = AppLoggerIos()
    }

    fun initialize(
        config: AppLoggerConfig,
        transport: LogTransport? = null
    ) {
        if (initialized) return
        initialized = true

        val resolvedConfig = resolveConfig(config)

        // Restore persisted consent level
        val savedConsent = NSUserDefaults.standardUserDefaults.stringForKey(PREFS_KEY_CONSENT)
        consentLevel = if (savedConsent != null) {
            runCatching { ConsentLevel.valueOf(savedConsent) }.getOrElse { resolvedConfig.defaultConsentLevel }
        } else resolvedConfig.defaultConsentLevel

        val deviceInfo = IosDeviceInfoProvider().get()
        val sessionManager = SessionManager()
        val filter = ChainedLogFilter(listOf(RateLimitFilter(120)))
        val buffer = InMemoryBuffer(DEFAULT_BUFFER_CAPACITY)
        val resolvedTransport = transport ?: object : LogTransport {
            override suspend fun send(events: List<LogEvent>): TransportResult =
                TransportResult.Success
            override fun isAvailable(): Boolean = false
        }
        val formatter = JsonLogFormatter(prettyPrint = resolvedConfig.isDebugMode)
        val integrityManager = if (resolvedConfig.integritySecret.isNotBlank())
            BatchIntegrityManager(resolvedConfig.integritySecret) else null

        val dataBudget = if (resolvedConfig.dailyDataLimitMb > 0) {
            DataBudgetManager(
                resolvedConfig.dailyDataLimitMb.toLong() * BYTES_PER_MB,
                IosDataBudgetPersistence()
            )
        } else DataBudgetManager(DataBudgetManager.DISABLED)

        val processor = BatchProcessor(
            buffer = buffer,
            transport = resolvedTransport,
            formatter = formatter,
            config = resolvedConfig,
            integrityManager = integrityManager,
            dataBudget = dataBudget
        )

        val impl = AppLoggerImpl(
            deviceInfo = deviceInfo,
            sessionManager = sessionManager,
            filter = filter,
            processor = processor,
            config = resolvedConfig,
            systemSnapshotProvider = buildSystemSnapshotProvider(),
            consentProvider = { consentLevel },
            deviceIdAnonymizer = { id -> sha256Hex(id) }
        )

        wireInstance(resolvedConfig, impl, sessionManager, processor, resolvedTransport, buffer)
    }

    private fun wireInstance(
        config: AppLoggerConfig,
        impl: AppLoggerImpl,
        sessionManager: SessionManager,
        processor: BatchProcessor,
        transport: LogTransport,
        buffer: InMemoryBuffer
    ) {
        deviceFingerprint = captureDeviceFingerprint()
        impl.addGlobalExtra("device_fingerprint", deviceFingerprint)

        val bundleId = NSBundle.mainBundle.bundleIdentifier ?: "unknown"
        impl.addGlobalExtra("app_package", bundleId)
        impl.addGlobalExtra("install_source", captureInstallSource())

        instance = impl
        implRef = impl
        sessionManagerRef = sessionManager
        resolvedConfigRef = config

        AppLoggerHealth.processor = processor
        AppLoggerHealth.transport = transport
        AppLoggerHealth.buffer = buffer
        AppLoggerHealth.bufferCapacity = DEFAULT_BUFFER_CAPACITY
        AppLoggerHealth.initialized = true

        if (!config.isDebugMode) {
            IosCrashHandler(impl).install()
        }

        if (config.remoteConfigEnabled) {
            startRemoteConfigPolling(config, impl)
        }
    }

    // ── Config resolution ───────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun resolveConfig(config: AppLoggerConfig): AppLoggerConfig {
        val debugFlag = try {
            NSBundle.mainBundle.infoDictionary
                ?.get("APPLOGGER_DEBUG") as? String == "true"
        } catch (_: Exception) { false }
        return if (debugFlag && !config.isDebugMode) config.copy(isDebugMode = true) else config
    }

    // ── Device fingerprint ──────────────────────────────────────────────────

    /**
     * Captures a pseudonymized device fingerprint using IDFV (identifierForVendor).
     * Formula: SHA-256(IDFV + ":" + bundleIdentifier)
     * IDFV persists across reinstalls as long as at least one app from the same
     * vendor is on the device. Combined with bundleIdentifier for per-app uniqueness.
     */
    private fun captureDeviceFingerprint(): String {
        return try {
            val idfv = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: return ""
            val bundleId = NSBundle.mainBundle.bundleIdentifier ?: return ""
            sha256Hex("$idfv:$bundleId")
        } catch (_: Exception) { "" }
    }

    /**
     * Returns the persistent device fingerprint captured during initialization.
     * On iOS this is SHA-256(IDFV + bundleIdentifier).
     * Returns empty string if SDK is not initialized.
     */
    fun getDeviceFingerprint(): String = deviceFingerprint

    // ── Remote config ───────────────────────────────────────────────────────

    private fun startRemoteConfigPolling(config: AppLoggerConfig, impl: AppLoggerImpl) {
        pollingJob = pollingScope.launch {
            while (isActive) {
                fetchAndApplyRemoteConfig(config, impl)
                delay(config.remoteConfigIntervalSeconds.toLong() * POLLING_DELAY_MULTIPLIER)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun fetchAndApplyRemoteConfig(config: AppLoggerConfig, impl: AppLoggerImpl) {
        try {
            val endpoint = config.endpoint.trimEnd('/')
            val fp = deviceFingerprint
            val filterParam = if (fp.isNotBlank()) {
                "or=(device_fingerprint.eq.$fp,device_fingerprint.is.null)"
            } else {
                "device_fingerprint=is.null"
            }
            val urlStr = "$endpoint/rest/v1/device_remote_config" +
                "?select=min_level,debug_enabled,tags_allow,tags_block,sampling_rate,device_fingerprint" +
                "&enabled=eq.true&$filterParam"

            val url = platform.Foundation.NSURL.URLWithString(urlStr) ?: return
            val request = platform.Foundation.NSMutableURLRequest.requestWithURL(url)
            request.setHTTPMethod("GET")
            request.setValue(config.apiKey, forHTTPHeaderField = "apikey")
            request.setValue("Bearer ${config.apiKey}", forHTTPHeaderField = "Authorization")
            request.setValue("application/json", forHTTPHeaderField = "Accept")
            request.setTimeoutInterval(REMOTE_CONFIG_TIMEOUT_MS.toDouble() / 1000.0)

            val semaphore = platform.darwin.dispatch_semaphore_create(0)
            var responseData: platform.Foundation.NSData? = null

            platform.Foundation.NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, _ ->
                responseData = data
                platform.darwin.dispatch_semaphore_signal(semaphore)
            }.resume()

            platform.darwin.dispatch_semaphore_wait(
                semaphore,
                platform.darwin.dispatch_time(
                    platform.darwin.DISPATCH_TIME_NOW,
                    REMOTE_CONFIG_TIMEOUT_MS.toLong() * NS_PER_MS
                )
            )

            val dataStr = responseData?.let {
                platform.Foundation.NSString.create(data = it, encoding = platform.Foundation.NSUTF8StringEncoding)
                    ?.toString()
            } ?: return

            val overrides = parseRemoteConfigResponse(dataStr)
            if (overrides != null) {
                impl.applyRemoteConfig(overrides)
            }
        } catch (_: Exception) {
            // Remote config fetch is best-effort — never crash the app
        }
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun parseRemoteConfigResponse(json: String): RemoteConfigOverrides? {
        return try {
            if (json.isBlank() || json.trim() == "[]") return null

            // Simple JSON parsing — find device-specific entry or fallback to global
            val entries = json.trim().removeSurrounding("[", "]")
            if (entries.isBlank()) return null

            var deviceEntry: String? = null
            var globalEntry: String? = null

            // Split entries (simplified — assumes well-formed PostgREST JSON)
            val parts = splitJsonArray(json)
            for (part in parts) {
                if (part.contains("\"device_fingerprint\"") && !part.contains("null")) {
                    deviceEntry = part
                } else {
                    globalEntry = part
                }
            }

            val entry = deviceEntry ?: globalEntry ?: return null
            extractRemoteOverrides(entry)
        } catch (_: Exception) { null }
    }

    private fun splitJsonArray(json: String): List<String> {
        val trimmed = json.trim().removeSurrounding("[", "]")
        if (trimmed.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in trimmed.indices) {
            when (trimmed[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        result.add(trimmed.substring(start, i + 1))
                        start = i + 1
                    }
                }
                ',' -> if (depth == 0) start = i + 1
            }
        }
        return result
    }

    @Suppress("CyclomaticComplexMethod")
    private fun extractRemoteOverrides(entry: String): RemoteConfigOverrides {
        fun extractString(key: String): String? {
            val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
            val match = Regex(pattern).find(entry)
            return match?.groupValues?.get(1)
        }
        fun extractBool(key: String): Boolean? {
            val pattern = "\"$key\"\\s*:\\s*(true|false)"
            val match = Regex(pattern).find(entry)
            return match?.groupValues?.get(1)?.toBooleanStrictOrNull()
        }
        fun extractDouble(key: String): Double? {
            val pattern = "\"$key\"\\s*:\\s*([0-9.]+)"
            val match = Regex(pattern).find(entry)
            return match?.groupValues?.get(1)?.toDoubleOrNull()
        }
        // PostgREST returns TEXT[] as JSON arrays: ["tag1","tag2"]
        fun extractStringArray(key: String): Set<String>? {
            val pattern = "\"$key\"\\s*:\\s*\\[([^\\]]*)]"
            val match = Regex(pattern).find(entry) ?: return null
            val inner = match.groupValues[1]
            if (inner.isBlank()) return null
            return inner.split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
                .toSet()
                .takeIf { it.isNotEmpty() }
        }

        return RemoteConfigOverrides(
            debugEnabled = extractBool("debug_enabled"),
            minLevel = extractString("min_level")?.let {
                runCatching { LogMinLevel.valueOf(it.uppercase()) }.getOrNull()
            },
            allowedTags = extractStringArray("tags_allow"),
            blockedTags = extractStringArray("tags_block"),
            samplingRate = extractDouble("sampling_rate")
        )
    }

    /**
     * Triggers an immediate remote config refresh.
     */
    fun refreshRemoteConfig() {
        val config = resolvedConfigRef ?: return
        val impl = implRef ?: return
        fetchAndApplyRemoteConfig(config, impl)
    }

    // ── System snapshot (iOS) ───────────────────────────────────────────────

    @Suppress("TooGenericExceptionCaught")
    private fun buildSystemSnapshotProvider(): () -> Map<String, String> {
        return {
            buildMap {
                captureThermalState()?.let { put("thermal_status", it) }
                captureIsLowMemory()?.let { put("is_low_memory", it) }
            }
        }
    }

    private fun captureThermalState(): String? = try {
        when (NSProcessInfo.processInfo.thermalState) {
            platform.Foundation.NSProcessInfoThermalStateNominal -> "none"
            platform.Foundation.NSProcessInfoThermalStateFair -> "light"
            platform.Foundation.NSProcessInfoThermalStateSerious -> "severe"
            platform.Foundation.NSProcessInfoThermalStateCritical -> "critical"
            else -> "unknown"
        }
    } catch (_: Exception) { null }

    private fun captureIsLowMemory(): String? = try {
        val isLow = NSProcessInfo.processInfo.lowPowerModeEnabled
        if (isLow) "true" else "false"
    } catch (_: Exception) { null }

    // ── Install source (iOS) ────────────────────────────────────────────────

    /**
     * Detects the iOS install source:
     * - **testflight**: App Store receipt URL contains "sandboxReceipt" (TestFlight builds)
     * - **app_store**: Receipt exists but NOT sandbox (production App Store)
     * - **sideload**: No receipt at all (Xcode install, enterprise deploy, ad-hoc)
     */
    @Suppress("TooGenericExceptionCaught")
    private fun captureInstallSource(): String = try {
        val receiptUrl = NSBundle.mainBundle.appStoreReceiptURL
        if (receiptUrl != null) {
            val receiptPath = receiptUrl.path ?: ""
            if (receiptPath.contains("sandboxReceipt")) "testflight" else "app_store"
        } else {
            "sideload"
        }
    } catch (_: Exception) {
        "unknown"
    }

    // ── Logging API ─────────────────────────────────────────────────────────

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
    ) =
        instance.warn(tag, message, throwable, anomalyType, extra)

    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.error(tag, message, throwable, extra)

    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.critical(tag, message, throwable, extra)

    override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) =
        instance.metric(name, value, unit, tags)

    override fun flush() = instance.flush()

    // ── Identity ────────────────────────────────────────────────────────────

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

    // ── Distributed tracing ─────────────────────────────────────────────────

    fun setTraceId(id: String) {
        implRef?.setTraceId(id)
    }

    fun clearTraceId() {
        implRef?.setTraceId(null)
    }

    // ── Breadcrumbs ─────────────────────────────────────────────────────────

    fun recordBreadcrumb(
        action: String,
        screen: String? = null,
        metadata: Map<String, String>? = null
    ) {
        implRef?.recordBreadcrumb(action, screen, metadata)
    }

    // ── Session ─────────────────────────────────────────────────────────────

    fun newSession() {
        sessionManagerRef?.rotate()
    }

    override fun setSessionVariant(variant: String?) {
        implRef?.setSessionVariant(variant)
    }

    // ── Consent ─────────────────────────────────────────────────────────────

    fun setConsent(level: ConsentLevel) {
        consentLevel = level
        NSUserDefaults.standardUserDefaults.setObject(level.name, forKey = PREFS_KEY_CONSENT)
    }

    fun getConsent(): ConsentLevel = consentLevel

    // ── Beta tester ─────────────────────────────────────────────────────────

    fun setBetaTester(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return
        implRef?.addGlobalExtra("beta_tester_email", trimmed)
        implRef?.addGlobalExtra("is_beta_tester", "true")
    }

    fun clearBetaTester() {
        implRef?.removeGlobalExtra("beta_tester_email")
        implRef?.removeGlobalExtra("is_beta_tester")
    }

    // ── User properties ─────────────────────────────────────────────────────

    override fun setUserProperty(key: String, value: String) {
        implRef?.addGlobalExtra("user_prop_$key", value)
    }

    override fun removeUserProperty(key: String) {
        implRef?.removeGlobalExtra("user_prop_$key")
    }

    // ── Scoped logger ───────────────────────────────────────────────────────

    fun newScope(
        vararg attributes: Pair<String, Any>,
        consentLevel: ConsentLevel? = null
    ): ScopedAppLogger =
        instance.newScope(*attributes, consentLevel = consentLevel)

    // ── Coroutine exception handler ─────────────────────────────────────────

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

    // ── Global extras ───────────────────────────────────────────────────────

    override fun addGlobalExtra(key: String, value: String) = instance.addGlobalExtra(key, value)
    override fun removeGlobalExtra(key: String) = instance.removeGlobalExtra(key)
    override fun clearGlobalExtra() = instance.clearGlobalExtra()

    // ── Reset (testing only) ────────────────────────────────────────────────

    internal fun reset() {
        pollingJob?.cancel()
        pollingJob = null
        initialized = false
        instance = NoOpLogger()
        implRef = null
        sessionManagerRef = null
        resolvedConfigRef = null
        consentLevel = ConsentLevel.MARKETING
        deviceFingerprint = ""
        AppLoggerHealth.initialized = false
        AppLoggerHealth.processor = null
        AppLoggerHealth.transport = null
        AppLoggerHealth.buffer = null
    }
}

/**
 * NSUserDefaults-backed persistence for [DataBudgetManager] on iOS.
 * Survives process restarts within the same UTC day.
 */
private class IosDataBudgetPersistence : DataBudgetPersistence {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun loadBytesUsed(): Long =
        defaults.doubleForKey(PREFS_KEY_BUDGET_BYTES).toLong()

    override fun loadDayIndex(): Int =
        defaults.integerForKey(PREFS_KEY_BUDGET_DAY).toInt()

    override fun save(bytesUsed: Long, dayIndex: Int) {
        defaults.setDouble(bytesUsed.toDouble(), forKey = PREFS_KEY_BUDGET_BYTES)
        defaults.setInteger(dayIndex.toLong(), forKey = PREFS_KEY_BUDGET_DAY)
    }
}
