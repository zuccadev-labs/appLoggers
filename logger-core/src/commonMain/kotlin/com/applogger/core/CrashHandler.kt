package com.applogger.core

/**
 * Platform-specific handler for uncaught exceptions.
 *
 * Once [install]ed, captures unhandled crashes and logs them as
 * [com.applogger.core.model.LogLevel.CRITICAL] events before re-throwing.
 *
 * @see com.applogger.core.AndroidCrashHandler for the Android implementation.
 * @see com.applogger.core.IosCrashHandler for the iOS implementation.
 */
interface CrashHandler {

    /** Registers this handler as the global uncaught exception handler. */
    fun install()

    /** Restores the previous uncaught exception handler. */
    fun uninstall()
}
