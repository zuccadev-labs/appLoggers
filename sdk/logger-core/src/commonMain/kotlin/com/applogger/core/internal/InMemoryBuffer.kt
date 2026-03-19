package com.applogger.core.internal

import com.applogger.core.LogBuffer
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel

/**
 * Buffer en memoria con capacidad limitada y política de overflow configurable.
 *
 * @param maxCapacity Capacidad máxima del buffer (puede ser fija o calculada por estrategia).
 * @param overflowPolicy Política a aplicar cuando el buffer está lleno.
 */
internal class InMemoryBuffer(
    private val maxCapacity: Int = 1000,
    private val overflowPolicy: com.applogger.core.BufferOverflowPolicy = com.applogger.core.BufferOverflowPolicy.DISCARD_OLDEST
) : LogBuffer {

    private val buffer = ArrayDeque<LogEvent>()
    private var overflowCount = 0

    override fun push(event: LogEvent): Boolean {
        platformSynchronized(buffer) {
            if (buffer.size >= maxCapacity) {
                overflowCount++
                when (overflowPolicy) {
                    com.applogger.core.BufferOverflowPolicy.DISCARD_OLDEST -> buffer.removeFirst()
                    com.applogger.core.BufferOverflowPolicy.DISCARD_NEWEST -> return@platformSynchronized false
                    com.applogger.core.BufferOverflowPolicy.PRIORITY_AWARE -> {
                        // Descarta el evento de menor prioridad que no sea CRITICAL
                        val indexToRemove = buffer.indexOfFirst { it.level != com.applogger.core.model.LogLevel.CRITICAL }
                        if (indexToRemove != -1) {
                            buffer.removeAt(indexToRemove)
                        } else {
                            // Todos son CRITICAL, descarta el más antiguo
                            buffer.removeFirst()
                        }
                    }
                }
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

    /**
     * Returns the number of overflow events that have been discarded.
     * This metric can be exposed via AppLoggerHealth for SLA monitoring.
     */
    fun getOverflowCount(): Long = overflowCount
}
