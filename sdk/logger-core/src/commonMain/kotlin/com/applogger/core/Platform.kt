package com.applogger.core

/** Genera un UUID como String. Implementación por plataforma. */
expect fun generateUUID(): String

data class CallerInfo(
	val sourceScope: String? = null,
	val sourceFile: String? = null,
	val sourceMethod: String? = null
)

/** Timestamp actual en milisegundos epoch. Implementación por plataforma. */
expect fun currentTimeMillis(): Long

/** Imprime un mensaje en la consola nativa de la plataforma. */
expect fun platformLog(tag: String, message: String)

/** Returns SHA-256 hash encoded as lowercase hex. */
expect fun sha256Hex(input: String): String

/** Returns HMAC-SHA256 of [data] keyed with [secret], encoded as lowercase hex. */
expect fun hmacSha256Hex(secret: String, data: String): String

/** Returns the first non-SDK caller frame when platform support is available. */
expect fun captureCallerInfo(): CallerInfo?
