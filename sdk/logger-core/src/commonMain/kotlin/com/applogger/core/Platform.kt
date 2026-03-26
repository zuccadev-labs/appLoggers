package com.applogger.core

/** Genera un UUID como String. Implementación por plataforma. */
expect fun generateUUID(): String

/** Timestamp actual en milisegundos epoch. Implementación por plataforma. */
expect fun currentTimeMillis(): Long

/** Imprime un mensaje en la consola nativa de la plataforma. */
expect fun platformLog(tag: String, message: String)

/** Returns SHA-256 hash encoded as lowercase hex. */
expect fun sha256Hex(input: String): String

/** Returns HMAC-SHA256 of [data] keyed with [secret], encoded as lowercase hex. */
expect fun hmacSha256Hex(secret: String, data: String): String
