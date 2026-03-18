package com.applogger.core

/**
 * Configuración del SDK. Inmutable tras creación.
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
