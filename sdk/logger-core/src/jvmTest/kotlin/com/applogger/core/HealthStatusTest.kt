package com.applogger.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HealthStatusTest {

    private fun buildHealth(snapshotTimestamp: Long) = HealthStatus(
        isInitialized = true,
        transportAvailable = true,
        bufferedEvents = 0,
        deadLetterCount = 0,
        consecutiveFailures = 0,
        snapshotTimestamp = snapshotTimestamp
    )

    @Test
    fun `isStale returns false for fresh snapshot`() {
        val health = buildHealth(currentTimeMillis())
        assertFalse(health.isStale())
    }

    @Test
    fun `isStale returns true when snapshot exceeds default threshold`() {
        val twoMinutesAgo = currentTimeMillis() - 120_000L
        val health = buildHealth(twoMinutesAgo)
        assertTrue(health.isStale())
    }

    @Test
    fun `isStale respects custom maxAgeMs`() {
        val fiveSecondsAgo = currentTimeMillis() - 5_000L
        val health = buildHealth(fiveSecondsAgo)
        // Not stale with 10s threshold
        assertFalse(health.isStale(maxAgeMs = 10_000L))
        // Stale with 3s threshold
        assertTrue(health.isStale(maxAgeMs = 3_000L))
    }

    @Test
    fun `isStale returns false when snapshotTimestamp is zero`() {
        // Zero means snapshot was never taken — not considered stale
        val health = buildHealth(snapshotTimestamp = 0L)
        assertFalse(health.isStale())
    }

    @Test
    fun `snapshot from AppLoggerHealth has non-zero timestamp`() {
        val health = AppLoggerHealth.snapshot()
        assertTrue(health.snapshotTimestamp > 0L)
        assertFalse(health.isStale())
    }

    @Test
    fun `lastSuccessfulFlushTimestamp defaults to zero`() {
        val health = buildHealth(snapshotTimestamp = currentTimeMillis())
        assertEquals(0L, health.lastSuccessfulFlushTimestamp)
    }

    @Test
    fun `lastSuccessfulFlushTimestamp can detect silent outage`() {
        val twoHoursAgo = currentTimeMillis() - 7_200_000L
        val health = HealthStatus(
            isInitialized = true,
            transportAvailable = true,
            bufferedEvents = 50,
            deadLetterCount = 0,
            consecutiveFailures = 0,
            snapshotTimestamp = currentTimeMillis(),
            lastSuccessfulFlushTimestamp = twoHoursAgo
        )
        // SDK appears healthy but hasn't flushed in 2 hours — silent outage
        assertTrue(health.lastSuccessfulFlushTimestamp > 0L)
        val silentOutage = (currentTimeMillis() - health.lastSuccessfulFlushTimestamp) > 3_600_000L
        assertTrue(silentOutage)
    }

    // --- AppLoggerHealthProvider interface tests (C5) ---

    @Test
    fun `AppLoggerHealth implements AppLoggerHealthProvider`() {
        val provider: AppLoggerHealthProvider = AppLoggerHealth
        val snapshot = provider.snapshot()
        assertNotNull(snapshot)
    }

    @Test
    fun `fake health provider can be injected for testing`() {
        val fakeStatus = HealthStatus(
            isInitialized = true,
            transportAvailable = false,
            bufferedEvents = 42,
            deadLetterCount = 3,
            consecutiveFailures = 5,
            snapshotTimestamp = currentTimeMillis()
        )
        val fakeProvider: AppLoggerHealthProvider = object : AppLoggerHealthProvider {
            override fun snapshot() = fakeStatus
        }

        val result = fakeProvider.snapshot()
        assertEquals(42, result.bufferedEvents)
        assertEquals(3, result.deadLetterCount)
        assertEquals(5, result.consecutiveFailures)
        assertFalse(result.transportAvailable)
    }

    @Test
    fun `AppLoggerHealthProvider can be used as dependency in production code`() {
        // Simulates a ViewModel or service that depends on the interface, not the object
        class HealthMonitor(private val provider: AppLoggerHealthProvider) {
            fun isHealthy(): Boolean {
                val s = provider.snapshot()
                return s.isInitialized && s.transportAvailable && s.consecutiveFailures == 0
            }
        }

        val monitor = HealthMonitor(AppLoggerHealth)
        // Just verify it doesn't throw — actual value depends on SDK state
        assertNotNull(monitor.isHealthy())
    }
}
