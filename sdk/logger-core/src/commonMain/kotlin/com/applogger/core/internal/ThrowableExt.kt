package com.applogger.core.internal

import com.applogger.core.model.ThrowableInfo

/**
 * Convierte un Throwable a ThrowableInfo con stack trace limitado.
 *
 * Usa el fully-qualified class name para distinguir excepciones con el mismo
 * simpleName en paquetes distintos (e.g. java.io.IOException vs okhttp3.internal.IOException).
 */
internal fun Throwable.toThrowableInfo(maxLines: Int): ThrowableInfo {
    return ThrowableInfo(
        type = this::class.qualifiedName ?: this::class.simpleName ?: "Unknown",
        message = this.message,
        stackTrace = this.stackTraceToString()
            .lines()
            .take(maxLines)
    )
}
