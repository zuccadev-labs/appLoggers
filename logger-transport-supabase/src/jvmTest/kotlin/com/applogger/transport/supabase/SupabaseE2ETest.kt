package com.applogger.transport.supabase

import com.applogger.core.TransportResult
import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Test E2E real contra Supabase.
 *
 * Solo se ejecuta si las variables de entorno están configuradas:
 *   APPLOGGER_SUPABASE_URL=https://tu-proyecto.supabase.co
 *   APPLOGGER_SUPABASE_ANON_KEY=eyJhbGc...
 *   APPLOGGER_SUPABASE_SERVICE_KEY=eyJhbGc... (para lectura/verificación)
 *
 * Para ejecutar:
 *   $env:APPLOGGER_SUPABASE_URL = "https://xxx.supabase.co"
 *   $env:APPLOGGER_SUPABASE_ANON_KEY = "eyJ..."
 *   $env:APPLOGGER_SUPABASE_SERVICE_KEY = "eyJ..."
 *   .\gradlew.bat :logger-transport-supabase:jvmTest
 */
@EnabledIfEnvironmentVariable(named = "APPLOGGER_SUPABASE_URL", matches = "https://.+")
@DisplayName("E2E — Supabase Real Integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SupabaseE2ETest {

    companion object {
        private val url = System.getenv("APPLOGGER_SUPABASE_URL") ?: ""
        private val anonKey = System.getenv("APPLOGGER_SUPABASE_ANON_KEY") ?: ""
        private val serviceKey = System.getenv("APPLOGGER_SUPABASE_SERVICE_KEY") ?: ""

        private val testSessionId = "e2e-test-${System.currentTimeMillis()}"

        private val testDeviceInfo = DeviceInfo(
            brand = "E2E-Test", model = "CI-Runner", osVersion = "test",
            apiLevel = 0, platform = "JVM_TEST", appVersion = "0.1.1",
            appBuild = 1, isLowRamDevice = false, connectionType = "ethernet"
        )

        private val json = Json { ignoreUnknownKeys = true }
        private lateinit var transport: SupabaseTransport

        @BeforeAll
        @JvmStatic
        fun setup() {
            transport = SupabaseTransport(
                endpoint = url,
                apiKey = anonKey
            )
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            // Limpia los datos de test usando service_role
            if (serviceKey.isNotBlank()) {
                runBlocking {
                    val client = HttpClient()
                    client.delete("${url.trimEnd('/')}/rest/v1/app_logs?session_id=eq.${testSessionId}") {
                        header("apikey", serviceKey)
                        header("Authorization", "Bearer $serviceKey")
                    }
                    client.delete("${url.trimEnd('/')}/rest/v1/app_metrics?session_id=eq.${testSessionId}") {
                        header("apikey", serviceKey)
                        header("Authorization", "Bearer $serviceKey")
                    }
                    client.close()
                }
            }
            transport.close()
        }
    }

    private fun buildLogEvent(
        level: LogLevel = LogLevel.INFO,
        tag: String = "E2E_TEST",
        message: String = "Test event",
        extra: Map<String, String>? = null
    ) = LogEvent(
        id = "e2e-${System.currentTimeMillis()}-${(Math.random() * 10000).toInt()}",
        timestamp = System.currentTimeMillis(),
        level = level,
        tag = tag,
        message = message,
        deviceInfo = testDeviceInfo,
        sessionId = testSessionId,
        extra = extra
    )

    // ──────────── INSERT TESTS ────────────

    @Test
    @Order(1)
    fun `send single INFO log to Supabase`() = runBlocking {
        val event = buildLogEvent(LogLevel.INFO, "E2E_TEST", "Single info event from SDK E2E test")
        val result = transport.send(listOf(event))

        assertTrue(result is TransportResult.Success, "Expected Success but got: $result")
    }

    @Test
    @Order(2)
    fun `send ERROR log with throwable to Supabase`() = runBlocking {
        val event = buildLogEvent(
            level = LogLevel.ERROR,
            tag = "E2E_TEST",
            message = "Error event with throwable"
        ).let { evt ->
            evt.copy(
                throwableInfo = com.applogger.core.model.ThrowableInfo(
                    type = "RuntimeException",
                    message = "E2E test exception",
                    stackTrace = listOf(
                        "com.applogger.test.E2E.testMethod(E2ETest.kt:42)",
                        "com.applogger.test.E2E.runTest(E2ETest.kt:10)"
                    )
                )
            )
        }
        val result = transport.send(listOf(event))
        assertTrue(result is TransportResult.Success, "Expected Success but got: $result")
    }

    @Test
    @Order(3)
    fun `send batch of multiple log levels`() = runBlocking {
        val events = listOf(
            buildLogEvent(LogLevel.DEBUG, "E2E_BATCH", "Debug event"),
            buildLogEvent(LogLevel.INFO, "E2E_BATCH", "Info event"),
            buildLogEvent(LogLevel.WARN, "E2E_BATCH", "Warn event"),
            buildLogEvent(LogLevel.ERROR, "E2E_BATCH", "Error event"),
            buildLogEvent(LogLevel.CRITICAL, "E2E_BATCH", "Critical event")
        )
        val result = transport.send(events)
        assertTrue(result is TransportResult.Success, "Batch send failed: $result")
    }

    @Test
    @Order(4)
    fun `send METRIC event to app_metrics table`() = runBlocking {
        val event = buildLogEvent(
            level = LogLevel.METRIC,
            tag = "METRIC",
            message = "screen_load_time=1234.0 ms",
            extra = mapOf(
                "metric_name" to "screen_load_time",
                "metric_value" to "1234.0",
                "metric_unit" to "ms",
                "screen" to "HomeScreen"
            )
        )
        val result = transport.send(listOf(event))
        assertTrue(result is TransportResult.Success, "Metric send failed: $result")
    }

    @Test
    @Order(5)
    fun `send log with extra metadata`() = runBlocking {
        val event = buildLogEvent(
            level = LogLevel.INFO,
            tag = "E2E_EXTRA",
            message = "Event with extra fields",
            extra = mapOf("content_id" to "movie_123", "screen" to "PlayerScreen")
        )
        val result = transport.send(listOf(event))
        assertTrue(result is TransportResult.Success)
    }

    // ──────────── READ-BACK VERIFICATION ────────────

    @Test
    @Order(10)
    fun `verify logs exist in Supabase by reading them back`() = runBlocking {
        Assumptions.assumeTrue(serviceKey.isNotBlank(), "SERVICE_KEY needed for read-back verification")

        val client = HttpClient()
        val response = client.get("${url.trimEnd('/')}/rest/v1/app_logs?session_id=eq.${testSessionId}&order=created_at.asc") {
            header("apikey", serviceKey)
            header("Authorization", "Bearer $serviceKey")
            header("Accept", "application/json")
        }

        assertEquals(HttpStatusCode.OK, response.status, "Failed to read logs back")

        val body = response.bodyAsText()
        val rows = json.parseToJsonElement(body).jsonArray

        assertTrue(rows.size >= 7, "Expected at least 7 log rows, got ${rows.size}. Body: $body")

        // Verificar que hay diferentes niveles
        val levels = rows.map { it.jsonObject["level"]?.jsonPrimitive?.content }.toSet()
        assertTrue(levels.containsAll(setOf("INFO", "ERROR", "DEBUG", "WARN", "CRITICAL")),
            "Expected all log levels, got: $levels")

        // Verificar que el throwable se guardó correctamente
        val errorRow = rows.first { it.jsonObject["level"]?.jsonPrimitive?.content == "ERROR" }
        assertEquals("RuntimeException",
            errorRow.jsonObject["throwable_type"]?.jsonPrimitive?.content,
            "Throwable type mismatch")

        // Verificar device_info como JSONB
        val deviceInfo = errorRow.jsonObject["device_info"]?.jsonObject
        assertNotNull(deviceInfo, "device_info should not be null")
        assertEquals("E2E-Test", deviceInfo?.get("brand")?.jsonPrimitive?.content)
        assertEquals("JVM_TEST", deviceInfo?.get("platform")?.jsonPrimitive?.content)

        // Verificar extra metadata
        val extraRow = rows.first {
            it.jsonObject["tag"]?.jsonPrimitive?.content == "E2E_EXTRA"
        }
        val extra = extraRow.jsonObject["extra"]?.jsonObject
        assertEquals("movie_123", extra?.get("content_id")?.jsonPrimitive?.content)

        client.close()
        println("✓ Verificados ${rows.size} logs en Supabase")
    }

    @Test
    @Order(11)
    fun `verify metrics exist in Supabase`() = runBlocking {
        Assumptions.assumeTrue(serviceKey.isNotBlank(), "SERVICE_KEY needed for read-back verification")

        val client = HttpClient()
        val response = client.get("${url.trimEnd('/')}/rest/v1/app_metrics?session_id=eq.${testSessionId}") {
            header("apikey", serviceKey)
            header("Authorization", "Bearer $serviceKey")
            header("Accept", "application/json")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        val rows = json.parseToJsonElement(body).jsonArray

        assertTrue(rows.isNotEmpty(), "Expected at least 1 metric row, got 0. Body: $body")

        val metric = rows[0].jsonObject
        assertEquals("screen_load_time", metric["name"]?.jsonPrimitive?.content)
        assertEquals(1234.0, metric["value"]?.jsonPrimitive?.double)
        assertEquals("ms", metric["unit"]?.jsonPrimitive?.content)

        client.close()
        println("✓ Verificada ${rows.size} métrica(s) en Supabase")
    }

    // ──────────── PERFORMANCE / RESILIENCE ────────────

    @Test
    @Order(20)
    fun `large batch of 50 events performs within 5 seconds`() = runBlocking {
        val events = (1..50).map {
            buildLogEvent(LogLevel.INFO, "E2E_PERF", "Performance test event #$it")
        }

        val start = System.currentTimeMillis()
        val result = transport.send(events)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(result is TransportResult.Success, "Batch of 50 failed: $result")
        assertTrue(elapsed < 5000, "Batch of 50 took ${elapsed}ms, expected < 5000ms")
        println("✓ 50 events enviados en ${elapsed}ms")
    }

    @Test
    @Order(21)
    fun `transport to invalid endpoint returns Failure not exception`() = runBlocking {
        val badTransport = SupabaseTransport(
            endpoint = "https://invalid-project-does-not-exist.supabase.co",
            apiKey = "fake-key"
        )
        val event = buildLogEvent(LogLevel.INFO, "E2E_BAD", "Should fail gracefully")
        val result = badTransport.send(listOf(event))

        assertTrue(result is TransportResult.Failure, "Expected Failure for bad endpoint, got: $result")
        assertTrue((result as TransportResult.Failure).retryable, "Should be retryable")
        badTransport.close()
    }
}
