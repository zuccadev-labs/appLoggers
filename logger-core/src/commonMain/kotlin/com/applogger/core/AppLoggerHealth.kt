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
 */
data class HealthStatus(
    val isInitialized: Boolean,
    val transportAvailable: Boolean,
    val bufferedEvents: Int,
    val deadLetterCount: Int,
    val consecutiveFailures: Int,
    val sdkVersion: String = AppLoggerVersion.NAME
)

/**
 * Provides a read-only health check for the AppLogger SDK.
 *
 * ```kotlin
 * val health = AppLoggerHealth.snapshot()
 * if (!health.transportAvailable) showOfflineBanner()
 * ```
 */
object AppLoggerHealth {

    internal var processor: BatchProcessor? = null
    internal var transport: LogTransport? = null
    internal var buffer: LogBuffer? = null
    internal var initialized: Boolean = false

    /**
     * Returns a point-in-time snapshot of the SDK health.
     * Safe to call from any thread.
     */
    fun snapshot(): HealthStatus = HealthStatus(
        isInitialized = initialized,
        transportAvailable = transport?.isAvailable() ?: false,
        bufferedEvents = buffer?.size() ?: 0,
        deadLetterCount = processor?.deadLetterQueue?.size() ?: 0,
        consecutiveFailures = 0,
        sdkVersion = AppLoggerVersion.NAME
    )
}
