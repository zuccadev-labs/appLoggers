package com.applogger.core.internal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ThrowableExtTest {

    @Test
    fun `toThrowableInfo extracts type correctly`() {
        val exception = NullPointerException("oops")
        val info = exception.toThrowableInfo(maxLines = 10)
        // Fully-qualified name — distinguishes same simpleName in different packages
        assertEquals("java.lang.NullPointerException", info.type)
    }

    @Test
    fun `toThrowableInfo uses fully qualified name for java io exceptions`() {
        val exception = java.io.IOException("network error")
        val info = exception.toThrowableInfo(maxLines = 10)
        assertEquals("java.io.IOException", info.type)
    }

    @Test
    fun `toThrowableInfo uses fully qualified name for custom exceptions`() {
        class CustomDomainException(msg: String) : Exception(msg)
        val exception = CustomDomainException("domain error")
        val info = exception.toThrowableInfo(maxLines = 10)
        // qualifiedName includes enclosing class for local classes
        assertTrue(info.type.contains("CustomDomainException"), "type was: ${info.type}")
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
        // toThrowableInfo uses qualifiedName (fully-qualified) by design
        assertEquals("java.lang.RuntimeException", info.type)
        assertEquals("wrapped", info.message)
    }
}

// Needed because IOException is in java.io
private typealias IOException = java.io.IOException
