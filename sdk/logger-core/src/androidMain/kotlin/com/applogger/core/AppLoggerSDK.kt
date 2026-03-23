package com.applogger.core

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ProcessLifecycleOwner
import com.applogger.core.internal.*
import kotlin.concurrent.Volatile
import java.util.concurrent.atomic.AtomicBoolean

private const val LOW_RESOURCE_BUFFER_CAPACITY = 100
private const val DEFAULT_BUFFER_CAPACITY = 1000
private const val ADAPTIVE_RAM_PERCENTAGE = 0.001
private const val MIN_ADAPTIVE_BUFFER_CAPACITY = 50
private const val MAX_ADAPTIVE_BUFFER_CAPACITY = 5000

/**
 * Android entry point for the AppLogger SDK.
 *
 * Thread-safe singleton with idempotent initialization. Delegates all calls
 * to [com.applogger.core.internal.AppLoggerImpl] once [initialize] succeeds;
 * before that, all calls are no-ops.
 *
 * ## Quick start
 * ```kotlin
 * // In Application.onCreate()
 * val transport = SupabaseTransport(url, key)
 * AppLoggerSDK.initialize(this, config, transport)
 *
 * // Anywhere in the app
 * AppLoggerSDK.info("Cart", "Item added", mapOf("sku" to "A123"))
 * ```
 *
 * @see AppLoggerConfig for configuration options.
 * @see com.applogger.transport.supabase.SupabaseTransport for the Supabase transport.
 */
@Suppress("TooManyFunctions")
object AppLoggerSDK : AppLogger {

    @Volatile
    private var instance: AppLogger = NoOpLogger()
    private val isInitialized = AtomicBoolean(false)

    @Volatile
    private var implRef: AppLoggerImpl? = null

    /**
     * Initializes the SDK. Must be called exactly once, in `Application.onCreate()`.
     * Subsequent calls are silently ignored (idempotent).
     *
     * @param context   Android [Context]; the application context will be extracted.
     * @param config    SDK configuration built via [AppLoggerConfig.Builder].
     * @param transport Optional [LogTransport]; defaults to no-op if omitted.
     */
    fun initialize(
        context: Context,
        config: AppLoggerConfig,
        transport: LogTransport? = null
    ) {
        if (!isInitialized.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        val platform = PlatformDetector.detect(appContext)
        val resolvedConfig = if (platform.isLowResource) config.resolveForLowResource() else config

        val deviceInfoProvider = AndroidDeviceInfoProvider(appContext, platform)
        val deviceInfo = deviceInfoProvider.get()
        val sessionManager = SessionManager()
        val filter = ChainedLogFilter(
            listOf(RateLimitFilter(if (platform.isLowResource) 30 else 120))
        )

        val bufferCapacity = computeBufferCapacity(appContext, platform, resolvedConfig)

        val buffer = InMemoryBuffer(
            maxCapacity = bufferCapacity,
            overflowPolicy = resolvedConfig.bufferOverflowPolicy
        )

        val resolvedTransport = transport ?: NoOpTransport()
        val formatter = JsonLogFormatter()

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

        updateHealthReferences(processor, resolvedTransport, buffer, bufferCapacity)

        if (!resolvedConfig.isDebugMode) {
            val crashHandler = AndroidCrashHandler(impl)
            crashHandler.install()
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLoggerLifecycleObserver { impl.flush() }
        )
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
    ) = instance.warn(tag, message, throwable, anomalyType, extra)

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

    override fun addGlobalExtra(key: String, value: String) {
        implRef?.addGlobalExtra(key, value)
    }

    override fun removeGlobalExtra(key: String) {
        implRef?.removeGlobalExtra(key)
    }

    override fun clearGlobalExtra() {
        implRef?.clearGlobalExtra()
    }

    /**
     * Resets the SDK to its uninitialized state.
     *
     * **FOR TESTING ONLY.** Allows re-initialization between test cases.
     * Never call this in production code.
     */
    @VisibleForTesting
    fun reset() {
        isInitialized.set(false)
        instance = NoOpLogger()
        implRef = null
        AppLoggerHealth.initialized = false
        AppLoggerHealth.processor = null
        AppLoggerHealth.transport = null
        AppLoggerHealth.buffer = null
    }

    private fun computeBufferCapacity(
        appContext: Context,
        platform: Platform,
        config: AppLoggerConfig
    ): Int {
        return when (config.bufferSizeStrategy) {
            BufferSizeStrategy.FIXED -> defaultBufferCapacity(platform)
            BufferSizeStrategy.ADAPTIVE_TO_RAM -> adaptiveRamBufferCapacity(appContext)
            BufferSizeStrategy.ADAPTIVE_TO_LOG_RATE -> defaultBufferCapacity(platform)
        }
    }

    private fun defaultBufferCapacity(platform: Platform): Int {
        return if (platform.isLowResource) {
            LOW_RESOURCE_BUFFER_CAPACITY
        } else {
            DEFAULT_BUFFER_CAPACITY
        }
    }

    private fun adaptiveRamBufferCapacity(appContext: Context): Int {
        val activityManager = appContext.getSystemService(
            Context.ACTIVITY_SERVICE
        ) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem
        val target = (totalRam * ADAPTIVE_RAM_PERCENTAGE).toInt()
        return target.coerceIn(
            MIN_ADAPTIVE_BUFFER_CAPACITY,
            MAX_ADAPTIVE_BUFFER_CAPACITY
        )
    }

    private fun updateHealthReferences(
        processor: BatchProcessor,
        transport: LogTransport,
        buffer: InMemoryBuffer,
        bufferCapacity: Int
    ) {
        AppLoggerHealth.processor = processor
        AppLoggerHealth.transport = transport
        AppLoggerHealth.buffer = buffer
        AppLoggerHealth.bufferCapacity = bufferCapacity
        AppLoggerHealth.initialized = true
    }
}

/** No-op transport used when no backend is configured. */
internal class NoOpTransport : LogTransport {
    override suspend fun send(events: List<com.applogger.core.model.LogEvent>): TransportResult =
        TransportResult.Success
    override fun isAvailable(): Boolean = false
}
