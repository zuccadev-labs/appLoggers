package com.applogger.core

import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChainedLogFilterTest {

    private val testDeviceInfo = com.applogger.core.model.DeviceInfo(
        brand = "Test", model = "TestDevice", osVersion = "14",
        apiLevel = 34, platform = "ANDROID_MOBILE", appVersion = "1.0.0",
        appBuild = 1, isLowRamDevice = false, connectionType = "wifi"
    )

    private fun buildEvent(level: LogLevel = LogLevel.INFO) = LogEvent(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = System.currentTimeMillis(),
        level = level,
        tag = "TEST",
        message = "test",
        deviceInfo = testDeviceInfo,
        sessionId = "session-123"
    )

    @Test
    fun `empty chain passes all events`() {
        val chain = ChainedLogFilter(emptyList())
        assertTrue(chain.passes(buildEvent()))
    }

    @Test
    fun `single passing filter allows event`() {
        val chain = ChainedLogFilter(listOf(AllowAllFilter()))
        assertTrue(chain.passes(buildEvent()))
    }

    @Test
    fun `single blocking filter blocks event`() {
        val chain = ChainedLogFilter(listOf(BlockAllFilter()))
        assertFalse(chain.passes(buildEvent()))
    }

    @Test
    fun `all filters must pass for event to go through`() {
        val chain = ChainedLogFilter(listOf(AllowAllFilter(), BlockAllFilter()))
        assertFalse(chain.passes(buildEvent()))
    }

    @Test
    fun `multiple passing filters allow event`() {
        val chain = ChainedLogFilter(listOf(AllowAllFilter(), AllowAllFilter(), AllowAllFilter()))
        assertTrue(chain.passes(buildEvent()))
    }

    private class AllowAllFilter : LogFilter {
        override fun passes(event: LogEvent): Boolean = true
    }

    private class BlockAllFilter : LogFilter {
        override fun passes(event: LogEvent): Boolean = false
    }
}
