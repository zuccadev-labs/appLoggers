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
        val mergedExtra = buildMap {
            extra?.forEach { (k, v) -> put(k, v.toString()) }
            anomalyType?.let { put("anomaly_type", it) }
        }.ifEmpty { null }
        process(LogLevel.WARN, tag, message, throwable, extraStr = mergedExtra)
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

            // Auto-enrich tags with device context the SDK already has.
            // Developers don't need to pass platform/app_version/device_model manually.
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

    private fun process(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        extra: Map<String, Any>? = null,
        extraStr: Map<String, String>? = null
    ) {
        try {
            // Guard: debug events descartados en producción
            if (level == LogLevel.DEBUG && !config.isDebugMode) return

            // Consola en modo debug
            if (config.isDebugMode && config.consoleOutput) {
                platformLog("AppLogger/$tag", "[${level.name}] $message")
            }

            // Preserve native types (Int, Double, Boolean) — only stringify unknown types.
            // This allows richer JSONB queries in Supabase (e.g. extra->>'retry_count' > 2).
            val resolvedExtra: Map<String, String>? = when {
                extraStr != null -> extraStr
                extra != null -> extra.mapValues { (_, v) ->
                    when (v) {
                        is String  -> v
                        is Number  -> v.toString()
                        is Boolean -> v.toString()
                        else       -> v.toString()
                    }
                }
                else -> null
            }

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

            // Aplicar filtros
            if (!filter.passes(event)) return

            // Entregar al procesador (non-blocking)
            processor.enqueue(event)
        } catch (_: Exception) {
            // El SDK NUNCA lanza excepciones al caller
        }
    }
}
