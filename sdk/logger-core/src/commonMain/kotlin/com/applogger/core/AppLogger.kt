package com.applogger.core

/**
 * Public contract for the AppLogger SDK.
 *
 * All implementations guarantee:
 * - No call blocks the caller's thread.
 * - No call throws exceptions to the caller.
 * - [debug] calls are no-ops in production mode.
 *
 * ## Usage
 * ```kotlin
 * logger.info("PlayerScreen", "Video started", mapOf("content_id" to "movie_123"))
 * logger.error("Network", "Request failed", exception)
 * logger.metric("frame_drop", 3.0, "count")
 * ```
 *
 * @see AppLoggerConfig for SDK configuration.
 * @see AppLoggerSDK for the Android entry point.
 */
interface AppLogger {

    /**
     * Logs a debug-level message. Suppressed in production (`isDebugMode = false`).
     *
     * @param tag   Short identifier for the source (e.g. class or screen name).
     * @param message Human-readable description.
     * @param throwable Optional exception whose stack trace will be captured.
     * @param extra Optional key-value metadata. Values of type [Int], [Long], [Double], and
     *              [Boolean] are preserved as native JSON primitives in Supabase JSONB, enabling
     *              richer queries (e.g. `extra->>'retry_count' > 2`). Other types are stringified.
     */
    fun debug(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)

    /**
     * Logs an informational message.
     *
     * @param tag   Short identifier for the source.
     * @param message Human-readable description.
     * @param throwable Optional exception whose stack trace will be captured.
     * @param extra Optional key-value metadata. Values of type [Int], [Long], [Double], and
     *              [Boolean] are preserved as native JSON primitives in Supabase JSONB.
     */
    fun info(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)

    /**
     * Logs a warning. Use for recoverable anomalies.
     *
     * @param tag   Short identifier for the source.
     * @param message Human-readable description.
     * @param throwable Optional exception whose stack trace will be captured.
     * @param anomalyType Optional classification of the anomaly (e.g. "slow_response").
     * @param extra Optional key-value metadata. Values of type [Int], [Long], [Double], and
     *              [Boolean] are preserved as native JSON primitives in Supabase JSONB.
     */
    fun warn(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        anomalyType: String? = null,
        extra: Map<String, Any>? = null
    )

    /**
     * Logs an error. Triggers immediate batch flush.
     *
     * @param tag   Short identifier for the source.
     * @param message Human-readable description.
     * @param throwable Optional exception whose stack trace will be captured.
     * @param extra Optional key-value metadata. Values of type [Int], [Long], [Double], and
     *              [Boolean] are preserved as native JSON primitives in Supabase JSONB.
     */
    fun error(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)

    /**
     * Logs a critical/fatal error. Triggers immediate batch flush.
     *
     * @param tag   Short identifier for the source.
     * @param message Human-readable description.
     * @param throwable Optional exception whose stack trace will be captured.
     * @param extra Optional key-value metadata. Values of type [Int], [Long], [Double], and
     *              [Boolean] are preserved as native JSON primitives in Supabase JSONB.
     */
    fun critical(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)

    /**
     * Records a numeric metric event. Stored in the `app_metrics` table.
     *
     * @param name  Metric identifier (e.g. "screen_load_time", "frame_drop").
     * @param value Numeric measurement.
     * @param unit  Unit of measurement (e.g. "ms", "count", "bytes").
     * @param tags  Optional contextual tags (e.g. screen name, content type).
     */
    fun metric(name: String, value: Double, unit: String, tags: Map<String, String>? = null)

    /**
     * Forces an immediate flush of buffered events to the transport.
     * Call this before the app goes to background or terminates.
     */
    fun flush()
}
