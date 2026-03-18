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
    private var userId: String? = null

    override fun debug(tag: String, message: String, extra: Map<String, Any>?) {
        process(LogLevel.DEBUG, tag, message, extra = extra)
    }

    override fun info(tag: String, message: String, extra: Map<String, Any>?) {
        process(LogLevel.INFO, tag, message, extra = extra)
    }

    override fun warn(tag: String, message: String, anomalyType: String?, extra: Map<String, Any>?) {
        val mergedExtra = buildMap {
            extra?.forEach { (k, v) -> put(k, v.toString()) }
            anomalyType?.let { put("anomaly_type", it) }
        }.ifEmpty { null }
        process(LogLevel.WARN, tag, message, extraStr = mergedExtra)
    }

    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        process(LogLevel.ERROR, tag, message, throwable, extra)
    }

    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        process(LogLevel.CRITICAL, tag, message, throwable, extra)
    }

    override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) {
        val extra = buildMap {
            put("metric_name", name)
            put("metric_value", value.toString())
            put("metric_unit", unit)
            tags?.forEach { (k, v) -> put(k, v) }
        }
        process(LogLevel.METRIC, "METRIC", "$name=$value $unit", extraStr = extra)
    }

    override fun flush() = processor.flush()

    fun setUserId(id: String) {
        userId = id
    }

    fun clearUserId() {
        userId = null
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

            val resolvedExtra = extraStr ?: extra?.mapValues { it.value.toString() }

            val event = LogEvent(
                id = generateUUID(),
                timestamp = currentTimeMillis(),
                level = level,
                tag = tag.take(100),
                message = message.take(10_000),
                throwableInfo = throwable?.toThrowableInfo(config.maxStackTraceLines),
                deviceInfo = deviceInfo,
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
