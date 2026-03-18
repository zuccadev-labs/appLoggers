package com.applogger.core.internal

import com.applogger.core.LogFilter
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import kotlin.concurrent.Volatile

/**
 * Limitador de rate por tag con bypass automático para ERROR y CRITICAL.
 */
internal class RateLimitFilter(
    private val maxEventsPerMinutePerTag: Int = 60
) : LogFilter {

    private val counters = mutableMapOf<String, Int>()

    @Volatile
    private var lastResetTime: Long = 0L

    override fun passes(event: LogEvent): Boolean {
        // ERROR y CRITICAL siempre pasan (no usar >= porque METRIC tiene ordinal mayor)
        if (event.level == LogLevel.ERROR || event.level == LogLevel.CRITICAL) return true

        val now = com.applogger.core.currentTimeMillis()
        // Reset counters every minute
        if (now - lastResetTime > 60_000L) {
            counters.clear()
            lastResetTime = now
        }

        val count = counters.getOrPut(event.tag) { 0 }
        return if (count < maxEventsPerMinutePerTag) {
            counters[event.tag] = count + 1
            true
        } else {
            false
        }
    }
}
