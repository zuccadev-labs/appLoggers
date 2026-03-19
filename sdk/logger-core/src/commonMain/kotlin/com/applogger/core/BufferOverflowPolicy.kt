package com.applogger.core

/**
 * Policy for handling buffer overflow when the buffer is full.
 */
enum class BufferOverflowPolicy {
    /** Discard the oldest event (FIFO) to make space for the new one. */
    DISCARD_OLDEST,
    /** Discard the newest event (the one being added) to keep the oldest. */
    DISCARD_NEWEST,
    /** Discard events based on priority (lowest priority first). */
    PRIORITY_AWARE
}