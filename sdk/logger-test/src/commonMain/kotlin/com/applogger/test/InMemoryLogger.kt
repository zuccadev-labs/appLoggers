package com.applogger.test

import com.applogger.core.AppLogger
import com.applogger.core.model.LogLevel

/**
 * In-memory [AppLogger] that records every event for test assertions.
 *
 * Provides convenience counters ([debugCount], [errorCount], etc.) and
 * assertion helpers ([assertLogged], [assertNotLogged]).
 *
 * ```kotlin
 * val logger = InMemoryLogger()
 * sut.doWork(logger)
 * logger.assertLogged(LogLevel.ERROR, tag = "Network")
 * ```
 */
class InMemoryLogger : AppLogger {

    data class LogEntry(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
        val extra: Map<String, Any>? = null
    )

    private val _logs = mutableListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs.toList()

    val debugCount get() = _logs.count { it.level == LogLevel.DEBUG }
    val infoCount get() = _logs.count { it.level == LogLevel.INFO }
    val warnCount get() = _logs.count { it.level == LogLevel.WARN }
    val errorCount get() = _logs.count { it.level == LogLevel.ERROR }
    val criticalCount get() = _logs.count { it.level == LogLevel.CRITICAL }
    val metricCount get() = _logs.count { it.level == LogLevel.METRIC }

    val lastError get() = _logs.lastOrNull { it.level == LogLevel.ERROR }
    val lastCritical get() = _logs.lastOrNull { it.level == LogLevel.CRITICAL }

    override fun debug(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        _logs.add(LogEntry(LogLevel.DEBUG, tag, message, throwable, extra))
    }

    override fun info(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        _logs.add(LogEntry(LogLevel.INFO, tag, message, throwable, extra))
    }

    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?,
        anomalyType: String?,
        extra: Map<String, Any>?
    ) {
        _logs.add(LogEntry(LogLevel.WARN, tag, message, throwable, extra))
    }

    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        _logs.add(LogEntry(LogLevel.ERROR, tag, message, throwable, extra))
    }

    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        _logs.add(LogEntry(LogLevel.CRITICAL, tag, message, throwable, extra))
    }

    override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) {
        _logs.add(LogEntry(LogLevel.METRIC, "METRIC", "$name=$value $unit"))
    }

    override fun flush() = Unit

    fun assertLogged(level: LogLevel, tag: String? = null) {
        val found = _logs.any { it.level == level && (tag == null || it.tag == tag) }
        check(found) { "Expected log with level=$level tag=$tag but none found" }
    }

    fun assertNotLogged(level: LogLevel) {
        val found = _logs.any { it.level == level }
        check(!found) { "Expected no log with level=$level but found ${_logs.count { it.level == level }}" }
    }

    fun clear() {
        _logs.clear()
    }
}
