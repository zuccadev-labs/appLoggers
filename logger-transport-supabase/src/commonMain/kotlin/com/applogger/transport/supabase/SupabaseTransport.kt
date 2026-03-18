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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Transporte a Supabase (PostgreSQL) via REST API.
 * Usa Ktor client KMP para ser multiplataforma.
 */
class SupabaseTransport(
    private val endpoint: String,
    private val apiKey: String,
    private val tableName: String = "app_logs",
    private val metricsTableName: String = "app_metrics"
) : LogTransport {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(this@SupabaseTransport.json)
        }
    }

    private val restUrl get() = "${endpoint.trimEnd('/')}/rest/v1"

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
            setBody(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SupabaseLogEntry.serializer()), payload))
        }
        if (response.status.value !in 200..299) {
            throw RuntimeException("Supabase insert failed: ${response.status} - ${response.bodyAsText()}")
        }
    }

    private suspend fun sendMetrics(events: List<LogEvent>) {
        val payload = events.map { it.toSupabaseMetric() }
        val response = client.post("$restUrl/$metricsTableName") {
            header("apikey", apiKey)
            header("Authorization", "Bearer $apiKey")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SupabaseMetricEntry.serializer()), payload))
        }
        if (response.status.value !in 200..299) {
            throw RuntimeException("Supabase metrics insert failed: ${response.status} - ${response.bodyAsText()}")
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
    val throwable_type: String? = null,
    val throwable_msg: String? = null,
    val stack_trace: List<String>? = null,
    val device_info: Map<String, String>,
    val api_level: Int,
    val sdk_version: String,
    val session_id: String,
    val user_id: String? = null,
    val extra: Map<String, String>? = null
)

@Serializable
internal data class SupabaseMetricEntry(
    val name: String,
    val value: Double,
    val unit: String,
    val tags: Map<String, String>,
    val session_id: String,
    val sdk_version: String
)

private fun LogEvent.toSupabaseLog() = SupabaseLogEntry(
    level = level.name,
    tag = tag,
    message = message,
    throwable_type = throwableInfo?.type,
    throwable_msg = throwableInfo?.message,
    stack_trace = throwableInfo?.stackTrace,
    device_info = mapOf(
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
    api_level = deviceInfo.apiLevel,
    sdk_version = sdkVersion,
    session_id = sessionId,
    user_id = userId,
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
        session_id = sessionId,
        sdk_version = sdkVersion
    )
}
