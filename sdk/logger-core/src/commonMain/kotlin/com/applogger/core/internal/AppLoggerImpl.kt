package com.applogger.core.internal

import com.applogger.core.*
import com.applogger.core.model.*
import kotlin.concurrent.Volatile

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
    // Reads are lock-free (volatile snapshot). Writes replace the reference atomically.
    @Volatile
    private var globalExtra: Map<String, String> = emptyMap()

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
        // Merge anomalyType into extra as Map<String, Any> so native types are preserved
        // through the same path as all other levels — no premature toString() here.
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
     * Copy-on-write: reads are always lock-free.
     */
    override fun addGlobalExtra(key: String, value: String) {
        globalExtra = globalExtra + (key to value)
    }

    override fun removeGlobalExtra(key: String) {
        globalExtra = globalExtra - key
    }

    override fun clearGlobalExtra() {
        globalExtra = emptyMap()
    }

    /**
     * Stringifies per-call extra and merges with globalExtra.
     * globalExtra has lower priority — per-call values win on key collision.
     */
    private fun mergeExtra(
        extra: Map<String, Any>?,
        global: Map<String, String>
    ): Map<String, String>? {
        val perCall = extra?.mapValues { (_, v) ->
            when (v) {
                is String  -> v
                is Number  -> v.toString()
                is Boolean -> v.toString()
                else       -> v.toString()
            }
        }
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
            if (level == LogLevel.DEBUG && !config.isDebugMode) return

            if (config.isDebugMode && config.consoleOutput) {
                platformLog("AppLogger/$tag", "[${level.name}] $message")
            }

            // Stringify per-call extra, preserving numeric/boolean representation.
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
