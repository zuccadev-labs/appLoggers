package com.applogger.core.internal

import com.applogger.core.LogBuffer
import com.applogger.core.model.LogEvent

/**
 * Buffer en memoria con capacidad limitada (FIFO: descarta el más antiguo).
 */
internal class InMemoryBuffer(
    private val maxCapacity: Int = 1000
) : LogBuffer {

    private val buffer = ArrayDeque<LogEvent>()

    override fun push(event: LogEvent): Boolean {
        platformSynchronized(buffer) {
            if (buffer.size >= maxCapacity) {
                buffer.removeFirst() // FIFO: descarta el más antiguo
            }
            buffer.addLast(event)
        }
        return true
    }

    override fun drain(): List<LogEvent> {
        return platformSynchronized(buffer) {
            val events = buffer.toList()
            buffer.clear()
            events
        }
    }

    override fun peek(): List<LogEvent> {
        return platformSynchronized(buffer) {
            buffer.toList()
        }
    }

    override fun size(): Int = platformSynchronized(buffer) { buffer.size }

    override fun clear() {
        platformSynchronized(buffer) { buffer.clear() }
    }
}
