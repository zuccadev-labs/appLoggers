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
internal class AppLoggerImpl(
    private val deviceInfo: DeviceInfo,
    private val sessionManager: SessionManager,
    private val filter: LogFilter,
    private val processor: BatchProcessor,
    private val config: AppLoggerConfig,
    private val systemSnapshotProvider: (() -> Map<String, String>)? = null
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

    fun setDeviceId(id: String) {
        val normalized = id.trim()
        if (normalized.isNotEmpty()) {
            deviceId = normalized
        }
    }

    fun clearDeviceId() {
        deviceId = defaultDeviceId(deviceInfo)
    }

    /** Sets the distributed trace ID attached to all subsequent events. */
    fun setTraceId(id: String) {
        traceId = id.trim().ifBlank { null }
    }

    /** Clears the distributed trace ID. */
    fun clearTraceId() {
        traceId = null
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
     * Converts per-call Map<String, Any> to Map<String, JsonElement> and merges
     * with globalExtra. Per-call values win on key collision.
     * Returns null if both sources are empty.
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

    private fun process(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        extra: Map<String, Any>? = null
    ) {
        try {
            // DEBUG suprimido en producción
            if (level == LogLevel.DEBUG && !config.isDebugMode) return

            // minLevel: descarta eventos por debajo del umbral configurado.
            // METRIC siempre pasa (ordinal 5 > cualquier minLevel).
            if (level != LogLevel.METRIC) {
                val eventOrdinal = LEVEL_ORDINAL[level] ?: 0
                val minOrdinal = MIN_LEVEL_ORDINAL[config.minLevel] ?: 0
                if (eventOrdinal < minOrdinal) return
            }

            // Logcat/consoleOutput solo cuando isDebugMode está activo.
            // APPLOGGER_DEBUG=true activa isDebugMode en AppLoggerSDK.initialize().
            if (config.isDebugMode && config.consoleOutput) {
                platformLog("AppLogger/$tag", "[${level.name}] $message")
            }

            // Pillar 1 — Auto-flattening del Throwable:
            // Si hay excepción y no se pasó anomaly_type explícito, lo inferimos del nivel.
            // Esto garantiza que la columna anomaly_type en Supabase nunca quede vacía
            // cuando hay un error real, sin forzar al desarrollador a recordarlo.
            val effectiveExtra: Map<String, Any>? = if (
                throwable != null && extra?.containsKey("anomaly_type") != true
            ) {
                val autoAnomalyType = when (level) {
                    LogLevel.CRITICAL -> "critical"
                    LogLevel.ERROR    -> "error"
                    LogLevel.WARN     -> "warn"
                    else              -> null
                }
                if (autoAnomalyType != null) {
                    (extra ?: emptyMap()) + mapOf("anomaly_type" to autoAnomalyType)
                } else extra
            } else extra

            // Convierte Map<String,Any> a Map<String,JsonElement> preservando tipos nativos.
            // Merge: globalExtra (lower priority) + perCall (higher priority).
            val resolvedExtra = mergeExtra(effectiveExtra, globalExtra)
            var event = LogEvent(
                id = generateUUID(),
                timestamp = currentTimeMillis(),
                level = level,
                tag = tag.take(100),
                message = message.take(10_000),
                throwableInfo = throwable?.toThrowableInfo(config.maxStackTraceLines),
                deviceInfo = deviceInfo,
                deviceId = deviceId,
                sessionId = sessionManager.sessionId,
                userId = userId,
                environment = config.environment,
                extra = resolvedExtra,
                traceId = traceId
            )

            if (!filter.passes(event)) return

            // High-severity enrichment: breadcrumbs + system snapshot only for ERROR/CRITICAL.
            // These operations are guarded to keep debug/info paths zero-cost.
            if (level == LogLevel.ERROR || level == LogLevel.CRITICAL) {
                var enriched: Map<String, JsonElement> = event.extra ?: emptyMap()
                val sizeBefore = enriched.size

                // Auto-attach user interaction breadcrumbs — "what did the user do before the crash?"
                val crumbs = breadcrumbs
                if (crumbs != null && !crumbs.isEmpty()) {
                    enriched = enriched + mapOf("breadcrumbs" to crumbs.snapshot())
                }

                // Auto-capture platform diagnostics — thermal, memory, network at error time.
                if (systemSnapshotProvider != null) {
                    val snapshot = runCatching { systemSnapshotProvider.invoke() }.getOrElse { emptyMap() }
                    if (snapshot.isNotEmpty()) {
                        val snapJson: Map<String, JsonElement> = snapshot.mapValues { (_, v) -> JsonPrimitive(v) }
                        enriched = enriched + snapJson
                    }
                }

                // Only copy the event if enrichment actually added keys (avoids allocation).
                if (enriched.size != sizeBefore) {
                    event = event.copy(extra = enriched.ifEmpty { null })
                }
            }

            // Deduplication: collapses identical error bursts into one event with occurrence_count=N.
            // When debouncer is disabled (null) events pass through unchanged.
            // When enabled, returns null to suppress duplicates within the active window.
            if (debouncer != null) {
                val deduped = debouncer.process(event) ?: return
                processor.enqueue(deduped)
            } else {
                processor.enqueue(event)
            }
        } catch (_: Exception) {
            // El SDK NUNCA lanza excepciones al caller
        }
    }
}
