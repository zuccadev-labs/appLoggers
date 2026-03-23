package com.applogger.test

import com.applogger.core.AppLogger

/**
 * [AppLogger] that silently discards all events.
 *
 * Use this in tests where a logger is a required dependency but is not
 * the subject under test. For tests that need to assert on logged events,
 * use [InMemoryLogger] instead.
 *
 * This is a public alias for the internal `NoOpLogger` in `logger-core`,
 * exposed here so test modules don't need to depend on internal APIs.
 */
class NoOpTestLogger : AppLogger {
    override fun debug(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) = Unit
    override fun info(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) = Unit
    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?,
        anomalyType: String?,
        extra: Map<String, Any>?
    ) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) = Unit
    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) = Unit
    override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) = Unit
    override fun flush() = Unit
    override fun addGlobalExtra(key: String, value: String) = Unit
    override fun removeGlobalExtra(key: String) = Unit
    override fun clearGlobalExtra() = Unit
}
