package com.applogger.core.internal

import com.applogger.core.model.LogEvent

/**
 * Contrato para persistencia offline de eventos.
 *
 * Implementado por plataforma:
 * - Android/JVM: SQLite via SQLDelight
 * - iOS: SQLite via SQLDelight (native driver)
 * - Stub: NoOpOfflineStorage (cuando offlinePersistenceMode == NONE)
 */
internal interface OfflineStorage {
    /** Persiste un lote de eventos. Silencia errores internamente. */
    fun persist(events: List<LogEvent>)

    /** Recupera y elimina hasta [limit] eventos persistidos (FIFO). */
    fun drain(limit: Int = 200): List<LogEvent>

    /** Número de eventos actualmente persistidos. */
    fun count(): Long

    /** Elimina todos los eventos persistidos. */
    fun clear()
}

/** Implementación vacía para cuando offlinePersistenceMode == NONE. */
internal object NoOpOfflineStorage : OfflineStorage {
    override fun persist(events: List<LogEvent>) = Unit
    override fun drain(limit: Int): List<LogEvent> = emptyList()
    override fun count(): Long = 0L
    override fun clear() = Unit
}
