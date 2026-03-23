package com.applogger.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LogEventSerializationTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val testDeviceInfo = DeviceInfo(
        brand = "Google", model = "Pixel 8", osVersion = "14",
        apiLevel = 34, platform = "ANDROID_MOBILE", appVersion = "2.1.0",
        appBuild = 42, isLowRamDevice = false, connectionType = "wifi"
    )

    @Test
    fun `LogEvent serializes and deserializes correctly`() {
        val event = LogEvent(
            id = "uuid-123",
            timestamp = 1700000000000L,
            level = LogLevel.ERROR,
            tag = "PAYMENT",
            message = "Transaction failed",
            deviceInfo = testDeviceInfo,
            sessionId = "session-abc"
        )

        val encoded = json.encodeToString(event)
        val decoded = json.decodeFromString<LogEvent>(encoded)

        assertEquals(event.id, decoded.id)
        assertEquals(event.level, decoded.level)
        assertEquals(event.tag, decoded.tag)
        assertEquals(event.message, decoded.message)
        assertEquals(event.sessionId, decoded.sessionId)
    }

    @Test
    fun `LogEvent with throwableInfo serializes`() {
        val event = LogEvent(
            id = "uuid-456",
            timestamp = 1700000000000L,
            level = LogLevel.CRITICAL,
            tag = "AUTH",
            message = "Token expired",
            throwableInfo = ThrowableInfo(
                type = "IllegalStateException",
                message = "token is null",
                stackTrace = listOf("at com.example.Auth.refresh(Auth.kt:42)")
            ),
            deviceInfo = testDeviceInfo,
            sessionId = "session-def"
        )

        val encoded = json.encodeToString(event)
        assertTrue(encoded.contains("IllegalStateException"))
        assertTrue(encoded.contains("token is null"))

        val decoded = json.decodeFromString<LogEvent>(encoded)
        assertEquals("IllegalStateException", decoded.throwableInfo?.type)
    }

    @Test
    fun `LogEvent with extra data serializes`() {
        val event = LogEvent(
            id = "uuid-789",
            timestamp = 1700000000000L,
            level = LogLevel.INFO,
            tag = "PLAYER",
            message = "Started",
            deviceInfo = testDeviceInfo,
            sessionId = "session-ghi",
            extra = mapOf(
                "content_id" to JsonPrimitive("movie_123"),
                "quality" to JsonPrimitive("4K")
            )
        )

        val encoded = json.encodeToString(event)
        assertTrue(encoded.contains("movie_123"))
        assertTrue(encoded.contains("4K"))
    }

    @Test
    fun `LogLevel enum values are all serializable`() {
        LogLevel.entries.forEach { level ->
            val event = LogEvent(
                id = "test", timestamp = 0L, level = level,
                tag = "T", message = "m", deviceInfo = testDeviceInfo,
                sessionId = "s"
            )
            val encoded = json.encodeToString(event)
            assertTrue(encoded.contains("\"level\":\"${level.name}\""))
        }
    }

    @Test
    fun `DeviceInfo serializes completely`() {
        val encoded = json.encodeToString(testDeviceInfo)
        assertTrue(encoded.contains("\"brand\":\"Google\""))
        assertTrue(encoded.contains("\"model\":\"Pixel 8\""))
        assertTrue(encoded.contains("\"apiLevel\":34"))
        assertTrue(encoded.contains("\"isLowRamDevice\":false"))
    }

    @Test
    fun `DeviceInfo with isTV flag serializes`() {
        val tvDevice = testDeviceInfo.copy(isTV = true, platform = "ANDROID_TV")
        val encoded = json.encodeToString(tvDevice)
        assertTrue(encoded.contains("\"isTV\":true"))
        assertTrue(encoded.contains("\"platform\":\"ANDROID_TV\""))
    }

    @Test
    fun `ThrowableInfo with empty stackTrace serializes`() {
        val info = ThrowableInfo(type = "Exception", message = null, stackTrace = emptyList())
        val encoded = json.encodeToString(info)
        assertTrue(encoded.contains("\"stackTrace\":[]"))
    }

    @Test
    fun `LogEvent with userId serializes`() {
        val event = LogEvent(
            id = "uuid-user", timestamp = 0L, level = LogLevel.INFO,
            tag = "T", message = "m", deviceInfo = testDeviceInfo,
            sessionId = "s", userId = "anonymous-user-id"
        )
        val encoded = json.encodeToString(event)
        assertTrue(encoded.contains("anonymous-user-id"))
    }

    @Test
    fun `LogEvent with deviceId serializes`() {
        val event = LogEvent(
            id = "uuid-device", timestamp = 0L, level = LogLevel.INFO,
            tag = "T", message = "m", deviceInfo = testDeviceInfo,
            deviceId = "device-custom-001",
            sessionId = "s"
        )
        val encoded = json.encodeToString(event)
        assertTrue(encoded.contains("device-custom-001"))
    }

    @Test
    fun `LogEvent environment field serializes and defaults to production`() {
        val event = LogEvent(
            id = "uuid-env", timestamp = 0L, level = LogLevel.INFO,
            tag = "T", message = "m", deviceInfo = testDeviceInfo,
            sessionId = "s"
        )
        val encoded = json.encodeToString(event)
        assertTrue(encoded.contains("\"environment\":\"production\""))

        val decoded = json.decodeFromString<LogEvent>(encoded)
        assertEquals("production", decoded.environment)
    }

    @Test
    fun `LogEvent environment staging serializes correctly`() {
        val event = LogEvent(
            id = "uuid-staging", timestamp = 0L, level = LogLevel.INFO,
            tag = "T", message = "m", deviceInfo = testDeviceInfo,
            sessionId = "s", environment = "staging"
        )
        val encoded = json.encodeToString(event)
        assertTrue(encoded.contains("\"environment\":\"staging\""))
    }
}
