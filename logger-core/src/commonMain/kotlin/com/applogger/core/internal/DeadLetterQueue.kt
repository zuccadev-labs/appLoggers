package com.applogger.core.internal

import com.applogger.core.currentTimeMillis
import com.applogger.core.model.LogEvent

/**
 * In-memory dead letter queue for events that exhaust all retry attempts.
 *
 * Bounded to [maxCapacity] events (FIFO eviction). Thread-safe.
 */
internal class DeadLetterQueue(
    private val maxCapacity: Int = 200
) {
    private val queue = ArrayDeque<FailedEvent>(maxCapacity)

    /**
     * Adds a failed batch to the DLQ.
     * Oldest events are evicted if the queue is at capacity.
     */
    @Synchronized
    fun enqueue(events: List<LogEvent>, reason: String) {
        val timestamp = currentTimeMillis()
        events.forEach { event ->
            if (queue.size >= maxCapacity) {
                queue.removeFirst()
            }
            queue.addLast(FailedEvent(event, reason, timestamp))
        }
    }

    /** Returns all dead-lettered events and clears the queue. */
    @Synchronized
    fun drain(): List<FailedEvent> {
        val result = queue.toList()
        queue.clear()
        return result
    }

    /** Returns the current count of dead-lettered events. */
    @Synchronized
    fun size(): Int = queue.size

    /** Returns `true` if the DLQ is empty. */
    @Synchronized
    fun isEmpty(): Boolean = queue.isEmpty()

    data class FailedEvent(
        val event: LogEvent,
        val reason: String,
        val failedAt: Long
    )
}
