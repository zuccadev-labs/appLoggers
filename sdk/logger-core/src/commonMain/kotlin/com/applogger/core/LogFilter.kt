package com.applogger.core

import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel

/**
 * Decides whether a [LogEvent] should be processed or discarded.
 *
 * Filters are composable via [ChainedLogFilter].
 *
 * Implement this to add custom filtering logic (e.g. tag-based, sampling).
 *
 * @see com.applogger.core.internal.RateLimitFilter for the built-in rate limiter.
 * @see ChainedLogFilter for composing multiple filters.
 */
interface LogFilter {

    /**
     * @param event The candidate event.
     * @return `true` to process the event, `false` to discard it.
     */
    fun passes(event: LogEvent): Boolean
}

/**
 * Composes multiple [LogFilter]s with AND logic: an event passes only
 * if **all** filters return `true`.
 *
 * @property filters Ordered list of filters to apply.
 */
class ChainedLogFilter(private val filters: List<LogFilter>) : LogFilter {
    override fun passes(event: LogEvent): Boolean =
        filters.all { it.passes(event) }
}

/**
 * Filters events by tag — only events with a matching tag pass.
 * Supports both allowlist (include) and blocklist (exclude) modes.
 *
 * ```kotlin
 * // Only allow specific tags:
 * TagFilter(allowed = setOf("Auth", "Network", "Payment"))
 *
 * // Block noisy tags:
 * TagFilter(blocked = setOf("Heartbeat", "Analytics"))
 * ```
 *
 * When both [allowed] and [blocked] are provided, allowed takes precedence:
 * an event passes if its tag is in [allowed] AND not in [blocked].
 *
 * ERROR and CRITICAL events always pass regardless of tag filtering.
 */
class TagFilter(
    private val allowed: Set<String>? = null,
    private val blocked: Set<String>? = null
) : LogFilter {
    override fun passes(event: LogEvent): Boolean {
        if (event.level == LogLevel.ERROR || event.level == LogLevel.CRITICAL) return true
        if (blocked != null && event.tag in blocked) return false
        if (allowed != null) return event.tag in allowed
        return true
    }
}

/**
 * Probabilistic sampling filter — passes events at the given [rate].
 *
 * ```kotlin
 * // Keep 10% of events (all severities):
 * SamplingFilter(rate = 0.1)
 *
 * // Keep 50% of events but always pass errors:
 * SamplingFilter(rate = 0.5, alwaysPassErrors = true)
 * ```
 *
 * @param rate Sampling rate in [0.0, 1.0]. 1.0 = keep all, 0.0 = keep none.
 * @param alwaysPassErrors When true, ERROR and CRITICAL events bypass sampling.
 */
class SamplingFilter(
    private val rate: Double,
    private val alwaysPassErrors: Boolean = true
) : LogFilter {
    override fun passes(event: LogEvent): Boolean {
        if (alwaysPassErrors && (event.level == LogLevel.ERROR || event.level == LogLevel.CRITICAL)) {
            return true
        }
        return kotlin.random.Random.nextDouble() < rate
    }
}
