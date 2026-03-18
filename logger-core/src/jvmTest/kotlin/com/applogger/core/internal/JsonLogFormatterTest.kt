package com.applogger.core.internal

import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonLogFormatterTest {

    private val formatter = JsonLogFormatter()
    private val testDeviceInfo = com.applogger.core.model.DeviceInfo(
        brand = "Google", model = "Pixel 8", osVersion = "14",
        apiLevel = 34, platform = "ANDROID_MOBILE", appVersion = "2.1.0",
        appBuild = 42, isLowRamDevice = false, connectionType = "wifi"
    )

    private fun buildEvent(
        level: LogLevel = LogLevel.ERROR,
        tag: String = "TEST",
        message: String = "Something failed"
    ) = LogEvent(
        id = "test-uuid-123",
        timestamp = 1700000000000L,
        level = level,
        tag = tag,
        message = message,
        deviceInfo = testDeviceInfo,
        sessionId = "session-abc"
    )

    @Test
    fun `format produces valid JSON with all fields`() {
        val event = buildEvent()
        val json = formatter.format(event)

        assertTrue(json.contains("\"id\":\"test-uuid-123\""))
        assertTrue(json.contains("\"level\":\"ERROR\""))
        assertTrue(json.contains("\"tag\":\"TEST\""))
        assertTrue(json.contains("\"message\":\"Something failed\""))
        assertTrue(json.contains("\"sessionId\":\"session-abc\""))
        assertTrue(json.contains("\"brand\":\"Google\""))
    }

    @Test
    fun `format includes sdkVersion`() {
        val json = formatter.format(buildEvent())
        assertTrue(json.contains("\"sdkVersion\""))
    }

    @Test
    fun `format handles null throwableInfo`() {
        val json = formatter.format(buildEvent())
        assertTrue(json.contains("\"throwableInfo\":null"))
    }

    @Test
    fun `format handles event with throwableInfo`() {
        val event = buildEvent().copy(
            throwableInfo = com.applogger.core.model.ThrowableInfo(
                type = "NullPointerException",
                message = "value is null",
                stackTrace = listOf("at com.example.Main.run(Main.kt:42)")
            )
        )
        val json = formatter.format(event)
        assertTrue(json.contains("NullPointerException"))
        assertTrue(json.contains("value is null"))
    }

    @Test
    fun `format handles event with extra data`() {
        val event = buildEvent().copy(extra = mapOf("key1" to "val1", "key2" to "val2"))
        val json = formatter.format(event)
        assertTrue(json.contains("\"key1\":\"val1\""))
        assertTrue(json.contains("\"key2\":\"val2\""))
    }

    @Test
    fun `formatBatch produces valid JSON array`() {
        val events = listOf(
            buildEvent(message = "msg1"),
            buildEvent(message = "msg2"),
            buildEvent(message = "msg3")
        )
        val json = formatter.formatBatch(events)
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("msg1"))
        assertTrue(json.contains("msg2"))
        assertTrue(json.contains("msg3"))
    }

    @Test
    fun `formatBatch with empty list produces empty array`() {
        val json = formatter.formatBatch(emptyList())
        assertEquals("[]", json)
    }

    @Test
    fun `format handles METRIC level`() {
        val json = formatter.format(buildEvent(level = LogLevel.METRIC, tag = "METRIC"))
        assertTrue(json.contains("\"level\":\"METRIC\""))
    }
}
