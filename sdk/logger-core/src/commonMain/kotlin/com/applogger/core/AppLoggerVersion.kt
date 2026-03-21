package com.applogger.core

/**
 * Single source of truth for the SDK version.
 *
 * Embedded in every [com.applogger.core.model.LogEvent.sdkVersion] field.
 * Update this constant when publishing a new release.
 */
object AppLoggerVersion {
    /** Semantic version string following [semver.org](https://semver.org). */
    const val NAME = "0.1.1-alpha.3"
}
