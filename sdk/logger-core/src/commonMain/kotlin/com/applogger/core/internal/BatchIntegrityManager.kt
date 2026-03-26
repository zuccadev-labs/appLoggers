package com.applogger.core.internal

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
internal class BatchIntegrityManager(private val secret: String) {

    val isEnabled: Boolean get() = secret.isNotBlank()

    fun prepareBatch(events: List<LogEvent>): BatchPacket {
        val batchId = generateUUID()
        val tagged = events.map { it.copy(batchId = batchId) }
        val hash = if (isEnabled) computeHash(tagged) else ""
        return BatchPacket(batchId, tagged, hash)
    }

    private fun computeHash(events: List<LogEvent>): String {
        val canonical = events.sortedBy { it.id }
            .joinToString("|") { "${it.id}:${it.timestamp}:${it.level.name}:${it.tag}:${it.message.take(200)}" }
        return runCatching { hmacSha256Hex(secret, canonical) }.getOrElse { "" }
    }
}

internal data class BatchPacket(
    val batchId: String,
    val events: List<LogEvent>,
    val hash: String
)
