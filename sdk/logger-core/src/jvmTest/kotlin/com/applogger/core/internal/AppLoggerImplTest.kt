package com.applogger.core.internal

import com.applogger.core.*
import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogLevel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.double
import kotlinx.serialization.json.boolean
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
        flushIntervalSeconds: Int = 300,
        minLevel: LogMinLevel = LogMinLevel.DEBUG
    ) = AppLoggerConfig.Builder()
        .debugMode(debugMode)
        .batchSize(batchSize)
        .flushIntervalSeconds(flushIntervalSeconds)
        .minLevel(minLevel)
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
        assertEquals("movie_123", event.extra?.get("content_id")?.jsonPrimitive?.content)
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
        assertEquals("java.lang.RuntimeException", event.throwableInfo?.type)
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
        assertEquals("HIGH_LATENCY", event.extra?.get("anomaly_type")?.jsonPrimitive?.content)
    }

    @Test
    fun `warn preserves native types in extra like other levels`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.warn("NETWORK", "Slow response", extra = mapOf("retry_count" to 3, "is_cached" to false))
        delay(200)
        processor.sendBatch()

        val event = fakeTransport.sentEvents[0]
        // Native types preserved — Int stays Int, Boolean stays Boolean
        assertEquals(3, event.extra?.get("retry_count")?.jsonPrimitive?.int)
        assertEquals(false, event.extra?.get("is_cached")?.jsonPrimitive?.boolean)
    }

    @Test
    fun `warn merges anomalyType and extra without losing either`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.warn("NET", "Timeout", anomalyType = "TIMEOUT", extra = mapOf("endpoint" to "/api/v1"))
        delay(200)
        processor.sendBatch()

        val event = fakeTransport.sentEvents[0]
        assertEquals("TIMEOUT", event.extra?.get("anomaly_type")?.jsonPrimitive?.content)
        assertEquals("/api/v1", event.extra?.get("endpoint")?.jsonPrimitive?.content)
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

        logger.setDeviceId(null)
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
        assertEquals("java.lang.IllegalStateException", event.throwableInfo?.type)
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
        assertEquals("java.lang.RuntimeException", fakeTransport.sentEvents[0].throwableInfo?.type)
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
        assertEquals("java.lang.Exception", event.throwableInfo?.type)
        assertEquals("HIGH_LATENCY", event.extra?.get("anomaly_type")?.jsonPrimitive?.content)
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
    fun `extra values with numeric and boolean types are stored as native JsonElement`() = runBlocking {
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
        // Native types preserved directly in JsonElement — no string conversion
        assertEquals(3, event.extra?.get("retry_count")?.jsonPrimitive?.int)
        assertEquals(123.45, event.extra?.get("latency_ms")?.jsonPrimitive?.double)
        assertEquals(true, event.extra?.get("is_cached")?.jsonPrimitive?.boolean)
        assertEquals("home", event.extra?.get("label")?.jsonPrimitive?.content)
    }

    // ── Global extra ──────────────────────────────────────────────────────────

    @Test
    fun `globalExtra is attached to every event`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.addGlobalExtra("ab_test", "checkout_v2")
        logger.addGlobalExtra("experiment", "group_b")
        logger.info("TAG", "event with global context")
        delay(200)
        processor.sendBatch()

        val event = fakeTransport.sentEvents[0]
        assertEquals("checkout_v2", event.extra?.get("ab_test")?.jsonPrimitive?.content)
        assertEquals("group_b", event.extra?.get("experiment")?.jsonPrimitive?.content)
    }

    @Test
    fun `per-call extra overrides globalExtra on key collision`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.addGlobalExtra("source", "global")
        logger.info("TAG", "override test", extra = mapOf("source" to "local"))
        delay(200)
        processor.sendBatch()

        assertEquals("local", fakeTransport.sentEvents[0].extra?.get("source")?.jsonPrimitive?.content)
    }

    @Test
    fun `removeGlobalExtra stops attaching that key`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.addGlobalExtra("flag", "on")
        logger.removeGlobalExtra("flag")
        logger.info("TAG", "after remove")
        delay(200)
        processor.sendBatch()

        assertNull(fakeTransport.sentEvents[0].extra?.get("flag"))
    }

    @Test
    fun `clearGlobalExtra removes all global context`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.addGlobalExtra("k1", "v1")
        logger.addGlobalExtra("k2", "v2")
        logger.clearGlobalExtra()
        logger.info("TAG", "after clear")
        delay(200)
        processor.sendBatch()

        assertNull(fakeTransport.sentEvents[0].extra)
    }

    // ── environment ───────────────────────────────────────────────────────────

    @Test
    fun `environment is attached to every event`() = runBlocking {
        val config = buildConfig(debugMode = false).let {
            AppLoggerConfig.Builder()
                .debugMode(false)
                .environment("staging")
                .batchSize(100)
                .flushIntervalSeconds(300)
                .build()
        }
        logger = createLogger(config)
        logger.info("TAG", "staging event")
        delay(200)
        processor.sendBatch()

        assertEquals("staging", fakeTransport.sentEvents[0].environment)
    }

    @Test
    fun `environment defaults to production`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false))
        logger.info("TAG", "prod event")
        delay(200)
        processor.sendBatch()

        assertEquals("production", fakeTransport.sentEvents[0].environment)
    }

    // ── minLevel ──────────────────────────────────────────────────────────────
    @Test
    fun `minLevel WARN discards DEBUG and INFO events`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = true, minLevel = LogMinLevel.WARN))
        logger.debug("TAG", "debug")
        logger.info("TAG", "info")
        logger.warn("TAG", "warn")
        delay(200)
        processor.sendBatch()

        assertEquals(1, fakeTransport.sentEvents.size)
        assertEquals(LogLevel.WARN, fakeTransport.sentEvents[0].level)
    }

    @Test
    fun `minLevel ERROR discards DEBUG INFO WARN but keeps ERROR and CRITICAL`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = true, minLevel = LogMinLevel.ERROR))
        logger.debug("TAG", "debug")
        logger.info("TAG", "info")
        logger.warn("TAG", "warn")
        logger.error("TAG", "error")
        logger.critical("TAG", "critical")
        delay(200)
        processor.sendBatch()

        assertEquals(2, fakeTransport.sentEvents.size)
        assertEquals(LogLevel.ERROR, fakeTransport.sentEvents[0].level)
        assertEquals(LogLevel.CRITICAL, fakeTransport.sentEvents[1].level)
    }

    @Test
    fun `METRIC always passes regardless of minLevel`() = runBlocking {
        logger = createLogger(buildConfig(debugMode = false, minLevel = LogMinLevel.CRITICAL))
        logger.info("TAG", "info — should be discarded")
        logger.metric("cpu_usage", 42.0, "percent")
        delay(200)
        processor.sendBatch()

        assertEquals(1, fakeTransport.sentEvents.size)
        assertEquals(LogLevel.METRIC, fakeTransport.sentEvents[0].level)
    }

    // ── SessionManager rotation ───────────────────────────────────────────────

    @Test
    fun `SessionManager rotate generates new sessionId`() {
        val sm = SessionManager()
        val first = sm.sessionId
        sm.rotate()
        assertNotEquals(first, sm.sessionId)
    }

    @Test
    fun `SessionManager onForeground rotates session after timeout`() {
        val sm = SessionManager(sessionTimeoutMs = 100L)
        val first = sm.sessionId
        sm.onBackground()
        Thread.sleep(150)
        sm.onForeground()
        assertNotEquals(first, sm.sessionId)
    }

    @Test
    fun `SessionManager onForeground does NOT rotate before timeout`() {
        val sm = SessionManager(sessionTimeoutMs = 60_000L)
        val first = sm.sessionId
        sm.onBackground()
        Thread.sleep(50)
        sm.onForeground()
        assertEquals(first, sm.sessionId)
    }

    /**
     * Simple recording transport for tests.
     */
    private class RecordingTransport : LogTransport {        val sentEvents = java.util.Collections.synchronizedList(mutableListOf<com.applogger.core.model.LogEvent>())

        override suspend fun send(events: List<com.applogger.core.model.LogEvent>): TransportResult {
            sentEvents.addAll(events)
            return TransportResult.Success
        }

        override fun isAvailable(): Boolean = true
    }
}
