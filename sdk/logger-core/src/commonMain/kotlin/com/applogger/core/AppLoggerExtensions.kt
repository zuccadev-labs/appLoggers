package com.applogger.core

/**
 * Kotlin extension functions for [AppLogger] that infer the tag from the calling class name.
 *
 * These helpers reduce boilerplate in classes that already hold a logger reference.
 * The tag is derived from the simple class name of the receiver.
 *
 * ## Usage
 * ```kotlin
 * class PlayerController(private val logger: AppLogger) {
 *     fun start() {
 *         logger.logI("PLAYER", "Playback started")
 *         logger.logW("PLAYER", "Buffer low", anomalyType = "BUFFER_LOW")
 *         logger.logE("PLAYER", "Playback failed", throwable = e)
 *     }
 * }
 * ```
 *
 * ## Against-the-receiver style (tag auto-inferred as class name)
 * ```kotlin
 * class AuthRepository(private val logger: AppLogger) {
 *     fun login() {
 *         this.logD(logger, "Login attempt")
 *         this.logE(logger, "Login failed", throwable = e)
 *     }
 * }
 * ```
 *
 * @see AppLogger for the full low-level API.
 */

// ─── Convenience methods  on AppLogger (no tag inference) ─────────────────────

/** Logs a debug message. Shorthand for [AppLogger.debug]. */
fun AppLogger.logD(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
    debug(tag, message, throwable, extra)

/** Logs an info message. Shorthand for [AppLogger.info]. */
fun AppLogger.logI(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
    info(tag, message, throwable, extra)

/** Logs a warning. Shorthand for [AppLogger.warn]. */
fun AppLogger.logW(
    tag: String,
    message: String,
    throwable: Throwable? = null,
    anomalyType: String? = null,
    extra: Map<String, Any>? = null
) = warn(tag, message, throwable, anomalyType, extra)

/** Logs an error. Shorthand for [AppLogger.error]. */
fun AppLogger.logE(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
    error(tag, message, throwable, extra)

/** Logs a critical/fatal event. Shorthand for [AppLogger.critical]. */
fun AppLogger.logC(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
    critical(tag, message, throwable, extra)

// ─── Tag-inferring extensions on Any ──────────────────────────────────────────

/**
 * Returns a safe tag derived from the simple class name of the receiver,
 * suitable for use as an [AppLogger] tag.
 *
 * Anonymous or lambda classes return `"Anonymous"`. Tags longer than 100
 * characters are truncated (SDK limit).
 */
fun Any.logTag(): String =
    this::class.simpleName?.take(100) ?: "Anonymous"

/** Logs a debug message, inferring the tag from the receiver's class name. */
fun Any.logD(logger: AppLogger, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
    logger.debug(logTag(), message, throwable, extra)

/** Logs an info message, inferring the tag from the receiver's class name. */
fun Any.logI(logger: AppLogger, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
    logger.info(logTag(), message, throwable, extra)

/** Logs a warning, inferring the tag from the receiver's class name. */
fun Any.logW(
    logger: AppLogger,
    message: String,
    throwable: Throwable? = null,
    anomalyType: String? = null,
    extra: Map<String, Any>? = null
) = logger.warn(logTag(), message, throwable, anomalyType, extra)

/** Logs an error, inferring the tag from the receiver's class name. */
fun Any.logE(logger: AppLogger, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
    logger.error(logTag(), message, throwable, extra)

/** Logs a critical event, inferring the tag from the receiver's class name. */
fun Any.logC(logger: AppLogger, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
    logger.critical(logTag(), message, throwable, extra)

// ─── Metric shorthands ────────────────────────────────────────────────────────

/**
 * Records a metric. Shorthand for [AppLogger.metric].
 *
 * ```kotlin
 * logger.logM("screen_load_time", 320.0, "ms", mapOf("screen" to "Home"))
 * ```
 */
fun AppLogger.logM(name: String, value: Double, unit: String = "count", tags: Map<String, String>? = null) =
    metric(name, value, unit, tags)

/**
 * Records a metric, inferring a context tag from the receiver's class name
 * and adding it automatically to the tags map.
 *
 * ```kotlin
 * class HomeScreen(private val logger: AppLogger) {
 *     fun onResume() {
 *         this.logM(logger, "screen_load_time", 320.0, "ms")
 *         // tags will include "source" -> "HomeScreen" automatically
 *     }
 * }
 * ```
 */
fun Any.logM(
    logger: AppLogger,
    name: String,
    value: Double,
    unit: String = "count",
    tags: Map<String, String>? = null
) {
    val enriched = buildMap {
        tags?.forEach { (k, v) -> put(k, v) }
        putIfAbsent("source", logTag())
    }
    logger.metric(name, value, unit, enriched)
}

// ─── Companion object helper ──────────────────────────────────────────────────

/**
 * Returns a tag string derived from the enclosing class of a companion object,
 * or from the class itself. Designed for use in companion objects:
 *
 * ```kotlin
 * class NetworkRepository(private val logger: AppLogger) {
 *     companion object {
 *         val TAG = loggerTag<NetworkRepository>()
 *     }
 *
 *     fun fetch() {
 *         logger.logI(TAG, "Fetching data")
 *     }
 * }
 * ```
 */
inline fun <reified T : Any> loggerTag(): String =
    T::class.simpleName?.take(100) ?: "Anonymous"
