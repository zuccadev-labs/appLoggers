package com.applogger.core

/**
 * Contrato público de AppLogger.
 *
 * Todas las implementaciones deben garantizar:
 * - Ninguna llamada bloquea el hilo del llamador.
 * - Ninguna llamada lanza excepciones al llamador.
 * - Las llamadas a [debug] no hacen nada en modo producción.
 */
interface AppLogger {
    fun debug(tag: String, message: String, extra: Map<String, Any>? = null)
    fun info(tag: String, message: String, extra: Map<String, Any>? = null)
    fun warn(tag: String, message: String, anomalyType: String? = null, extra: Map<String, Any>? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun critical(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun metric(name: String, value: Double, unit: String, tags: Map<String, String>? = null)
    fun flush()
}
