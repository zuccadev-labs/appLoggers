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
 */
internal class AppLoggerImpl(
    private val deviceInfo: DeviceInfo,
    private val sessionManager: SessionManager,
    private val filter: LogFilter,
    private val processor: BatchProcessor,
    private val config: AppLoggerConfig
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

    override fun flush() = processor.flush()

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

            // Convierte Map<String,Any> a Map<String,JsonElement> preservando tipos nativos.
            // Merge: globalExtra (lower priority) + perCall (higher priority).
            val resolvedExtra = mergeExtra(extra, globalExtra)
            val event = LogEvent(
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
                extra = resolvedExtra
            )

            if (!filter.passes(event)) return
            processor.enqueue(event)
        } catch (_: Exception) {
            // El SDK NUNCA lanza excepciones al caller
        }
    }
}
