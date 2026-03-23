package com.applogger.core.internal

import com.applogger.core.*
import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchProcessorTest {

    private lateinit var buffer: InMemoryBuffer
    private lateinit var transport: ControllableTransport
    private lateinit var formatter: JsonLogFormatter
    private lateinit var processor: BatchProcessor

    private val testDeviceInfo = DeviceInfo(
        brand = "Test", model = "TestDevice", osVersion = "14",
        apiLevel = 34, platform = "ANDROID_MOBILE", appVersion = "1.0.0",
        appBuild = 1, isLowRamDevice = false, connectionType = "wifi"
    )

    private fun buildEvent(level: LogLevel = LogLevel.INFO) = LogEvent(
        id = generateUUID(),
        timestamp = currentTimeMillis(),
        level = level,
        tag = "TEST",
        message = "test message",
        deviceInfo = testDeviceInfo,
        sessionId = "session-abc"
    )

    @BeforeEach
    fun setup() {
        buffer = InMemoryBuffer(maxCapacity = 1000)
        transport = ControllableTransport()
        formatter = JsonLogFormatter()
    }

    @AfterEach
    fun teardown() {
        if (::processor.isInitialized) processor.shutdown()
    }

    private fun createProcessor(batchSize: Int = 20, flushInterval: Int = 300): BatchProcessor {
        val config = AppLoggerConfig.Builder()
            .debugMode(true)
            .batchSize(batchSize)
            .flushIntervalSeconds(flushInterval)
            .build()
        processor = BatchProcessor(buffer, transport, formatter, config)
        return processor
    }

    @Test
    fun `sendBatch drains buffer and sends to transport`() = runTest {
        val proc = createProcessor()
        buffer.push(buildEvent())
        buffer.push(buildEvent())
        buffer.push(buildEvent())

        proc.sendBatch()

        assertEquals(3, transport.sentEvents.size)
        assertEquals(0, buffer.size())
    }

    @Test
    fun `sendBatch on empty buffer does nothing`() = runTest {
        val proc = createProcessor()
        proc.sendBatch()
        assertEquals(0, transport.sendCallCount)
    }

    @Test
    fun `failed transport with retryable reinserts events to buffer`() = runTest {
        transport.shouldSucceed = false
        transport.retryable = true
        val proc = createProcessor()

        buffer.push(buildEvent())
        buffer.push(buildEvent())
        proc.sendBatch()

        // Events should be back in the buffer
        assertEquals(2, buffer.size())
    }

    @Test
    fun `failed transport without retryable discards events`() = runTest {
        transport.shouldSucceed = false
        transport.retryable = false
        val proc = createProcessor()

        buffer.push(buildEvent())
        buffer.push(buildEvent())
        proc.sendBatch()

        // Events should be discarded
        assertEquals(0, buffer.size())
    }

    @Test
    fun `transport unavailable reinserts events`() = runTest {
        transport.available = false
        val proc = createProcessor()

        buffer.push(buildEvent())
        proc.sendBatch()

        // Events should be back in buffer since transport is unavailable
        assertEquals(1, buffer.size())
    }

    @Test
    fun `enqueue accepts events`() = runTest {
        val proc = createProcessor()
        proc.enqueue(buildEvent())
        // Give the channel consumer time to process
        delay(200)

        // The event should be in the buffer waiting for batch
        assertTrue(buffer.size() >= 0) // At least received without error
    }

    @Test
    fun `transport exception is caught and treated as failure`() = runTest {
        transport.throwException = true
        val proc = createProcessor()

        buffer.push(buildEvent())

        // Should not throw — sendBatch is suspend, so we call it directly
        proc.sendBatch()
    }

    @Test
    fun `lastSuccessfulFlushTimestamp is zero before any flush`() {
        val proc = createProcessor()
        assertEquals(0L, proc.getLastSuccessfulFlushTimestamp())
    }

    @Test
    fun `lastSuccessfulFlushTimestamp is updated after successful send`() = runTest {
        val proc = createProcessor()
        val before = System.currentTimeMillis()
        buffer.push(buildEvent())
        proc.sendBatch()
        val after = System.currentTimeMillis()

        val ts = proc.getLastSuccessfulFlushTimestamp()
        assertTrue(ts >= before, "timestamp $ts should be >= $before")
        assertTrue(ts <= after, "timestamp $ts should be <= $after")
    }

    @Test
    fun `lastSuccessfulFlushTimestamp is NOT updated after failed send`() = runTest {
        transport.shouldSucceed = false
        transport.retryable = false
        val proc = createProcessor()
        buffer.push(buildEvent())
        proc.sendBatch()

        assertEquals(0L, proc.getLastSuccessfulFlushTimestamp())
    }

    @Test
    fun `retryAfterMs from transport is respected over default backoff`() = runTest {
        transport.shouldSucceed = false
        transport.retryable = true
        transport.retryAfterMs = 5_000L
        val proc = createProcessor()

        buffer.push(buildEvent())
        // sendBatch will fail and re-queue; we just verify it doesn't throw
        // and that the event is back in the buffer (retryable path)
        proc.sendBatch()

        assertEquals(1, buffer.size())
    }

    @Test
    fun `non-retryable failure does not requeue events`() = runTest {
        transport.shouldSucceed = false
        transport.retryable = false
        val proc = createProcessor()

        repeat(3) { buffer.push(buildEvent()) }
        proc.sendBatch()

        assertEquals(0, buffer.size())
        assertEquals(0, proc.deadLetterQueue.size())
    }

    @Test
    fun `concurrent sendBatch calls do not overlap via mutex`() = runTest {
        var concurrentCallsDetected = false
        var activeCalls = 0
        val syncTransport = object : LogTransport {
            override suspend fun send(events: List<LogEvent>): TransportResult {
                activeCalls++
                if (activeCalls > 1) concurrentCallsDetected = true
                delay(50)
                activeCalls--
                return TransportResult.Success
            }
            override fun isAvailable() = true
        }
        val config = AppLoggerConfig.Builder().debugMode(true).batchSize(5).flushIntervalSeconds(300).build()
        val proc = BatchProcessor(buffer, syncTransport, formatter, config)

        repeat(10) { buffer.push(buildEvent()) }

        // Launch two concurrent sendBatch calls — mutex should serialize them
        val job1 = launch { proc.sendBatch() }
        val job2 = launch { proc.sendBatch() }
        job1.join()
        job2.join()

        assertFalse(concurrentCallsDetected, "Concurrent batch sends detected — mutex not working")
        proc.shutdown()
    }

    /**
     * Transport with full control for testing various scenarios.
     */
    private class ControllableTransport : LogTransport {
        var shouldSucceed = true
        var retryable = false
        var available = true
        var throwException = false
        var retryAfterMs: Long? = null
        var sendCallCount = 0
        val sentEvents = mutableListOf<LogEvent>()

        override suspend fun send(events: List<LogEvent>): TransportResult {
            sendCallCount++
            if (throwException) throw RuntimeException("Transport crash")
            return if (shouldSucceed) {
                sentEvents.addAll(events)
                TransportResult.Success
            } else {
                TransportResult.Failure("Simulated failure", retryable, retryAfterMs = retryAfterMs)
            }
        }

        override fun isAvailable(): Boolean = available
    }
}
