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
                    // callStackSymbols contiene el stack trace nativo de iOS
                    val stackFrames = nsEx.callStackSymbols
                        ?.filterIsInstance<String>()
                        ?.take(50)
                        ?: emptyList()

                    val crashMessage = buildString {
                        append(nsEx.reason ?: "NSException: ${nsEx.name}")
                        if (stackFrames.isNotEmpty()) {
                            append("\n")
                            append(stackFrames.joinToString("\n"))
                        }
                    }

                    logger.critical(
                        tag = "IOS_CRASH",
                        message = crashMessage
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
