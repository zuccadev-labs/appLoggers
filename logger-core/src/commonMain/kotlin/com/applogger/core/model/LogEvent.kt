package com.applogger.core.model

import com.applogger.core.AppLoggerVersion
import kotlinx.serialization.Serializable

@Serializable
data class LogEvent(
    val id: String,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwableInfo: ThrowableInfo? = null,
    val deviceInfo: DeviceInfo,
    val sessionId: String,
    val userId: String? = null,
    val extra: Map<String, String>? = null,
    val sdkVersion: String = AppLoggerVersion.NAME
)
