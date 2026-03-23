package com.applogger.core.internal

import com.applogger.core.*
import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppLoggerImplTest {

    private lateinit var buffer: InMemoryBuffer
    private lateinit var fakeTransport: RecordingTransport
    private lateinit var processor: BatchProcessor
    private lateinit var logger: AppLoggerImpl

    private val testDeviceInfo = DeviceInfo(
        brand = "Google", model = "Pixel 8", osVersion = "14",
        apiLevel = 34, platform = "ANDROID_MOBILE", appVersion = "2.1.0",
        appBuild = 42, isLowRamDevice = false, connectionType = "wifi"
    )

    private fun buildConfig(
        debugMode: Boolean = false,
        batchSize: Int = 100,
        flushIntervalSeconds: Int = 300
    ) = AppLoggerConfig.Builder()
        .debugMode(debugMode)
        .batchSize(batchSize)
        .flushIntervalSeconds(flushIntervalSeconds)
        .build()

    @BeforeEach
    fun setup() {
        buffer = InMemoryBuffer(maxCapacity = 1000)
        fakeTransport = RecordingTransport()
    }

    @AfterEach
    fun teardown() {
        if (::processor.isInitialized) {
            processor.shutdown()
        }
    }

    private fun createLogger(config: AppLoggerConfig): AppLoggerImpl {
        val formatter = JsonLogFormatter()
        processor = BatchProcessor(buffer, fakeTransport, formatter, config)
        return AppLoggerImpl(
            deviceInfo = testDeviceInfo,
            sessionManager = SessionManager(),
            filter = RateLimitFilter(maxEventsPerMinutePerTag = 1000),
            processor = processor,
            config = config
        )
    }

    private fun isUuid(value: String): Boolean {
        val uuidPattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        return uuidPattern.matches(value)
    }

    @Test
    fun `debug events are discarded in production mode`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.debug("TAG", "debug message")
        delay(200)
        processor.sendBatch()

        assertEquals(0, fakeTransport.sentEvents.size)
    }

    @Test
    fun `debug events are processed in debug mode`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = true))
        logger.debug("TAG", "debug message")
        delay(200)
        processor.sendBatch()

        assertEquals(1, fakeTransport.sentEvents.size)
        assertEquals(LogLevel.DEBUG, fakeTransport.sentEvents[0].level)
    }

    @Test
    fun `info events include correct fields`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.info("PLAYER", "Playback started", extra = mapOf("content_id" to "movie_123"))
        delay(200)
        processor.sendBatch()

        assertEquals(1, fakeTransport.sentEvents.size)
        val event = fakeTransport.sentEvents[0]
        assertEquals(LogLevel.INFO, event.level)
        assertEquals("PLAYER", event.tag)
        assertEquals("Playback started", event.message)
        assertEquals("movie_123", event.extra?.get("content_id"))
    }

    @Test
    fun `error events include throwable info`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        val exception = RuntimeException("payment failed")
        logger.error("PAYMENT", "Transaction error", throwable = exception)
        delay(200)
        processor.sendBatch()

        assertEquals(1, fakeTransport.sentEvents.size)
        val event = fakeTransport.sentEvents[0]
        assertNotNull(event.throwableInfo)
        assertEquals("RuntimeException", event.throwableInfo?.type)
        assertEquals("payment failed", event.throwableInfo?.message)
    }

    @Test
    fun `critical events are captured`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.critical("AUTH", "Token expired", throwable = IllegalStateException("expired"))
        delay(200)
        processor.sendBatch()

        assertEquals(1, fakeTransport.sentEvents.size)
        assertEquals(LogLevel.CRITICAL, fakeTransport.sentEvents[0].level)
    }

    @Test
    fun `warn events include anomaly type in extra`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.warn("NETWORK", "Slow response", anomalyType = "HIGH_LATENCY")
        delay(200)
        processor.sendBatch()

        val event = fakeTransport.sentEvents[0]
        assertEquals("HIGH_LATENCY", event.extra?.get("anomaly_type"))
    }

    @Test
    fun `metric events include metric metadata`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.metric("screen_load_time", 1234.0, "ms", tags = mapOf("screen" to "Home"))
        delay(200)
        processor.sendBatch()

        val event = fakeTransport.sentEvents[0]
        assertEquals(LogLevel.METRIC, event.level)
        assertEquals("screen_load_time", event.metricName)
        assertEquals(1234.0, event.metricValue)
        assertEquals("ms", event.metricUnit)
        assertEquals("Home", event.metricTags?.get("screen"))
        // extra should be null — metric data lives in typed fields
        assertNull(event.extra)
    }

    @Test
    fun `all events have deviceInfo and sessionId`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.info("TAG", "test")
        delay(200)
        processor.sendBatch()

        val event = fakeTransport.sentEvents[0]
        assertEquals("Google", event.deviceInfo.brand)
        assertEquals("Pixel 8", event.deviceInfo.model)
        assertTrue(event.sessionId.isNotBlank())
    }

    @Test
    fun `tag is truncated to 100 chars`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        val longTag = "A".repeat(200)
        logger.info(longTag, "test")
        delay(200)
        processor.sendBatch()

        assertEquals(100, fakeTransport.sentEvents[0].tag.length)
    }

    @Test
    fun `message is truncated to 10000 chars`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        val longMessage = "X".repeat(20_000)
        logger.info("TAG", longMessage)
        delay(200)
        processor.sendBatch()

        assertEquals(10_000, fakeTransport.sentEvents[0].message.length)
    }

    @Test
    fun `logger never throws exceptions to caller`() {
        // Even with a broken transport, the logger should not throw
        logger = createLogger(buildConfig(debugMode = false))
        assertDoesNotThrow {
            logger.info("TAG", "test")
            logger.error("TAG", "error", throwable = RuntimeException("boom"))
            logger.critical("TAG", "critical")
            logger.flush()
        }
    }

    @Test
    fun `setUserId sanitizes non UUID values to UUID and adds userId to events`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.setUserId("user-abc")
        logger.info("TAG", "with user")
        delay(200)
        processor.sendBatch()

        val userId = fakeTransport.sentEvents[0].userId
        assertNotNull(userId)
        assertTrue(isUuid(userId!!))
        assertNotEquals("user-abc", userId)
    }

    @Test
    fun `setUserId keeps valid UUID unchanged`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        val uuid = "123e4567-e89b-12d3-a456-426614174000"

        logger.setUserId(uuid)
        logger.info("TAG", "with uuid")
        delay(200)
        processor.sendBatch()

        assertEquals(uuid, fakeTransport.sentEvents[0].userId)
    }

    @Test
    fun `clearUserId removes userId from events`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.setUserId("user-abc")
        logger.clearUserId()
        logger.info("TAG", "without user")
        delay(200)
        processor.sendBatch()

        assertNull(fakeTransport.sentEvents[0].userId)
    }

    @Test
    fun `deviceId is populated by default and can be overridden`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.info("TAG", "default device")
        delay(200)
        processor.sendBatch()

        val defaultDeviceId = fakeTransport.sentEvents[0].deviceId
        assertTrue(defaultDeviceId.isNotBlank())
        assertTrue(isUuid(defaultDeviceId))

        logger.setDeviceId("custom-device-id")
        logger.info("TAG", "custom device")
        delay(200)
        processor.sendBatch()

        assertEquals("custom-device-id", fakeTransport.sentEvents[1].deviceId)

        logger.clearDeviceId()
        logger.info("TAG", "restored")
        delay(200)
        processor.sendBatch()

        assertEquals(defaultDeviceId, fakeTransport.sentEvents[2].deviceId)
    }

    // ── Throwable propagation on non-critical levels ───────────────────────────

    @Test
    fun `debug throwable is captured in debug mode`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = true))
        val exception = IllegalStateException("unexpected state")
        logger.debug("TAG", "debug with throwable", throwable = exception)
        delay(200)
        processor.sendBatch()

        assertEquals(1, fakeTransport.sentEvents.size)
        val event = fakeTransport.sentEvents[0]
        assertNotNull(event.throwableInfo)
        assertEquals("IllegalStateException", event.throwableInfo?.type)
        assertEquals("unexpected state", event.throwableInfo?.message)
    }

    @Test
    fun `info throwable is captured in throwableInfo`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        val exception = RuntimeException("info-level anomaly")
        logger.info("PLAYER", "Recovered after error", throwable = exception)
        delay(200)
        processor.sendBatch()

        assertEquals(1, fakeTransport.sentEvents.size)
        assertNotNull(fakeTransport.sentEvents[0].throwableInfo)
        assertEquals("RuntimeException", fakeTransport.sentEvents[0].throwableInfo?.type)
    }

    @Test
    fun `warn throwable is captured alongside anomalyType`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        val exception = Exception("timeout")
        logger.warn("NETWORK", "Slow response", throwable = exception, anomalyType = "HIGH_LATENCY")
        delay(200)
        processor.sendBatch()

        val event = fakeTransport.sentEvents[0]
        assertNotNull(event.throwableInfo)
        assertEquals("Exception", event.throwableInfo?.type)
        assertEquals("HIGH_LATENCY", event.extra?.get("anomaly_type"))
    }

    @Test
    fun `debug without throwable produces null throwableInfo`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = true))
        logger.debug("TAG", "no throwable")
        delay(200)
        processor.sendBatch()

        assertNull(fakeTransport.sentEvents[0].throwableInfo)
    }

    @Test
    fun `extra values with numeric and boolean types are stored as strings in LogEvent`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.info("TAG", "typed extra", extra = mapOf(
            "retry_count" to 3,
            "latency_ms" to 123.45,
            "is_cached" to true,
            "label" to "home"
        ))
        delay(200)
        processor.sendBatch()

        val event = fakeTransport.sentEvents[0]
        // LogEvent stores everything as String — type preservation happens at transport layer
        assertEquals("3", event.extra?.get("retry_count"))
        assertEquals("123.45", event.extra?.get("latency_ms"))
        assertEquals("true", event.extra?.get("is_cached"))
        assertEquals("home", event.extra?.get("label"))
    }

    /**
     * Simple recording transport for tests.
     */
    private class RecordingTransport : LogTransport {
        val sentEvents = java.util.Collections.synchronizedList(mutableListOf<com.applogger.core.model.LogEvent>())

        override suspend fun send(events: List<com.applogger.core.model.LogEvent>): TransportResult {
            sentEvents.addAll(events)
            return TransportResult.Success
        }

        override fun isAvailable(): Boolean = true
    }
}
