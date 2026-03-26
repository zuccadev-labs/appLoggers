package com.applogger.core.internal

import com.applogger.core.*
import com.applogger.core.model.*
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// Orden de severidad para comparar contra minLevel
private val LEVEL_ORDINAL = mapOf(
    LogLevel.DEBUG    to 0,
    LogLevel.INFO     to 1,
    LogLevel.WARN     to 2,
    LogLevel.ERROR    to 3,
    LogLevel.CRITICAL to 4,
    LogLevel.METRIC   to 5  // METRIC siempre pasa
)

private val MIN_LEVEL_ORDINAL = mapOf(
    LogMinLevel.DEBUG    to 0,
    LogMinLevel.INFO     to 1,
    LogMinLevel.WARN     to 2,
    LogMinLevel.ERROR    to 3,
    LogMinLevel.CRITICAL to 4
)

/**
 * Pillar 1 — Auto-inject anomaly_type when a Throwable is present and the caller
 * did not supply one explicitly. Ensures the Supabase column is never empty on real errors.
 * Top-level to avoid counting against AppLoggerImpl's TooManyFunctions limit.
 */
private fun injectAnomalyType(
    level: LogLevel,
    throwable: Throwable?,
    extra: Map<String, Any>?
): Map<String, Any>? {
    if (throwable == null || extra?.containsKey("anomaly_type") == true) return extra
    val autoType = when (level) {
        LogLevel.CRITICAL -> "critical"
        LogLevel.ERROR    -> "error"
        LogLevel.WARN     -> "warn"
        else              -> return extra
    }
    return (extra ?: emptyMap()) + mapOf("anomaly_type" to autoType)
}

/**
 * Enqueues an event through the deduplication debouncer if active, or directly.
 * Top-level to reduce CyclomaticComplexity of AppLoggerImpl.process().
 */
private fun enqueueWithDebounce(event: LogEvent, debouncer: EventDebouncer?, processor: BatchProcessor) {
    if (debouncer != null) {
        val deduped = debouncer.process(event)
        if (deduped != null) processor.enqueue(deduped)
    } else {
        processor.enqueue(event)
    }
}

/**
 * Converts per-call Map<String, Any> to Map<String, JsonElement> and merges with globalExtra.
 * Per-call values win on key collision. Returns null when both sources are empty.
 * Top-level to avoid counting against AppLoggerImpl's TooManyFunctions limit.
 */
private fun mergeExtra(
    extra: Map<String, Any>?,
    global: Map<String, JsonElement>
): Map<String, JsonElement>? {
    val perCall = extra?.mapValues { (_, v) -> anyToJsonElement(v) }
    return when {
        global.isEmpty() && perCall == null -> null
        global.isEmpty() -> perCall
        perCall == null  -> global
        else             -> global + perCall  // perCall wins on collision
    }
}

/**
 * Removes user_prop_* keys from globalExtra when consent is below MARKETING.
 * Top-level to avoid counting against AppLoggerImpl's TooManyFunctions limit.
 */
private fun filterGlobalExtraForConsent(
    global: Map<String, JsonElement>,
    consentProvider: (() -> ConsentLevel)?
): Map<String, JsonElement> {
    val consent = consentProvider?.invoke() ?: ConsentLevel.MARKETING
    if (consent == ConsentLevel.MARKETING) return global
    return global.filterKeys { !it.startsWith("user_prop_") }
}

