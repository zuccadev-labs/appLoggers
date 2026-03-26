package com.applogger.core.internal

import platform.Foundation.NSRecursiveLock

private val globalLock = NSRecursiveLock()
private val lockMap = mutableMapOf<Any, NSRecursiveLock>()

/**
 * iOS actual: real mutual exclusion using NSRecursiveLock.
 *
 * Each lock object is mapped to its own NSRecursiveLock instance (created lazily).
 * The global lock protects only the lookup — actual block execution holds only
 * the per-object lock, so contention between different lock objects is zero.
 */
internal actual inline fun <T> platformSynchronized(lock: Any, block: () -> T): T {
    val nsLock: NSRecursiveLock
    globalLock.lock()
    try {
        nsLock = lockMap.getOrPut(lock) { NSRecursiveLock() }
    } finally {
        globalLock.unlock()
    }
    nsLock.lock()
    try {
        return block()
    } finally {
        nsLock.unlock()
    }
}
