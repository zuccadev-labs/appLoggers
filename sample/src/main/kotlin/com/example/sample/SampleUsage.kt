package com.example.sample

import com.applogger.core.AppLogger

/**
 * Ejemplos de todas las APIs públicas del SDK.
 * Este archivo demuestra cómo usar cada método del logger.
 */
class SampleUsage(private val logger: AppLogger) {

    // ─── Niveles de Log ──────────────────────────

    /** Solo visible en modo debug (Logcat). No se envía a producción. */
    fun exampleDebug() {
        logger.debug("PLAYER", "Buffer state: loading")
        logger.debug("PLAYER", "Con extra data", extra = mapOf("buffer_ms" to 500))
    }

    /** Flujos normales de la app. */
    fun exampleInfo() {
        logger.info("PLAYER", "Playback started")
        logger.info("PLAYER", "Content loaded", extra = mapOf("content_id" to "movie_123"))
    }

    /** Anomalías recuperables — NO errores. */
    fun exampleWarn() {
        logger.warn("NETWORK", "Slow response detected", anomalyType = "HIGH_LATENCY")
        logger.warn("STREAM", "Quality degraded", anomalyType = "QUALITY_DROP",
            extra = mapOf("from" to "1080p", "to" to "480p"))
    }

    /** Fallos que el usuario probablemente nota. */
    fun exampleError() {
        try {
            // Simular operación que falla
            throw java.io.IOException("Connection timeout")
        } catch (e: Exception) {
            logger.error("PAYMENT", "Transaction failed", throwable = e,
                extra = mapOf("payment_method" to "credit_card"))
        }
    }

    /** Fallos que bloquean el uso de la app. */
    fun exampleCritical() {
        try {
            throw IllegalStateException("Database corrupted")
        } catch (e: Exception) {
            logger.critical("AUTH", "Token refresh failed completely", throwable = e)
        }
    }

    /** Métricas de performance — datos cuantitativos. */
    fun exampleMetric() {
        logger.metric("screen_load_time", 1234.0, "ms",
            tags = mapOf("screen" to "HomeScreen"))
        logger.metric("api_response_time", 350.0, "ms",
            tags = mapOf("endpoint" to "/v1/content"))
        logger.metric("memory_usage", 128.0, "MB")
    }

    // ─── Buenas Prácticas ──────────────────────────

    /** Tags consistentes usando constantes. */
    object LogTags {
        const val PLAYER = "PLAYER"
        const val NETWORK = "NETWORK"
        const val AUTH = "AUTH"
        const val PAYMENT = "PAYMENT"
    }

    fun exampleWithConstantTags() {
        logger.info(LogTags.PLAYER, "Started")
        logger.error(LogTags.NETWORK, "API timeout")
    }

    // ─── Lo que NUNCA debes hacer ──────────────────

    // ❌ logger.error("AUTH", "Error for user: ${user.email}")   // PII!
    // ❌ logger.debug("AUTH", "Token: $accessToken")             // Credenciales!
    // ❌ logger.info("TAG", jsonResponse.toString())             // Datos del usuario!
}
