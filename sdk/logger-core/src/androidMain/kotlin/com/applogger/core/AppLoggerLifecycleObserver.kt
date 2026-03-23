package com.applogger.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Flush automático + notificación de background cuando la app entra en background (onStop).
 */
internal class AppLoggerLifecycleObserver(
    private val onBackground: () -> Unit
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                onBackground()
            } catch (_: Exception) {
                // Silenciar errores en background flush
            }
        }
    }
}

/**
 * Notificación de foreground para rotación de sesión por timeout (onStart).
 */
internal class AppLoggerForegroundObserver(
    private val onForeground: () -> Unit
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        try {
            onForeground()
        } catch (_: Exception) {
            // Silenciar errores en foreground callback
        }
    }
}