/**
 * Returns false if the event should be dropped based on remote config + local config.
 * Checks: debug gate, minLevel, tag filtering, sampling.
 * ERROR/CRITICAL always pass tag/sampling filters.
 * Top-level to reduce CyclomaticComplexity of AppLoggerImpl.process().
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod")
private fun passesRemoteGate(
    level: LogLevel,
    tag: String,
    remote: RemoteConfigOverrides?,
    config: AppLoggerConfig
): Boolean {
    // Debug gate
    val effectiveDebug = remote?.debugEnabled ?: config.isDebugMode
    if (level == LogLevel.DEBUG && !effectiveDebug) return false

    // minLevel override
    if (level != LogLevel.METRIC) {
        val effectiveMinLevel = remote?.minLevel ?: config.minLevel
        val eventOrdinal = LEVEL_ORDINAL.getValue(level)
        val minOrdinal = MIN_LEVEL_ORDINAL.getValue(effectiveMinLevel)
        if (eventOrdinal < minOrdinal) return false
    }

    // Tag filtering (ERROR/CRITICAL always pass)
    if (remote != null && level != LogLevel.ERROR && level != LogLevel.CRITICAL) {
        if (remote.blockedTags != null && tag in remote.blockedTags) return false
        if (remote.allowedTags != null && tag !in remote.allowedTags) return false
    }

    // Sampling (ERROR/CRITICAL always pass)
    if (remote?.samplingRate != null && remote.samplingRate < 1.0) {
        if (level != LogLevel.ERROR && level != LogLevel.CRITICAL) {
            if (kotlin.random.Random.nextDouble() >= remote.samplingRate) return false
        }
    }

    return true
}

/** Convierte un valor Any a JsonElement preservando el tipo nativo. */
internal fun anyToJsonElement(value: Any): JsonElement = when (value) {
    is Boolean -> JsonPrimitive(value)
    is Int     -> JsonPrimitive(value)
    is Long    -> JsonPrimitive(value)
    is Float   -> JsonPrimitive(value)
    is Double  -> JsonPrimitive(value)
    is String  -> JsonPrimitive(value)
    else       -> JsonPrimitive(value.toString())
}

/**
 * Implementación core del pipeline de eventos.
 *
 * @param systemSnapshotProvider Optional platform-injected lambda called on ERROR/CRITICAL
 *   events to capture real-time system diagnostics (thermal, memory, network).
 *   On Android this is provided by [com.applogger.core.AppLoggerSDK].
 *   Returns a `Map<String, String>` merged into the event's extra.
 *   Must be thread-safe (called from the SDK's internal thread).
 */
