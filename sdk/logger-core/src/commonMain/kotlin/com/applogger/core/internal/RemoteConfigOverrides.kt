package com.applogger.core.internal

import com.applogger.core.LogMinLevel

/**
 * Runtime configuration overrides fetched from the `device_remote_config` Supabase table.
 *
 * Applied dynamically by [AppLoggerImpl] — device-specific rules override global rules.
 * All fields are nullable: `null` means "use the local [com.applogger.core.AppLoggerConfig] default".
 *
 * ## Priority order (highest wins)
 * 1. Device-specific rule (`device_fingerprint = <this device>`)
 * 2. Global rule (`device_fingerprint IS NULL`)
 * 3. Local AppLoggerConfig (compile-time default)
 */
internal data class RemoteConfigOverrides(
    /** Minimum log level override. Events below this level are discarded. */
    val minLevel: LogMinLevel? = null,
    /** Enable debug mode + console output remotely. */
    val debugEnabled: Boolean? = null,
    /** Allowlist of tags — only these tags pass. NULL = all tags allowed. */
    val allowedTags: Set<String>? = null,
    /** Blocklist of tags — these tags are dropped. NULL = no tags blocked. */
    val blockedTags: Set<String>? = null,
    /** Sampling rate [0.0, 1.0]. 1.0 = keep all. 0.1 = keep 10%. */
    val samplingRate: Double? = null
)
