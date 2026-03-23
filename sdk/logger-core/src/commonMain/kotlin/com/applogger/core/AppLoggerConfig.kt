package com.applogger.core

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
    val offlinePersistenceMode: OfflinePersistenceMode
) {
    class Builder {
        private var endpoint: String = ""
        private var apiKey: String = ""
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

        fun endpoint(url: String) = apply { endpoint = url }
        fun apiKey(key: String) = apply { apiKey = key }
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

        fun build(): AppLoggerConfig {
            require(endpoint.startsWith("https://") || isDebugMode || endpoint.isEmpty()) {
                "AppLogger: production endpoint must use HTTPS"
            }
            return AppLoggerConfig(
                endpoint = endpoint,
                apiKey = apiKey,
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
                offlinePersistenceMode = offlinePersistenceMode
            )
        }
    }

    /**
     * Ajusta los valores por defecto según la plataforma detectada.
     */
    internal fun resolveForLowResource(): AppLoggerConfig {
        return copy(
            batchSize = minOf(batchSize, 5),
            flushIntervalSeconds = maxOf(flushIntervalSeconds, 60),
            maxStackTraceLines = minOf(maxStackTraceLines, 5),
            flushOnlyWhenIdle = true
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
