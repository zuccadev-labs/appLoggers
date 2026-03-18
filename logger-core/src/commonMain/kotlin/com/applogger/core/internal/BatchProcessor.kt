package com.applogger.core.internal

import com.applogger.core.*
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * Corazón del pipeline: acumula eventos y los envía en batches.
 */
internal class BatchProcessor(
    private val buffer: LogBuffer,
    private val transport: LogTransport,
    private val formatter: LogFormatter,
    private val config: AppLoggerConfig
) {
    private val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("AppLogger-Processor")
    )

    private val eventChannel = Channel<LogEvent>(capacity = Channel.BUFFERED)

    init {
        scope.launch { startConsuming() }
        scope.launch { startPeriodicFlush() }
    }

    fun enqueue(event: LogEvent) {
        val accepted = eventChannel.trySend(event).isSuccess
        if (!accepted && config.verboseTransportLogging) {
            platformLog("AppLogger", "Event dropped: channel at capacity")
        }
    }

    private suspend fun startConsuming() {
        for (event in eventChannel) {
            buffer.push(event)

            val shouldFlushImmediately = event.level == LogLevel.ERROR || event.level == LogLevel.CRITICAL
            if (shouldFlushImmediately || buffer.size() >= config.batchSize) {
                sendBatch()
            }
        }
    }

    private suspend fun startPeriodicFlush() {
        while (scope.isActive) {
            delay(config.flushIntervalSeconds * 1_000L)
            if (!buffer.isEmpty()) sendBatch()
        }
    }

    internal suspend fun sendBatch() {
        val batch = buffer.drain()
        if (batch.isEmpty()) return

        if (!transport.isAvailable()) {
            // Sin red — reinsertar en el buffer
            batch.forEach { buffer.push(it) }
            return
        }

        val result = runCatching { transport.send(batch) }
            .getOrElse { TransportResult.Failure(it.message ?: "unknown", retryable = true, cause = it) }

        when (result) {
            is TransportResult.Success -> { /* batch enviado correctamente */ }
            is TransportResult.Failure -> {
                if (result.retryable) {
                    // Reinsertar en buffer para reenvío posterior
                    batch.forEach { buffer.push(it) }
                }
                if (config.verboseTransportLogging) {
                    platformLog("AppLogger", "Transport failed: ${result.reason}")
                }
            }
        }
    }

    fun flush() {
        scope.launch { sendBatch() }
    }

    fun shutdown() {
        eventChannel.close()
        scope.cancel()
    }
}
