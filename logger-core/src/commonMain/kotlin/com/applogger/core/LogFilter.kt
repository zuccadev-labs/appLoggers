package com.applogger.core

import com.applogger.core.model.LogEvent

/**
 * Decide si un LogEvent debe ser procesado o descartado.
 * Los filtros son componibles mediante [ChainedLogFilter].
 */
interface LogFilter {
    fun passes(event: LogEvent): Boolean
}

/**
 * Compone múltiples filtros: un evento pasa solo si pasa TODOS los filtros.
 */
class ChainedLogFilter(private val filters: List<LogFilter>) : LogFilter {
    override fun passes(event: LogEvent): Boolean =
        filters.all { it.passes(event) }
}
