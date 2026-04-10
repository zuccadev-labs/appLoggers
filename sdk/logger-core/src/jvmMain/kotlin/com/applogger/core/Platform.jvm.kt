package com.applogger.core

import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual fun generateUUID(): String = UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun platformLog(tag: String, message: String) {
    println("[$tag] $message")
}

actual fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

actual fun hmacSha256Hex(secret: String, data: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val digest = mac.doFinal(data.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

actual fun captureCallerInfo(): CallerInfo? {
    val frame = Throwable("caller capture").stackTrace.firstOrNull(::isApplicationFrame) ?: return null
    return CallerInfo(
        sourceScope = frame.className.substringBefore('$'),
        sourceFile = frame.fileName,
        sourceMethod = frame.methodName
    )
}

private fun isApplicationFrame(frame: StackTraceElement): Boolean {
    val className = frame.className
    return className.isNotBlank() && !IGNORED_CALLER_PREFIXES.any { prefix -> className.startsWith(prefix) }
}

private val IGNORED_CALLER_PREFIXES = listOf(
    "com.applogger.core.internal.AppLoggerImpl",
    "com.applogger.core.internal.AppLoggerImplKt",
    "com.applogger.core.internal.BatchProcessor",
    "com.applogger.core.internal.EventDebouncer",
    "com.applogger.core.Platform",
    "com.applogger.core.AppLoggerExtensions",
    "com.applogger.core.TaggedLogger",
    "com.applogger.transport.",
    "java.",
    "javax.",
    "jdk.",
    "kotlin.",
    "kotlinx.",
    "sun.",
    "org.junit.",
    "org.gradle."
)
