package com.applogger.core.internal

import com.applogger.core.*
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.math.min
import kotlin.random.Random

/**
 * Core pipeline: buffers events and delivers them in batches with
 * exponential backoff + jitter on transport failure.
 *
 * Cuando [offlineStorage] está configurado (offlinePersistenceMode != NONE):
 * - Al inicio, drena SQLite y reencola los eventos persistidos.
 * - Cuando se agotan los reintentos, persiste en SQLite en lugar de solo DLQ.
 *   Con CRITICAL_ONLY solo persiste ERROR y CRITICAL; con ALL persiste todo.
 */
internal class BatchProcessor(
    private val buffer: LogBuffer,
    private val transport: LogTransport,
    @Suppress("UnusedPrivateProperty")
    private val formatter: LogFormatter,
    private val config: AppLoggerConfig,
    private val offlineStorage: OfflineStorage = NoOpOfflineStorage,
    private val maxRetries: Int = 5,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L
) {
    private val scope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("AppLogger-Processor")
    )

    private val eventChannel = Channel<LogEvent>(capacity = Channel.BUFFERED)
    /** Prevents concurrent batch sends from overlapping (B4). */
    private val sendMutex = Mutex()
    private var consecutiveFailures = 0
    @Volatile private var lastSuccessfulFlushTimestamp: Long = 0L
    internal val deadLetterQueue = DeadLetterQueue()

    /** Exposes current consecutive failure count for health monitoring. */
    internal fun getConsecutiveFailures(): Int = consecutiveFailures

    /** Exposes timestamp of last successful transport flush for health monitoring. */
    internal fun getLastSuccessfulFlushTimestamp(): Long = lastSuccessfulFlushTimestamp

    init {
        scope.launch { drainOfflineStorage() }
        scope.launch { startConsuming() }
        scope.launch { startPeriodicFlush() }
    }

    fun enqueue(event: LogEvent) {
        val accepted = eventChannel.trySend(event).isSuccess
        if (!accepted && config.verboseTransportLogging) {
            platformLog("AppLogger", "Event dropped: channel at capacity")
        }
    }

    /**
     * Al arrancar, recupera eventos persistidos offline y los reencola.
     * Espera a que el transport esté disponible antes de intentar enviar.
     */
    private suspend fun drainOfflineStorage() {
        if (config.offlinePersistenceMode == OfflinePersistenceMode.NONE) return
        val stored = offlineStorage.drain(limit = 500)
        if (stored.isEmpty()) return
        if (config.verboseTransportLogging) {
            platformLog("AppLogger", "Recovered ${stored.size} events from offline storage")
        }
        stored.forEach { buffer.push(it) }
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

    internal suspend fun sendBatch() = sendMutex.withLock {
        val batch = buffer.drain()
        if (batch.isEmpty()) return

        if (!transport.isAvailable()) {
            // Sin conectividad: persiste offline si está configurado, sino devuelve al buffer
            persistOrRequeue(batch)
            return
        }

        val result = runCatching { transport.send(batch) }
            .getOrElse { TransportResult.Failure(it.message ?: "unknown", retryable = true, cause = it) }

        when (result) {
            is TransportResult.Success -> {
                consecutiveFailures = 0
                lastSuccessfulFlushTimestamp = currentTimeMillis()
            }
            is TransportResult.Failure -> handleFailure(batch, result)
        }
    }

    private fun persistOrRequeue(batch: List<LogEvent>) {
        when (config.offlinePersistenceMode) {
            OfflinePersistenceMode.NONE -> batch.forEach { buffer.push(it) }
            OfflinePersistenceMode.CRITICAL_ONLY -> {
                val critical = batch.filter {
                    it.level == LogLevel.ERROR || it.level == LogLevel.CRITICAL
                }
                val rest = batch.filter {
                    it.level != LogLevel.ERROR && it.level != LogLevel.CRITICAL
                }
                if (critical.isNotEmpty()) offlineStorage.persist(critical)
                rest.forEach { buffer.push(it) }
            }
            OfflinePersistenceMode.ALL -> offlineStorage.persist(batch)
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
            // Respect Retry-After from server (e.g. HTTP 429) over our own backoff
            val delayMs = result.retryAfterMs ?: backoffWithJitter(consecutiveFailures)
            if (config.verboseTransportLogging) {
                platformLog("AppLogger", "Retry #$consecutiveFailures in ${delayMs}ms")
            }
            delay(delayMs)
        } else {
            // Reintentos agotados: persiste offline si está configurado, sino DLQ en memoria.
            // NO reseteamos consecutiveFailures aquí — el transporte sigue caído.
            // Se resetea solo cuando un send() tiene éxito.
            if (config.offlinePersistenceMode != OfflinePersistenceMode.NONE) {
                persistOrRequeue(batch)
            } else {
                deadLetterQueue.enqueue(batch, result.reason)
            }
            if (config.verboseTransportLogging) {
                platformLog("AppLogger", "Max retries exhausted, ${batch.size} events persisted/DLQ")
            }
            // Reset solo el contador de intentos del batch actual, no el estado global de fallos.
            // Esto permite que el próximo batch empiece con backoff máximo, no desde cero.
            consecutiveFailures = maxRetries
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
