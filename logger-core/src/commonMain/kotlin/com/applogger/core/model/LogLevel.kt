package com.applogger.core.model

/**
 * Severity levels for log events, ordered from least to most severe.
 *
 * | Level      | Description                                    |
 * |------------|------------------------------------------------|
 * | [DEBUG]    | Verbose info, suppressed in production.        |
 * | [INFO]     | Normal operational messages.                   |
 * | [WARN]     | Recoverable anomalies.                         |
 * | [ERROR]    | Failures that need attention.                  |
 * | [CRITICAL] | Fatal errors / crashes.                        |
 * | [METRIC]   | Numeric measurements (stored in app_metrics).  |
 *
 * The `ordinal` value is **not** part of the public API.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    CRITICAL,
    METRIC
}
