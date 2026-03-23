package com.applogger.core.internal

import com.applogger.core.LogFilter
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import kotlin.concurrent.Volatile

/**
 * Limitador de rate por tag con bypass automático para ERROR y CRITICAL.
 *
 * La clave de conteo incluye el sessionId para aislar correctamente
 * múltiples proyectos o sesiones concurrentes en el mismo proceso.
 *
 * Thread-safe: todas las mutaciones sobre [counters] y [lastResetTime]
 * están protegidas por [lock]. El check-then-act de reset+incremento
 * es atómico dentro del mismo bloque sincronizado.
 */
internal class RateLimitFilter(
    private val maxEventsPerMinutePerTag: Int = 60
) : LogFilter {

    // Key: "sessionId:tag" — isolates rate limit per session/project
    private val counters = mutableMapOf<String, Int>()
    private val lock = Any()

    @Volatile
    private var lastResetTime: Long = 0L

    override fun passes(event: LogEvent): Boolean {
        // ERROR y CRITICAL siempre pasan — sin adquirir lock
        if (event.level == LogLevel.ERROR || event.level == LogLevel.CRITICAL) return true

        val now = com.applogger.core.currentTimeMillis()

        return platformSynchronized(lock) {
            // Reset atómico: check + clear dentro del mismo bloque
            if (now - lastResetTime > 60_000L) {
                counters.clear()
                lastResetTime = now
            }

            val key = "${event.sessionId}:${event.tag}"
            val count = counters.getOrPut(key) { 0 }
            if (count < maxEventsPerMinutePerTag) {
                counters[key] = count + 1
                true
            } else {
                false
            }
        }
    }
}
