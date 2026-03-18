package com.applogger.core.internal

internal expect inline fun <T> platformSynchronized(lock: Any, block: () -> T): T
