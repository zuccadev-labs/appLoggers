package com.applogger.core

import com.applogger.core.model.LogEvent

/**
 * Defines how a batch of [LogEvent]s is delivered to a remote destination.
 *
 * Implement this interface to create custom transports (e.g. Firebase, gRPC, file-based).
 *
 * **Contract:**
 * - [send] is a suspend function — execute in an appropriate coroutine context.
 * - [send] must **never** throw exceptions; capture internally and return [TransportResult.Failure].
 * - [isAvailable] must be fast (no I/O). Use cached network state.
 *
 * @see SupabaseTransport for the default Supabase HTTP transport.
 */
interface LogTransport {

    /**
     * Sends a batch of events to the remote destination.
     *
     * @param events Non-empty list of events to deliver.
     * @return [TransportResult.Success] or [TransportResult.Failure].
     */
    suspend fun send(events: List<LogEvent>): TransportResult

    /**
     * Quick check whether the transport can currently deliver events.
     * Should use cached network state — no I/O allowed.
     */
    fun isAvailable(): Boolean
}

/**
 * Result of a [LogTransport.send] operation.
 */
sealed class TransportResult {

    /** Batch was delivered successfully. */
    data object Success : TransportResult()

    /**
     * Batch delivery failed.
     *
     * @property reason Human-readable failure description.
     * @property retryable If true, the batch will be re-queued for a later attempt.
     * @property cause Optional underlying exception.
     */
    data class Failure(
        val reason: String,
        val retryable: Boolean,
        val cause: Throwable? = null
    ) : TransportResult()
}
