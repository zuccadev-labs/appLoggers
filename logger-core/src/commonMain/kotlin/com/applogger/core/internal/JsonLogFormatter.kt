package com.applogger.core.internal

import com.applogger.core.LogFormatter
import com.applogger.core.model.LogEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializa LogEvent a JSON usando kotlinx.serialization.
 */
internal class JsonLogFormatter : LogFormatter {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    override fun format(event: LogEvent): String {
        return json.encodeToString(event)
    }

    override fun formatBatch(events: List<LogEvent>): String {
        return json.encodeToString(events)
    }
}
