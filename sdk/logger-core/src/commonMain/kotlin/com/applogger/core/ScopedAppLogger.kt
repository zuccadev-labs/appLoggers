package com.applogger.core

/**
 * Lightweight [AppLogger] delegate that pre-merges a fixed, immutable set of contextual
 * attributes into every event dispatched through it.
 *
 * ## Why ScopedAppLogger instead of globalExtra?
 * [AppLogger.addGlobalExtra] pollutes the whole SDK instance — every concurrent
 * coroutine, feature module, or background job sees the same global context.
 * `ScopedAppLogger` is **isolated**: its attributes are invisible to other loggers
 * sharing the same underlying implementation.
 *
 * ## Priority chain (lowest → highest)
 * `globalExtra` → scope attributes → per-call `extra`
 *
 * Per-call values always win, scope attributes override global attributes.
 *
 * ## Consent override
 * Pass a [consentLevel] to tag every event from this scope with an explicit consent
 * requirement, overriding the default inference from log level. Useful for scopes
 * that handle strictly performance-only data regardless of event level.
 *
 * ## Usage
 * ```kotlin
 * // Create a scope for a specific playback session:
 * val log = AppLoggerSDK.newScope(
 *     "content_id"     to "movie_123",
 *     "stream_quality" to "4K",
 *     "drm_type"       to "widevine"
 * )
 * log.error("Player", "Codec failed", exception)
 * // ↳ event includes content_id, stream_quality, drm_type automatically
 *
 * // Child scope — inherits parent attributes, adds more:
 * val segLog = log.childScope("segment_id" to "seg_001", "offset_ms" to 72000)
 * segLog.warn("Segment", "Buffer underrun", anomalyType = "buffer_underrun")
 *
 * // Use with TaggedLogger for zero-boilerplate per-class logging:
 * private val log = AppLoggerSDK
 *     .newScope("content_id" to contentId)
 *     .withTag("PlayerController")
 * log.e("Stall detected", throwable = e)
 * ```
 *
 * Thread-safe: scope attributes are immutable after construction; delegate calls
 * are thread-safe according to the underlying [AppLogger] contract.
 *
 * @see AppLogger.newScope for the factory extension function.
 */
class ScopedAppLogger internal constructor(
    private val delegate: AppLogger,
    private val scopeExtra: Map<String, Any>,
    internal val consentLevel: ConsentLevel? = null
) : AppLogger by delegate {

    override fun debug(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        delegate.debug(tag, message, throwable, extra.mergedWithScope())

    override fun info(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        delegate.info(tag, message, throwable, extra.mergedWithScope())

    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?,
        anomalyType: String?,
        extra: Map<String, Any>?
    ) = delegate.warn(tag, message, throwable, anomalyType, extra.mergedWithScope())

    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        delegate.error(tag, message, throwable, extra.mergedWithScope())

    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        delegate.critical(tag, message, throwable, extra.mergedWithScope())

    /**
     * Creates a child scope that inherits all attributes from this scope and adds [pairs].
     * Child values override parent values on key collision.
     * The [consentLevel] is inherited from the parent scope by default.
     *
     * ```kotlin
     * val sessionLog = AppLoggerSDK.newScope("session_id" to sessionId)
     * val playerLog  = sessionLog.childScope("content_id" to contentId)
     * ```
     */
    fun childScope(vararg pairs: Pair<String, Any>, consentLevel: ConsentLevel? = this.consentLevel): ScopedAppLogger =
        ScopedAppLogger(delegate, scopeExtra + pairs.toMap(), consentLevel)

    // Merge order: scopeExtra (base) + consentLevel tag + per-call extra (wins on collision)
    private fun Map<String, Any>?.mergedWithScope(): Map<String, Any> {
        val withConsent: Map<String, Any> = if (consentLevel != null) {
            scopeExtra + mapOf(CONSENT_EXTRA_KEY to consentLevel.name.lowercase())
        } else {
            scopeExtra
        }
        return if (this == null) withConsent else withConsent + this
    }
}

/**
 * Creates a [ScopedAppLogger] that automatically merges [attributes] into every event.
 *
 * ```kotlin
 * val log = AppLoggerSDK.newScope(
 *     "content_id" to contentId,
 *     "user_tier"  to "premium"
 * )
 * log.error("Player", "Stall detected", stall.cause)
 * // → event includes content_id and user_tier without extra boilerplate
 * ```
 *
 * @see ScopedAppLogger for full documentation and priority semantics.
 */
fun AppLogger.newScope(vararg attributes: Pair<String, Any>, consentLevel: ConsentLevel? = null): ScopedAppLogger =
    ScopedAppLogger(this, attributes.toMap(), consentLevel)
