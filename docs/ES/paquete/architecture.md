# AppLogger — Arquitectura del Paquete

**Versión:** 0.1.1-alpha.3  
**Fecha:** 2026-03-17  
**Paradigma:** Trait-based design · Clean Architecture · SOLID  
**Lenguaje:** Kotlin Multiplatform 2.0 — Android (Mobile + TV) · iOS · JVM

---

## Índice

1. [Filosofía de Diseño](#1-filosofía-de-diseño)
2. [Estructura de Módulos — KMP](#2-estructura-de-módulos--kmp)
3. [Mapa de Traits (Interfaces)](#3-mapa-de-traits-interfaces)
4. [Implementación de Cada Trait](#4-implementación-de-cada-trait)
5. [Ensamblaje — El Objeto AppLoggerSDK](#5-ensamblaje--el-objeto-apploggersdk)
6. [Pipeline del Evento — Flujo Completo](#6-pipeline-del-evento--flujo-completo)
7. [Gestión del Ciclo de Vida](#7-gestión-del-ciclo-de-vida)
8. [Configuración — Builder Pattern](#8-configuración--builder-pattern)
9. [Extensibilidad — Implementar Transportes Propios](#9-extensibilidad--implementar-transportes-propios)
10. [Concurrencia y Thread Safety](#10-concurrencia-y-thread-safety)
11. [KMP — Arquitectura Multiplataforma](#11-kmp--arquitectura-multiplataforma)

---

## 1. Filosofía de Diseño

### 1.1 Principios Fundamentales

**1. El SDK nunca debe ser la causa de un fallo**

Todo el código interno está envuelto para capturar y silenciar excepciones. Ni un `NullPointerException` interno debe llegar al stack de la app consumidora.

**2. Diseño por contratos (traits), no por implementaciones**

Cada componente del SDK expone solo su interfaz. El código que usa un componente no necesita saber si está hablando con la implementación de producción, un mock de test, o una implementación custom del equipo.

**3. Fire-and-forget en el llamador**

`AppLoggerSDK.error("TAG", "msg")` debe ejecutarse en microsegundos. El hilo del llamador nunca espera al transporte de red.

**4. Comportamiento adaptativo sin código condicional en el llamador**

La app que consume el SDK no escribe `if (isTV) { ... } else { ... }`. El SDK adapta su comportamiento internamente.

### 1.2 Principios SOLID Aplicados

| Principio | Aplicación en AppLogger |
|---|---|
| **S** — Responsabilidad Única | Cada trait hace exactamente una cosa: `LogTransport` solo transporta, `LogBuffer` solo almacena |
| **O** — Abierto/Cerrado | Nuevos transportes (Firebase, Datadog) se añaden implementando `LogTransport`, sin tocar el core |
| **L** — Sustitución de Liskov | Cualquier `LogTransport` puede reemplazar a `SupabaseTransport` sin cambiar el comportamiento del SDK |
| **I** — Segregación de Interfaces | `AppLogger` y `Flushable` son interfaces separadas. El caller solo conoce lo que necesita |
| **D** — Inversión de Dependencias | `AppLoggerImpl` depende de `LogTransport` (abstracción), no de `SupabaseTransport` (concreción) |

---

## 2. Estructura de Módulos — KMP

El proyecto usa Kotlin Multiplatform con un módulo `logger-core` que contiene toda la lógica compartida y sourceSets de plataforma para las implementaciones nativas.

```
appLoggers/
├── logger-core/                         ← Módulo KMP principal
│   └── src/
│       ├── commonMain/kotlin/
│       │   └── com/applogger/core/
│       │       ├── AppLogger.kt             (trait público)
│       │       ├── AppLoggerExtensions.kt   (extension functions logD/I/W/E/C)
│       │       ├── LogTransport.kt          (trait de transporte)
│       │       ├── LogBuffer.kt             (trait de buffer)
│       │       ├── LogFormatter.kt          (trait de formato)
│       │       ├── LogFilter.kt             (trait de filtro)
│       │       ├── DeviceInfoProvider.kt    (trait expect)
│       │       ├── CrashHandler.kt          (trait expect)
│       │       ├── model/
│       │       │   ├── LogEvent.kt
│       │       │   ├── LogLevel.kt
│       │       │   ├── DeviceInfo.kt
│       │       │   └── ThrowableInfo.kt
│       │       └── internal/
│       │           ├── AppLoggerImpl.kt
│       │           ├── BatchProcessor.kt
│       │           ├── RateLimitFilter.kt
│       │           ├── DeduplicationFilter.kt
│       │           └── SessionManager.kt
│       │
│       ├── androidMain/kotlin/
│       │   └── com/applogger/core/
│       │       ├── AppLoggerSDK.kt               (entry point Android)
│       │       ├── AppLoggerLifecycleObserver.kt
│       │       ├── AndroidDeviceInfoProvider.kt  (actual)
│       │       ├── AndroidCrashHandler.kt        (actual)
│       │       ├── PlatformDetector.kt
│       │       └── Platform.android.kt
│       │
│       ├── iosMain/kotlin/
│       │   └── com/applogger/core/
│       │       ├── AppLoggerIos.kt               (entry point iOS en Kotlin)
│       │       ├── IosDeviceInfoProvider.kt      (actual)
│       │       ├── IosCrashHandler.kt            (actual)
│       │       └── Platform.ios.kt
│       │
│       └── jvmMain/kotlin/
│           └── com/applogger/core/
│               └── Platform.jvm.kt
│
├── logger-transport-supabase/           ← Módulo de transporte (KMP, intercambiable)
│   └── src/commonMain/kotlin/
│       └── com/applogger/transport/supabase/
│           └── SupabaseTransport.kt     (Ktor client KMP)
│
└── logger-test/                         ← Utilidades de testing para consumidores del SDK
    └── src/commonMain/kotlin/
        └── com/applogger/test/
```

### 2.1 Configuración `build.gradle.kts` del módulo core

```kotlin
// logger-core/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilations.all { kotlinOptions { jvmTarget = "11" } }
    }

    listOf(
        iosX64(), iosArm64(), iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AppLogger"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.android)
            implementation(libs.sqldelight.android.driver)
            api(libs.androidx.lifecycle.process)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
```
│           ├── SupabaseTransport.kt
│           └── SupabaseLogFormatter.kt
│
└── logger-test/                   ← Utilidades de testing
    └── src/main/kotlin/
        └── com/applogger/test/
            ├── NoOpLogger.kt          (logger que descarta todo — para unit tests)
            ├── InMemoryLogger.kt      (logger que guarda en memoria — para assertions)
            └── FakeTransport.kt       (transport mock con control total)
```

---

## 3. Mapa de Traits (Interfaces)

### Diagrama de dependencias

```
AppLoggerSDK (object — entry point)
       │
       ▼
AppLoggerImpl (implements AppLogger)
       │
       ├── DeviceInfoProvider ──▶ AndroidDeviceInfoProvider
       ├── LogFilter          ──▶ RateLimitFilter (+ chain)
       ├── LogBuffer          —▶ InMemoryBuffer (default; SQLDelight schema disponible en commonMain para extensión)
       ├── LogFormatter       ──▶ JsonLogFormatter
       ├── LogTransport       ──▶ SupabaseTransport / NoOpTransport / Custom
       └── CrashHandler       ──▶ AndroidCrashHandler / NoOpCrashHandler
```

Cada dependencia es **inyectable**: en producción se instancian las implementaciones reales; en tests se usan mocks o implementaciones de `logger-test`.

---

## 4. Implementación de Cada Trait

### 4.1 `AppLogger` — Contrato Público

```kotlin
// logger-core/src/main/kotlin/com/applogger/core/AppLogger.kt

/**
 * Contrato público de AppLogger.
 *
 * Todas las implementaciones deben garantizar:
 * - Ninguna llamada bloquea el hilo del llamador.
 * - Ninguna llamada lanza excepciones al llamador.
 * - Las llamadas a [debug] no hacen nada en modo producción.
 */
interface AppLogger {
    fun debug(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun info(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun warn(tag: String, message: String, throwable: Throwable? = null, anomalyType: String? = null, extra: Map<String, Any>? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun critical(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun metric(name: String, value: Double, unit: String, tags: Map<String, String>? = null)
    fun flush()
}
```

### 4.1.1 `AppLoggerExtensions` — Extension Functions (commonMain)

`AppLoggerExtensions.kt` extiende tanto `AppLogger` como `Any` con helpers que reducen boilerplate.
Disponible en **todos los targets** (Android, iOS, JVM) sin dependencias adicionales.

```kotlin
// logger-core/src/commonMain/kotlin/com/applogger/core/AppLoggerExtensions.kt

// ── Shorthands sobre AppLogger (tag explícito) ──────────────────────────────
fun AppLogger.logD(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
fun AppLogger.logI(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
fun AppLogger.logW(tag: String, message: String, throwable: Throwable? = null, anomalyType: String? = null, extra: Map<String, Any>? = null)
fun AppLogger.logE(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
fun AppLogger.logC(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)

// ── Extensión sobre Any (tag inferido del nombre de clase) ──────────────────
fun Any.logTag(): String  // → this::class.simpleName ?: "Anonymous"

fun Any.logD(logger: AppLogger, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
fun Any.logI(logger: AppLogger, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
fun Any.logW(logger: AppLogger, message: String, throwable: Throwable? = null, anomalyType: String? = null, extra: Map<String, Any>? = null)
fun Any.logE(logger: AppLogger, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
fun Any.logC(logger: AppLogger, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
```

**Uso recomendado:**

```kotlin
class PlayerController(private val logger: AppLogger) {
    fun onError(t: Throwable) {
        // Tag inferido automáticamente → "PlayerController"
        this.logE(logger, "Playback failed", throwable = t, extra = mapOf("codec" to "h264"))
    }

    fun onStart() {
        // Shorthand sin inferencia de tag
        logger.logI("PLAYER", "Playback started")
    }
}
```

---

### 4.2 `LogTransport` — Contrato de Transporte

```kotlin
// logger-core/src/main/kotlin/com/applogger/core/LogTransport.kt

/**
 * Define cómo se envía un batch de LogEvent al destino final.
 *
 * Contrato:
 * - [send] es una función suspend: debe ejecutarse en un coroutine context adecuado.
 * - [send] NUNCA debe lanzar excepciones: capturar internamente y retornar [TransportResult.Failure].
 * - [isAvailable] debe ser rápido (no hacer I/O). Usa el estado de red en caché.
 */
interface LogTransport {
    suspend fun send(events: List<LogEvent>): TransportResult
    fun isAvailable(): Boolean
}

sealed class TransportResult {
    data object Success : TransportResult()
    data class Failure(
        val reason: String,
        val retryable: Boolean,
        val cause: Throwable? = null
    ) : TransportResult()
}
```

### 4.3 `LogBuffer` — Almacenamiento Temporal

```kotlin
// logger-core/src/main/kotlin/com/applogger/core/LogBuffer.kt

/**
 * Almacenamiento temporal de LogEvent antes de su envío al transporte.
 *
 * Contrato:
 * - [push] debe ser thread-safe.
 * - [push] retorna false si el evento fue descartado (política de overflow).
 * - [drain] retorna todos los eventos y limpia el buffer.
 * - Las implementaciones deciden la política de overflow (descartar más nuevos o más viejos).
 */
interface LogBuffer {
    fun push(event: LogEvent): Boolean
    fun drain(): List<LogEvent>
    fun peek(): List<LogEvent>
    fun size(): Int
    fun clear()
    fun isEmpty(): Boolean = size() == 0
}
```

### 4.4 `LogFilter` — Filtrado y Rate Limiting

```kotlin
// logger-core/src/main/kotlin/com/applogger/core/LogFilter.kt

/**
 * Decide si un LogEvent debe ser procesado o descartado.
 *
 * Los filtros son componibles mediante [ChainedLogFilter].
 * Los eventos de nivel ERROR o CRITICAL Always pasan, independientemente del filtro.
 */
interface LogFilter {
    fun passes(event: LogEvent): Boolean
}

/**
 * Compone múltiples filtros: un evento pasa solo si pasa TODOS los filtros.
 */
class ChainedLogFilter(private val filters: List<LogFilter>) : LogFilter {
    override fun passes(event: LogEvent): Boolean =
        filters.all { it.passes(event) }
}
```

### 4.5 `LogFormatter` — Serialización

```kotlin
// logger-core/src/main/kotlin/com/applogger/core/LogFormatter.kt

/**
 * Serializa un [LogEvent] al formato requerido por el [LogTransport].
 *
 * La separación entre formato y transporte permite usar el mismo
 * SupabaseTransport con diferentes formatos (JSON, protobuf, etc.)
 * sin modificar ninguno de los dos.
 */
interface LogFormatter {
    fun format(event: LogEvent): String
    fun formatBatch(events: List<LogEvent>): String
}
```

### 4.6 `DeviceInfoProvider` — Metadatos del Dispositivo

```kotlin
// logger-core/src/main/kotlin/com/applogger/core/DeviceInfoProvider.kt

/**
 * Provee metadatos técnicos del dispositivo.
 *
 * Contrato de privacidad:
 * - NUNCA incluir PII: nombre, email, número de teléfono, IMEI, Android ID.
 * - NUNCA incluir ubicación GPS.
 * - Solo metadatos técnicos: modelo, SO, versión de app, tipo de conexión.
 */
interface DeviceInfoProvider {
    fun get(): DeviceInfo
}
```

---

## 5. Ensamblaje — El Objeto AppLoggerSDK

`AppLoggerSDK` es el único point of entry para la app consumidora. Internamente es un **facade** que oculta toda la complejidad de ensamblaje.

```kotlin
// logger-android/src/main/kotlin/com/applogger/android/AppLoggerSDK.kt

object AppLoggerSDK : AppLogger {

    @Volatile private var instance: AppLogger = NoOpLogger()
    private val isInitialized = AtomicBoolean(false)

    /**
     * Inicializa el SDK. Debe llamarse exactamente una vez, en Application.onCreate().
     * Llamadas subsiguientes son ignoradas (idempotente).
     */
    fun initialize(context: Context, config: AppLoggerConfig) {
        if (!isInitialized.compareAndSet(false, true)) return

        val platform        = PlatformDetector.detect(context)
        val resolvedConfig  = config.resolveDefaults(platform)

        val deviceInfo      = AndroidDeviceInfoProvider(context, resolvedConfig).get()
        val sessionManager  = SessionManager()
        val filter          = buildFilter(resolvedConfig)
        val buffer          = buildBuffer(context, resolvedConfig, platform)
        val transport       = buildTransport(resolvedConfig)
        val formatter       = JsonLogFormatter()

        val processor = BatchProcessor(
            buffer          = buffer,
            transport       = transport,
            formatter       = formatter,
            config          = resolvedConfig
        )

        instance = AppLoggerImpl(
            deviceInfo      = deviceInfo,
            sessionManager  = sessionManager,
            filter          = filter,
            processor       = processor,
            config          = resolvedConfig
        )

        if (!resolvedConfig.isDebugMode) {
            installCrashHandler(context)
        }

        registerLifecycleObserver(context)
    }

    // Delegación a la instancia activa — si no está inicializado, NoOpLogger absorbe la llamada
    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.error(tag, message, throwable, extra)

    override fun info(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.info(tag, message, throwable, extra)

    override fun warn(tag: String, message: String, throwable: Throwable?, anomalyType: String?, extra: Map<String, Any>?) =
        instance.warn(tag, message, throwable, anomalyType, extra)

    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.critical(tag, message, throwable, extra)

    override fun debug(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
        instance.debug(tag, message, throwable, extra)

    override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) =
        instance.metric(name, value, unit, tags)

    override fun flush() = instance.flush()

    fun setAnonymousUserId(userId: String) {
        (instance as? AppLoggerImpl)?.setUserId(userId)
    }

    fun clearAnonymousUserId() {
        (instance as? AppLoggerImpl)?.clearUserId()
    }
}
```

### 5.1 `NoOpLogger` — Seguridad antes de la inicialización

```kotlin
// logger-core/src/main/kotlin/com/applogger/core/internal/NoOpLogger.kt

/**
 * Implementación vacía de AppLogger.
 * Se usa como estado por defecto del SDK antes de initialize().
 * Garantiza que llamadas tempranas al SDK (en ContentProviders, etc.) no crasheen.
 */
internal class NoOpLogger : AppLogger {
    override fun debug(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) = Unit
    override fun info(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) = Unit
    override fun warn(tag: String, message: String, throwable: Throwable?, anomalyType: String?, extra: Map<String, Any>?) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) = Unit
    override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) = Unit
    override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) = Unit
    override fun flush() = Unit
}
```

---

## 6. Pipeline del Evento — Flujo Completo

```kotlin
// logger-core/src/main/kotlin/com/applogger/core/internal/AppLoggerImpl.kt

internal class AppLoggerImpl(
    private val deviceInfo:     DeviceInfo,
    private val sessionManager: SessionManager,
    private val filter:         LogFilter,
    private val processor:      BatchProcessor,
    private val config:         AppLoggerConfig
) : AppLogger {

    @Volatile private var userId: String? = null

    override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) {
        process(LogLevel.ERROR, tag, message, throwable, extra)
    }

    private fun process(
        level:     LogLevel,
        tag:       String,
        message:   String,
        throwable: Throwable? = null,
        extra:     Map<String, Any>? = null
    ) {
        // Guard: debug events son descartados silenciosamente en producción
        if (level == LogLevel.DEBUG && !config.isDebugMode) return

        // Consola en modo debug
        if (config.isDebugMode && config.consoleOutput) {
            printToConsole(level, tag, message, throwable)
        }

        // Construir el evento (operación ligera — solo en memoria)
        val event = LogEvent(
            level         = level,
            tag           = tag.take(100),
            message       = message.take(10_000),
            throwableInfo = throwable?.toThrowableInfo(config.maxStackTraceLines),
            deviceInfo    = deviceInfo,
            sessionId     = sessionManager.sessionId,
            userId        = userId,
            extra         = extra
        )

        // Aplicar filtros (rate limiting, nivel mínimo, etc.)
        if (!filter.passes(event)) return

        // Entregar al procesador (non-blocking: usa Channel.trySend)
        processor.enqueue(event)
    }

    fun setUserId(id: String) { userId = id }
    fun clearUserId() { userId = null }

    override fun flush() = processor.flush()
}
```

### 6.1 `BatchProcessor` — Corazón del Pipeline

```kotlin
// logger-core/src/main/kotlin/com/applogger/core/internal/BatchProcessor.kt

internal class BatchProcessor(
    private val buffer:    LogBuffer,
    private val transport: LogTransport,
    private val formatter: LogFormatter,
    private val config:    AppLoggerConfig
) {
    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("AppLogger-Processor")
    )

    private val eventChannel = Channel<LogEvent>(capacity = Channel.BUFFERED)

    init {
        scope.launch { startConsuming() }
        scope.launch { startPeriodicFlush() }
    }

    fun enqueue(event: LogEvent) {
        // Non-blocking: si el channel está lleno, el evento se descarta silenciosamente
        // El canal nunca bloquea el hilo del llamador
        val accepted = eventChannel.trySend(event).isSuccess
        if (!accepted && config.verboseTransportLogging) {
            Log.w("AppLogger", "Event dropped: channel at capacity")
        }
    }

    private suspend fun startConsuming() {
        for (event in eventChannel) {
            buffer.push(event)

            val shouldFlushImmediately = event.level >= LogLevel.ERROR
            if (shouldFlushImmediately || buffer.size() >= config.batchSize) {
                sendBatch()
            }
        }
    }

    private suspend fun startPeriodicFlush() {
        while (scope.isActive) {
            delay(config.flushIntervalSeconds * 1_000L)
            if (!buffer.isEmpty()) sendBatch()
        }
    }

    private suspend fun sendBatch() {
        val batch = buffer.drain()
        if (batch.isEmpty()) return

        if (!transport.isAvailable()) {
            // Sin red — reinsertar en el buffer offline (SQLite)
            batch.forEach { buffer.push(it) }
            return
        }

        val result = runCatching { transport.send(batch) }
            .getOrElse { TransportResult.Failure(it.message ?: "unknown", retryable = true, cause = it) }

        when (result) {
            is TransportResult.Success       -> { /* batch enviado correctamente */ }
            is TransportResult.Failure -> if (result.retryable) {
                // Programar reintento con backoff exponencial
                RetryQueue.schedule(batch, transport)
            }
        }
    }

    suspend fun flush() {
        sendBatch()
    }
}
```

---

## 7. Gestión del Ciclo de Vida

```kotlin
// logger-android/src/main/kotlin/com/applogger/android/AppLoggerLifecycleObserver.kt

internal class AppLoggerLifecycleObserver(
    private val processor: BatchProcessor
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        // App entró en background: flush sin bloquear el hilo principal
        CoroutineScope(Dispatchers.IO).launch {
            processor.flush()
        }
    }
}

// Registro en AppLoggerSDK.initialize():
ProcessLifecycleOwner.get().lifecycle.addObserver(
    AppLoggerLifecycleObserver(processor)
)
```

---

## 8. Configuración — Builder Pattern

```kotlin
// logger-android/src/main/kotlin/com/applogger/android/AppLoggerConfig.kt

data class AppLoggerConfig internal constructor(
    val endpoint:              String,
    val apiKey:                String,
    val isDebugMode:           Boolean,
    val consoleOutput:         Boolean,
    val batchSize:             Int,
    val flushIntervalSeconds:  Int,
    val maxStackTraceLines:    Int,
    val flushOnlyWhenIdle:     Boolean,
    val verboseTransportLogging: Boolean
) {
    class Builder {
        private var endpoint:              String  = ""
        private var apiKey:                String  = ""
        private var isDebugMode:           Boolean = false
        private var consoleOutput:         Boolean = true
        private var batchSize:             Int     = 20
        private var flushIntervalSeconds:  Int     = 30
        private var maxStackTraceLines:    Int     = 50
        private var flushOnlyWhenIdle:     Boolean = false
        private var verboseTransportLogging: Boolean = false

        fun endpoint(url: String)              = apply { endpoint = url }
        fun apiKey(key: String)                = apply { apiKey = key }
        fun debugMode(debug: Boolean)          = apply { isDebugMode = debug }
        fun consoleOutput(enabled: Boolean)    = apply { consoleOutput = enabled }
        fun batchSize(size: Int)               = apply { batchSize = size }
        fun flushIntervalSeconds(sec: Int)     = apply { flushIntervalSeconds = sec }
        fun maxStackTraceLines(lines: Int)     = apply { maxStackTraceLines = lines }
        fun flushOnlyWhenIdle(idle: Boolean)   = apply { flushOnlyWhenIdle = idle }
        fun verboseTransportLogging(v: Boolean) = apply { verboseTransportLogging = v }

        fun build(): AppLoggerConfig {
            require(endpoint.startsWith("https://") || isDebugMode) {
                "AppLogger: production endpoint must use HTTPS"
            }
            return AppLoggerConfig(
                endpoint              = endpoint,
                apiKey                = apiKey,
                isDebugMode           = isDebugMode,
                consoleOutput         = consoleOutput,
                batchSize             = batchSize.coerceIn(1, 100),
                flushIntervalSeconds  = flushIntervalSeconds.coerceIn(5, 300),
                maxStackTraceLines    = maxStackTraceLines.coerceIn(1, 100),
                flushOnlyWhenIdle     = flushOnlyWhenIdle,
                verboseTransportLogging = verboseTransportLogging
            )
        }
    }

    /**
     * Ajusta los valores por defecto según la plataforma detectada.
     * El desarrollador no necesita saber que está en TV.
     */
    internal fun resolveDefaults(platform: Platform): AppLoggerConfig {
        return if (platform.isLowResource) {
            copy(
                batchSize            = minOf(batchSize, 5),
                flushIntervalSeconds = maxOf(flushIntervalSeconds, 60),
                maxStackTraceLines   = minOf(maxStackTraceLines, 5),
                flushOnlyWhenIdle    = true
            )
        } else this
    }
}
```

---

## 9. Extensibilidad — Implementar Transportes Propios

Para usar un backend diferente a Supabase (Firebase, Datadog, servidor HTTP propio, etc.), implementar `LogTransport`:

```kotlin
// Ejemplo: transporte a Firebase Realtime Database
class FirebaseTransport(
    private val database: FirebaseDatabase
) : LogTransport {

    override suspend fun send(events: List<LogEvent>): TransportResult {
        return try {
            val ref = database.getReference("app_logs")
            withContext(Dispatchers.IO) {
                events.forEach { event ->
                    ref.push().setValue(event.toMap())
                }
            }
            TransportResult.Success
        } catch (e: Exception) {
            TransportResult.Failure(
                reason    = e.message ?: "Firebase write failed",
                retryable = true,
                cause     = e
            )
        }
    }

    override fun isAvailable(): Boolean {
        // Firebase SDK gestiona internamente la conectividad
        return true
    }
}

// Uso en la configuración:
AppLoggerSDK.initialize(
    context = this,
    config  = AppLoggerConfig.Builder()
        .debugMode(BuildConfig.DEBUG)
        .build(),
    transport = FirebaseTransport(FirebaseDatabase.getInstance())
)
```

---

## 10. Concurrencia y Thread Safety

### 10.1 Garantías del SDK

| Componente | Thread Safety | Mecanismo |
|---|---|---|
| `AppLoggerSDK` (entry point) | ✅ | `@Volatile` + `AtomicBoolean` en inicialización |
| `AppLoggerImpl.process()` | ✅ | `Channel.trySend()` es thread-safe |
| `BatchProcessor.enqueue()` | ✅ | `Channel` de Kotlin Coroutines es concurrency-safe |
| `InMemoryBuffer` | ✅ | `ConcurrentLinkedQueue` |
| `RateLimitFilter` | ✅ | `ConcurrentHashMap` + `AtomicInteger` |
| `SessionManager` | ✅ | Inmutable tras creación |
| `AppLoggerConfig` | ✅ | `data class` inmutable |

### 10.2 Coroutine Scope del SDK

El `BatchProcessor` usa un `SupervisorJob()`: si un batch falla, el job del coroutine no se cancela. El scope de procesamiento es tan longevo como la app.

```kotlin
private val scope = CoroutineScope(
    Dispatchers.IO          // No bloquea el hilo principal
    + SupervisorJob()       // Fallos individuales no cancelan el scope
    + CoroutineName("AppLogger-Processor")  // Debugging en Coroutine Debugger
)
```

### 10.3 El `runBlocking` justificado en Crash Handler

El único lugar donde `runBlocking` está justificado es el `CrashHandler`, porque:
1. El proceso está en proceso de terminación.
2. No hay riesgo de ANR (el sistema ya sabe que la app va a morir).
3. El flush síncrono es la única garantía de que el evento de crash llega al backend.

```kotlin
override fun uncaughtException(thread: Thread, throwable: Throwable) {
    logger.critical("CRASH", "Uncaught exception in: ${thread.name}", throwable)
    // runBlocking está justificado aquí: es el último acto del proceso
    runBlocking(Dispatchers.IO) { logger.flush() }
    previousHandler?.uncaughtException(thread, throwable)
}
```

---

## 11. KMP — Arquitectura Multiplataforma

### 11.1 El Patrón `expect` / `actual`

Los traits `DeviceInfoProvider` y `CrashHandler` son `expect` en `commonMain` y tienen una implementación `actual` en cada plataforma. Esto permite que `AppLoggerImpl` (en `commonMain`) los use sin conocer la plataforma.

```kotlin
// commonMain — interfaces + factories expect
interface DeviceInfoProvider {
    fun get(): DeviceInfo
}

interface CrashHandler {
    fun install()
    fun uninstall()
}

expect fun createDefaultDeviceInfoProvider(): DeviceInfoProvider
expect fun createDefaultCrashHandler(logger: AppLogger): CrashHandler
```

```kotlin
// androidMain
actual fun createDefaultDeviceInfoProvider(): DeviceInfoProvider =
    AndroidDeviceInfoProvider(ApplicationContextHolder.get())

actual fun createDefaultCrashHandler(logger: AppLogger): CrashHandler =
    AndroidCrashHandler(logger)
```

```kotlin
// iosMain — Kotlin/Native
actual fun createDefaultDeviceInfoProvider(): DeviceInfoProvider =
    IosDeviceInfoProvider()

actual fun createDefaultCrashHandler(logger: AppLogger): CrashHandler =
    IosCrashHandler(logger)
```

```kotlin
// jvmMain
actual fun createDefaultDeviceInfoProvider(): DeviceInfoProvider =
    JvmDeviceInfoProvider()

actual fun createDefaultCrashHandler(logger: AppLogger): CrashHandler =
    JvmCrashHandler(logger)
```

### 11.2 `DeviceInfo` — Campos por Plataforma

```kotlin
// commonMain/model/DeviceInfo.kt
@Serializable
data class DeviceInfo(
    val brand:          String,
    val model:          String,
    val osVersion:      String,
    val apiLevel:       Int,        // 0 en iOS y JVM
    val platform:       String,     // "android_mobile" | "android_tv" | "ios" | "jvm"
    val appVersion:     String,
    val appBuild:       Int,
    val isLowRamDevice: Boolean,
    val isTV:           Boolean,
    val connectionType: String      // "wifi" | "cellular" | "ethernet" | "none"
)
```

### 11.3 `LogEvent` — Modelo Común

El modelo `LogEvent` vive en `commonMain` y es la unidad que fluye por todo el pipeline:

```kotlin
// commonMain/model/LogEvent.kt
@Serializable
data class LogEvent(
    val id:            String        = generateUUID(),
    val timestamp:     Long,         // epoch ms — commonMain usa `Clock.System.now()`
    val level:         LogLevel,
    val tag:           String,
    val message:       String,
    val throwableInfo: ThrowableInfo? = null,
    val deviceInfo:    DeviceInfo,
    val sessionId:     String,
    val userId:        String?       = null,
    val extra:         Map<String, String>? = null,
    val sdkVersion:    String        = AppLoggerVersion.NAME
)
```

### 11.4 Concurrencia en iOS — Kotlin/Native

En Kotlin/Native (iOS), el modelo de concurrencia es diferente al de JVM. A partir de Kotlin 1.9+ el new memory model elimina la mayoría de las restricciones, pero hay consideraciones:

- **`Channel`**: Compatible con el new memory model. Funciona igual que en JVM.
- **`CoroutineScope`**: Usar `MainScope()` en entrypoints iOS para integración con el main thread de la app.
- **Frozen objects**: Con el new memory model ya no es necesario `freeze()` manual.
- **Dispatchers**: En iOS, `Dispatchers.Default` usa threads de GCD (Grand Central Dispatch).

```kotlin
// iosMain — entry point Kotlin para iOS
object AppLoggerSDK {
    private val scope = MainScope()    // iOS main thread integration

    fun initialize(config: AppLoggerConfig) {
        val logger = AppLoggerImpl(
            transport         = SupabaseTransport(config.endpoint, config.apiKey),
            deviceInfoProvider = createDefaultDeviceInfoProvider(),
            crashHandler      = createDefaultCrashHandler(/* self reference */),
            config            = config
        )
        AppLoggerHolder.set(logger)
        logger.crashHandler.install()
    }

    fun error(tag: String, message: String) {
        AppLoggerHolder.get()?.error(tag, message)
    }
}
```

### 11.5 Distribución del SDK por Plataforma

| Consumidor | Artefacto | Cómo incluirlo |
|---|---|---|
| Android (Gradle) | `.aar` via maven-publish | `implementation("com.github.zuccadev-labs.appLoggers:logger-core:v0.1.1-alpha.3")` |
| iOS (KMP puro) | XCFramework | Build con Gradle KMP desde `iosMain` |
| JVM (Gradle) | `.jar` via maven-publish | `implementation("com.github.zuccadev-labs.appLoggers:logger-core:v0.1.1-alpha.3")` |
