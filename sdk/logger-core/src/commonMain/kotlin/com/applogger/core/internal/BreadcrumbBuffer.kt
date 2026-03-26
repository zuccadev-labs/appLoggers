package com.applogger.core.internal

import com.applogger.core.currentTimeMillis
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Thread-safe circular buffer for recording user interaction breadcrumbs.
 *
 * Breadcrumbs represent the sequence of user actions leading up to an error and are
 * automatically attached to ERROR and CRITICAL events as a `"breadcrumbs"` JSON array
 * in the event's extra map.
 *
 * ## Example output in Supabase
 * ```json
 * "breadcrumbs": [
 *   { "ts": 1716000000000, "action": "tap_settings",    "screen": "HomeScreen"     },
 *   { "ts": 1716000001500, "action": "tap_disney_plus", "screen": "SettingsScreen" },
 *   { "ts": 1716000003000, "action": "tap_play",        "screen": "ContentDetail"  }
 * ]
 * ```
 *
 * Combined with [com.applogger.core.internal.EventDebouncer], this gives the full
 * "what the user did → what broke" story without any manual instrumentation at the
 * error call site.
 *
 * Thread-safe via [platformSynchronized].
 *
 * @param capacity Maximum number of breadcrumbs retained. When full, oldest is evicted (FIFO).
 */
internal class BreadcrumbBuffer(private val capacity: Int) {

    private data class Crumb(
        val timestamp: Long,
        val action: String,
        val screen: String?,
        val metadata: Map<String, String>?
    )

    private val lock = Any()
    private val buffer = ArrayDeque<Crumb>(capacity)

    /**
     * Records a new breadcrumb. Evicts the oldest entry when [capacity] is reached.
     *
     * @param action   Short label for the action (e.g. `"tap_play"`, `"swipe_left"`). Truncated to 200 chars.
     * @param screen   Optional screen or component name (e.g. `"HomeScreen"`). Truncated to 100 chars.
     * @param metadata Optional key-value metadata (e.g. `mapOf("content_id" to "movie_123")`).
     *                 Values truncated to 200 chars each.
     */
    fun record(action: String, screen: String? = null, metadata: Map<String, String>? = null) {
        val crumb = Crumb(
            timestamp = currentTimeMillis(),
            action = action.take(200),
            screen = screen?.take(100),
            metadata = metadata?.mapValues { (_, v) -> v.take(200) }
        )
        platformSynchronized(lock) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(crumb)
        }
    }

    /**
     * Returns a snapshot of all current breadcrumbs as a [JsonElement] (JsonArray).
     * Each crumb is a JSON object with `ts`, `action`, `screen` (optional), and any
     * additional [Crumb.metadata] keys spread at the top level.
     */
    fun snapshot(): JsonElement = platformSynchronized(lock) {
        JsonArray(buffer.map { crumb ->
            buildJsonObject {
                put("ts", JsonPrimitive(crumb.timestamp))
                put("action", JsonPrimitive(crumb.action))
                crumb.screen?.let { put("screen", JsonPrimitive(it)) }
                crumb.metadata?.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            }
        })
    }

    /** Returns `true` if no breadcrumbs have been recorded yet. */
    fun isEmpty(): Boolean = platformSynchronized(lock) { buffer.isEmpty() }
}
