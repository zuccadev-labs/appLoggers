package com.applogger.core

/**
 * Data processing consent levels for GDPR/CCPA compliance.
 *
 * The SDK enforces consent at the event pipeline level: events requiring a higher
 * consent level than the current setting are silently dropped before transport.
 *
 * ## Consent inference (default, no override needed)
 * | Event level          | Required consent |
 * |----------------------|-----------------|
 * | CRITICAL / ERROR     | STRICT          |
 * | METRIC / WARN        | PERFORMANCE     |
 * | INFO / DEBUG         | MARKETING       |
 *
 * ## Scope override
 * Per-call override via [AppLogger.newScope]:
 * ```kotlin
 * val perfLog = AppLoggerSDK.newScope("component" to "network", consentLevel = ConsentLevel.PERFORMANCE)
 * perfLog.info("TAG", "Request latency: 120ms")  // emitted even in PERFORMANCE mode
 * ```
 *
 * ## Legal basis
 * - [STRICT]: Legitimate interest / vital interest (Art. 6(1)(f) GDPR). No opt-in required
 *   in most jurisdictions for essential app operability and crash reporting.
 * - [PERFORMANCE]: "Service Improvement" consent category. Requires accepted T&C.
 * - [MARKETING]: Explicit opt-in under GDPR Art. 7 / CCPA § 1798.100.
 *
 * ## Data minimization (Art. 5(1)(c) GDPR)
 * When [AppLoggerConfig.dataMinimizationEnabled] is true (default), [STRICT] mode
 * automatically anonymizes: user_id → null, device_id → SHA-256 one-way hash.
 */
enum class ConsentLevel {
    /**
     * Essential processing only. Crashes and critical errors, anonymized.
     * No user identity. Device ID is pseudonymized via one-way hash.
     * No behavioral data, no breadcrumbs, no user properties.
     */
    STRICT,

    /**
     * Performance telemetry. Includes STRICT + metrics, timing, network info.
     * User identity optional. No marketing segmentation or behavioral tracking.
     */
    PERFORMANCE,

    /**
     * Full telemetry. Includes PERFORMANCE + user identification, session analytics,
     * behavioral data, A/B variant tracking, user properties.
     * Requires explicit opt-in.
     */
    MARKETING
}

/** Key injected into event extra to override consent inference. Internal SDK use only. */
internal const val CONSENT_EXTRA_KEY = "_consent"
