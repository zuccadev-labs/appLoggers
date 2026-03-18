package com.applogger.core.internal

internal actual inline fun <T> platformSynchronized(lock: Any, block: () -> T): T =
    synchronized(lock, block)
