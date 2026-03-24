package com.applogger.core

import kotlin.concurrent.Volatile

private const val BYTES_PER_MB = 1_048_576.0
private const val MS_PER_SECOND = 1_000.0

/**
 * Generic span API for timing any operation and emitting a structured metric on completion.
 *
 * Create via [AppLogger.startTrace] — the extension function is the intended entry point.
 *
 * ```kotlin
 * val trace = AppLoggerSDK.startTrace("video_load", "content_id" to contentId)
 * trace.tag("quality", "4K").tag("drm", "widevine")
 *
 * // On success:
 * trace.end(mapOf("buffer_ms" to bufferTime))
 *
 * // On failure:
 * trace.endWithError(exception, failureReason = "drm_error")
 * ```
 *
 * Each [end]/[endWithError] call is guarded against double-end: subsequent calls are no-ops.
 *
 * @param logger          Underlying [AppLogger] used to emit the span metric/error.
 * @param traceId         UUID identifying this specific span instance.
 * @param operationName   Short snake_case name for the operation (e.g. "video_load").
 * @param startTimeMs     Epoch-millis when the span started (from [currentTimeMillis]).
 * @param initialAttributes Optional key-value context captured at span start.
 */
class OperationTrace(
    private val logger: AppLogger,
    val traceId: String,
    val operationName: String,
    private val startTimeMs: Long,
    initialAttributes: Map<String, Any> = emptyMap()
) {
    private val attributes: MutableMap<String, Any> = initialAttributes.toMutableMap()

    @Volatile private var bytesCount: Long = 0L
    @Volatile private var ended: Boolean = false

    /** Attaches a key-value attribute to this span. Fluent — returns `this`. */
    fun tag(key: String, value: Any): OperationTrace {
        if (!ended) attributes[key] = value
        return this
    }

    /** Records bytes transferred, enabling auto-computation of `throughput_mbps` on [end]. */
    fun bytes(count: Long): OperationTrace {
        if (!ended) bytesCount = count
        return this
    }

    /**
     * Ends the span successfully and emits a `trace.<operationName>` metric.
     * Automatically computes `duration_ms`, and `throughput_mbps` / `bytes_transferred`
     * when [bytes] was called.
     *
     * @param extraAttributes Additional attributes merged at end time.
     */
    fun end(extraAttributes: Map<String, Any>? = null) {
        if (ended) return
        ended = true
        val durationMs = currentTimeMillis() - startTimeMs
        val tags = buildFinalTags(durationMs, extraAttributes, success = true)
        logger.metric("trace.$operationName", durationMs.toDouble(), "ms", tags.mapValues { it.value.toString() })
    }

    /**
     * Ends the span with an error, emitting an ERROR-level log event with full context.
     *
     * @param error          The exception that caused the failure.
     * @param failureReason  Optional human-readable classification (e.g. "timeout", "drm_error").
     * @param extraAttributes Additional attributes merged at end time.
     */
    fun endWithError(
        error: Throwable,
        failureReason: String? = null,
        extraAttributes: Map<String, Any>? = null
    ) {
        if (ended) return
        ended = true
        val durationMs = currentTimeMillis() - startTimeMs
        val extra = buildFinalTags(durationMs, extraAttributes, success = false).toMutableMap<String, Any>()
        if (failureReason != null) extra["failure_reason"] = failureReason
        logger.error(
            tag = "Trace.$operationName",
            message = "trace.$operationName failed after ${durationMs}ms",
            throwable = error,
            extra = extra
        )
    }

    private fun buildFinalTags(
        durationMs: Long,
        extraAttributes: Map<String, Any>?,
        success: Boolean
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        result["duration_ms"] = durationMs
        result["trace_id"] = traceId
        result["success"] = success
        result.putAll(attributes)
        if (bytesCount > 0L) {
            result["bytes_transferred"] = bytesCount
            val seconds = durationMs / MS_PER_SECOND
            if (seconds > 0) {
                result["throughput_mbps"] = (bytesCount / BYTES_PER_MB / seconds)
            }
        }
        if (extraAttributes != null) result.putAll(extraAttributes)
        return result
    }
}

/**
 * Starts a new [OperationTrace] span for [operation].
 *
 * ```kotlin
 * val trace = AppLoggerSDK.startTrace("network_request", "endpoint" to url)
 * // ... perform operation ...
 * trace.end()
 * ```
 */
fun AppLogger.startTrace(operation: String, vararg attributes: Pair<String, Any>): OperationTrace =
    OperationTrace(
        logger = this,
        traceId = generateUUID(),
        operationName = operation,
        startTimeMs = currentTimeMillis(),
        initialAttributes = attributes.toMap()
    )
