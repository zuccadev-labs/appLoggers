package com.applogger.core.internal

import com.applogger.core.currentTimeMillis
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import kotlinx.serialization.json.JsonPrimitive

private val DEDUP_LEVELS = setOf(LogLevel.WARN, LogLevel.ERROR, LogLevel.CRITICAL)

/**
 * Deduplication engine that collapses identical high-frequency errors into a single
 * enriched event with [FIELD_OCCURRENCE_COUNT] = N.
 *
 * ## Problem solved
 * High-frequency event loops (e.g. playback ticks, sync polling) can flood the backend
 * with thousands of identical errors in seconds. This class detects those patterns and
 * emits a single representative event with `occurrence_count = N`, dramatically reducing
 * backend write costs while preserving full diagnostic context.
 *
 * ## Dedup key
 * Two events are considered identical when they share:
 * `level | tag | message (first 200 chars) | throwable type`
 *
 * Only [DEDUP_LEVELS] events (WARN/ERROR/CRITICAL) with a non-null [com.applogger.core.model.ThrowableInfo]
 * are candidates. All other events (INFO, DEBUG, METRIC, throwable-free events) pass through unchanged.
 *
 * ## Window semantics
 * - First occurrence of a key → passes through, starts [windowMs]-length window.
 * - Duplicate within window → suppressed (returns `null`), counter incremented.
 * - New event after window expires → emits *aggregated* first event (with occurrence_count),
 *   starts a fresh window for the new event.
 *
 * Call [drainAggregated] on SDK flush/shutdown to flush pending counts before process exit.
 *
 * Thread-safe via [platformSynchronized].
 *
 * @param windowMs Deduplication window length in milliseconds (e.g. `10_000` = 10 s).
 */
internal class EventDebouncer(private val windowMs: Long) {

    companion object {
        /** Key injected into the aggregated event's extra map. Value is an Int JSON primitive. */
        const val FIELD_OCCURRENCE_COUNT = "occurrence_count"
    }

    private data class Entry(
        val firstEvent: LogEvent,
        val windowStart: Long,
        var count: Int = 1
    )

    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()

    /**
     * Processes a single event through the deduplication pipeline.
     *
     * @return
     *   - The event itself (possibly enriched) on first occurrence or expired window.
     *   - `null` when the event is a duplicate within the active window.
     */
    fun process(event: LogEvent): LogEvent? {
        // Only deduplicate thrown exceptions at error/warn severity.
        // Everything else (INFO, DEBUG, METRIC, no-throwable events) is inherently unique.
        if (event.level !in DEDUP_LEVELS || event.throwableInfo == null) return event

        val key = buildKey(event)
        val now = currentTimeMillis()

        return platformSynchronized(lock) {
            val entry = entries[key]
            when {
                entry == null -> {
                    // First occurrence — pass through, start tracking.
                    entries[key] = Entry(firstEvent = event, windowStart = now)
                    event
                }
                now - entry.windowStart > windowMs -> {
                    // Window expired — emit aggregated summary, reset window for new event.
                    val aggregated = entry.buildAggregated()
                    entries[key] = Entry(firstEvent = event, windowStart = now)
                    aggregated
                }
                else -> {
                    // Within active window — suppress duplicate, increment counter.
                    entry.count++
                    null
                }
            }
        }
    }

    /**
     * Emits aggregated events for all active windows and clears internal state.
     *
     * Should be called during [com.applogger.core.AppLogger.flush] and SDK shutdown to
     * guarantee occurrence_count is never silently discarded.
     *
     * @return List of aggregated events (one per dedup key that has count > 1). May be empty.
     */
    fun drainAggregated(): List<LogEvent> = platformSynchronized(lock) {
        val result = entries.values
            .filter { it.count > 1 }
            .map { it.buildAggregated() }
        entries.clear()
        result
    }

    private fun buildKey(event: LogEvent): String =
        "${event.level}|${event.tag}|${event.message.take(200)}|${event.throwableInfo?.type}"

    private fun Entry.buildAggregated(): LogEvent {
        if (count <= 1) return firstEvent
        val enriched = (firstEvent.extra ?: emptyMap()) +
            mapOf(FIELD_OCCURRENCE_COUNT to JsonPrimitive(count))
        return firstEvent.copy(extra = enriched)
    }
}
