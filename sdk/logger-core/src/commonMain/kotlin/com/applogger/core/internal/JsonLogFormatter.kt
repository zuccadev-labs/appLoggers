package com.applogger.core.internal

import com.applogger.core.LogFormatter
import com.applogger.core.model.LogEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializa LogEvent a JSON usando kotlinx.serialization.
 *
 * En debug mode ([prettyPrint] = true) el JSON es legible en logcat.
 * En producción ([prettyPrint] = false) el JSON es compacto para minimizar payload.
 */
internal class JsonLogFormatter(private val prettyPrint: Boolean = false) : LogFormatter {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        this.prettyPrint = prettyPrint
    }

    override fun format(event: LogEvent): String {
        return json.encodeToString(event)
    }

    override fun formatBatch(events: List<LogEvent>): String {
        return json.encodeToString(events)
    }
}
