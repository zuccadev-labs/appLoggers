package com.applogger.core.internal

// Kotlin/Native new memory model: coroutine-level serialization is sufficient
internal actual inline fun <T> platformSynchronized(lock: Any, block: () -> T): T = block()
