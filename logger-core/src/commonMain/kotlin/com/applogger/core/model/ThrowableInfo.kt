package com.applogger.core.model

import kotlinx.serialization.Serializable

/**
 * Serializable snapshot of a caught [Throwable].
 *
 * Stack traces are truncated to [com.applogger.core.AppLoggerConfig.maxStackTraceLines].
 *
 * @property type       Fully-qualified exception class name (e.g. "java.io.IOException").
 * @property message    Exception message, may be null.
 * @property stackTrace List of stack frame strings, most recent first.
 */
@Serializable
data class ThrowableInfo(
    val type: String,
    val message: String?,
    val stackTrace: List<String>
)
