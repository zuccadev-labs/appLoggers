package com.applogger.test

import com.applogger.core.TransportResult
import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class FakeTransportTest {

    private val testDeviceInfo = DeviceInfo(
        brand = "Test", model = "Test", osVersion = "14",
        apiLevel = 34, platform = "ANDROID_MOBILE", appVersion = "1.0.0",
        appBuild = 1, isLowRamDevice = false, connectionType = "wifi"
    )

    private fun buildEvent() = LogEvent(
        id = "test-id", timestamp = 0L, level = LogLevel.INFO,
        tag = "TEST", message = "test", deviceInfo = testDeviceInfo,
        sessionId = "session"
    )

    @Test
    fun `successful transport records events`() = runTest {
        val transport = FakeTransport(shouldSucceed = true)
        val events = listOf(buildEvent(), buildEvent())

        val result = transport.send(events)

        assertIs<TransportResult.Success>(result)
        assertEquals(2, transport.sentEvents.size)
        assertEquals(1, transport.sendCallCount)
    }

    @Test
    fun `failed transport returns failure`() = runTest {
        val transport = FakeTransport(shouldSucceed = false, retryable = true)
        val result = transport.send(listOf(buildEvent()))

        assertIs<TransportResult.Failure>(result)
        assertTrue((result as TransportResult.Failure).retryable)
        assertEquals(0, transport.sentEvents.size)
    }

    @Test
    fun `throwing transport throws exception`() = runTest {
        val transport = FakeTransport(throwException = true)

        assertThrows(RuntimeException::class.java) {
            kotlinx.coroutines.runBlocking { transport.send(listOf(buildEvent())) }
        }
    }

    @Test
    fun `isAvailable always returns true`() {
        val transport = FakeTransport()
        assertTrue(transport.isAvailable())
    }

    @Test
    fun `clear resets state`() = runTest {
        val transport = FakeTransport(shouldSucceed = true)
        transport.send(listOf(buildEvent()))
        assertEquals(1, transport.sendCallCount)

        transport.clear()
        assertEquals(0, transport.sendCallCount)
        assertEquals(0, transport.sentEvents.size)
    }

    @Test
    fun `sendCallCount tracks number of send invocations`() = runTest {
        val transport = FakeTransport(shouldSucceed = true)
        transport.send(listOf(buildEvent()))
        transport.send(listOf(buildEvent(), buildEvent()))

        assertEquals(2, transport.sendCallCount)
        assertEquals(3, transport.sentEvents.size)
    }
}
