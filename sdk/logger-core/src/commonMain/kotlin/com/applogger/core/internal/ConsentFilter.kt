package com.applogger.core.internal

import com.applogger.core.CONSENT_EXTRA_KEY
import com.applogger.core.ConsentLevel
import com.applogger.core.LogFilter
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pipeline filter that silently drops events requiring a higher consent level
 * than the current setting.
 *
 * Consent inference (when no [CONSENT_EXTRA_KEY] override in event.extra):
 * - CRITICAL / ERROR → [ConsentLevel.STRICT]
 * - METRIC / WARN    → [ConsentLevel.PERFORMANCE]
 * - INFO / DEBUG     → [ConsentLevel.MARKETING]
 *
 * Override per-event by injecting `extra["_consent"] = "strict"|"performance"|"marketing"`.
 * This is done automatically by [com.applogger.core.ScopedAppLogger] when a scope is
 * created with an explicit [ConsentLevel].
 */
internal class ConsentFilter(
    private val consentProvider: () -> ConsentLevel
) : LogFilter {

    override fun passes(event: LogEvent): Boolean {
        val required = resolveRequired(event)
        return required.ordinal <= consentProvider().ordinal
    }

    private fun resolveRequired(event: LogEvent): ConsentLevel {
        val override = event.extra?.get(CONSENT_EXTRA_KEY)
            ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        if (override != null) {
            return when (override.lowercase()) {
                "strict"      -> ConsentLevel.STRICT
                "performance" -> ConsentLevel.PERFORMANCE
                "marketing"   -> ConsentLevel.MARKETING
                else          -> inferFromLevel(event.level)
            }
        }
        return inferFromLevel(event.level)
    }

    private fun inferFromLevel(level: LogLevel): ConsentLevel = when (level) {
        LogLevel.CRITICAL -> ConsentLevel.STRICT
        LogLevel.ERROR    -> ConsentLevel.STRICT
        LogLevel.WARN     -> ConsentLevel.PERFORMANCE
        LogLevel.METRIC   -> ConsentLevel.PERFORMANCE
        LogLevel.INFO     -> ConsentLevel.MARKETING
        LogLevel.DEBUG    -> ConsentLevel.MARKETING
    }
}
