package com.applogger.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers

/**
 * Captura de UncaughtExceptions en JVM.
 */
internal class JvmCrashHandler(
    private val logger: AppLogger
) : CrashHandler {

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    override fun install() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logger.critical("CRASH", "Uncaught exception in thread: ${thread.name}", throwable)
                runBlocking(Dispatchers.IO) {
                    logger.flush()
                }
            } catch (_: Exception) {
                // Never aggravate a crash
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun uninstall() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }
}
