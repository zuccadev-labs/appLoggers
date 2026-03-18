package com.applogger.core.internal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ThrowableExtTest {

    @Test
    fun `toThrowableInfo extracts type correctly`() {
        val exception = NullPointerException("oops")
        val info = exception.toThrowableInfo(maxLines = 10)
        assertEquals("NullPointerException", info.type)
    }

    @Test
    fun `toThrowableInfo extracts message`() {
        val exception = IllegalArgumentException("bad input")
        val info = exception.toThrowableInfo(maxLines = 10)
        assertEquals("bad input", info.message)
    }

    @Test
    fun `toThrowableInfo handles null message`() {
        val exception = RuntimeException()
        val info = exception.toThrowableInfo(maxLines = 10)
        assertNull(info.message)
    }

    @Test
    fun `toThrowableInfo limits stack trace lines`() {
        val exception = RuntimeException("deep stack")
        val info = exception.toThrowableInfo(maxLines = 3)
        assertTrue(info.stackTrace.size <= 3)
    }

    @Test
    fun `toThrowableInfo captures stack trace content`() {
        val exception = RuntimeException("test")
        val info = exception.toThrowableInfo(maxLines = 50)
        assertTrue(info.stackTrace.isNotEmpty())
        assertTrue(info.stackTrace.any { it.contains("RuntimeException") || it.contains("ThrowableExtTest") })
    }

    @Test
    fun `toThrowableInfo handles nested exception`() {
        val cause = IOException("network error")
        val exception = RuntimeException("wrapped", cause)
        val info = exception.toThrowableInfo(maxLines = 50)
        assertEquals("RuntimeException", info.type)
        assertEquals("wrapped", info.message)
    }
}

// Needed because IOException is in java.io
private typealias IOException = java.io.IOException
