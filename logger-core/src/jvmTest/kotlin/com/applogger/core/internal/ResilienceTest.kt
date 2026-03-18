package com.applogger.core.internal

import com.applogger.core.*
import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests de resiliencia: verifican que el SDK NUNCA lanza excepciones
 * al código de la app, incluso bajo condiciones adversas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("Resilience Tests — SDK must never crash the host app")
class ResilienceTest {

    private lateinit var processor: BatchProcessor

    private val testDeviceInfo = DeviceInfo(
        brand = "Test", model = "Test", osVersion = "14",
        apiLevel = 34, platform = "ANDROID_MOBILE", appVersion = "1.0.0",
        appBuild = 1, isLowRamDevice = false, connectionType = "wifi"
    )

    @AfterEach
    fun teardown() {
        if (::processor.isInitialized) processor.shutdown()
    }

    private fun createLogger(transport: LogTransport): AppLoggerImpl {
        val buffer = InMemoryBuffer(maxCapacity = 100)
        val config = AppLoggerConfig.Builder().debugMode(true).build()
        processor = BatchProcessor(buffer, transport, JsonLogFormatter(), config)
        return AppLoggerImpl(
            deviceInfo = testDeviceInfo,
            sessionManager = SessionManager(),
            filter = RateLimitFilter(),
            processor = processor,
            config = config
        )
    }

    @Test
    fun `crashing transport does not propagate to caller`() {
        val logger = createLogger(CrashingTransport())

        assertDoesNotThrow {
            logger.error("TAG", "Error during crash transport")
            logger.critical("TAG", "Critical during crash transport")
            logger.flush()
        }
    }

    @Test
    fun `logger handles rapid fire events without crash`() {
        val logger = createLogger(SlowTransport())

        assertDoesNotThrow {
            repeat(10_000) {
                logger.info("RAPID", "Event #$it")
            }
        }
    }

    @Test
    fun `logger handles null extra gracefully`() {
        val logger = createLogger(NoOpTransport())

        assertDoesNotThrow {
            logger.info("TAG", "msg", extra = null)
            logger.error("TAG", "msg", throwable = null, extra = null)
            logger.warn("TAG", "msg", anomalyType = null, extra = null)
        }
    }

    @Test
    fun `logger handles empty strings`() {
        val logger = createLogger(NoOpTransport())

        assertDoesNotThrow {
            logger.info("", "")
            logger.error("", "", throwable = RuntimeException(""))
        }
    }

    @Test
    fun `logger handles very large messages`() {
        val logger = createLogger(NoOpTransport())
        val largeMessage = "X".repeat(1_000_000)

        assertDoesNotThrow {
            logger.info("TAG", largeMessage)
        }
    }

    @Test
    fun `logger handles very large extra maps`() {
        val logger = createLogger(NoOpTransport())
        val largeExtra = (1..1000).associate { "key_$it" to "value_$it" }

        assertDoesNotThrow {
            logger.info("TAG", "msg", extra = largeExtra)
        }
    }

    @Test
    fun `batch processor survives transport exception during send`() = runTest {
        val buffer = InMemoryBuffer(maxCapacity = 100)
        val config = AppLoggerConfig.Builder().debugMode(true).build()
        val crashTransport = CrashingTransport()
        processor = BatchProcessor(buffer, crashTransport, JsonLogFormatter(), config)

        buffer.push(buildEvent())
        // Should not throw — sendBatch is suspend, call directly in runTest
        processor.sendBatch()
    }

    @Test
    fun `flush after shutdown does not crash`() {
        val logger = createLogger(NoOpTransport())
        processor.shutdown()

        assertDoesNotThrow {
            logger.flush()
        }
    }

    private fun buildEvent() = LogEvent(
        id = generateUUID(),
        timestamp = currentTimeMillis(),
        level = LogLevel.ERROR,
        tag = "TEST",
        message = "test",
        deviceInfo = testDeviceInfo,
        sessionId = "session"
    )

    private class CrashingTransport : LogTransport {
        override suspend fun send(events: List<LogEvent>): TransportResult {
            throw RuntimeException("Transport exploded!")
        }
        override fun isAvailable(): Boolean = true
    }

    private class SlowTransport : LogTransport {
        override suspend fun send(events: List<LogEvent>): TransportResult {
            kotlinx.coroutines.delay(1000) // Simulate slow network
            return TransportResult.Success
        }
        override fun isAvailable(): Boolean = true
    }

    private class NoOpTransport : LogTransport {
        override suspend fun send(events: List<LogEvent>): TransportResult = TransportResult.Success
        override fun isAvailable(): Boolean = true
    }
}
