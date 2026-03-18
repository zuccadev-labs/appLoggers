package com.applogger.test

import com.applogger.core.model.LogLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InMemoryLoggerTest {

    private lateinit var logger: InMemoryLogger

    @BeforeEach
    fun setup() {
        logger = InMemoryLogger()
    }

    @Test
    fun `debug is recorded`() {
        logger.debug("TAG", "debug msg")
        assertEquals(1, logger.debugCount)
        assertEquals(0, logger.errorCount)
    }

    @Test
    fun `info is recorded`() {
        logger.info("TAG", "info msg")
        assertEquals(1, logger.infoCount)
    }

    @Test
    fun `warn is recorded`() {
        logger.warn("TAG", "warn msg", anomalyType = "SLOW")
        assertEquals(1, logger.warnCount)
    }

    @Test
    fun `error is recorded with throwable`() {
        val exception = RuntimeException("boom")
        logger.error("TAG", "error msg", throwable = exception)
        assertEquals(1, logger.errorCount)
        assertEquals(exception, logger.lastError?.throwable)
    }

    @Test
    fun `critical is recorded`() {
        logger.critical("TAG", "critical msg")
        assertEquals(1, logger.criticalCount)
    }

    @Test
    fun `metric is recorded`() {
        logger.metric("load_time", 100.0, "ms")
        assertEquals(1, logger.metricCount)
    }

    @Test
    fun `assertLogged passes when event exists`() {
        logger.error("PAYMENT", "failed")
        assertDoesNotThrow { logger.assertLogged(LogLevel.ERROR, tag = "PAYMENT") }
    }

    @Test
    fun `assertLogged fails when event is missing`() {
        assertThrows<IllegalStateException> {
            logger.assertLogged(LogLevel.ERROR, tag = "MISSING")
        }
    }

    @Test
    fun `assertLogged without tag matches any tag`() {
        logger.info("ANY_TAG", "msg")
        assertDoesNotThrow { logger.assertLogged(LogLevel.INFO) }
    }

    @Test
    fun `assertNotLogged passes when no matching events`() {
        logger.info("TAG", "msg")
        assertDoesNotThrow { logger.assertNotLogged(LogLevel.ERROR) }
    }

    @Test
    fun `assertNotLogged fails when matching events exist`() {
        logger.error("TAG", "msg")
        assertThrows<IllegalStateException> { logger.assertNotLogged(LogLevel.ERROR) }
    }

    @Test
    fun `clear removes all logs`() {
        logger.info("TAG", "msg1")
        logger.error("TAG", "msg2")
        logger.clear()
        assertEquals(0, logger.logs.size)
        assertEquals(0, logger.infoCount)
        assertEquals(0, logger.errorCount)
    }

    @Test
    fun `lastError returns most recent error`() {
        logger.error("TAG", "error 1")
        logger.error("TAG", "error 2")
        assertEquals("error 2", logger.lastError?.message)
    }

    @Test
    fun `lastCritical returns most recent critical`() {
        logger.critical("TAG", "critical 1")
        logger.critical("TAG", "critical 2")
        assertEquals("critical 2", logger.lastCritical?.message)
    }

    @Test
    fun `logs list is a snapshot not live reference`() {
        logger.info("TAG", "msg1")
        val snapshot = logger.logs
        logger.info("TAG", "msg2")
        assertEquals(1, snapshot.size) // Snapshot doesn't grow
        assertEquals(2, logger.logs.size) // But actual logs does
    }
}
