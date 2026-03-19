package com.applogger.core

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.applogger.core.internal.*
import kotlin.concurrent.Volatile
import java.util.concurrent.atomic.AtomicBoolean

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

        // Determinar capacidad del buffer según estrategia
        val bufferCapacity = when (resolvedConfig.bufferSizeStrategy) {
            AppLoggerConfig.BufferSizeStrategy.FIXED -> if (platform.isLowResource) 100 else 1000
            AppLoggerConfig.BufferSizeStrategy.ADAPTIVE_TO_RAM -> {
                // Ejemplo: 0.1% de RAM, con mínimo 50 y máximo 5000
                val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val totalRam = memoryInfo.totalMem
                val target = (totalRam * 0.001).toInt() // 0.1%
                target.coerceIn(50, 5000)
            }
            AppLoggerConfig.BufferSizeStrategy.ADAPTIVE_TO_LOG_RATE -> {
                // Por ahora usamos el valor por defecto; en futura versión se ajustaría dinámicamente
                if (platform.isLowResource) 100 else 1000
            }
        }

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

        // Wire up health check API
        AppLoggerHealth.processor = processor
        AppLoggerHealth.transport = resolvedTransport
        AppLoggerHealth.buffer = buffer
        AppLoggerHealth.bufferCapacity = bufferCapacity
        AppLoggerHealth.initialized = true

        if (!resolvedConfig.isDebugMode) {
            val crashHandler = AndroidCrashHandler(impl)
            crashHandler.install()
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLoggerLifecycleObserver { impl.flush() }
        )
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

/** No-op transport used when no backend is configured. */
internal class NoOpTransport : LogTransport {
    override suspend fun send(events: List<com.applogger.core.model.LogEvent>): TransportResult =
        TransportResult.Success
    override fun isAvailable(): Boolean = false
}
