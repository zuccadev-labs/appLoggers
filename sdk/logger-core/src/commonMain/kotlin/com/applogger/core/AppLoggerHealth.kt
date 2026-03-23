package com.applogger.core

import com.applogger.core.internal.BatchProcessor

/**
 * Read-only snapshot of the SDK's internal health state.
 *
 * Obtain via [AppLoggerHealth.snapshot].
 *
 * @property isInitialized     True after successful SDK initialization.
 * @property transportAvailable True if the current transport reports connectivity.
 * @property bufferedEvents    Number of events waiting in the send buffer.
 * @property deadLetterCount   Number of events in the dead letter queue.
 * @property consecutiveFailures Current consecutive transport failure count.
 * @property sdkVersion        Embedded SDK version string.
 * @property eventsDroppedDueToBufferOverflow Total count of events discarded due to buffer overflow.
 * @property bufferUtilizationPercentage Current buffer fill percentage (0-100).
 * @property snapshotTimestamp Unix epoch millis when this snapshot was taken.
 * @property lastSuccessfulFlushTimestamp Unix epoch millis of the last successful transport flush.
 *           Zero if no flush has succeeded yet. Use to detect silent outages:
 *           if `currentTime - lastSuccessfulFlushTimestamp > threshold` the SDK may be stuck.
 */
data class HealthStatus(
    val isInitialized: Boolean,
    val transportAvailable: Boolean,
    val bufferedEvents: Int,
    val deadLetterCount: Int,
    val consecutiveFailures: Int,
    val sdkVersion: String = AppLoggerVersion.NAME,
    val eventsDroppedDueToBufferOverflow: Long = 0,
    val bufferUtilizationPercentage: Float = 0f,
    val snapshotTimestamp: Long = 0L,
    val lastSuccessfulFlushTimestamp: Long = 0L
) {
    /**
     * Returns `true` if this snapshot is older than [maxAgeMs] milliseconds.
     *
     * A stale snapshot may not reflect the current SDK state. The recommended
     * threshold for dashboards is 60 seconds; for alerting, 10 seconds.
     *
     * ```kotlin
     * val health = AppLoggerHealth.snapshot()
     * if (health.isStale()) {
     *     // Re-fetch or show a warning that data may be outdated
     * }
     * ```
     *
     * @param maxAgeMs Maximum acceptable age in milliseconds. Default: 60_000 (1 minute).
     */
    fun isStale(maxAgeMs: Long = 60_000L): Boolean =
        snapshotTimestamp > 0L && (currentTimeMillis() - snapshotTimestamp) > maxAgeMs
}

/**
 * Provides a read-only health check for the AppLogger SDK.
 *
 * Implement this interface to inject a fake/stub in tests:
 * ```kotlin
 * class FakeHealthProvider(private val status: HealthStatus) : AppLoggerHealthProvider {
 *     override fun snapshot() = status
 * }
 * ```
 */
interface AppLoggerHealthProvider {
    /** Returns a point-in-time snapshot of the SDK health. Safe to call from any thread. */
    fun snapshot(): HealthStatus
}

/**
 * Provides a read-only health check for the AppLogger SDK.
 *
 * ```kotlin
 * val health = AppLoggerHealth.snapshot()
 * if (!health.transportAvailable) showOfflineBanner()
 * ```
 */
object AppLoggerHealth : AppLoggerHealthProvider {

    internal var processor: BatchProcessor? = null
    internal var transport: LogTransport? = null
    internal var buffer: com.applogger.core.internal.InMemoryBuffer? = null
    internal var bufferCapacity: Int = 1000
    internal var initialized: Boolean = false

    /**
     * Returns a point-in-time snapshot of the SDK health.
     * Safe to call from any thread.
     */
    override fun snapshot(): HealthStatus = HealthStatus(
        isInitialized = initialized,
        transportAvailable = transport?.isAvailable() ?: false,
        bufferedEvents = buffer?.size() ?: 0,
        deadLetterCount = processor?.deadLetterQueue?.size() ?: 0,
        consecutiveFailures = processor?.getConsecutiveFailures() ?: 0,
        sdkVersion = AppLoggerVersion.NAME,
        eventsDroppedDueToBufferOverflow = buffer?.getOverflowCount() ?: 0,
        bufferUtilizationPercentage = if (bufferCapacity > 0) {
            (buffer?.size()?.toFloat() ?: 0f) / bufferCapacity * 100f
        } else {
            0f
        },
        snapshotTimestamp = currentTimeMillis(),
        lastSuccessfulFlushTimestamp = processor?.getLastSuccessfulFlushTimestamp() ?: 0L
    )
}
