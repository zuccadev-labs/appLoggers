package com.applogger.core

import com.applogger.core.model.LogEvent

/**
 * Define cómo se envía un batch de LogEvent al destino final.
 *
 * Contrato:
 * - [send] es una función suspend: debe ejecutarse en un coroutine context adecuado.
 * - [send] NUNCA debe lanzar excepciones: capturar internamente y retornar [TransportResult.Failure].
 * - [isAvailable] debe ser rápido (no hacer I/O). Usa el estado de red en caché.
 */
interface LogTransport {
    suspend fun send(events: List<LogEvent>): TransportResult
    fun isAvailable(): Boolean
}

sealed class TransportResult {
    data object Success : TransportResult()
    data class Failure(
        val reason: String,
        val retryable: Boolean,
        val cause: Throwable? = null
    ) : TransportResult()
}
