package com.applogger.test

import com.applogger.core.LogTransport
import com.applogger.core.TransportResult
import com.applogger.core.model.LogEvent

/**
 * Mock de transporte con control total para tests.
 */
class FakeTransport(
    private val shouldSucceed: Boolean = true,
    private val retryable: Boolean = false,
    private val throwException: Boolean = false
) : LogTransport {

    private val _sentEvents = mutableListOf<LogEvent>()
    val sentEvents: List<LogEvent> get() = _sentEvents.toList()

    var sendCallCount = 0
        private set

    override suspend fun send(events: List<LogEvent>): TransportResult {
        sendCallCount++

        if (throwException) {
            throw RuntimeException("FakeTransport simulated exception")
        }

        return if (shouldSucceed) {
            _sentEvents.addAll(events)
            TransportResult.Success
        } else {
            TransportResult.Failure(
                reason = "FakeTransport simulated failure",
                retryable = retryable
            )
        }
    }

    override fun isAvailable(): Boolean = true

    fun clear() {
        _sentEvents.clear()
        sendCallCount = 0
    }
}
