package com.applogger.transport.supabase

import com.applogger.core.LogTransport
import com.applogger.core.TransportResult
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * [LogTransport] implementation that delivers events to Supabase (PostgreSQL)
 * via the PostgREST API.
 *
 * Events are partitioned internally:
 * - Regular logs → `app_logs` table.
 * - [com.applogger.core.model.LogLevel.METRIC] events → `app_metrics` table.
 *
 * Uses a Ktor [HttpClient] for multiplatform HTTP.
 *
 * ## Certificate pinning
 * Pass a pre-configured [HttpClient] with engine-level TLS pinning:
 * ```kotlin
 * val pinned = HttpClient(OkHttp) {
 *     engine {
 *         config {
 *             certificatePinner(CertificatePinner.Builder()
 *                 .add("*.supabase.co", "sha256/AAAA...")
 *                 .build())
 *         }
 *     }
 * }
 * SupabaseTransport(url, key, httpClient = pinned)
 * ```
 *
 * @param endpoint          Supabase project URL (e.g. `https://xyz.supabase.co`).
 * @param apiKey            Supabase anon key.
 * @param tableName         Target table for log events (default: `"app_logs"`).
 * @param metricsTableName  Target table for metric events (default: `"app_metrics"`).
 * @param httpClient        Optional pre-configured [HttpClient] (e.g. with cert pinning).
 */
class SupabaseTransport(
    private val endpoint: String,
    private val apiKey: String,
    private val tableName: String = "app_logs",
    private val metricsTableName: String = "app_metrics",
    httpClient: HttpClient? = null
) : LogTransport {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val client = httpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(this@SupabaseTransport.json)
        }
    }

    private val restUrl get() = "${endpoint.trimEnd('/')}/rest/v1"

    @Suppress("TooGenericExceptionCaught")
    override suspend fun send(events: List<LogEvent>): TransportResult {
        return try {
            val (metrics, logs) = events.partition { it.level == LogLevel.METRIC }

            if (logs.isNotEmpty()) {
                sendLogs(logs)
            }
            if (metrics.isNotEmpty()) {
                sendMetrics(metrics)
            }

            TransportResult.Success
        } catch (e: Exception) {
            TransportResult.Failure(
                reason = e.message ?: "Supabase transport error",
                retryable = true,
                cause = e
            )
        }
    }

    private suspend fun sendLogs(events: List<LogEvent>) {
        val payload = events.map { it.toSupabaseLog() }
        val response = client.post("$restUrl/$tableName") {
            header("apikey", apiKey)
            header("Authorization", "Bearer $apiKey")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            val body = json.encodeToString(ListSerializer(SupabaseLogEntry.serializer()), payload)
            setBody(body)
        }
        if (response.status.value !in 200..299) {
            error("Supabase insert failed: ${response.status} - ${response.bodyAsText()}")
        }
    }

    private suspend fun sendMetrics(events: List<LogEvent>) {
        val payload = events.map { it.toSupabaseMetric() }
        val response = client.post("$restUrl/$metricsTableName") {
            header("apikey", apiKey)
            header("Authorization", "Bearer $apiKey")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            val body = json.encodeToString(ListSerializer(SupabaseMetricEntry.serializer()), payload)
            setBody(body)
        }
        if (response.status.value !in 200..299) {
            error("Supabase metrics insert failed: ${response.status} - ${response.bodyAsText()}")
        }
    }

    override fun isAvailable(): Boolean = endpoint.isNotBlank() && apiKey.isNotBlank()

    fun close() {
        client.close()
    }
}

@Serializable
internal data class SupabaseLogEntry(
    val level: String,
    val tag: String,
    val message: String,
    @SerialName("throwable_type") val throwableType: String? = null,
    @SerialName("throwable_msg") val throwableMsg: String? = null,
    @SerialName("stack_trace") val stackTrace: List<String>? = null,
    @SerialName("device_info") val deviceInfo: Map<String, String>,
    @SerialName("api_level") val apiLevel: Int,
    @SerialName("sdk_version") val sdkVersion: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String? = null,
    val extra: Map<String, String>? = null
)

@Serializable
internal data class SupabaseMetricEntry(
    val name: String,
    val value: Double,
    val unit: String,
    val tags: Map<String, String>,
    @SerialName("session_id") val sessionId: String,
    @SerialName("sdk_version") val sdkVersion: String
)

private fun LogEvent.toSupabaseLog() = SupabaseLogEntry(
    level = level.name,
    tag = tag,
    message = message,
    throwableType = throwableInfo?.type,
    throwableMsg = throwableInfo?.message,
    stackTrace = throwableInfo?.stackTrace,
    deviceInfo = mapOf(
        "brand" to deviceInfo.brand,
        "model" to deviceInfo.model,
        "os_version" to deviceInfo.osVersion,
        "api_level" to deviceInfo.apiLevel.toString(),
        "platform" to deviceInfo.platform,
        "app_version" to deviceInfo.appVersion,
        "app_build" to deviceInfo.appBuild.toString(),
        "is_low_ram" to deviceInfo.isLowRamDevice.toString(),
        "is_tv" to deviceInfo.isTV.toString(),
        "connection_type" to deviceInfo.connectionType
    ),
    apiLevel = deviceInfo.apiLevel,
    sdkVersion = sdkVersion,
    sessionId = sessionId,
    userId = userId,
    extra = extra
)

private fun LogEvent.toSupabaseMetric(): SupabaseMetricEntry {
    val metricName = extra?.get("metric_name") ?: tag
    val metricValue = extra?.get("metric_value")?.toDoubleOrNull() ?: 0.0
    val metricUnit = extra?.get("metric_unit") ?: "count"
    val metricTags = extra?.filterKeys { it !in setOf("metric_name", "metric_value", "metric_unit") }
        ?: emptyMap()

    return SupabaseMetricEntry(
        name = metricName,
        value = metricValue,
        unit = metricUnit,
        tags = metricTags + mapOf("platform" to deviceInfo.platform),
        sessionId = sessionId,
        sdkVersion = sdkVersion
    )
}
