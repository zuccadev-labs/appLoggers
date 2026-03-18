package com.applogger.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.applogger.core.internal.BatchProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Flush automático cuando la app entra en background.
 */
internal class AppLoggerLifecycleObserver(
    private val onFlush: () -> Unit
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                onFlush()
            } catch (_: Exception) {
                // Silenciar errores en background flush
            }
        }
    }
}
