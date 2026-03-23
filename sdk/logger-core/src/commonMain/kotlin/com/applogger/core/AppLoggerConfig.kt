package com.applogger.core

private const val LARGE_BATCH_THRESHOLD = 50
private const val SHORT_FLUSH_THRESHOLD = 10

/**
 * Immutable configuration for the AppLogger SDK.
 *
 * Created via [Builder]. Values are validated and coerced to safe ranges:
 * - [batchSize]: 1–100 (default 20)
 * - [flushIntervalSeconds]: 5–300 (default 30)
 * - [maxStackTraceLines]: 1–100 (default 50)
 *
 * In production mode ([isDebugMode] = false), [endpoint] must use HTTPS.
 *
 * ## Debug mode via environment variable
 * Setting `APPLOGGER_DEBUG=true` in the build environment (or as a manifest
 * placeholder) activates debug mode and logcat output automatically, without
 * changing code. When `APPLOGGER_DEBUG` is absent or `false`, logcat output
 * is suppressed regardless of [consoleOutput].
 *
 * ## Example
 * ```kotlin
 * val config = AppLoggerConfig.Builder()
 *     .endpoint("https://xyz.supabase.co")
 *     .apiKey("eyJ...")
 *     .minLevel(LogMinLevel.WARN)   // solo WARN, ERROR, CRITICAL en producción
 *     .batchSize(10)
 *     .build()
 * ```
 *
 * @property endpoint     Supabase project URL (HTTPS required in production).
 * @property apiKey       Supabase anon key for authentication.
 * @property environment  Deployment environment label (e.g. "production", "staging", "development").
 *                        Attached to every event — enables filtering QA vs production in Supabase.
 *                        Default: `"production"`.
 * @property isDebugMode  Enables debug logging and relaxes HTTPS requirement.
 *                        Automatically true when `APPLOGGER_DEBUG=true`.
 * @property consoleOutput Prints events to logcat/console when true AND [isDebugMode] is true.
 *                         In production ([isDebugMode]=false) logcat is always suppressed.
 * @property minLevel     Minimum level to process. Events below this level are discarded
 *                        before entering the pipeline. Default: [LogMinLevel.DEBUG] (all events).
 * @property batchSize    Number of events per transport batch (1–100).
 * @property flushIntervalSeconds Seconds between periodic flushes (5–300).
 * @property maxStackTraceLines Maximum stack trace lines per event (1–100).
 * @property flushOnlyWhenIdle Delays flush until the app is idle (Android TV optimization).
 * @property verboseTransportLogging Logs transport-level debug info when true.
 * @property bufferSizeStrategy Strategy for determining buffer capacity (default: FIXED).
 * @property bufferOverflowPolicy Policy when buffer is full (default: DISCARD_OLDEST).
 * @property offlinePersistenceMode Persistence for offline/critical events (default: NONE).
 */
