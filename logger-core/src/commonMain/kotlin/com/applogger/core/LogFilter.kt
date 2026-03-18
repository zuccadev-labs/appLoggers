package com.applogger.core

import com.applogger.core.model.LogEvent

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
