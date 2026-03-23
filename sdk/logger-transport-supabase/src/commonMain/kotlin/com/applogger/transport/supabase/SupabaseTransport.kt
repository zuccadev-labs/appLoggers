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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVICE_UNAVAILABLE = 503
private const val HTTP_CLIENT_ERROR_MIN = 400
private const val HTTP_CLIENT_ERROR_MAX = 499
private const val DEFAULT_RETRY_AFTER_SECONDS = 60L

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
 * ## Network availability
 * By default, [isAvailable] only checks that endpoint and apiKey are non-blank.
 * On Android, pass a [networkAvailabilityProvider] backed by `ConnectivityManager`
 * to avoid retry loops when the device is offline:
 * ```kotlin
 * val transport = SupabaseTransport(
 *     endpoint = url,
 *     apiKey = key,
 *     networkAvailabilityProvider = { connectivityManager.activeNetwork != null }
 * )
 * ```
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
 * @param endpoint                    Supabase project URL (e.g. `https://xyz.supabase.co`).
 * @param apiKey                      Supabase anon key.
 * @param tableName                   Target table for log events (default: `"app_logs"`).
 * @param metricsTableName            Target table for metric events (default: `"app_metrics"`).
 * @param networkAvailabilityProvider Optional lambda returning `true` when network is reachable.
 *                                    Defaults to checking endpoint/apiKey are non-blank.
 *                                    Should use cached state — no I/O.
 * @param httpClient                  Optional pre-configured [HttpClient] (e.g. with cert pinning).
 */
class SupabaseTransport(
    private val endpoint: String,
    private val apiKey: String,
    private val tableName: String = "app_logs",
    private val metricsTableName: String = "app_metrics",
    private val networkAvailabilityProvider: (() -> Boolean)? = null,
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
                val result = sendLogs(logs)
                if (result is TransportResult.Failure) return result
            }
            if (metrics.isNotEmpty()) {
                val result = sendMetrics(metrics)
                if (result is TransportResult.Failure) return result
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

    private suspend fun sendLogs(events: List<LogEvent>): TransportResult {
        val payload = events.map { it.toSupabaseLog() }
        val response = client.post("$restUrl/$tableName") {
            header("apikey", apiKey)
            header("Authorization", "Bearer $apiKey")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            val body = json.encodeToString(ListSerializer(SupabaseLogEntry.serializer()), payload)
            setBody(body)
        }
        return response.toTransportResult("app_logs")
    }

    private suspend fun sendMetrics(events: List<LogEvent>): TransportResult {
        val payload = events.map { it.toSupabaseMetric() }
        val response = client.post("$restUrl/$metricsTableName") {
            header("apikey", apiKey)
            header("Authorization", "Bearer $apiKey")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            val body = json.encodeToString(ListSerializer(SupabaseMetricEntry.serializer()), payload)
            setBody(body)
        }
        return response.toTransportResult("app_metrics")
    }

    /**
     * Maps an HTTP response to [TransportResult].
     *
     * - 2xx → Success
     * - 429 Too Many Requests → Failure(retryable=true, retryAfterMs from Retry-After header)
     * - 503 Service Unavailable → Failure(retryable=true)
     * - 4xx (other) → Failure(retryable=false) — bad request, retrying won't help
     * - 5xx (other) → Failure(retryable=true)
     */
    private suspend fun HttpResponse.toTransportResult(table: String): TransportResult {
        val code = status.value
        if (code in 200..299) return TransportResult.Success

        val body = bodyAsText()
        return when (code) {
            HTTP_TOO_MANY_REQUESTS -> {
                // Parse Retry-After header (seconds or HTTP-date — we only handle seconds)
                val retryAfterSeconds = headers["Retry-After"]?.toLongOrNull()
                    ?: DEFAULT_RETRY_AFTER_SECONDS
                TransportResult.Failure(
                    reason = "Rate limited by Supabase ($table): retry after ${retryAfterSeconds}s",
                    retryable = true,
                    retryAfterMs = retryAfterSeconds * 1_000L
                )
            }
            HTTP_SERVICE_UNAVAILABLE -> TransportResult.Failure(
                reason = "Supabase unavailable ($table): $body",
                retryable = true
            )
            in HTTP_CLIENT_ERROR_MIN..HTTP_CLIENT_ERROR_MAX -> TransportResult.Failure(
                reason = "Supabase client error $code ($table): $body",
                retryable = false  // Bad request — retrying won't help
            )
            else -> TransportResult.Failure(
                reason = "Supabase server error $code ($table): $body",
                retryable = true
            )
        }
    }

    override fun isAvailable(): Boolean {
        if (endpoint.isBlank() || apiKey.isBlank()) return false
        return networkAvailabilityProvider?.invoke() ?: true
    }

    fun close() {
        client.close()
    }
}

