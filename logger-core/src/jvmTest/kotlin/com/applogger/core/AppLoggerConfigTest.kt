package com.applogger.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppLoggerConfigTest {

    @Test
    fun `builder creates config with defaults`() {
        val config = AppLoggerConfig.Builder()
            .debugMode(true)
            .build()

        assertEquals("", config.endpoint)
        assertEquals("", config.apiKey)
        assertTrue(config.isDebugMode)
        assertTrue(config.consoleOutput)
        assertEquals(20, config.batchSize)
        assertEquals(30, config.flushIntervalSeconds)
        assertEquals(50, config.maxStackTraceLines)
        assertFalse(config.flushOnlyWhenIdle)
        assertFalse(config.verboseTransportLogging)
    }

    @Test
    fun `builder applies all values`() {
        val config = AppLoggerConfig.Builder()
            .endpoint("https://example.supabase.co")
            .apiKey("test-key")
            .debugMode(false)
            .consoleOutput(false)
            .batchSize(50)
            .flushIntervalSeconds(120)
            .maxStackTraceLines(10)
            .flushOnlyWhenIdle(true)
            .verboseTransportLogging(true)
            .build()

        assertEquals("https://example.supabase.co", config.endpoint)
        assertEquals("test-key", config.apiKey)
        assertFalse(config.isDebugMode)
        assertFalse(config.consoleOutput)
        assertEquals(50, config.batchSize)
        assertEquals(120, config.flushIntervalSeconds)
        assertEquals(10, config.maxStackTraceLines)
        assertTrue(config.flushOnlyWhenIdle)
        assertTrue(config.verboseTransportLogging)
    }

    @Test
    fun `batchSize is coerced to range 1-100`() {
        val configLow = AppLoggerConfig.Builder().debugMode(true).batchSize(0).build()
        assertEquals(1, configLow.batchSize)

        val configHigh = AppLoggerConfig.Builder().debugMode(true).batchSize(200).build()
        assertEquals(100, configHigh.batchSize)
    }

    @Test
    fun `flushIntervalSeconds is coerced to range 5-300`() {
        val configLow = AppLoggerConfig.Builder().debugMode(true).flushIntervalSeconds(1).build()
        assertEquals(5, configLow.flushIntervalSeconds)

        val configHigh = AppLoggerConfig.Builder().debugMode(true).flushIntervalSeconds(600).build()
        assertEquals(300, configHigh.flushIntervalSeconds)
    }

    @Test
    fun `maxStackTraceLines is coerced to range 1-100`() {
        val configLow = AppLoggerConfig.Builder().debugMode(true).maxStackTraceLines(0).build()
        assertEquals(1, configLow.maxStackTraceLines)

        val configHigh = AppLoggerConfig.Builder().debugMode(true).maxStackTraceLines(500).build()
        assertEquals(100, configHigh.maxStackTraceLines)
    }

    @Test
    fun `production endpoint must use HTTPS`() {
        assertThrows<IllegalArgumentException> {
            AppLoggerConfig.Builder()
                .endpoint("http://insecure.example.com")
                .debugMode(false)
                .build()
        }
    }

    @Test
    fun `debug mode allows HTTP endpoint`() {
        assertDoesNotThrow {
            AppLoggerConfig.Builder()
                .endpoint("http://localhost:8080")
                .debugMode(true)
                .build()
        }
    }

    @Test
    fun `empty endpoint is allowed in production`() {
        assertDoesNotThrow {
            AppLoggerConfig.Builder()
                .endpoint("")
                .debugMode(false)
                .build()
        }
    }

    @Test
    fun `HTTPS endpoint passes in production`() {
        assertDoesNotThrow {
            AppLoggerConfig.Builder()
                .endpoint("https://secure.supabase.co")
                .debugMode(false)
                .build()
        }
    }

    @Test
    fun `resolveForLowResource reduces values`() {
        val config = AppLoggerConfig.Builder()
            .debugMode(true)
            .batchSize(20)
            .flushIntervalSeconds(30)
            .maxStackTraceLines(50)
            .flushOnlyWhenIdle(false)
            .build()

        val lowResource = config.resolveForLowResource()

        assertEquals(5, lowResource.batchSize)
        assertEquals(60, lowResource.flushIntervalSeconds)
        assertEquals(5, lowResource.maxStackTraceLines)
        assertTrue(lowResource.flushOnlyWhenIdle)
    }

    @Test
    fun `resolveForLowResource keeps values if already low`() {
        val config = AppLoggerConfig.Builder()
            .debugMode(true)
            .batchSize(2)
            .flushIntervalSeconds(120)
            .maxStackTraceLines(3)
            .build()

        val lowResource = config.resolveForLowResource()

        assertEquals(2, lowResource.batchSize)
        assertEquals(120, lowResource.flushIntervalSeconds)
        assertEquals(3, lowResource.maxStackTraceLines)
    }

    @Test
    fun `config is immutable data class`() {
        val config1 = AppLoggerConfig.Builder().debugMode(true).build()
        val config2 = AppLoggerConfig.Builder().debugMode(true).build()
        assertEquals(config1, config2)
    }
}
