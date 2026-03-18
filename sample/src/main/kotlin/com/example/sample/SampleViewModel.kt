package com.example.sample

import com.applogger.core.AppLogger

/**
 * Ejemplo de ViewModel que recibe el logger por inyección.
 * Esto permite testear con InMemoryLogger.
 */
class SampleViewModel(
    private val logger: AppLogger
) {
    fun processPayment(amount: Double) {
        try {
            // Simular proceso de pago...
            if (amount <= 0) throw IllegalArgumentException("Invalid amount")
            logger.info("PAYMENT", "Payment successful", extra = mapOf("amount" to amount))
        } catch (e: Exception) {
            logger.error("PAYMENT", "Payment failed", throwable = e)
        }
    }

    fun loadContent(contentId: String) {
        val startTime = System.currentTimeMillis()
        try {
            // Simular carga...
            logger.info("CONTENT", "Content loaded", extra = mapOf("id" to contentId))
        } finally {
            val elapsed = System.currentTimeMillis() - startTime
            logger.metric("content_load_time", elapsed.toDouble(), "ms",
                tags = mapOf("content_id" to contentId))
        }
    }
}
