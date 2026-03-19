package com.applogger.core

/**
 * Strategy for determining the buffer size.
 */
enum class BufferSizeStrategy {
    /** Use a fixed buffer size (adjusted for low-resource platforms). */
    FIXED,
    /** Adjust buffer size based on available RAM (not yet implemented). */
    ADAPTIVE_TO_RAM,
    /** Adjust buffer size based on sustained log rate (not yet implemented). */
    ADAPTIVE_TO_LOG_RATE
}