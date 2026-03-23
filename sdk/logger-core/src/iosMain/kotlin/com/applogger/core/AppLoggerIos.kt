package com.applogger.core

import com.applogger.core.internal.*
import com.applogger.core.model.LogEvent
import platform.Foundation.NSBundle

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
    private var sessionManagerRef: SessionManager? = null
    private val isInitialized = java.util.concurrent.atomic.AtomicBoolean(false)

    companion object {
        val shared = AppLoggerIos()
    }

    fun initialize(
        config: AppLoggerConfig,
        transport: LogTransport? = null
    ) {
        if (!isInitialized.compareAndSet(false, true)) return

        // B1: Read APPLOGGER_DEBUG from iOS Info.plist (equivalent to Android manifest meta-data).
        // Set APPLOGGER_DEBUG = true in Info.plist to activate debug mode without changing code.
        @Suppress("UNCHECKED_CAST")
        val debugFlag = try {
            NSBundle.mainBundle.infoDictionary
                ?.get("APPLOGGER_DEBUG") as? String == "true"
        } catch (_: Exception) { false }
        val resolvedConfig = if (debugFlag && !config.isDebugMode) config.copy(isDebugMode = true) else config

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
        val formatter = JsonLogFormatter(prettyPrint = resolvedConfig.isDebugMode)

        val processor = BatchProcessor(
            buffer = buffer,
            transport = resolvedTransport,
            formatter = formatter,
            config = resolvedConfig
        )

        val impl = AppLoggerImpl(
            deviceInfo = deviceInfo,
            sessionManager = sessionManager,
            filter = filter,
            processor = processor,
            config = resolvedConfig
        )

        instance = impl
        implRef = impl
        sessionManagerRef = sessionManager

        AppLoggerHealth.processor = processor
        AppLoggerHealth.transport = resolvedTransport
        AppLoggerHealth.buffer = buffer
        AppLoggerHealth.bufferCapacity = 1000
        AppLoggerHealth.initialized = true

        if (!resolvedConfig.isDebugMode) {
            IosCrashHandler(impl).install()
        }
    }

    override fun debug(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.debug(tag, message, throwable, extra)

    override fun info(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.info(tag, message, throwable, extra)

    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?,
        anomalyType: String?,
        extra: Map<String, Any>?
    ) =
        instance.warn(tag, message, throwable, anomalyType, extra)

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

    fun setDeviceId(deviceId: String) {
        implRef?.setDeviceId(deviceId)
    }

    fun clearDeviceId() {
        implRef?.clearDeviceId()
    }

    /**
     * Fuerza el inicio de una nueva sesión inmediatamente.
     */
    fun newSession() {
        sessionManagerRef?.rotate()
    }

    /**
     * Resets the SDK to its uninitialized state. FOR TESTING ONLY.
     */
    internal fun reset() {
        isInitialized.set(false)
        instance = NoOpLogger()
        implRef = null
        sessionManagerRef = null
        AppLoggerHealth.initialized = false
        AppLoggerHealth.processor = null
        AppLoggerHealth.transport = null
        AppLoggerHealth.buffer = null
    }

    override fun addGlobalExtra(key: String, value: String) = instance.addGlobalExtra(key, value)
    override fun removeGlobalExtra(key: String) = instance.removeGlobalExtra(key)
    override fun clearGlobalExtra() = instance.clearGlobalExtra()
}
