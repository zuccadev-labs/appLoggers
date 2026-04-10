package com.applogger.core.internal

import com.applogger.core.BatchKind
import com.applogger.core.generateUUID
import com.applogger.core.hmacSha256Hex
import com.applogger.core.model.LogEvent

/**
 * Assigns a [batchId] to every event in a batch and computes an HMAC-SHA256 hash of
 * the canonical batch content. The hash can later be verified by the CLI to detect
 * truncated or tampered batches.
 *
 * When [secret] is blank, [isEnabled] is false and no hashing is performed.
 */
internal class BatchIntegrityManager(
    private val secret: String,
    private val secretId: String = ""
) {

    val isEnabled: Boolean get() = secret.isNotBlank()

    fun prepareBatch(events: List<LogEvent>, kind: BatchKind): BatchPacket {
        val batchId = generateUUID()
        val tagged = events.map { it.copy(batchId = batchId) }
        val hash = if (isEnabled) computeHash(tagged, kind) else ""
        return BatchPacket(batchId, tagged, hash, kind, secretId)
    }

    private fun computeHash(events: List<LogEvent>, kind: BatchKind): String {
        val canonical = events.sortedBy { it.id }.joinToString("|") { event ->
            when (kind) {
                BatchKind.LOGS -> {
                    "${event.id}:${event.timestamp}:${event.level.name}:${event.tag}:${event.message.take(200)}"
                }
                BatchKind.METRICS -> {
                    val name = event.metricName ?: event.tag
                    val value = canonicalMetricValue(event.metricValue ?: 0.0)
                    val unit = event.metricUnit ?: "count"
                    "${event.id}:${event.timestamp}:${name}:${value}:${unit}"
                }
            }
        }
        return runCatching { hmacSha256Hex(secret, canonical) }.getOrElse { "" }
    }

    private fun canonicalMetricValue(value: Double): String {
        val longValue = value.toLong()
        return if (value.isFinite() && value == longValue.toDouble()) {
            "${longValue}.0"
        } else {
            value.toString()
        }
    }
}

internal data class BatchPacket(
    val batchId: String,
    val events: List<LogEvent>,
    val hash: String,
    val kind: BatchKind,
    val keyId: String
)
