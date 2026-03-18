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
 * ## Example
 * ```kotlin
 * val config = AppLoggerConfig.Builder()
 *     .endpoint("https://xyz.supabase.co")
 *     .apiKey("eyJ...")
 *     .batchSize(10)
 *     .build()
 * ```
 *
 * @property endpoint     Supabase project URL (HTTPS required in production).
 * @property apiKey       Supabase anon key for authentication.
 * @property isDebugMode  Enables debug logging and relaxes HTTPS requirement.
 * @property consoleOutput Prints events to logcat/console when true.
 * @property batchSize    Number of events per transport batch (1–100).
 * @property flushIntervalSeconds Seconds between periodic flushes (5–300).
 * @property maxStackTraceLines Maximum stack trace lines per event (1–100).
 * @property flushOnlyWhenIdle Delays flush until the app is idle (Android TV optimization).
 * @property verboseTransportLogging Logs transport-level debug info when true.
 */
data class AppLoggerConfig(
    val endpoint: String,
    val apiKey: String,
    val isDebugMode: Boolean,
    val consoleOutput: Boolean,
    val batchSize: Int,
    val flushIntervalSeconds: Int,
    val maxStackTraceLines: Int,
    val flushOnlyWhenIdle: Boolean,
    val verboseTransportLogging: Boolean
) {
    class Builder {
        private var endpoint: String = ""
        private var apiKey: String = ""
        private var isDebugMode: Boolean = false
        private var consoleOutput: Boolean = true
        private var batchSize: Int = 20
        private var flushIntervalSeconds: Int = 30
        private var maxStackTraceLines: Int = 50
        private var flushOnlyWhenIdle: Boolean = false
        private var verboseTransportLogging: Boolean = false

        fun endpoint(url: String) = apply { endpoint = url }
        fun apiKey(key: String) = apply { apiKey = key }
        fun debugMode(debug: Boolean) = apply { isDebugMode = debug }
        fun consoleOutput(enabled: Boolean) = apply { consoleOutput = enabled }
        fun batchSize(size: Int) = apply { batchSize = size }
        fun flushIntervalSeconds(sec: Int) = apply { flushIntervalSeconds = sec }
        fun maxStackTraceLines(lines: Int) = apply { maxStackTraceLines = lines }
        fun flushOnlyWhenIdle(idle: Boolean) = apply { flushOnlyWhenIdle = idle }
        fun verboseTransportLogging(v: Boolean) = apply { verboseTransportLogging = v }

        fun build(): AppLoggerConfig {
            require(endpoint.startsWith("https://") || isDebugMode || endpoint.isEmpty()) {
                "AppLogger: production endpoint must use HTTPS"
            }
            return AppLoggerConfig(
                endpoint = endpoint,
                apiKey = apiKey,
                isDebugMode = isDebugMode,
                consoleOutput = consoleOutput,
                batchSize = batchSize.coerceIn(1, 100),
                flushIntervalSeconds = flushIntervalSeconds.coerceIn(5, 300),
                maxStackTraceLines = maxStackTraceLines.coerceIn(1, 100),
                flushOnlyWhenIdle = flushOnlyWhenIdle,
                verboseTransportLogging = verboseTransportLogging
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
