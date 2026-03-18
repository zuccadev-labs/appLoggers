package com.applogger.core

import com.applogger.core.model.LogEvent

/**
 * Serializes [LogEvent]s to the format expected by a [LogTransport].
 *
 * Implement this to customize event serialization (e.g. protobuf, CSV, custom JSON).
 *
 * @see com.applogger.core.internal.JsonLogFormatter for the default JSON implementation.
 */
interface LogFormatter {

    /**
     * Serializes a single event.
     * @param event The event to serialize.
     * @return Serialized string representation.
     */
    fun format(event: LogEvent): String

    /**
     * Serializes a batch of events.
     * @param events Non-empty list of events.
     * @return Serialized string representation (e.g. JSON array).
     */
    fun formatBatch(events: List<LogEvent>): String
}