data class AppLoggerConfig(
    val endpoint: String,
    val apiKey: String,
    val environment: String,
    val isDebugMode: Boolean,
    val consoleOutput: Boolean,
    val minLevel: LogMinLevel,
    val batchSize: Int,
    val flushIntervalSeconds: Int,
    val maxStackTraceLines: Int,
    val flushOnlyWhenIdle: Boolean,
    val verboseTransportLogging: Boolean,
    val bufferSizeStrategy: BufferSizeStrategy,
    val bufferOverflowPolicy: BufferOverflowPolicy,
    val offlinePersistenceMode: OfflinePersistenceMode,
    /**
     * Deduplication window for identical errors (same level + tag + message + throwable type).
     * Within this window, only the first occurrence is sent; subsequent duplicates are suppressed
     * and the final event is enriched with `occurrence_count = N`.
     *
     * Solves high-frequency event loops (e.g. playback ticks, sync polling) that would otherwise
     * flood the backend with thousands of identical rows.
     *
     * Set to `0` to disable deduplication entirely. Default: `10_000` ms (10 seconds).
     */
    val deduplicationWindowMs: Long = 10_000L,
    /**
     * Number of breadcrumbs (user interaction records) retained in the circular buffer.
     * Breadcrumbs are automatically attached to ERROR and CRITICAL events as a JSON array
     * in the `breadcrumbs` extra field, giving full "what the user did before the crash" context.
     *
     * Set to `0` to disable. Default: `10`.
     */
    val breadcrumbCapacity: Int = 10
) {
    class Builder {
        private var endpoint: String = ""
        private var apiKey: String = ""
        private var environment: String = "production"
        private var isDebugMode: Boolean = false
        private var consoleOutput: Boolean = true
        private var minLevel: LogMinLevel = LogMinLevel.DEBUG
        private var batchSize: Int = 20
        private var flushIntervalSeconds: Int = 30
        private var maxStackTraceLines: Int = 50
        private var flushOnlyWhenIdle: Boolean = false
        private var verboseTransportLogging: Boolean = false
        private var bufferSizeStrategy: BufferSizeStrategy = BufferSizeStrategy.FIXED
        private var bufferOverflowPolicy: BufferOverflowPolicy = BufferOverflowPolicy.DISCARD_OLDEST
        private var offlinePersistenceMode: OfflinePersistenceMode = OfflinePersistenceMode.NONE
        private var deduplicationWindowMs: Long = 10_000L
        private var breadcrumbCapacity: Int = 10

        fun endpoint(url: String) = apply { endpoint = url }
        fun apiKey(key: String) = apply { apiKey = key }
        fun environment(env: String) = apply { environment = env.trim().ifBlank { "production" } }
        fun debugMode(debug: Boolean) = apply { isDebugMode = debug }
        fun consoleOutput(enabled: Boolean) = apply { consoleOutput = enabled }
        fun minLevel(level: LogMinLevel) = apply { minLevel = level }
        fun batchSize(size: Int) = apply { batchSize = size }
        fun flushIntervalSeconds(sec: Int) = apply { flushIntervalSeconds = sec }
        fun maxStackTraceLines(lines: Int) = apply { maxStackTraceLines = lines }
        fun flushOnlyWhenIdle(idle: Boolean) = apply { flushOnlyWhenIdle = idle }
        fun verboseTransportLogging(v: Boolean) = apply { verboseTransportLogging = v }
        fun bufferSizeStrategy(strategy: BufferSizeStrategy) = apply { bufferSizeStrategy = strategy }
        fun bufferOverflowPolicy(policy: BufferOverflowPolicy) = apply { bufferOverflowPolicy = policy }
        fun offlinePersistenceMode(mode: OfflinePersistenceMode) = apply { offlinePersistenceMode = mode }
        /** @see AppLoggerConfig.deduplicationWindowMs */
        fun deduplicationWindowMs(ms: Long) = apply { deduplicationWindowMs = maxOf(0L, ms) }
        /** @see AppLoggerConfig.breadcrumbCapacity */
        fun breadcrumbCapacity(n: Int) = apply { breadcrumbCapacity = maxOf(0, n) }

        fun build(): AppLoggerConfig {
            require(endpoint.startsWith("https://") || isDebugMode || endpoint.isEmpty()) {
                "AppLogger: production endpoint must use HTTPS"
            }
            return AppLoggerConfig(
                endpoint = endpoint,
                apiKey = apiKey,
                environment = environment,
                isDebugMode = isDebugMode,
                consoleOutput = consoleOutput,
                minLevel = minLevel,
                batchSize = batchSize.coerceIn(1, 100),
                flushIntervalSeconds = flushIntervalSeconds.coerceIn(5, 300),
                maxStackTraceLines = maxStackTraceLines.coerceIn(1, 100),
                flushOnlyWhenIdle = flushOnlyWhenIdle,
                verboseTransportLogging = verboseTransportLogging,
                bufferSizeStrategy = bufferSizeStrategy,
                bufferOverflowPolicy = bufferOverflowPolicy,
                offlinePersistenceMode = offlinePersistenceMode,
                deduplicationWindowMs = deduplicationWindowMs,
                breadcrumbCapacity = breadcrumbCapacity
            )
        }
    }

    /**
     * Returns a list of human-readable validation warnings/errors for this config.
     *
     * An empty list means the config is valid. Non-empty list contains actionable messages
     * that should be logged or surfaced to the developer during initialization.
     *
     * ```kotlin
     * val issues = config.validate()
     * if (issues.isNotEmpty()) {
     *     issues.forEach { Log.w("AppLogger", "Config issue: $it") }
     * }
     * ```
     */
    fun validate(): List<String> {
        val issues = mutableListOf<String>()

        // Endpoint checks
        if (endpoint.isBlank()) {
            issues += "endpoint is blank — events will not be delivered"
        } else if (!endpoint.startsWith("https://") && !isDebugMode) {
            issues += "endpoint '$endpoint' does not use HTTPS — required in production"
        } else if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            issues += "endpoint '$endpoint' does not look like a valid URL"
        }

        // API key checks
        if (apiKey.isBlank()) {
            issues += "apiKey is blank — Supabase requests will be rejected (401)"
        } else if (!apiKey.startsWith("eyJ")) {
            issues += "apiKey does not look like a JWT (expected to start with 'eyJ') — verify your Supabase anon key"
        }

        // Environment label
        if (environment.isBlank()) {
            issues += "environment is blank — events will have no environment label"
        }

        // Batch size vs flush interval coherence
        if (batchSize >= LARGE_BATCH_THRESHOLD && flushIntervalSeconds <= SHORT_FLUSH_THRESHOLD) {
            issues += "batchSize=$batchSize with flushIntervalSeconds=$flushIntervalSeconds " +
                "may cause large bursts — consider increasing flushIntervalSeconds or reducing batchSize"
        }
        if (batchSize == 1) {
            issues += "batchSize=1 disables batching — every event triggers a network request; " +
                "use only for debugging"
        }

        // Debug mode in production warning
        if (isDebugMode && environment == "production") {
            issues += "isDebugMode=true with environment='production' — " +
                "debug mode should not be active in production builds"
        }

        return issues
    }

    /**
     * Ajusta los valores por defecto según la plataforma detectada.
     */
    internal fun resolveForLowResource(): AppLoggerConfig {
        return copy(
            batchSize = minOf(batchSize, 5),
            flushIntervalSeconds = maxOf(flushIntervalSeconds, 60),
            maxStackTraceLines = minOf(maxStackTraceLines, 5),
            flushOnlyWhenIdle = true,
            // On TV/low-RAM devices, reduce breadcrumb buffer to save memory.
            // Deduplication window stays: it *saves* memory by collapsing duplicate events.
            breadcrumbCapacity = minOf(breadcrumbCapacity, 5)
        )
    }
}

