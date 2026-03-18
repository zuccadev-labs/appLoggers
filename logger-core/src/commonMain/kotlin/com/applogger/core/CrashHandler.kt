package com.applogger.core

/**
 * Define cómo se capturan y manejan los errores no capturados.
 */
interface CrashHandler {
    fun install()
    fun uninstall()
}
