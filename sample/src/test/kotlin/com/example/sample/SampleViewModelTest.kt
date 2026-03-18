package com.example.sample

import com.applogger.core.model.LogLevel
import com.applogger.test.InMemoryLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Ejemplo de cómo testear un ViewModel inyectando InMemoryLogger.
 * Demuestra que el SDK funciona correctamente cuando se importa en un proyecto.
 */
class SampleViewModelTest {

    private lateinit var logger: InMemoryLogger
    private lateinit var viewModel: SampleViewModel

    @BeforeEach
    fun setup() {
        logger = InMemoryLogger()
        viewModel = SampleViewModel(logger)
    }

    @Test
    fun `successful payment logs info`() {
        viewModel.processPayment(99.99)

        assertEquals(1, logger.infoCount)
        logger.assertLogged(LogLevel.INFO, tag = "PAYMENT")
        logger.assertNotLogged(LogLevel.ERROR)
    }

    @Test
    fun `failed payment logs error`() {
        viewModel.processPayment(-1.0) // Invalid amount

        assertEquals(1, logger.errorCount)
        logger.assertLogged(LogLevel.ERROR, tag = "PAYMENT")
        assertNotNull(logger.lastError?.throwable)
    }

    @Test
    fun `loadContent logs info and metric`() {
        viewModel.loadContent("movie_123")

        logger.assertLogged(LogLevel.INFO, tag = "CONTENT")
        logger.assertLogged(LogLevel.METRIC)
        assertEquals(1, logger.metricCount)
    }

    @Test
    fun `multiple operations create independent log entries`() {
        viewModel.processPayment(50.0)
        viewModel.processPayment(75.0)
        viewModel.loadContent("show_456")

        assertEquals(3, logger.infoCount) // 2 payments + 1 content
        assertEquals(1, logger.metricCount) // 1 content load time
    }
}
