package com.applogger.core.internal

import com.applogger.core.model.ThrowableInfo

/**
 * Convierte un Throwable a ThrowableInfo con stack trace limitado.
 */
internal fun Throwable.toThrowableInfo(maxLines: Int): ThrowableInfo {
    return ThrowableInfo(
        type = this::class.simpleName ?: "Unknown",
        message = this.message,
        stackTrace = this.stackTraceToString()
            .lines()
            .take(maxLines)
    )
}
