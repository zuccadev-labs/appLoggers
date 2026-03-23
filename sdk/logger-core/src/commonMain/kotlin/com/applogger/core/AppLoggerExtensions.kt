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
    val sourceTag = logTag() // capture before entering buildMap lambda
    val enriched = buildMap {
        tags?.forEach { (k, v) -> put(k, v) }
        putIfAbsent("source", sourceTag)
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

// ─── Tagged logger wrapper ────────────────────────────────────────────────────

/**
 * A thin wrapper around [AppLogger] that fixes a [tag] for all calls.
 * Eliminates tag repetition in classes that always log under the same tag.
 *
 * ```kotlin
 * class PaymentRepository(logger: AppLogger) {
 *     private val log = logger.withTag("PaymentRepository")
 *
 *     fun charge() {
 *         log.i("Charging card")          // tag = "PaymentRepository"
 *         log.e("Charge failed", ex)      // tag = "PaymentRepository"
 *         log.metric("charge_latency", 120.0, "ms")
 *     }
 * }
 * ```
 */
class TaggedLogger internal constructor(
    private val delegate: AppLogger,
    val tag: String
) {
    fun d(message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
        delegate.debug(tag, message, throwable, extra)

    fun i(message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
        delegate.info(tag, message, throwable, extra)

    fun w(message: String, throwable: Throwable? = null, anomalyType: String? = null, extra: Map<String, Any>? = null) =
        delegate.warn(tag, message, throwable, anomalyType, extra)

    fun e(message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
        delegate.error(tag, message, throwable, extra)

    fun c(message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null) =
        delegate.critical(tag, message, throwable, extra)

    fun metric(name: String, value: Double, unit: String = "count", tags: Map<String, String>? = null) =
        delegate.metric(name, value, unit, tags)

    fun flush() = delegate.flush()
}

/**
 * Creates a [TaggedLogger] that fixes [tag] for all subsequent calls.
 *
 * ```kotlin
 * private val log = logger.withTag("NetworkClient")
 * log.i("Request sent")
 * log.e("Request failed", exception)
 * ```
 */
fun AppLogger.withTag(tag: String): TaggedLogger = TaggedLogger(this, tag)

/**
 * Creates a [TaggedLogger] inferring the tag from the receiver's class name.
 *
 * ```kotlin
 * class AuthViewModel(logger: AppLogger) {
 *     private val log = logger.withTag(this)   // tag = "AuthViewModel"
 * }
 * ```
 */
fun AppLogger.withTag(receiver: Any): TaggedLogger = TaggedLogger(this, receiver.logTag())

// ─── Timed metric block ───────────────────────────────────────────────────────

/**
 * Measures the wall-clock execution time of [block] and records it as a metric.
 * The result of [block] is returned transparently.
 *
 * ```kotlin
 * val user = logger.timed("db_query_user", "ms", mapOf("table" to "users")) {
 *     userDao.findById(id)
 * }
 * ```
 *
 * @param name  Metric name (e.g. "api_call_latency").
 * @param unit  Time unit label (default: "ms").
 * @param tags  Optional contextual tags.
 * @param block The block to measure.
 * @return The result of [block].
 */
inline fun <T> AppLogger.timed(
    name: String,
    unit: String = "ms",
    tags: Map<String, String>? = null,
    block: () -> T
): T {
    val start = currentTimeMillis()
    val result = block()
    metric(name, (currentTimeMillis() - start).toDouble(), unit, tags)
    return result
}

/**
 * Same as [AppLogger.timed] but infers a "source" tag from the receiver's class name.
 *
 * ```kotlin
 * class SearchRepository(private val logger: AppLogger) {
 *     suspend fun search(query: String) = this.timed(logger, "search_latency") {
 *         api.search(query)   // "source" -> "SearchRepository" added automatically
 *     }
 * }
 * ```
 */
inline fun <T> Any.timed(
    logger: AppLogger,
    name: String,
    unit: String = "ms",
    tags: Map<String, String>? = null,
    block: () -> T
): T {
    val sourceTag = logTag()
    val start = currentTimeMillis()
    val result = block()
    val enriched = buildMap<String, String> {
        tags?.forEach { (k, v) -> put(k, v) }
        putIfAbsent("source", sourceTag)
    }
    logger.metric(name, (currentTimeMillis() - start).toDouble(), unit, enriched)
    return result
}

// ─── Safe execution with auto-logging ────────────────────────────────────────

/**
 * Executes [block] and returns its result, or `null` if an exception is thrown.
 * Exceptions are automatically logged as errors — no try/catch boilerplate needed.
 *
 * ```kotlin
 * val result = logger.logCatching("NetworkClient", "fetch user") {
 *     api.getUser(id)
 * }
 * // result is null if the call threw; the exception is already logged
 * ```
 *
 * @param tag     Log tag for the error event.
 * @param context Human-readable description of what was attempted (used in the log message).
 * @param extra   Optional metadata attached to the error event.
 * @param block   The block to execute safely.
 * @return The result of [block], or `null` on exception.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> AppLogger.logCatching(
    tag: String,
    context: String = "operation",
    extra: Map<String, Any>? = null,
    block: () -> T
): T? = try {
    block()
} catch (e: Exception) {
    error(tag, "$context failed: ${e.message}", e, extra)
    null
}

/**
 * Same as [AppLogger.logCatching] but infers the tag from the receiver's class name.
 *
 * ```kotlin
 * class OrderRepository(private val logger: AppLogger) {
 *     fun submit(order: Order) = this.logCatching(logger, "submit order") {
 *         api.submitOrder(order)
 *     }
 * }
 * ```
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> Any.logCatching(
    logger: AppLogger,
    context: String = "operation",
    extra: Map<String, Any>? = null,
    block: () -> T
): T? = try {
    block()
} catch (e: Exception) {
    logger.error(logTag(), "$context failed: ${e.message}", e, extra)
    null
}
