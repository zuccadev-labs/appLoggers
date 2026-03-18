package com.applogger.core.internal

import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RateLimitFilterTest {

    private val testDeviceInfo = DeviceInfo(
        brand = "Test", model = "TestDevice", osVersion = "14",
        apiLevel = 34, platform = "ANDROID_MOBILE", appVersion = "1.0.0",
        appBuild = 1, isLowRamDevice = false, connectionType = "wifi"
    )

    private fun buildEvent(
        tag: String = "TEST",
        level: LogLevel = LogLevel.INFO
    ) = LogEvent(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = System.currentTimeMillis(),
        level = level,
        tag = tag,
        message = "test",
        deviceInfo = testDeviceInfo,
        sessionId = "test-session"
    )

    @Test
    fun `events within limit pass through`() {
        val filter = RateLimitFilter(maxEventsPerMinutePerTag = 3)
        repeat(3) {
            assertTrue(filter.passes(buildEvent()))
        }
    }

    @Test
    fun `events exceeding limit are blocked`() {
        val filter = RateLimitFilter(maxEventsPerMinutePerTag = 3)
        repeat(3) { filter.passes(buildEvent()) }
        assertFalse(filter.passes(buildEvent()))
    }

    @Test
    fun `ERROR events always pass regardless of rate limit`() {
        val filter = RateLimitFilter(maxEventsPerMinutePerTag = 1)
        // Exhaust the limit
        filter.passes(buildEvent(level = LogLevel.INFO))
        filter.passes(buildEvent(level = LogLevel.INFO))

        // ERROR should still pass
        assertTrue(filter.passes(buildEvent(level = LogLevel.ERROR)))
    }

    @Test
    fun `CRITICAL events always pass regardless of rate limit`() {
        val filter = RateLimitFilter(maxEventsPerMinutePerTag = 1)
        filter.passes(buildEvent(level = LogLevel.INFO))
        filter.passes(buildEvent(level = LogLevel.INFO))

        assertTrue(filter.passes(buildEvent(level = LogLevel.CRITICAL)))
    }

    @Test
    fun `different tags have independent rate limits`() {
        val filter = RateLimitFilter(maxEventsPerMinutePerTag = 2)

        assertTrue(filter.passes(buildEvent(tag = "TAG_A")))
        assertTrue(filter.passes(buildEvent(tag = "TAG_A")))
        assertFalse(filter.passes(buildEvent(tag = "TAG_A"))) // Blocked

        // TAG_B still has quota
        assertTrue(filter.passes(buildEvent(tag = "TAG_B")))
        assertTrue(filter.passes(buildEvent(tag = "TAG_B")))
    }

    @Test
    fun `WARN events are subject to rate limiting`() {
        val filter = RateLimitFilter(maxEventsPerMinutePerTag = 1)
        assertTrue(filter.passes(buildEvent(level = LogLevel.WARN)))
        assertFalse(filter.passes(buildEvent(level = LogLevel.WARN)))
    }

    @Test
    fun `DEBUG events are subject to rate limiting`() {
        val filter = RateLimitFilter(maxEventsPerMinutePerTag = 1)
        assertTrue(filter.passes(buildEvent(level = LogLevel.DEBUG)))
        assertFalse(filter.passes(buildEvent(level = LogLevel.DEBUG)))
    }

    @Test
    fun `METRIC events are subject to rate limiting`() {
        val filter = RateLimitFilter(maxEventsPerMinutePerTag = 1)
        assertTrue(filter.passes(buildEvent(level = LogLevel.METRIC)))
        assertFalse(filter.passes(buildEvent(level = LogLevel.METRIC)))
    }
}
