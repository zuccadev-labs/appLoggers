package com.example.sample

import com.applogger.core.AppLogger
import com.applogger.core.*

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

    // ─── withTag — tag fijo para toda la clase ────

    /**
     * [withTag] crea un logger con tag fijo. Elimina repetición de tag en cada llamada.
     * Ideal para repositorios, servicios y ViewModels.
     */
    private val log = logger.withTag("SampleUsage")

    fun exampleWithTag() {
        log.i("Operation started")                          // tag = "SampleUsage"
        log.w("Slow response", anomalyType = "HIGH_LATENCY")
        log.e("Operation failed", RuntimeException("timeout"))
        log.metric("operation_time", 120.0, "ms")
    }

    // ─── timed — medir latencia sin boilerplate ───

    /**
     * [timed] mide el tiempo de ejecución de un bloque y lo registra como métrica.
     * El resultado del bloque se retorna transparentemente.
     */
    fun exampleTimed(): String {
        // Mide la latencia y retorna el resultado
        val result = logger.timed("db_query_user", "ms", mapOf("table" to "users")) {
            "user_data" // Simula una query
        }

        // Con tag inferido automáticamente desde la clase
        this.timed(logger, "api_call_latency", tags = mapOf("endpoint" to "/v1/content")) {
            Thread.sleep(10) // Simula llamada HTTP
        }

        return result
    }

    // ─── logCatching — try/catch sin boilerplate ──

    /**
     * [logCatching] ejecuta un bloque y captura excepciones automáticamente.
     * Retorna null si hay excepción — ya logueada como ERROR.
     */
    fun exampleLogCatching(): String? {
        // Con tag explícito
        val result = logger.logCatching("NETWORK", "fetch user profile") {
            "user_profile" // Simula llamada que puede fallar
        }

        // Con tag inferido desde la clase
        val order = this.logCatching(logger, "submit order",
            extra = mapOf("order_id" to "ORD-123")) {
            null // Simula fallo
        }

        return result
    }

    // ─── loggerTag — constante de tag en companion ─

    /**
     * [loggerTag] genera una constante de tag a partir del nombre de la clase.
     * Patrón recomendado para clases con muchos métodos.
     */
    companion object {
        val TAG = loggerTag<SampleUsage>()  // = "SampleUsage"
    }

    fun exampleLoggerTag() {
        logger.info(TAG, "Using companion tag")
        logger.error(TAG, "Error with companion tag")
    }

    // ─── Global extra — contexto global ──────────

    /**
     * [addGlobalExtra] adjunta contexto a todos los eventos subsiguientes.
     * Ideal para AB tests, feature flags, grupos de experimentos.
     */
    fun exampleGlobalExtra() {
        logger.addGlobalExtra("ab_test", "checkout_v2")
        logger.addGlobalExtra("experiment", "group_b")

        logger.info("CART", "Item added")   // extra incluye ab_test y experiment
        logger.metric("checkout_time", 1200.0, "ms")  // también incluye el contexto

        logger.removeGlobalExtra("experiment")
        logger.clearGlobalExtra()
    }

    // ─── Buenas Prácticas ──────────────────────────

    object LogTags {
        const val PLAYER = "PLAYER"
        const val NETWORK = "NETWORK"
        const val AUTH = "AUTH"
        const val PAYMENT = "PAYMENT"
    }

    // ─── Lo que NUNCA debes hacer ──────────────────

    // ❌ logger.error("AUTH", "Error for user: ${user.email}")   // PII!
    // ❌ logger.debug("AUTH", "Token: $accessToken")             // Credenciales!
    // ❌ logger.info("TAG", jsonResponse.toString())             // Datos del usuario!
}
