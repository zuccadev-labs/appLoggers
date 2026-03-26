package com.applogger.core.internal

import com.applogger.core.currentTimeMillis
import kotlin.concurrent.Volatile

/**
 * Enforces a daily data budget: once [limitBytes] is reached, non-critical events
 * are shed until the next UTC day resets the counter.
 *
 * Thread-safe via [platformSynchronized]. When [persistence] is provided, the
 * byte counter survives process restarts within the same UTC day.
 *
 * **Network-type awareness**: when [connectionTypeProvider] returns `"wifi"`, the
 * effective limit is [limitBytes] × [wifiMultiplier] (default 2×). On cellular,
 * the base [limitBytes] applies. This allows the SDK to use more budget on WiFi
 * without penalizing users on mobile data.
 */
internal class DataBudgetManager(
    private val limitBytes: Long,
    private val persistence: DataBudgetPersistence? = null,
    private val connectionTypeProvider: (() -> String)? = null,
    private val wifiMultiplier: Int = DEFAULT_WIFI_MULTIPLIER
) {
    companion object {
        internal const val DISABLED = 0L
        private const val DEFAULT_WIFI_MULTIPLIER = 2
    }

    private val lock = Any()

    @Volatile private var bytesUsedToday: Long = persistence?.loadBytesUsed() ?: 0L
    @Volatile private var currentDay: Int = persistence?.loadDayIndex() ?: todayIndex()

    val isEnabled: Boolean get() = limitBytes > DISABLED

    private fun effectiveLimit(): Long {
        if (connectionTypeProvider == null) return limitBytes
        val connType = runCatching { connectionTypeProvider?.invoke() ?: "" }.getOrElse { "" }
        return if (connType.lowercase().contains("wifi")) limitBytes * wifiMultiplier else limitBytes
    }

    val shouldShedLowPriority: Boolean
        get() = isEnabled && platformSynchronized(lock) {
            rollDayIfNeeded()
            bytesUsedToday >= effectiveLimit()
        }

    fun recordBytesSent(estimatedBytes: Long) {
        if (!isEnabled) return
        platformSynchronized(lock) {
            rollDayIfNeeded()
            val sum = bytesUsedToday + estimatedBytes
            bytesUsedToday = if (sum < bytesUsedToday) Long.MAX_VALUE else sum // overflow guard
            persistence?.save(bytesUsedToday, currentDay)
        }
    }

    fun usageSummary(): DataBudgetSummary = platformSynchronized(lock) {
        rollDayIfNeeded()
        val effective = effectiveLimit()
        DataBudgetSummary(effective, bytesUsedToday, isEnabled && bytesUsedToday >= effective)
    }

    private fun rollDayIfNeeded() {
        val today = todayIndex()
        if (today != currentDay) {
            bytesUsedToday = 0L
            currentDay = today
            persistence?.save(0L, today)
        }
    }
}

internal data class DataBudgetSummary(val limitBytes: Long, val usedBytes: Long, val shedding: Boolean)

private const val MS_PER_DAY = 86_400_000L
private const val DAYS_PER_YEAR = 365L

private fun todayIndex(): Int = ((currentTimeMillis() / MS_PER_DAY) % DAYS_PER_YEAR).toInt()
