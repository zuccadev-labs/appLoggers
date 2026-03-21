package com.applogger.core.internal

import com.applogger.core.AppLogger

/**
 * Implementación vacía de AppLogger.
 * Se usa como estado por defecto del SDK antes de initialize().
 * Garantiza que llamadas tempranas no crasheen.
 */
internal class NoOpLogger : AppLogger {
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
}
