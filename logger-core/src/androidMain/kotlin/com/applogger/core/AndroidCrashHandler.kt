package com.applogger.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers

/**
 * Captura UncaughtExceptions con flush síncrono antes de morir.
 * Siempre encadena el handler previo para no romper Crashlytics u otros.
 */
internal class AndroidCrashHandler(
    private val logger: AppLogger
) : CrashHandler, Thread.UncaughtExceptionHandler {

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    override fun install() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uninstall() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            logger.critical("CRASH", "Uncaught exception in thread: ${thread.name}", throwable)
            // runBlocking justificado: es el último acto del proceso
            runBlocking(Dispatchers.IO) {
                logger.flush()
            }
        } catch (_: Exception) {
            // El SDK nunca agrava un crash
        }
        previousHandler?.uncaughtException(thread, throwable)
    }
}