@Serializable
internal data class SupabaseLogEntry(
    val level: String,
    val tag: String,
    val message: String,
    val environment: String,
    @SerialName("throwable_type") val throwableType: String? = null,
    @SerialName("throwable_msg") val throwableMsg: String? = null,
    @SerialName("stack_trace") val stackTrace: List<String>? = null,
    // Pillar 3 — Column Matcher: anomaly_type es una columna de primer nivel en app_logs,
    // no un campo dentro del JSONB extra. El transporte extrae este valor del mapa extra
    // (donde lo inyectó el SDK en Pillar 1) y lo promueve al payload raíz de la petición.
    @SerialName("anomaly_type") val anomalyType: String? = null,
    // Distributed tracing: allows correlating events across devices (mobile → TV → backend).
    // Filter in Supabase: SELECT * FROM app_logs WHERE trace_id = 'abc-123' ORDER BY timestamp
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("device_info") val deviceInfo: Map<String, String>,
    @SerialName("api_level") val apiLevel: Int,
    @SerialName("sdk_version") val sdkVersion: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("user_id") val userId: String? = null,
    val extra: JsonObject? = null
)

@Serializable
internal data class SupabaseMetricEntry(
    val name: String,
    val value: Double,
    val unit: String,
    val tags: Map<String, String>,
    val environment: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("sdk_version") val sdkVersion: String
)

private fun LogEvent.toSupabaseLog(): SupabaseLogEntry {
    // Pillar 3 — Column Matcher:
    // Extrae anomaly_type del mapa extra y lo promueve a columna de primer nivel.
    // Esto evita que el valor quede enterrado en el JSONB extra y permite queries
    // directas: SELECT * FROM app_logs WHERE anomaly_type = 'error'
    val anomalyType = (extra?.get("anomaly_type") as? JsonPrimitive)?.content
    val filteredExtra = extra
        ?.filterKeys { it != "anomaly_type" }
        ?.takeIf { it.isNotEmpty() }
        ?.toJsonObject()

    return SupabaseLogEntry(
        level = level.name,
        tag = tag,
        message = message,
        environment = environment,
        throwableType = throwableInfo?.type,
        throwableMsg = throwableInfo?.message,
        stackTrace = throwableInfo?.stackTrace,
        anomalyType = anomalyType,
        traceId = traceId,
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
        deviceId = deviceId,
        userId = userId,
        extra = filteredExtra
    )
}

private fun LogEvent.toSupabaseMetric(): SupabaseMetricEntry {
    return SupabaseMetricEntry(
        name = metricName ?: tag,
        value = metricValue ?: 0.0,
        unit = metricUnit ?: "count",
        tags = metricTags ?: emptyMap(),
        environment = environment,
        deviceId = deviceId,
        sessionId = sessionId,
        sdkVersion = sdkVersion
    )
}

/**
 * Converts a [Map<String, JsonElement>] to a [JsonObject].
 * Values are already native JSON primitives — no heuristic parsing needed.
 * Enables richer JSONB queries in Supabase:
 * ```sql
 * SELECT * FROM app_logs WHERE (extra->>'retry_count')::int > 2;
 * SELECT * FROM app_logs WHERE (extra->>'is_cached')::boolean = true;
 * ```
 */
private fun Map<String, JsonElement>.toJsonObject(): JsonObject = buildJsonObject {
    forEach { (key, element) -> put(key, element) }
}
