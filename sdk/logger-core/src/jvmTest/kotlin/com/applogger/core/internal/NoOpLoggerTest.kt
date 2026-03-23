package com.applogger.core.internal

import com.applogger.core.AppLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NoOpLoggerTest {

    private val logger: AppLogger = NoOpLogger()

    @Test
    fun `all log methods complete without throwing`() {
        assertDoesNotThrow {
            logger.debug("TAG", "debug msg")
            logger.info("TAG", "info msg")
            logger.warn("TAG", "warn msg", anomalyType = "SLOW")
            logger.error("TAG", "error msg", throwable = RuntimeException("boom"))
            logger.critical("TAG", "critical msg", throwable = RuntimeException("crash"))
            logger.metric("load_time", 100.0, "ms", tags = mapOf("screen" to "Home"))
            logger.flush()
        }
    }

    @Test
    fun `debug with null extra does not throw`() {
        assertDoesNotThrow { logger.debug("TAG", "msg", extra = null) }
    }

    @Test
    fun `error with null throwable does not throw`() {
        assertDoesNotThrow { logger.error("TAG", "msg", throwable = null) }
    }

    @Test
    fun `flush is idempotent`() {
        assertDoesNotThrow {
            repeat(100) { logger.flush() }
        }
    }

    @Test
    fun `addGlobalExtra does not throw`() {
        assertDoesNotThrow { logger.addGlobalExtra("key", "value") }
    }

    @Test
    fun `removeGlobalExtra does not throw`() {
        assertDoesNotThrow { logger.removeGlobalExtra("key") }
    }

    @Test
    fun `clearGlobalExtra does not throw`() {
        assertDoesNotThrow { logger.clearGlobalExtra() }
    }

    @Test
    fun `global extra methods are safe before initialize`() {
        // Simulates calling global extra before SDK.initialize() — must never crash
        assertDoesNotThrow {
            logger.addGlobalExtra("experiment", "group_b")
            logger.addGlobalExtra("ab_test", "checkout_v2")
            logger.removeGlobalExtra("experiment")
            logger.clearGlobalExtra()
        }
    }
}
