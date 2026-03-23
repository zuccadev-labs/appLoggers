package com.applogger.core.internal

import com.applogger.core.generateUUID
import kotlin.concurrent.Volatile

/**
 * Gestiona el ciclo de vida de la sesión del SDK.
 *
 * Una sesión se rota automáticamente cuando la app vuelve a foreground
 * después de [sessionTimeoutMs] milisegundos en background (default: 30 min).
 * También puede rotarse manualmente con [rotate].
 *
 * Thread-safe: [sessionId] es @Volatile — lecturas siempre ven el valor más reciente.
 */
internal class SessionManager(
    private val sessionTimeoutMs: Long = 30 * 60 * 1_000L // 30 minutos
) {
    @Volatile
    var sessionId: String = generateUUID()
        private set

    @Volatile
    private var backgroundedAt: Long = 0L

    /**
     * Llamar cuando la app pasa a background.
     * Registra el timestamp para calcular el tiempo fuera de foreground.
     */
    fun onBackground() {
        backgroundedAt = com.applogger.core.currentTimeMillis()
    }

    /**
     * Llamar cuando la app vuelve a foreground.
     * Si el tiempo en background superó [sessionTimeoutMs], rota la sesión.
     */
    fun onForeground() {
        val elapsed = com.applogger.core.currentTimeMillis() - backgroundedAt
        if (backgroundedAt > 0L && elapsed >= sessionTimeoutMs) {
            rotate()
        }
    }

    /**
     * Fuerza una nueva sesión inmediatamente.
     * Útil en login/logout, inicio de onboarding, o tests de integración.
     */
    fun rotate() {
        sessionId = generateUUID()
        backgroundedAt = 0L
    }
}
