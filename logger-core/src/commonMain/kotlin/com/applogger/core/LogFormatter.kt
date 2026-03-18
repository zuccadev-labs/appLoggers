package com.applogger.core

import com.applogger.core.model.LogEvent

/**
 * Serializa un [LogEvent] al formato requerido por el [LogTransport].
 */
interface LogFormatter {
    fun format(event: LogEvent): String
    fun formatBatch(events: List<LogEvent>): String
}
