package com.applogger.core

/**
 * Strategy for determining the buffer size.
 */
enum class BufferSizeStrategy {
    /** Use a fixed buffer size (adjusted for low-resource platforms). */
    FIXED,
    /** Adjust buffer size based on available RAM (not yet implemented). */
    ADAPTIVE_TO_RAM,
    /**
     * Adjust buffer size based on sustained log rate.
     *
     * **Not yet implemented** — behaves identically to [FIXED].
     * Will be implemented in a future release.
     */
    @Deprecated(
        message = "ADAPTIVE_TO_LOG_RATE is not yet implemented and behaves like FIXED. " +
            "Use FIXED or ADAPTIVE_TO_RAM instead.",
        level = DeprecationLevel.WARNING
    )
    ADAPTIVE_TO_LOG_RATE
}
