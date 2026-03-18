package com.applogger.core.internal

import com.applogger.core.*
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.math.min
import kotlin.random.Random

/**
 * Core pipeline: buffers events and delivers them in batches with
 * exponential backoff + jitter on transport failure.
 */
internal class BatchProcessor(
    private val buffer: LogBuffer,
    private val transport: LogTransport,
    @Suppress("UnusedPrivateProperty")
    private val formatter: LogFormatter,
    private val config: AppLoggerConfig,
    private val maxRetries: Int = 5,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L
) {
    private val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("AppLogger-Processor")
    )

    private val eventChannel = Channel<LogEvent>(capacity = Channel.BUFFERED)
    private var consecutiveFailures = 0
    internal val deadLetterQueue = DeadLetterQueue()

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
            batch.forEach { buffer.push(it) }
            return
        }

        val result = runCatching { transport.send(batch) }
            .getOrElse { TransportResult.Failure(it.message ?: "unknown", retryable = true, cause = it) }

        when (result) {
            is TransportResult.Success -> consecutiveFailures = 0
            is TransportResult.Failure -> handleFailure(batch, result)
        }
    }

    private suspend fun handleFailure(batch: List<LogEvent>, result: TransportResult.Failure) {
        if (config.verboseTransportLogging) {
            platformLog("AppLogger", "Transport failed: ${result.reason}")
        }
        if (!result.retryable) return
        consecutiveFailures++
        if (consecutiveFailures <= maxRetries) {
            batch.forEach { buffer.push(it) }
            val delayMs = backoffWithJitter(consecutiveFailures)
            if (config.verboseTransportLogging) {
                platformLog("AppLogger", "Retry #$consecutiveFailures in ${delayMs}ms")
            }
            delay(delayMs)
        } else {
            deadLetterQueue.enqueue(batch, result.reason)
            if (config.verboseTransportLogging) {
                platformLog("AppLogger", "Max retries exhausted, ${batch.size} events moved to DLQ")
            }
            consecutiveFailures = 0
        }
    }

    /** Exponential backoff with full jitter: random(0, min(cap, base * 2^attempt)). */
    private fun backoffWithJitter(attempt: Int): Long {
        val exponential = baseDelayMs * (1L shl min(attempt, 20))
        val capped = min(exponential, maxDelayMs)
        return Random.nextLong(capped / 2, capped)
    }

    fun flush() {
        scope.launch { sendBatch() }
    }

    fun shutdown() {
        eventChannel.close()
        scope.cancel()
    }
}
