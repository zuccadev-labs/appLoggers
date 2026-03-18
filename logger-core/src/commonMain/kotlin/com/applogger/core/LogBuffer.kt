package com.applogger.core

import com.applogger.core.model.LogEvent

/**
 * Almacenamiento temporal de LogEvent antes de su envío al transporte.
 *
 * Contrato:
 * - [push] debe ser thread-safe.
 * - [push] retorna false si el evento fue descartado (política de overflow).
 * - [drain] retorna todos los eventos y limpia el buffer.
 */
interface LogBuffer {
    fun push(event: LogEvent): Boolean
    fun drain(): List<LogEvent>
    fun peek(): List<LogEvent>
    fun size(): Int
    fun clear()
    fun isEmpty(): Boolean = size() == 0
}
