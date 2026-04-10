package com.applogger.transport.supabase

import com.applogger.core.BatchManifestCapable
import com.applogger.core.AtomicBatchCapable
import com.applogger.core.BatchKind
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
import kotlinx.serialization.json.JsonArray
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
private const val ATOMIC_LOG_BATCH_RPC = "ingest_log_batch"
private const val ATOMIC_METRIC_BATCH_RPC = "ingest_metric_batch"

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
 * @param schema                      PostgREST schema profile. Keep `public` for the default
 *                                    AppLoggers installation. Use `apploggers` only when the
 *                                    target project exposes compatible relations in that schema.
 * @param tableName                   Target table for log events (default: `"app_logs"`).
 * @param metricsTableName            Target table for metric events (default: `"app_metrics"`).
 * @param networkAvailabilityProvider Optional lambda returning `true` when network is reachable.
 *                                    Defaults to checking endpoint/apiKey are non-blank.
 *                                    Should use cached state — no I/O.
 * @param httpClient                  Optional pre-configured [HttpClient] (e.g. with cert pinning).
 */
private const val LOG_BATCH_MANIFESTS_TABLE = "log_batches"
private const val METRIC_BATCH_MANIFESTS_TABLE = "metric_batches"

class SupabaseTransport(
    private val endpoint: String,
    private val apiKey: String,
    private val schema: String = "public",
    private val tableName: String = "app_logs",
    private val metricsTableName: String = "app_metrics",
    private val networkAvailabilityProvider: (() -> Boolean)? = null,
    httpClient: HttpClient? = null
 ) : LogTransport, BatchManifestCapable, AtomicBatchCapable {

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
    private val schemaProfile get() = schema.trim().ifBlank { "public" }

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
            applyPostgrestHeaders()
            contentType(ContentType.Application.Json)
            val body = json.encodeToString(ListSerializer(SupabaseLogEntry.serializer()), payload)
            setBody(body)
        }
        return response.toTransportResult("app_logs")
    }

    private suspend fun sendMetrics(events: List<LogEvent>): TransportResult {
        val payload = events.map { it.toSupabaseMetric() }
        val response = client.post("$restUrl/$metricsTableName") {
            applyPostgrestHeaders()
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

    override suspend fun storeBatchManifest(
        kind: BatchKind,
        batchId: String,
        hash: String,
        eventCount: Int,
        environment: String,
        sdkVersion: String,
        keyId: String
    ) {
        runCatching {
            // sent_at omitted — the table default NOW() fills it server-side.
            val payload = buildJsonObject {
                put("batch_id", JsonPrimitive(batchId))
                put("event_count", JsonPrimitive(eventCount))
                put("batch_hash", JsonPrimitive(hash))
                if (environment.isNotBlank()) put("environment", JsonPrimitive(environment))
                if (sdkVersion.isNotBlank()) put("sdk_version", JsonPrimitive(sdkVersion))
                if (keyId.isNotBlank()) put("key_id", JsonPrimitive(keyId))
            }
            client.post("$restUrl/${manifestTable(kind)}") {
                applyPostgrestHeaders()
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
        }
    }

    override suspend fun sendBatchWithManifest(
        kind: BatchKind,
        events: List<LogEvent>,
        hash: String,
        eventCount: Int,
        environment: String,
        sdkVersion: String,
        keyId: String
    ): TransportResult {
        val payload = when (kind) {
            BatchKind.LOGS -> events.map { json.encodeToJsonElement(SupabaseLogEntry.serializer(), it.toSupabaseLog()) }
            BatchKind.METRICS -> events.map { json.encodeToJsonElement(SupabaseMetricEntry.serializer(), it.toSupabaseMetric()) }
        }
        val manifest = buildJsonObject {
            put("batch_id", JsonPrimitive(events.firstOrNull()?.batchId ?: ""))
            put("event_count", JsonPrimitive(eventCount))
            put("batch_hash", JsonPrimitive(hash))
            if (environment.isNotBlank()) put("environment", JsonPrimitive(environment))
            if (sdkVersion.isNotBlank()) put("sdk_version", JsonPrimitive(sdkVersion))
            if (keyId.isNotBlank()) put("key_id", JsonPrimitive(keyId))
        }
        val body = buildJsonObject {
            put(payloadField(kind), JsonArray(payload))
            put("manifest", manifest)
        }
        val response = client.post("$restUrl/rpc/${atomicRpc(kind)}") {
            applyPostgrestHeaders()
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return response.toTransportResult("${targetTable(kind)}+${manifestTable(kind)}")
    }

    private fun manifestTable(kind: BatchKind): String = when (kind) {
        BatchKind.LOGS -> LOG_BATCH_MANIFESTS_TABLE
        BatchKind.METRICS -> METRIC_BATCH_MANIFESTS_TABLE
    }

    private fun targetTable(kind: BatchKind): String = when (kind) {
        BatchKind.LOGS -> tableName
        BatchKind.METRICS -> metricsTableName
    }

    private fun atomicRpc(kind: BatchKind): String = when (kind) {
        BatchKind.LOGS -> ATOMIC_LOG_BATCH_RPC
        BatchKind.METRICS -> ATOMIC_METRIC_BATCH_RPC
    }

    private fun payloadField(kind: BatchKind): String = when (kind) {
        BatchKind.LOGS -> "log_entries"
        BatchKind.METRICS -> "metric_entries"
    }

    private fun HttpRequestBuilder.applyPostgrestHeaders() {
        header("apikey", apiKey)
        header("Authorization", "Bearer $apiKey")
        header("Prefer", "return=minimal")
        header("Content-Profile", schemaProfile)
        header("Accept-Profile", schemaProfile)
    }

    fun close() {
        client.close()
    }
}

@Serializable
internal data class SupabaseLogEntry(
    val id: String,
    val level: String,
    val tag: String,
    val message: String,
    val environment: String,
    @SerialName("app_package") val appPackage: String? = null,
    @SerialName("source_scope") val sourceScope: String? = null,
    @SerialName("source_file") val sourceFile: String? = null,
    @SerialName("source_method") val sourceMethod: String? = null,
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
    // Client-side epoch millis — stored as BIGINT in app_logs for HMAC batch integrity
    // verification. The CLI verify command recomputes the hash using this value, not
    // server-generated created_at.
    val timestamp: Long,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("device_info") val deviceInfo: Map<String, String>,
    @SerialName("api_level") val apiLevel: Int,
    @SerialName("sdk_version") val sdkVersion: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("user_id") val userId: String? = null,
    val variant: String? = null,
    val extra: JsonObject? = null
)

@Serializable
internal data class SupabaseMetricEntry(
    val id: String,
    val name: String,
    val value: Double,
    val unit: String,
    val tags: Map<String, String>,
    val environment: String,
    @SerialName("app_package") val appPackage: String? = null,
    @SerialName("source_scope") val sourceScope: String? = null,
    @SerialName("source_file") val sourceFile: String? = null,
    @SerialName("source_method") val sourceMethod: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("sdk_version") val sdkVersion: String,
    @SerialName("user_id") val userId: String? = null,
    val timestamp: Long,
    @SerialName("batch_id") val batchId: String? = null
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
        id = id,
        level = level.name,
        tag = tag,
        message = message,
        environment = environment,
        appPackage = appPackage,
        sourceScope = sourceScope,
        sourceFile = sourceFile,
        sourceMethod = sourceMethod,
        throwableType = throwableInfo?.type,
        throwableMsg = throwableInfo?.message,
        stackTrace = throwableInfo?.stackTrace,
        anomalyType = anomalyType,
        traceId = traceId,
        timestamp = timestamp,
        batchId = batchId,
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
        variant = variant,
        extra = filteredExtra
    )
}

private fun LogEvent.toSupabaseMetric(): SupabaseMetricEntry {
    return SupabaseMetricEntry(
        id = id,
        name = metricName ?: tag,
        value = metricValue ?: 0.0,
        unit = metricUnit ?: "count",
        tags = metricTags ?: emptyMap(),
        environment = environment,
        appPackage = appPackage,
        sourceScope = sourceScope,
        sourceFile = sourceFile,
        sourceMethod = sourceMethod,
        deviceId = deviceId,
        sessionId = sessionId,
        sdkVersion = sdkVersion,
        userId = userId,
        timestamp = timestamp,
        batchId = batchId
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
