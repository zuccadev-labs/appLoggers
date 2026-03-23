package com.applogger.core.integration

import com.applogger.core.*
import com.applogger.core.internal.*
import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Tests de integración que verifican el pipeline completo:
 * Logger → Filter → Buffer → BatchProcessor → Transport
 *
 * Usan un transport fake pero ejercitan todo el stack real.
 */
@DisplayName("Integration — Full Pipeline Tests")
class PipelineIntegrationTest {

    private lateinit var transport: RecordingTransport
    private lateinit var processor: BatchProcessor

    private val testDeviceInfo = DeviceInfo(
        brand = "Samsung", model = "Galaxy S24", osVersion = "14",
        apiLevel = 34, platform = "ANDROID_MOBILE", appVersion = "3.0.0",
        appBuild = 100, isLowRamDevice = false, connectionType = "wifi"
    )

    @AfterEach
    fun teardown() {
        if (::processor.isInitialized) processor.shutdown()
    }

    private fun createPipeline(
        debugMode: Boolean = false,
        batchSize: Int = 100,
        rateLimit: Int = 1000
    ): AppLoggerImpl {
        transport = RecordingTransport()
        val buffer = InMemoryBuffer(maxCapacity = 5000)
        val config = AppLoggerConfig.Builder()
            .debugMode(debugMode)
            .batchSize(batchSize)
            .flushIntervalSeconds(300)
            .build()
        val formatter = JsonLogFormatter()
        processor = BatchProcessor(buffer, transport, formatter, config)
        val filter = RateLimitFilter(maxEventsPerMinutePerTag = rateLimit)

        return AppLoggerImpl(
            deviceInfo = testDeviceInfo,
            sessionManager = SessionManager(),
            filter = filter,
            processor = processor,
            config = config
        )
    }

    @Test
    fun `complete flow - info event reaches transport after flush`() = runBlocking {
        val logger = createPipeline()
        logger.info("PLAYER", "Playback started", extra = mapOf("content" to "movie_123"))
        delay(200)
        processor.sendBatch()

        assertEquals(1, transport.sentEvents.size)
        val event = transport.sentEvents[0]
        assertEquals(LogLevel.INFO, event.level)
        assertEquals("PLAYER", event.tag)
        assertEquals("Playback started", event.message)
    }

    @Test
    fun `complete flow - error with exception reaches transport`() = runBlocking {
        val logger = createPipeline()
        val exception = java.io.IOException("Network timeout")
        logger.error("NETWORK", "API call failed", throwable = exception)
        delay(200)
        processor.sendBatch()

        assertEquals(1, transport.sentEvents.size)
        val event = transport.sentEvents[0]
        assertNotNull(event.throwableInfo)
        assertEquals("IOException", event.throwableInfo?.type)
    }

    @Test
    fun `complete flow - debug events filtered in production`() = runBlocking {
        val logger = createPipeline(debugMode = false)
        logger.debug("TAG", "Should not appear")
        logger.info("TAG", "Should appear")
        delay(200)
        processor.sendBatch()

        assertEquals(1, transport.sentEvents.size)
        assertEquals(LogLevel.INFO, transport.sentEvents[0].level)
    }

    @Test
    fun `complete flow - rate limited events are dropped`() = runBlocking {
        val logger = createPipeline(rateLimit = 2)
        repeat(5) { logger.info("FLOOD_TAG", "Event $it") }
        delay(200)
        processor.sendBatch()

        // Only 2 should pass the rate limit
        assertEquals(2, transport.sentEvents.size)
    }

    @Test
    fun `complete flow - ERROR bypasses rate limit`() = runBlocking {
        val logger = createPipeline(rateLimit = 1)
        // Exhaust rate limit with INFO
        logger.info("TAG", "First info")
        logger.info("TAG", "Second info - blocked")
        // ERROR should still pass
        logger.error("TAG", "Error always passes")
        delay(200)
        processor.sendBatch()

        val errors = transport.sentEvents.filter { it.level == LogLevel.ERROR }
        assertEquals(1, errors.size)
    }

    @Test
    fun `complete flow - multiple log levels in sequence`() = runBlocking {
        val logger = createPipeline()
        logger.info("TAG", "info")
        logger.warn("TAG", "warn")
        logger.error("TAG", "error")
        logger.critical("TAG", "critical")
        logger.metric("load_time", 100.0, "ms")
        delay(200)
        processor.sendBatch()

        assertEquals(5, transport.sentEvents.size)
        assertEquals(LogLevel.INFO, transport.sentEvents[0].level)
        assertEquals(LogLevel.WARN, transport.sentEvents[1].level)
        assertEquals(LogLevel.ERROR, transport.sentEvents[2].level)
        assertEquals(LogLevel.CRITICAL, transport.sentEvents[3].level)
        val metric = transport.sentEvents[4]
        assertEquals(LogLevel.METRIC, metric.level)
        assertEquals("load_time", metric.metricName)
        assertEquals(100.0, metric.metricValue)
        assertEquals("ms", metric.metricUnit)
    }

    @Test
    fun `complete flow - events have consistent sessionId`() = runBlocking {
        val logger = createPipeline()
        logger.info("TAG", "first")
        logger.error("TAG", "second")
        delay(200)
        processor.sendBatch()

        val sessionIds = transport.sentEvents.map { it.sessionId }.distinct()
        assertEquals(1, sessionIds.size, "All events should share the same sessionId")
    }

    @Test
    fun `complete flow - events have unique IDs`() = runBlocking {
        val logger = createPipeline()
        repeat(10) { logger.info("TAG", "event $it") }
        delay(200)
        processor.sendBatch()

        val ids = transport.sentEvents.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "All event IDs should be unique")
    }

    @Test
    fun `complete flow - transport failure preserves events for retry`() = runBlocking {
        val logger = createPipeline()
        logger.info("TAG", "important event")
        delay(200)

        // Mark transport as failing
        transport.shouldFail = true
        transport.retryable = true
        processor.sendBatch()

        // Events not lost — they're back in buffer
        // Now fix transport and try again
        transport.shouldFail = false
        processor.sendBatch()

        assertTrue(transport.sentEvents.isNotEmpty())
    }

    @Test
    fun `complete flow - consecutiveFailures increments on transport failure`() = runBlocking {
        val logger = createPipeline()
        logger.info("TAG", "event")
        delay(200)

        transport.shouldFail = true
        transport.retryable = true
        processor.sendBatch()

        assertTrue(processor.getConsecutiveFailures() > 0)

        // Recovers on success
        transport.shouldFail = false
        processor.sendBatch()
        assertEquals(0, processor.getConsecutiveFailures())
    }

    private class RecordingTransport : LogTransport {
        val sentEvents = java.util.Collections.synchronizedList(mutableListOf<com.applogger.core.model.LogEvent>())
        var shouldFail = false
        var retryable = true

        override suspend fun send(events: List<com.applogger.core.model.LogEvent>): TransportResult {
            return if (shouldFail) {
                TransportResult.Failure("Simulated failure", retryable)
            } else {
                sentEvents.addAll(events)
                TransportResult.Success
            }
        }

        override fun isAvailable(): Boolean = true
    }
}
