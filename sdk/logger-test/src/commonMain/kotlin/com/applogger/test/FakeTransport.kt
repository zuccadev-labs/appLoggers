package com.applogger.test

import com.applogger.core.LogTransport
import com.applogger.core.TransportResult
import com.applogger.core.model.LogEvent

/**
 * Configurable fake [LogTransport] for unit testing transport behavior.
 *
 * @param shouldSucceed  If `true`, [send] returns [TransportResult.Success].
 * @param retryable      Included in [TransportResult.Failure] when `shouldSucceed` is `false`.
 * @param retryAfterMs   Optional delay hint included in [TransportResult.Failure] (e.g. HTTP 429).
 * @param throwException If `true`, [send] throws a [RuntimeException] instead of returning a result.
 */
class FakeTransport(
    private val shouldSucceed: Boolean = true,
    private val retryable: Boolean = false,
    private val retryAfterMs: Long? = null,
    private val throwException: Boolean = false
) : LogTransport {

    private val _sentEvents = mutableListOf<LogEvent>()
    val sentEvents: List<LogEvent> get() = _sentEvents.toList()

    var sendCallCount = 0
        private set

    override suspend fun send(events: List<LogEvent>): TransportResult {
        sendCallCount++

        if (throwException) {
            error("FakeTransport simulated exception")
        }

        return if (shouldSucceed) {
            _sentEvents.addAll(events)
            TransportResult.Success
        } else {
            TransportResult.Failure(
                reason = "FakeTransport simulated failure",
                retryable = retryable,
                retryAfterMs = retryAfterMs
            )
        }
    }

    override fun isAvailable(): Boolean = true

    fun clear() {
        _sentEvents.clear()
        sendCallCount = 0
    }
}
