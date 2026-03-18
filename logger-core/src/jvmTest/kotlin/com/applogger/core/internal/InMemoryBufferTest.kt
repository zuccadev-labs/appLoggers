package com.applogger.core.internal

import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InMemoryBufferTest {

    private lateinit var buffer: InMemoryBuffer

    private fun buildEvent(
        level: LogLevel = LogLevel.INFO,
        tag: String = "TEST",
        message: String = "test message"
    ) = LogEvent(
        id = java.util.UUID.randomUUID().toString(),
        timestamp = System.currentTimeMillis(),
        level = level,
        tag = tag,
        message = message,
        deviceInfo = testDeviceInfo,
        sessionId = "test-session"
    )

    private val testDeviceInfo = DeviceInfo(
        brand = "Test",
        model = "TestDevice",
        osVersion = "14",
        apiLevel = 34,
        platform = "ANDROID_MOBILE",
        appVersion = "1.0.0",
        appBuild = 1,
        isLowRamDevice = false,
        connectionType = "wifi"
    )

    @BeforeEach
    fun setup() {
        buffer = InMemoryBuffer(maxCapacity = 5)
    }

    @Test
    fun `push adds event to buffer`() {
        val event = buildEvent()
        assertTrue(buffer.push(event))
        assertEquals(1, buffer.size())
    }

    @Test
    fun `drain returns all events and clears buffer`() {
        repeat(3) { buffer.push(buildEvent(message = "msg-$it")) }
        assertEquals(3, buffer.size())

        val drained = buffer.drain()
        assertEquals(3, drained.size)
        assertEquals(0, buffer.size())
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `FIFO overflow discards oldest event`() {
        // Capacity = 5, push 7 events
        repeat(7) { buffer.push(buildEvent(message = "msg-$it")) }

        assertEquals(5, buffer.size())
        val events = buffer.drain()
        // The first 2 (msg-0, msg-1) should have been discarded
        assertEquals("msg-2", events[0].message)
        assertEquals("msg-6", events[4].message)
    }

    @Test
    fun `peek returns events without clearing`() {
        repeat(3) { buffer.push(buildEvent()) }
        val peeked = buffer.peek()
        assertEquals(3, peeked.size)
        assertEquals(3, buffer.size()) // Still there
    }

    @Test
    fun `clear empties the buffer`() {
        repeat(3) { buffer.push(buildEvent()) }
        buffer.clear()
        assertEquals(0, buffer.size())
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `isEmpty returns true for new buffer`() {
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `drain on empty buffer returns empty list`() {
        val drained = buffer.drain()
        assertTrue(drained.isEmpty())
    }

    @Test
    fun `buffer handles concurrent access without crash`() {
        val threads = List(10) { threadIdx ->
            Thread {
                repeat(100) { i ->
                    buffer.push(buildEvent(message = "thread-$threadIdx-$i"))
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Buffer should have at most maxCapacity items
        assertTrue(buffer.size() <= 5)
    }
}
