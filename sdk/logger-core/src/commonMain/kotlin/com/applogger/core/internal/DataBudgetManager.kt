package com.applogger.core.internal

import com.applogger.core.currentTimeMillis
import kotlin.concurrent.Volatile

/**
 * Enforces a daily data budget: once [limitBytes] is reached, non-critical events
 * are shed until the next UTC day resets the counter.
 *
 * Thread-safe via [platformSynchronized].
 */
internal class DataBudgetManager(private val limitBytes: Long) {
    companion object {
        internal const val DISABLED = 0L
    }

    private val lock = Any()

    @Volatile private var bytesUsedToday: Long = 0L
    @Volatile private var currentDay: Int = todayIndex()

    val isEnabled: Boolean get() = limitBytes > DISABLED
    val shouldShedLowPriority: Boolean
        get() = isEnabled && platformSynchronized(lock) {
            rollDayIfNeeded()
            bytesUsedToday >= limitBytes
        }

    fun recordBytesSent(estimatedBytes: Long) {
        if (!isEnabled) return
        platformSynchronized(lock) {
            rollDayIfNeeded()
            bytesUsedToday += estimatedBytes
        }
    }

    fun usageSummary(): DataBudgetSummary = platformSynchronized(lock) {
        rollDayIfNeeded()
        DataBudgetSummary(limitBytes, bytesUsedToday, isEnabled && bytesUsedToday >= limitBytes)
    }

    private fun rollDayIfNeeded() {
        val today = todayIndex()
        if (today != currentDay) {
            bytesUsedToday = 0L
            currentDay = today
        }
    }
}

internal data class DataBudgetSummary(val limitBytes: Long, val usedBytes: Long, val shedding: Boolean)

private const val MS_PER_DAY = 86_400_000L
private const val DAYS_PER_YEAR = 365L

private fun todayIndex(): Int = ((currentTimeMillis() / MS_PER_DAY) % DAYS_PER_YEAR).toInt()
