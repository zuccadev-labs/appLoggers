package com.applogger.test

import com.applogger.core.AppLogger

/**
 * Logger que descarta todos los eventos. Útil para tests donde
 * el logger es un parámetro requerido pero no es el foco del test.
 */
class NoOpTestLogger : AppLogger {
    override fun debug(tag: String, message: String, extra: Map<String, Any>?) = Unit
    override fun info(tag: String, message: String, extra: Map<String, Any>?) = Unit
    override fun warn(tag: String, message: String, anomalyType: String?, extra: Map<String, Any>?) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) = Unit
    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) = Unit
    override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) = Unit
    override fun flush() = Unit
}
