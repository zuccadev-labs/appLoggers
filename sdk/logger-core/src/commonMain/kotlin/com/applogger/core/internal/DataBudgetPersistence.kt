package com.applogger.core.internal

/**
 * Persistence contract for [DataBudgetManager].
 *
 * Implementations must be thread-safe. The manager calls [save] on every
 * [DataBudgetManager.recordBytesSent] invocation so the counter survives
 * process restarts within the same UTC day.
 */
internal interface DataBudgetPersistence {
    fun loadBytesUsed(): Long
    fun loadDayIndex(): Int
    fun save(bytesUsed: Long, dayIndex: Int)
}
