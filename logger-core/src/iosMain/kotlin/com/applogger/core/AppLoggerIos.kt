package com.applogger.core

import com.applogger.core.internal.*
import com.applogger.core.model.LogEvent

/**
 * iOS entry point for the AppLogger SDK, exported to Swift via KMP framework.
 *
 * Access via the [shared] singleton. Thread-safe after [initialize].
 *
 * ## Swift usage
 * ```swift
 * let transport = SupabaseTransport(endpoint: url, apiKey: key)
 * AppLoggerIos.shared.initialize(config: config, transport: transport)
 *
 * AppLoggerIos.shared.info(tag: "Cart", message: "Item added", extra: nil)
 * ```
 *
 * @see AppLoggerConfig for configuration options.
 */
class AppLoggerIos private constructor() : AppLogger {

    private var instance: AppLogger = NoOpLogger()
    private var implRef: AppLoggerImpl? = null

    companion object {
        val shared = AppLoggerIos()
    }

    fun initialize(
        config: AppLoggerConfig,
        transport: LogTransport? = null
    ) {
        val deviceInfoProvider = IosDeviceInfoProvider()
        val deviceInfo = deviceInfoProvider.get()
        val sessionManager = SessionManager()
        val filter = ChainedLogFilter(listOf(RateLimitFilter(120)))
        val buffer = InMemoryBuffer(1000)
        val resolvedTransport = transport ?: object : LogTransport {
            override suspend fun send(events: List<LogEvent>): TransportResult =
                TransportResult.Success
            override fun isAvailable(): Boolean = false
        }
        val formatter = JsonLogFormatter()

        val processor = BatchProcessor(
            buffer = buffer,
            transport = resolvedTransport,
            formatter = formatter,
            config = config
        )

        val impl = AppLoggerImpl(
            deviceInfo = deviceInfo,
            sessionManager = sessionManager,
            filter = filter,
            processor = processor,
            config = config
        )

        instance = impl
        implRef = impl

        AppLoggerHealth.processor = processor
        AppLoggerHealth.transport = resolvedTransport
        AppLoggerHealth.buffer = buffer
        AppLoggerHealth.initialized = true

        if (!config.isDebugMode) {
            IosCrashHandler(impl).install()
        }
    }

    override fun debug(tag: String, message: String, extra: Map<String, Any>?) =
        instance.debug(tag, message, extra)

    override fun info(tag: String, message: String, extra: Map<String, Any>?) =
        instance.info(tag, message, extra)

    override fun warn(tag: String, message: String, anomalyType: String?, extra: Map<String, Any>?) =
        instance.warn(tag, message, anomalyType, extra)

    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.error(tag, message, throwable, extra)

    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.critical(tag, message, throwable, extra)

    override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) =
        instance.metric(name, value, unit, tags)

    override fun flush() = instance.flush()

    fun setAnonymousUserId(userId: String) {
        implRef?.setUserId(userId)
    }

    fun clearAnonymousUserId() {
        implRef?.clearUserId()
    }
}