@Suppress("TooManyFunctions")
internal class AppLoggerImpl(
    private val deviceInfo: DeviceInfo,
    private val sessionManager: SessionManager,
    private val filter: LogFilter,
    private val processor: BatchProcessor,
    private val config: AppLoggerConfig,
    private val systemSnapshotProvider: (() -> Map<String, String>)? = null,
    private val consentProvider: (() -> ConsentLevel)? = null,
    private val deviceIdAnonymizer: ((String) -> String)? = null
) : AppLogger {

    @Volatile
    private var deviceId: String = defaultDeviceId(deviceInfo)

    @Volatile
    private var userId: String? = null

    // Global extra: copy-on-write map — safe for multiplatform commonMain.
    // Stored as Map<String, JsonElement> — same type as LogEvent.extra.
    // Reads are lock-free (volatile snapshot). Writes replace the reference atomically.
    @Volatile
    private var globalExtra: Map<String, JsonElement> = emptyMap()

    // Distributed tracing — propagated across devices when set.
    @Volatile
    private var traceId: String? = null

    // Feature: Deduplication — null when deduplicationWindowMs == 0.
    private val debouncer: EventDebouncer? =
        if (config.deduplicationWindowMs > 0L) EventDebouncer(config.deduplicationWindowMs) else null

    // Feature: Breadcrumbs — null when breadcrumbCapacity == 0.
    private val breadcrumbs: BreadcrumbBuffer? =
        if (config.breadcrumbCapacity > 0) BreadcrumbBuffer(config.breadcrumbCapacity) else null

    // Feature: Consent — null when no consentProvider is supplied.
    private val consentFilter: ConsentFilter? = consentProvider?.let { ConsentFilter(it) }

    // Feature: Session Variant — A/B test or experiment tag.
    @Volatile
    private var sessionVariant: String? = null

    // Feature: Remote Config — dynamic overrides fetched from Supabase.
    @Volatile
    private var remoteOverrides: RemoteConfigOverrides? = null

    override fun debug(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        process(LogLevel.DEBUG, tag, message, throwable, extra = extra)
    }

    override fun info(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        process(LogLevel.INFO, tag, message, throwable, extra = extra)
    }

    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?,
        anomalyType: String?,
        extra: Map<String, Any>?
    ) {
        // Merge anomalyType into extra preserving native types — no premature toString().
        val mergedExtra: Map<String, Any>? = when {
            anomalyType != null && extra != null -> extra + mapOf<String, Any>("anomaly_type" to anomalyType)
            anomalyType != null -> mapOf("anomaly_type" to anomalyType)
            else -> extra
        }?.ifEmpty { null }
        process(LogLevel.WARN, tag, message, throwable, extra = mergedExtra)
    }

    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        process(LogLevel.ERROR, tag, message, throwable, extra)
    }

    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        process(LogLevel.CRITICAL, tag, message, throwable, extra)
    }

    override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) {
        try {
            // METRIC siempre pasa minLevel — pero respeta consoleOutput/isDebugMode para logcat
            if (config.isDebugMode && config.consoleOutput) {
                platformLog("AppLogger/METRIC", "[METRIC] $name=$value $unit")
            }

            val enrichedTags = buildMap {
                tags?.forEach { (k, v) -> put(k, v) }
                put("platform", deviceInfo.platform)
                put("app_version", deviceInfo.appVersion)
                put("device_model", deviceInfo.model)
            }

            val event = LogEvent(
                id = generateUUID(),
                timestamp = currentTimeMillis(),
                level = LogLevel.METRIC,
                tag = "METRIC",
                message = "$name=$value $unit",
                deviceInfo = deviceInfo,
                deviceId = deviceId,
                sessionId = sessionManager.sessionId,
                userId = userId,
                environment = config.environment,
                sdkVersion = AppLoggerVersion.NAME,
                metricName = name,
                metricValue = value,
                metricUnit = unit,
                metricTags = enrichedTags
            )

            if (consentFilter != null && !consentFilter.passes(event)) return
            if (!filter.passes(event)) return
            processor.enqueue(event)
        } catch (_: Exception) {
            // El SDK NUNCA lanza excepciones al caller
        }
    }

    override fun flush() {
        // Drain any pending deduplicated events before flushing — guarantees
        // occurrence_count is never silently discarded on app background/exit.
        debouncer?.drainAggregated()?.forEach { processor.enqueue(it) }
        processor.flush()
    }

    fun setUserId(id: String) {
        val normalized = normalizeAnonymousUserId(id)
        userId = normalized.ifBlank { null }
    }

    fun clearUserId() {
        userId = null
    }

    /**
     * Sets the device ID. Pass `null` to revert to the SDK-generated default.
     * AppLoggerSDK exposes this as separate setDeviceId(String) and clearDeviceId().
     */
    fun setDeviceId(id: String?) {
        deviceId = if (id.isNullOrBlank()) defaultDeviceId(deviceInfo) else id.trim()
    }

    /**
     * Sets or clears the distributed trace ID attached to all subsequent events.
     * Pass `null` to clear. Blank strings are treated as null.
     */
    fun setTraceId(id: String?) {
        traceId = id?.trim()?.ifBlank { null }
    }

    /**
     * Records a user interaction breadcrumb.
     * The last [AppLoggerConfig.breadcrumbCapacity] breadcrumbs are auto-attached
     * to ERROR and CRITICAL events.
     */
    fun recordBreadcrumb(action: String, screen: String? = null, metadata: Map<String, String>? = null) {
        breadcrumbs?.record(action, screen, metadata)
    }

    // ── Global extra ──────────────────────────────────────────────────────────

    /**
     * Attaches a key-value pair to every subsequent event until removed.
     * Stored as JsonPrimitive(String) — copy-on-write, lock-free reads.
     */
    override fun addGlobalExtra(key: String, value: String) {
        globalExtra = globalExtra + (key to JsonPrimitive(value))
    }

    override fun removeGlobalExtra(key: String) {
        globalExtra = globalExtra - key
    }

    override fun clearGlobalExtra() {
        globalExtra = emptyMap()
    }


    /**
     * Applies remote config overrides fetched from the `device_remote_config` table.
     * Thread-safe: single volatile reference swap.
     */
    fun applyRemoteConfig(overrides: RemoteConfigOverrides?) {
        remoteOverrides = overrides
    }

    @Suppress("ReturnCount")
    private fun process(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        extra: Map<String, Any>? = null
    ) {
        try {
            val remote = remoteOverrides
            if (!passesRemoteGate(level, tag, remote, config)) return

            val effectiveDebug = remote?.debugEnabled ?: config.isDebugMode
            val effectiveConsole = remote?.debugEnabled ?: config.consoleOutput
            if (effectiveDebug && effectiveConsole) {
                platformLog("AppLogger/$tag", "[${level.name}] $message")
            }
            val effectiveExtra = injectAnomalyType(level, throwable, extra)
            val resolvedExtra  = mergeExtra(effectiveExtra, filterGlobalExtraForConsent(globalExtra, consentProvider))
            var event = buildLogEvent(level, tag, message, throwable, resolvedExtra)
            if (consentFilter != null && !consentFilter.passes(event)) return
            if (!filter.passes(event)) return
            if (level == LogLevel.ERROR || level == LogLevel.CRITICAL) {
                event = enrichHighSeverityEvent(event)
            }
            enqueueWithDebounce(event, debouncer, processor)
        } catch (_: Exception) {
            // El SDK NUNCA lanza excepciones al caller
        }
    }

    override fun setSessionVariant(variant: String?) {
        sessionVariant = variant?.trim()?.ifBlank { null }
    }

    private fun buildLogEvent(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
        resolvedExtra: Map<String, JsonElement>?
    ): LogEvent {
        val isStrict = consentProvider?.invoke() == ConsentLevel.STRICT && config.dataMinimizationEnabled
        val effectiveUserId = if (isStrict) null else userId
        val effectiveDeviceId = if (isStrict && deviceIdAnonymizer != null) deviceIdAnonymizer(deviceId) else deviceId
        return LogEvent(
            id = generateUUID(),
            timestamp = currentTimeMillis(),
            level = level,
            tag = tag.take(100),
            message = message.take(10_000),
            throwableInfo = throwable?.toThrowableInfo(config.maxStackTraceLines),
            deviceInfo = deviceInfo,
            deviceId = effectiveDeviceId,
            sessionId = sessionManager.sessionId,
            userId = effectiveUserId,
            environment = config.environment,
            extra = resolvedExtra,
            traceId = traceId,
            variant = sessionVariant
        )
    }


    /**
     * Enriches ERROR/CRITICAL events with breadcrumbs and platform diagnostics.
     * Guarded to keep INFO/DEBUG paths zero-cost.
     */
    private fun enrichHighSeverityEvent(event: LogEvent): LogEvent {
        var enriched = event.extra ?: emptyMap<String, JsonElement>()
        val sizeBefore = enriched.size

        val crumbs = breadcrumbs
        if (crumbs != null && !crumbs.isEmpty()) {
            enriched = enriched + mapOf("breadcrumbs" to crumbs.snapshot())
        }
        // Capture as local val so Kotlin can smart-cast the nullable property inside the lambda.
        val snapshotFn = systemSnapshotProvider
        if (snapshotFn != null) {
            val snap = runCatching { snapshotFn.invoke() }.getOrElse { emptyMap() }
            if (snap.isNotEmpty()) {
                enriched = enriched + snap.mapValues { (_, v) -> JsonPrimitive(v) as JsonElement }
            }
        }
        return if (enriched.size != sizeBefore) event.copy(extra = enriched.ifEmpty { null }) else event
    }

}
