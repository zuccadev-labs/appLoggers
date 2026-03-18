package com.applogger.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSSetUncaughtExceptionHandler

/**
 * Captura de crashes en iOS usando NSSetUncaughtExceptionHandler.
 */
internal class IosCrashHandler(
    private val logger: AppLogger
) : CrashHandler {

    override fun install() {
        NSSetUncaughtExceptionHandler { exception ->
            exception?.let { nsEx ->
                try {
                    logger.critical(
                        tag = "IOS_CRASH",
                        message = nsEx.reason ?: "NSException: ${nsEx.name}"
                    )
                    runBlocking(Dispatchers.Default) {
                        logger.flush()
                    }
                } catch (_: Exception) {
                    // Never aggravate a crash
                }
            }
        }
    }

    override fun uninstall() {
        NSSetUncaughtExceptionHandler(null)
    }
}
