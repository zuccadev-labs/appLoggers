package com.applogger.core.internal

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import android.content.Context
import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import com.applogger.core.model.ThrowableInfo
import com.applogger.db.AppLoggerDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private const val MAX_STORED_EVENTS = 2_000
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/**
 * Implementación Android de [OfflineStorage] usando SQLDelight + SQLite.
 *
 * Bounded a [MAX_STORED_EVENTS] eventos (FIFO eviction).
 * Thread-safe: SQLDelight serializa las escrituras internamente.
 */
internal class SqliteOfflineStorage(context: Context) : OfflineStorage {

    private val driver = AndroidSqliteDriver(
        schema = AppLoggerDatabase.Schema,
        context = context.applicationContext,
        name = "applogger_offline.db"
    )
    private val db = AppLoggerDatabase(driver)
    private val queries = db.offlineLogsQueries

    override fun persist(events: List<LogEvent>) {
        try {
            db.transaction {
                // Evict oldest if over capacity
                val current = queries.count().executeAsOne()
                val overflow = (current + events.size - MAX_STORED_EVENTS).toInt()
                if (overflow > 0) {
                    queries.deleteOldest(overflow.toLong())
                }
                val now = System.currentTimeMillis()
                events.forEach { event ->
                    queries.insert(
                        id = event.id,
                        timestamp = event.timestamp,
                        level = event.level.name,
                        tag = event.tag,
                        message = event.message,
                        throwable_type = event.throwableInfo?.type,
                        throwable_msg = event.throwableInfo?.message,
                        stack_trace = event.throwableInfo?.stackTrace?.joinToString("\n"),
                        device_info = json.encodeToString(event.deviceInfo),
                        device_id = event.deviceId,
                        session_id = event.sessionId,
                        user_id = event.userId,
                        environment = event.environment,
                        extra = event.extra?.let { json.encodeToString(it) },
                        sdk_version = event.sdkVersion,
                        metric_name = event.metricName,
                        metric_value = event.metricValue,
                        metric_unit = event.metricUnit,
                        metric_tags = event.metricTags?.let { json.encodeToString(it) },
                        created_at = now
                    )
                }
            }
        } catch (_: Exception) {
            // Persistencia offline nunca lanza al caller
        }
    }

    override fun drain(limit: Int): List<LogEvent> {
        return try {
            val rows = queries.selectOldest(limit.toLong()).executeAsList()
            if (rows.isEmpty()) return emptyList()

            db.transaction {
                rows.forEach { queries.deleteById(it.id) }
            }

            rows.mapNotNull { row ->
                try {
                    val deviceInfo = json.decodeFromString<DeviceInfo>(row.device_info)
                    val extra = row.extra?.let {
                        json.decodeFromString<Map<String, JsonElement>>(it)
                    }
                    val metricTags = row.metric_tags?.let {
                        json.decodeFromString<Map<String, String>>(it)
                    }
                    val throwableInfo = if (row.throwable_type != null) {
                        ThrowableInfo(
                            type = row.throwable_type,
                            message = row.throwable_msg,
                            stackTrace = row.stack_trace?.lines() ?: emptyList()
                        )
                    } else null

                    LogEvent(
                        id = row.id,
                        timestamp = row.timestamp,
                        level = LogLevel.valueOf(row.level),
                        tag = row.tag,
                        message = row.message,
                        throwableInfo = throwableInfo,
                        deviceInfo = deviceInfo,
                        deviceId = row.device_id,
                        sessionId = row.session_id,
                        userId = row.user_id,
                        environment = row.environment,
                        extra = extra,
                        sdkVersion = row.sdk_version,
                        metricName = row.metric_name,
                        metricValue = row.metric_value,
                        metricUnit = row.metric_unit,
                        metricTags = metricTags
                    )
                } catch (_: Exception) {
                    null // Fila corrupta — descartada silenciosamente
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun count(): Long = try {
        queries.count().executeAsOne()
    } catch (_: Exception) {
        0L
    }

    override fun clear() {
        try { queries.deleteAll() } catch (_: Exception) {}
    }
}