/**
 * Nivel mínimo de log que el SDK procesa.
 *
 * Eventos por debajo de este nivel son descartados antes de entrar al pipeline,
 * sin coste de serialización ni red.
 *
 * | Valor       | Eventos procesados                        |
 * |-------------|-------------------------------------------|
 * | DEBUG       | Todos (default en debug mode)             |
 * | INFO        | INFO, WARN, ERROR, CRITICAL, METRIC       |
 * | WARN        | WARN, ERROR, CRITICAL, METRIC             |
 * | ERROR       | ERROR, CRITICAL, METRIC                   |
 * | CRITICAL    | Solo CRITICAL y METRIC                    |
 *
 * METRIC siempre pasa independientemente del nivel configurado.
 */
enum class LogMinLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    CRITICAL
}

/**
 * Modo de persistencia offline para eventos.
 */
enum class OfflinePersistenceMode {
    /**
     * Sin persistencia. Solo memoria (default).
     */
    NONE,

    /**
     * Solo eventos ERROR y CRITICAL se guardan en SQLite.
     * Para apps reguladas que requieren retención de incidentes graves.
     */
    CRITICAL_ONLY,

    /**
     * Todos los eventos se guardan en SQLite.
     * Para apps que requieren auditoría completa incluso durante outages prolongados.
     */
    ALL
}
