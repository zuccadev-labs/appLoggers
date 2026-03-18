package com.applogger.core

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.applogger.core.internal.*
import kotlin.concurrent.Volatile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Entry point público del SDK para Android.
 * Singleton thread-safe con inicialización idempotente.
 */
object AppLoggerSDK : AppLogger {

    @Volatile
    private var instance: AppLogger = NoOpLogger()
    private val isInitialized = AtomicBoolean(false)

    @Volatile
    private var implRef: AppLoggerImpl? = null

    /**
     * Inicializa el SDK. Debe llamarse exactamente una vez, en Application.onCreate().
     * Llamadas subsiguientes son ignoradas (idempotente).
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
        val buffer = InMemoryBuffer(if (platform.isLowResource) 100 else 1000)
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

/**
 * Transporte vacío para modo debug o cuando no se configura backend.
 */
internal class NoOpTransport : LogTransport {
    override suspend fun send(events: List<com.applogger.core.model.LogEvent>): TransportResult =
        TransportResult.Success
    override fun isAvailable(): Boolean = false
}
