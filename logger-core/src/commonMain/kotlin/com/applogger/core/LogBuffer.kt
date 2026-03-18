package com.applogger.core

import com.applogger.core.model.LogEvent

/**
 * Temporary storage for [LogEvent]s before transport delivery.
 *
 * **Thread-safety contract:**
 * - [push] must be thread-safe (concurrent calls from multiple coroutines).
 * - [push] returns `false` if the event was discarded (overflow policy).
 * - [drain] atomically returns all events and clears the buffer.
 *
 * @see com.applogger.core.internal.InMemoryBuffer for the default FIFO implementation.
 */
interface LogBuffer {

    /** Adds an event to the buffer. Returns `false` if discarded due to overflow. */
    fun push(event: LogEvent): Boolean

    /** Atomically returns all buffered events and clears the buffer. */
    fun drain(): List<LogEvent>

    /** Returns a snapshot of buffered events without clearing. */
    fun peek(): List<LogEvent>

    /** Returns the current number of buffered events. */
    fun size(): Int

    /** Discards all buffered events. */
    fun clear()

    /** Returns `true` if the buffer contains no events. */
    fun isEmpty(): Boolean = size() == 0
}
