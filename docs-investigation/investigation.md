# AppLogger — Investigación Técnica

**Versión:** 0.1.1  
**Fecha:** 2026-03-17  
**Estado:** Investigación activa  
**Scope:** Kotlin Multiplatform — Android Mobile / Android TV / iOS / JVM

---

## Índice

1. [Contexto y Problemática](#1-contexto-y-problemática)
2. [Estándares Mundiales de Referencia](#2-estándares-mundiales-de-referencia)
3. [Decisiones Arquitectónicas Clave](#3-decisiones-arquitectónicas-clave)
4. [Contrato del Estándar — Diseño por Traits](#4-contrato-del-estándar--diseño-por-traits)
5. [Pipeline de Datos — Del Evento al Transporte](#5-pipeline-de-datos--del-evento-al-transporte)
6. [Captura Automática de Errores y Cierres Inesperados](#6-captura-automática-de-errores-y-cierres-inesperados)
7. [Manejo de Flujos de Alta Velocidad — gRPC y WebSockets](#7-manejo-de-flujos-de-alta-velocidad--grpc-y-websockets)
8. [Adaptación por Plataforma — Mobile vs Android TV](#8-adaptación-por-plataforma--mobile-vs-android-tv)
9. [Privacidad por Diseño — GDPR / LGPD](#9-privacidad-por-diseño--gdpr--lgpd)
10. [Configuración por Entorno](#10-configuración-por-entorno)
11. [Publicación del Paquete](#11-publicación-del-paquete)
12. [Kotlin Multiplatform — Decisión Arquitectónica](#12-kotlin-multiplatform--decisión-arquitectónica)
13. [iOS — Soporte de Plataforma](#13-ios--soporte-de-plataforma)
14. [Compatibilidad de API y Versión Mínima](#14-compatibilidad-de-api-y-versión-mínima)
15. [App de Monitoreo Externo — Herramienta de Beta Testers](#15-app-de-monitoreo-externo--herramienta-de-beta-testers)
16. [Gestión Inteligente de Logs — Batería y Red](#16-gestión-inteligente-de-logs--batería-y-red)
17. [Referencias y Estándares Consultados](#17-referencias-y-estándares-consultados)

---

## 1. Contexto y Problemática

### 1.1 El Problema Real

En grupos de beta testers cerrados (familiares, QA interno, early adopters), la retroalimentación verbal es insuficiente. El usuario dice _"la app se cerró"_ pero el equipo de desarrollo no tiene el contexto técnico real: qué pantalla, qué operación, qué estado de red, qué versión del SO.

Esto genera ciclos de debugging ineficientes, especialmente cuando:

- Las apps consumen **gRPC** o **WebSockets** con flujos de datos de alta velocidad.
- Las apps corren en **Android TV**, donde los recursos (RAM, almacenamiento flash, batería) son más limitados que en mobile.
- El equipo no tiene acceso físico al dispositivo del tester.

### 1.2 El Riesgo Principal

Implementar logging de forma ingenua puede convertir **la solución en el problema**:

| Riesgo | Consecuencia |
|---|---|
| Enviar un request HTTP por cada log | Saturar la red, drenar la batería |
| Capturar stack traces completos en TV | Crashear la app por OOM en envío |
| Guardar logs en SQLite sin límite | Llenar el almacenamiento flash del dispositivo |
| Llamar al logger en el hilo principal | ANR (Application Not Responding) |
| Sin control de volumen en WebSocket/gRPC | 10.000 eventos/minuto → colapso del backend |

### 1.3 Objetivo del Paquete

Diseñar `AppLogger` como un paquete Kotlin de código abierto que:

- Sea **opaco** al desarrollador consumidor: añadir logs no debe requerir conocer el transporte.
- Sea **seguro**: nunca debe ser la causa de un crash, ANR o degradación de performance.
- Sea **adaptativo**: comportamiento diferente según plataforma, estado de red y modo de ejecución.
- Sea **privado por diseño**: ningún dato identificable personal se captura sin consentimiento.
- Sea **extensible**: cambiar de Supabase a otro backend no afecta a las apps consumidoras.

---

## 2. Estándares Mundiales de Referencia

La arquitectura de `AppLogger` está informada por los siguientes estándares y herramientas establecidas en la industria:

### 2.1 Herramientas de Referencia en la Industria

| Herramienta | Empresa | Patrón clave adoptado |
|---|---|---|
| **Firebase Crashlytics** | Google | Crash handler + reporte asíncrono post-reinicio |
| **Sentry** | Sentry Inc. | Buffer circular + breadcrumbs contextuales |
| **Datadog Mobile SDK** | Datadog | Batching con flush por tiempo o tamaño |
| **Timber** | Jake Wharton | Tree pattern — abstracción del destino del log |
| **LMAX Disruptor** | LMAX Group | Ring buffer lock-free para alta velocidad |
| **OpenTelemetry** | CNCF | Separación: Tracing / Metrics / Logging |

### 2.2 Patrones Arquitectónicos Adoptados

- **Tree Pattern** (de Timber): cada "árbol" es un destino de log (consola, remoto, archivo). El núcleo no sabe cuántos ni cuáles destinos existen.
- **Channel-based Producer/Consumer** (Kotlin Coroutines): el productor (la app) nunca espera al consumidor (el transport).
- **Batching + Flush por condición**: los eventos se acumulan y se envían en bloque bajo condiciones: tamaño de batch, tiempo transcurrido, o criticidad del evento.
- **Dead-simple API**: la API pública del logger es tan simple que no requiere documentación para usarla. `AppLogger.e("tag", "message")`.

---

## 3. Decisiones Arquitectónicas Clave

### 3.1 Diseño por Traits (Interfaces en Kotlin)

La librería se construye sobre un conjunto de **interfaces (traits)** que definen contratos sin imponer implementaciones. Esto permite:

- Testear cada componente de forma aislada con mocks.
- Reemplazar cualquier parte (transporte, buffer, formatter) sin romper el contrato.
- Que contribuidores externos implementen transportes alternativos (Firebase, Datadog, etc.).

| Trait (Interface) | Responsabilidad |
|---|---|
| `AppLogger` | Contrato público de logging (debug, info, warn, error, critical) |
| `LogTransport` | Cómo se envían los eventos (REST, gRPC, stdio, noop) |
| `LogBuffer` | Cómo se almacenan los eventos temporalmente (memoria, SQLite, ring buffer) |
| `LogFormatter` | Cómo se serializa un `LogEvent` (JSON, plain text, protobuf) |
| `DeviceInfoProvider` | Qué metadatos del dispositivo se incluyen |
| `CrashHandler` | Qué pasa cuando la app cierra inesperadamente |
| `LogFilter` | Qué eventos pasan o se descartan según reglas |

### 3.2 Separación entre Producción y Generación del Log

El hilo de la UI **nunca espera** al sistema de logging. La firma de llamada al logger es fire-and-forget usando `Channel.trySend()`. Si el buffer está lleno, el evento se descarta silenciosamente antes de afectar la app.

```
UI Thread          LogChannel (buffer)       IO Dispatcher
    │                    │                        │
    │──trySend(event)──▶ │                        │
    │  (non-blocking)    │──receive() in loop──▶  │
    │                    │                        │──batch accumulate
    │                    │                        │──flush to transport
```

### 3.3 Estrategia de Flush

Un evento entra al buffer y sale al transporte bajo estas condiciones (OR logic):

1. El batch acumula **N eventos** (configurable, default: 20 mobile / 5 TV).
2. Han pasado **T segundos** desde el último flush (configurable, default: 30s).
3. El evento tiene severidad **CRITICAL o ERROR** → flush inmediato.
4. La app entra en background / `onPause` → flush de emergencia.
5. Se detecta un crash → flush síncrono bloqueante antes de morir.

---

## 4. Contrato del Estándar — Diseño por Traits

### 4.1 Interfaz Principal: `AppLogger`

```kotlin
/**
 * Contrato unificado de logging para el ecosistema AppLogger.
 * Toda implementación debe garantizar que ninguna llamada
 * bloquea el hilo del llamador ni lanza excepciones no controladas.
 */
interface AppLogger {
    fun debug(tag: String, message: String, extra: Map<String, Any>? = null)
    fun info(tag: String, message: String, extra: Map<String, Any>? = null)
    fun warn(tag: String, message: String, anomalyType: String? = null, extra: Map<String, Any>? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun critical(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun metric(name: String, value: Double, unit: String, tags: Map<String, String>? = null)
    fun flush()
}
```

### 4.2 Trait de Transporte: `LogTransport`

```kotlin
/**
 * Define cómo se envía un batch de eventos al destino final.
 * Las implementaciones deben ser thread-safe y no lanzar excepciones.
 * En caso de fallo, deben retornar TransportResult.Failure.
 */
interface LogTransport {
    suspend fun send(events: List<LogEvent>): TransportResult
    fun isAvailable(): Boolean
}

sealed class TransportResult {
    data object Success : TransportResult()
    data class Failure(val reason: String, val retryable: Boolean) : TransportResult()
}
```

### 4.3 Trait de Buffer: `LogBuffer`

```kotlin
/**
 * Almacenamiento temporal de eventos antes de su envío.
 * Las implementaciones deciden la política de descarte cuando está lleno.
 */
interface LogBuffer {
    fun push(event: LogEvent): Boolean  // false = descartado por política de overflow
    fun drain(): List<LogEvent>
    fun size(): Int
    fun clear()
}
```

### 4.4 Trait de Información del Dispositivo: `DeviceInfoProvider`

```kotlin
/**
 * Provee metadatos técnicos del dispositivo.
 * Nunca debe incluir información personal identificable (PII).
 */
interface DeviceInfoProvider {
    fun get(): DeviceInfo
}

data class DeviceInfo(
    val brand: String,
    val model: String,
    val osVersion: String,
    val apiLevel: Int,
    val platform: String,           // "android_mobile" | "android_tv" | "jvm"
    val appVersion: String,
    val appBuild: Int,
    val isLowRamDevice: Boolean,
    val connectionType: String      // "wifi" | "cellular" | "ethernet" | "none"
)
```

### 4.5 Modelo de Datos Central: `LogEvent`

```kotlin
data class LogEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwableInfo: ThrowableInfo? = null,
    val deviceInfo: DeviceInfo,
    val sessionId: String,
    val extra: Map<String, Any>? = null
)

enum class LogLevel { DEBUG, INFO, WARN, ERROR, CRITICAL, METRIC }

data class ThrowableInfo(
    val type: String,
    val message: String?,
    val stackTrace: List<String>    // Limitado a primeras N líneas según plataforma
)
```

---

## 5. Pipeline de Datos — Del Evento al Transporte

### 5.1 Flujo Completo de un Evento

```
App Code
   │
   ▼
AppLogger.error("TAG", "message", throwable)
   │
   ▼  [hilo del llamador — no bloqueante]
LogEvent creado con DeviceInfo, sessionId, timestamp
   │
   ▼
LogFilter.passes(event)?  ──No──▶  Descartado silenciosamente
   │ Sí
   ▼
Channel<LogEvent>.trySend(event)
   │  [si el channel está lleno → evento descartado, nunca bloquea]
   ▼
[Coroutine en Dispatchers.IO — background worker]
   │
   ▼
Batch accumulator
   │
   ├──▶ ¿Condición de flush cumplida?
   │         │
   │         ▼
   │    LogFormatter.format(batch)
   │         │
   │         ▼
   │    LogTransport.send(formattedBatch)
   │         │
   │         ├──▶ TransportResult.Success → LogBuffer.clear()
   │         └──▶ TransportResult.Failure(retryable=true) → RetryQueue
   │
   └──▶ No → seguir acumulando
```

### 5.2 Política de Reintento

Los fallos de transporte retryables se colocan en una `RetryQueue` con backoff exponencial:

- Primer reintento: 5 segundos
- Segundo reintento: 30 segundos
- Tercer reintento: 5 minutos
- Después de 3 fallos: log persiste en SQLite local hasta próxima sesión

---

## 6. Captura Automática de Errores y Cierres Inesperados

### 6.1 UncaughtExceptionHandler — Crash Reporter

La librería instala un `Thread.UncaughtExceptionHandler` en el momento de inicialización en modo producción. Es crítico que este handler sea **síncrono y bloqueante**, ya que el proceso va a morir.

```kotlin
internal class AppLoggerCrashHandler(
    private val logger: AppLogger,
    private val previousHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Paso 1: registrar el crash con flush síncrono (runBlocking está justificado aquí)
        logger.critical("CRASH", "Uncaught exception in thread: ${thread.name}", throwable)
        runBlocking(Dispatchers.IO) {
            (logger as? Flushable)?.flush()
        }
        // Paso 2: delegar al handler anterior (sistema / Firebase Crashlytics si existe)
        previousHandler?.uncaughtException(thread, throwable)
    }
}
```

**Principio de Seguridad**: la librería **siempre** encadena el handler previo. No rompe otros sistemas de crash reporting presentes en la app.

### 6.2 Lifecycle Observer — Flush en Background

En Android, el logger se suscribe al `ProcessLifecycleOwner` para hacer flush cuando la app entra en background:

```kotlin
internal class AppLoggerLifecycleObserver(
    private val logger: AppLogger
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        // La app entró en background: flush no bloqueante
        CoroutineScope(Dispatchers.IO).launch {
            logger.flush()
        }
    }
}
```

---

## 7. Manejo de Flujos de Alta Velocidad — gRPC y WebSockets

### 7.1 El Riesgo de Alta Frecuencia

En aplicaciones con gRPC o WebSocket activos, pueden generarse **miles de eventos por minuto**. Loguear cada frame, cada mensaje o cada llamada RPC sin control colapsaría tanto la app como el backend.

**Regla de oro**: el logger debe ser instrumentado en los **interceptores**, no en el código de negocio. Esto garantiza captura automática y consistente sin saturación.

### 7.2 gRPC — ClientInterceptor

```kotlin
/**
 * Interceptor de gRPC que solo registra eventos cuando superan umbrales de anomalía.
 * No loguea llamadas exitosas dentro del tiempo normal.
 */
class GrpcLoggingInterceptor(
    private val logger: AppLogger,
    private val latencyThresholdMs: Long = 500
) : ClientInterceptor {

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        options: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        val startTime = System.currentTimeMillis()

        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, options)
        ) {
            override fun onClose(status: Status, trailers: Metadata) {
                val duration = System.currentTimeMillis() - startTime

                when {
                    !status.isOk -> logger.error(
                        tag = "GRPC",
                        message = "${method.fullMethodName} failed: ${status.code}",
                        extra = mapOf("duration_ms" to duration, "code" to status.code.name)
                    )
                    duration > latencyThresholdMs -> logger.warn(
                        tag = "GRPC_LATENCY",
                        message = "${method.fullMethodName} took ${duration}ms",
                        anomalyType = "HIGH_LATENCY"
                    )
                    // Llamada normal: no se loguea — sin costo para el pipeline
                }
                super.onClose(status, trailers)
            }
        }
    }
}
```

### 7.3 WebSocket — Listener Wrapper

```kotlin
/**
 * Wrapper de OkHttp WebSocketListener que captura anomalías de conexión.
 * Filtra reconexiones normales de fallos reales.
 */
class LoggingWebSocketListener(
    private val delegate: WebSocketListener,
    private val logger: AppLogger,
    private val tag: String = "WEBSOCKET"
) : WebSocketListener() {

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        logger.error(tag, "WebSocket failure: ${t.message}", t)
        delegate.onFailure(webSocket, t, response)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        // Solo es anomalía si el código no es cierre normal (1000)
        if (code != 1000) {
            logger.warn(tag, "WebSocket closing abnormally: code=$code reason=$reason", anomalyType = "ABNORMAL_CLOSE")
        }
        delegate.onClosing(webSocket, code, reason)
    }
    // onOpen, onMessage, onClosed → se delegan sin logging para no saturar
}
```

### 7.4 Control de Volumen — Rate Limiter por Tag

Para prevenir flood de logs del mismo origen:

```kotlin
class RateLimitFilter(
    private val maxEventsPerMinutePerTag: Int = 60
) : LogFilter {
    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    override fun passes(event: LogEvent): Boolean {
        // CRITICAL y ERROR siempre pasan, sin importar el rate
        if (event.level >= LogLevel.ERROR) return true

        val counter = counters.getOrPut(event.tag) { AtomicInteger(0) }
        return counter.incrementAndGet() <= maxEventsPerMinutePerTag
    }
}
```

---

## 8. Adaptación por Plataforma — Mobile vs Android TV

| Parámetro | Android Mobile | Android TV |
|---|---|---|
| Batch size máximo | 20 eventos | 5 eventos |
| Stack trace lines | Completo (50 líneas) | Truncado (5 líneas) |
| SQLite buffer local | 1000 registros (FIFO) | 100 registros (FIFO) |
| Flush en idle | No aplica | Sí (solo en `onPause`) |
| Rate limit por tag | 120 evt/min | 30 evt/min |
| Retry en fallo | Inmediato + backoff | Solo en WiFi, en idle |
| Metadatos extra | RAM disponible, GPS presente | `is_leanback`, canal activo |

### 8.1 Detección Automática de Plataforma

```kotlin
internal object PlatformDetector {
    fun detect(context: Context): Platform {
        val packageManager = context.packageManager
        return when {
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> Platform.ANDROID_TV
            packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) -> Platform.WEAR_OS
            else -> Platform.ANDROID_MOBILE
        }
    }
}

enum class Platform(val isLowResource: Boolean) {
    ANDROID_MOBILE(isLowResource = false),
    ANDROID_TV(isLowResource = true),
    WEAR_OS(isLowResource = true),
    JVM(isLowResource = false)
}
```

---

## 9. Privacidad por Diseño — GDPR / LGPD

### 9.1 Principios Aplicados

`AppLogger` adopta los principios de **Privacy by Design** (Ann Cavoukian):

1. **Proactivo, no reactivo**: la privacidad se diseña en el core, no se añade después.
2. **Default privado**: sin configuración explícita, no se captura ningún dato identificable.
3. **Minimización de datos**: solo se capturan metadatos técnicos necesarios para el diagnóstico.

### 9.2 Clasificación de Datos Capturados

| Dato | Tipo | Justificación legal |
|---|---|---|
| `device_model` | Técnico | Telemetría técnica no PII |
| `os_version` | Técnico | Telemetría técnica no PII |
| `app_version` | Técnico | Telemetría técnica no PII |
| `platform` | Técnico | Clasificación de plataforma |
| `connection_type` | Técnico | Estado de red, no ubicación |
| `is_tv` | Técnico | Clasificación de plataforma |
| `session_id` | Pseudónimo | UUID regenerado por sesión, sin persistencia |
| `user_id` | **OPCIONAL** | Solo con consentimiento explícito, UUID anónimo |
| IP del dispositivo | **PROHIBIDO** | Requiere consentimiento GDPR explícito |
| GPS / Ubicación | **PROHIBIDO** | Fuera del scope de telemetría técnica |
| Contenido de mensajes | **PROHIBIDO** | Nunca se loguean payloads de gRPC/WS |

---

## 10. Configuración por Entorno

### 10.1 local.properties (no commiteable)

```properties
# AppLogger — Configuración local de desarrollo (NO commitear)
APPLOGGER_URL=https://tu-proyecto.supabase.co
APPLOGGER_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
APPLOGGER_DEBUG=true
appLogger_logToConsole=true
appLogger_batchSize=20
appLogger_flushIntervalSeconds=30
appLogger_lowStorageMode=false
appLogger_maxStackTraceLines=50
```

### 10.2 build.gradle.kts — Mapeo a BuildConfig

```kotlin
android {
    defaultConfig {
        val props = Properties().also {
            it.load(rootProject.file("local.properties").inputStream())
        }
        buildConfigField("String",  "LOGGER_URL",   "\"${props["APPLOGGER_URL"] ?: ""}\"")
        buildConfigField("String",  "LOGGER_KEY",   "\"${props["APPLOGGER_ANON_KEY"] ?: ""}\"")
        buildConfigField("Boolean", "LOGGER_DEBUG", "${props["APPLOGGER_DEBUG"] ?: false}")
        buildConfigField("Boolean", "LOG_CONSOLE",  "${props["appLogger_logToConsole"] ?: true}")
        buildConfigField("Int",     "LOGGER_BATCH", "${props["appLogger_batchSize"] ?: 20}")
    }
}
```

### 10.3 Inicialización en Application.kt

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLoggerSDK.initialize(
            context = this,
            config = AppLoggerConfig.Builder()
                .endpoint(BuildConfig.LOGGER_URL)
                .apiKey(BuildConfig.LOGGER_KEY)
                .debugMode(BuildConfig.LOGGER_DEBUG)
                .consoleOutput(BuildConfig.LOG_CONSOLE)
                .batchSize(BuildConfig.LOGGER_BATCH)
                .build()
        )
    }
}
```

---

## 11. Publicación del Paquete

### 11.1 Estrategia de Publicación por Fase

| Fase | Método | Audiencia |
|---|---|---|
| Alpha / interno | GitHub Releases + JitPack | Equipo propio |
| Beta público | GitHub Packages (Maven) | Contributors / early adopters |
| Estable | Maven Central | Ecosistema Kotlin general |

### 11.2 Versionado Semántico (SemVer)

```
MAJOR.MINOR.PATCH[-PRERELEASE]

1.0.0         → Estable, API pública fija
1.1.0         → Nueva funcionalidad, backwards compatible
1.1.1         → Bugfix
2.0.0         → Breaking change en la API pública
1.0.0-alpha.1 → Pre-release, API puede cambiar
1.0.0-beta.1  → Feature-complete, en período de prueba
```

### 11.3 Seguridad en la API Key

La API key de Supabase usada por el transporte debe:
- Tener permisos **solo de INSERT** en la tabla `app_logs` (RLS en Supabase).
- Ser la **anon key** (no la service key). La service key nunca se embebe en cliente Android.
- Estar en `local.properties` en desarrollo y en variables de entorno CI/CD en producción.

---

## 12. Referencias y Estándares Consultados

| # | Referencia | Relevancia |
|---|---|---|
| [1] | Android Developers — Logging Best Practices | Base del sistema de logs en Android |
| [2] | Timber by Jake Wharton — GitHub | Patrón Tree para abstracción de destinos |
| [3] | GDPR Article 4 — Definition of Personal Data | Clasificación de datos capturados |
| [4] | LGPD (Lei 13.709/2018) — Brasil | Requisito para apps en mercado latinoamericano |
| [5] | OpenTelemetry Specification — Logs | Estándar de campos en eventos de log |
| [6] | Firebase Crashlytics Architecture | Crash handler + flush pre-muerte |
| [7] | JitPack.io Documentation | Publicación de librerías desde GitHub |
| [8] | Maven Central Publishing Guide | Publicación estándar en ecosistema JVM |
| [9] | LMAX Disruptor — Technical Paper | Ring buffer de alta velocidad (referencia conceptual) |
| [10] | Android WorkManager Documentation | Background processing en Android |
| [11] | Kotlin Coroutines — Channel API | Comunicación asíncrona productor/consumidor |
| [12] | RFC 7519 — JWT | Seguridad en la API key de transporte |

---

## 12. Kotlin Multiplatform — Decisión Arquitectónica

### 12.1 Por qué Kotlin Multiplatform (KMP)

El SDK debe correr en tres plataformas con un único codebase de lógica:

| Plataforma | Donde se ejecuta | Compilación KMP |
|---|---|---|
| Android Mobile | `androidMain` | JVM / ART |
| Android TV | `androidMain` | JVM / ART |
| iOS | `iosMain` | Kotlin/Native → XCFramework |
| JVM (servidor / tests) | `jvmMain` | JDK bytecode |

**Separación de código:**

```
commonMain/       ← Lógica de negocio 100% compartida
    AppLogger.kt         (trait/interface)
    LogEvent.kt          (modelo de datos)
    BatchProcessor.kt    (pipeline de batching)
    RateLimitFilter.kt   (rate limiter)
    SessionManager.kt    (session_id)

androidMain/      ← Solo código Android
    AndroidDeviceInfoProvider.kt
    AndroidCrashHandler.kt
    AppLoggerLifecycleObserver.kt
    SqliteOfflineBuffer.kt (usa SQLDelight, no Room)
    GrpcLoggingInterceptor.kt
    LoggingWebSocketListener.kt

iosMain/          ← Solo código iOS
    IosDeviceInfoProvider.kt
    IosCrashHandler.kt         (NSException handler)
    SqliteOfflineBuffer.kt     (usa SQLDelight)
    URLSessionLoggingDelegate.kt

jvmMain/          ← Solo JVM (servidor / desktop)
    JvmDeviceInfoProvider.kt
    JvmCrashHandler.kt
```

### 12.2 Dependencias en el grafo KMP

La regla es: **`commonMain` no tiene dependencias de plataforma nativas**. Todas las implementaciones nativas se inyectan en el nivel de plataforma.

```kotlin
// commonMain — zero platform dependencies
interface DeviceInfoProvider {
    fun get(): DeviceInfo
}

// androidMain — implementación Android
actual class PlatformDeviceInfoProvider(val context: Context) : DeviceInfoProvider {
    override fun get(): DeviceInfo = DeviceInfo(
        brand          = Build.BRAND,
        model          = Build.MODEL,
        osVersion      = Build.VERSION.RELEASE,
        ...
    )
}

// iosMain — implementación iOS (Kotlin/Native)
actual class PlatformDeviceInfoProvider : DeviceInfoProvider {
    override fun get(): DeviceInfo = DeviceInfo(
        brand    = UIDevice.currentDevice.model,
        model    = UIDevice.currentDevice.localizedModel,
        osVersion = UIDevice.currentDevice.systemVersion,
        ...
    )
}
```

---

## 13. iOS — Soporte de Plataforma

### 13.1 Distribución como XCFramework

El SDK se distribuye para iOS como un **XCFramework** generado por Kotlin/Native, consumible desde:
- Swift Package Manager (SPM) — método preferido.
- CocoaPods — soporte alternativo.

```swift
// iOS (Swift) — API pública expuesta automáticamente desde Kotlin
import AppLogger

@main
struct MyApp: App {
    init() {
        AppLoggerSDK.shared.initialize(
            config: AppLoggerConfigBuilder()
                .endpoint(endpoint: "https://tu-proyecto.supabase.co")
                .apiKey(key: "eyJ...")
                .debugMode(debug: false)
                .build()
        )
    }
}

// Uso desde Swift
AppLoggerSDK.shared.error(tag: "PLAYER", message: "Playback failed")
AppLoggerSDK.shared.metric(name: "buffer_time", value: 450.0, unit: "ms")
```

### 13.2 Captura de Crashes en iOS

En iOS no existe `Thread.UncaughtExceptionHandler`. Se usan dos mecanismos:

1. **NSSetUncaughtExceptionHandler** — para excepciones Objective-C (NSException).
2. **signal handler para SIGTERM, SIGSEGV, SIGBUS** — para errores de memoria/señales del OS.

```kotlin
// iosMain/AppLoggerCrashHandler.kt
actual class PlatformCrashHandler actual constructor(
    private val logger: AppLogger
) : CrashHandler {

    actual override fun install() {
        NSSetUncaughtExceptionHandler { exception ->
            exception?.let { nsEx ->
                logger.critical(
                    tag     = "IOS_CRASH",
                    message = nsEx.reason ?: "NSException",
                    // Stack trace limitado — NSException.callStackSymbols
                )
                // flush síncrono antes de que el runtime limpie el estado
            }
        }
        // signal handlers para SIGSEGV, SIGBUS, SIGABRT
        installSignalHandlers(logger)
    }
}
```

### 13.3 Metadatos del Dispositivo en iOS

```kotlin
// iosMain
import platform.UIKit.UIDevice
import platform.Foundation.NSBundle

actual class PlatformDeviceInfoProvider : DeviceInfoProvider {
    override fun get(): DeviceInfo = DeviceInfo(
        brand          = "Apple",
        model          = UIDevice.currentDevice.model,
        osVersion      = UIDevice.currentDevice.systemVersion,
        apiLevel       = 0,    // No aplica en iOS
        platform       = "ios",
        appVersion     = NSBundle.mainBundle.infoDictionary
                            ?.get("CFBundleShortVersionString") as? String ?: "",
        appBuild       = (NSBundle.mainBundle.infoDictionary
                            ?.get("CFBundleVersion") as? String)?.toIntOrNull() ?: 0,
        isLowRamDevice = false,    // Detectar con os_proc_available_memory() si se requiere
        connectionType = getConnectionTypeIos()    // Reachability framework
    )
}
```

---

## 14. Compatibilidad de API y Versión Mínima

### 14.1 Política de Versión Mínima

La política es: **API mínima soportada = última versión con > 5% de cuota de mercado activa + 1**.

Al momento de publicación (2026-03-17), la distribución de versiones Android coloca ese umbral en:

| Plataforma | API Mínima | Versión Android | Justificación |
|---|---|---|---|
| Android Mobile | **API 22** | Android 5.1 (Lollipop MR1) | Cubre > 99% del mercado activo |
| Android TV | **API 22** | Android 5.1 | Equiparar con Mobile para un solo sourceset |
| iOS | **iOS 15** | — | Swift Concurrency (async/await) y XCFramework estable |
| JVM | **JDK 11** | — | LTS más bajo con soporte activo |

> **Nota:** API 21 (Android 5.0) se descarta porque tiene un bug crítico en el runtime ART con `Dispatchers.IO` y `Channel` de coroutines que produce deadlocks esporádicos en dispositivos de baja RAM.

### 14.2 Garantías de Compatibilidad por Feature

| Feature | API mínima | Fallback si no disponible |
|---|---|---|
| `WorkManager` | API 14 | No se usa; se usa `CoroutineScope` directo |
| `JobScheduler` | API 21 | No se usa |
| `ConnectivityManager.registerNetworkCallback` | API 21 | Polling cada 30s |
| `ProcessLifecycleOwner` | API 14 (via Jetpack) | Ignorar; flush solo en crash |
| JDBC para SQLite | Siempre (JVM) | N/A |
| SQLDelight Android | API 14 | N/A |
| NSURLSession | iOS 7 | N/A |

### 14.3 Kotlin y Coroutines — Versiones

```toml
# gradle/libs.versions.toml
[versions]
kotlin            = "2.0.0"           # Kotlin 2.0 — primer release estable con K2 compiler
coroutines        = "1.8.1"           # Coroutines 1.8+ para structured concurrency y Channel
serialization     = "1.6.3"           # kotlinx.serialization para LogEvent → JSON
sqldelight        = "2.0.2"           # SQLDelight 2 — KMP native SQLite
ktor              = "2.3.10"          # Ktor client para transport HTTP (KMP)
agp               = "8.4.0"
minSdk            = "22"
targetSdk         = "35"
compileSdk        = "35"
```

### 14.4 No usar APIs deprecadas por debajo de minSdk

```kotlin
// ✅ Compatible desde API 22
val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
    as ConnectivityManager
val networkInfo = connectivityManager.activeNetworkInfo  // deprecated en API 29
// Para API 29+:
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    connectivityManager.registerDefaultNetworkCallback(...)
} else {
    @Suppress("DEPRECATION")
    val info = connectivityManager.activeNetworkInfo
    isConnected = info?.isConnected == true
}
```

---

## 15. App de Monitoreo Externo — Herramienta de Beta Testers

### 15.1 Arquitectura Separada

El SDK AppLogger **no incluye herramientas de monitoreo embebidas**. La captura y visualización de logs se delega a una **aplicación externa separada** que consume los datos almacenados por el SDK.

```
┌─────────────────────┐         ┌─────────────────────┐         ┌─────────────────────┐
│  App del Cliente    │         │  Backend (Supabase) │         │  App de Monitoreo   │
│  (consuming app)    │────────▶│  PostgreSQL / RLS   │◀────────│  (external tool)    │
│                     │  SDK    │                     │  API    │                     │
│  AppLogger SDK      │  envía  │  app_logs table     │  lee    │  Visualización      │
│  (library)          │────────▶│  app_metrics table  │◀────────│  Filtros, alerts    │
└─────────────────────┘         └─────────────────────┘         └─────────────────────┘
```

**Principio:** El SDK solo escribe. La app de monitoreo solo lee. Separación completa de responsabilidades.

### 15.2 Responsabilidades de la App Externa

| Función | Descripción |
|---|---|
| **Consulta de logs** | Leer `app_logs` y `app_metrics` desde Supabase con credenciales de solo lectura |
| **Filtros en tiempo real** | Filtrar por plataforma, nivel, tag, rango de fechas, session_id |
| **Visualización de crashes** | Vista dedicada para eventos `CRITICAL` con stack traces completos |
| **Métricas de performance** | Gráficos de `app_metrics` (buffer_time, api_response_time, etc.) |
| **Exportación** | Exportar sesiones de beta testing a JSON/CSV para adjuntar en issues |
| **Notificaciones** | Alertas cuando llegan eventos `CRITICAL` o `ERROR` de apps monitoreadas |

### 15.3 Acceso a Datos

La app de monitoreo usa credenciales de **solo lectura** (service_role key con RLS restrictivo) para consultar los mismos datos que el SDK escribe:

```sql
-- Política RLS para la app de monitoreo (service_role)
-- La app de monitoreo usa una service key con permisos SELECT
CREATE POLICY monitor_read_policy ON app_logs
    FOR SELECT
    USING (true);  -- Puede leer todos los logs

CREATE POLICY monitor_read_metrics ON app_metrics
    FOR SELECT
    USING (true);
```

### 14.4 Flujo de Trabajo del Beta Tester

1. El beta tester usa la app del cliente (con AppLogger SDK integrado).
2. El SDK captura errores, crashes y métricas automáticamente.
3. Los datos se envían a Supabase (o se almacenan en SQLite offline).
4. El equipo de QA abre la **app de monitoreo** para ver los datos en tiempo real.
5. Se filtra por `session_id` del beta tester específico.
6. Se exporta el reporte y se adjunta al issue tracker.

---

## 16. Gestión Inteligente de Logs — Batería y Red

### 16.1 Inteligencia de Red — NetworkAwareScheduler

El SDK monitorea el estado de red y adapta la estrategia de envío:

```
Estado de red        Estrategia
───────────────────  ──────────────────────────────────────────────
WiFi / Ethernet      Flush normal cada 30s, batch de 20
4G/LTE               Flush cada 60s, batch de 50 (menos requests)
3G / Edge            Solo flush en ERROR/CRITICAL, no periódico
Sin red              Acumular en SQLite, no reintentar hasta reconexión
Batería < 15%        Suspender flush periódico; solo ERROR/CRITICAL
Modo Ahorro          Equiparar a "Sin red" hasta que se desactive
```

```kotlin
internal class NetworkAwareScheduler(
    private val context: Context,
    private val processor: BatchProcessor
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun getFlushStrategy(): FlushStrategy {
        val network = connectivityManager.activeNetwork
        val caps    = connectivityManager.getNetworkCapabilities(network ?: return FlushStrategy.OFFLINE)

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> FlushStrategy.AGGRESSIVE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)  -> FlushStrategy.NORMAL
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)           -> FlushStrategy.CONSERVATIVE
            else                                                                 -> FlushStrategy.OFFLINE
        }
    }
}

enum class FlushStrategy(
    val batchSize:            Int,
    val intervalSeconds:      Int,
    val flushOnlyOnCritical:  Boolean
) {
    AGGRESSIVE   (batchSize = 20,  intervalSeconds = 30,  flushOnlyOnCritical = false),
    NORMAL       (batchSize = 50,  intervalSeconds = 60,  flushOnlyOnCritical = false),
    CONSERVATIVE (batchSize = 100, intervalSeconds = 300, flushOnlyOnCritical = false),
    OFFLINE      (batchSize = 0,   intervalSeconds = 0,   flushOnlyOnCritical = true)
}
```

### 16.2 Deduplicación de Eventos

En flujos de alta velocidad (gRPC streaming, WebSocket continuo), el mismo evento puede generarse decenas de veces en segundos. La deduplicación evita spam:

```kotlin
internal class DeduplicationFilter(
    private val windowMs: Long = 5_000,
    private val maxSameEventPerWindow: Int = 3
) : LogFilter {

    private data class EventKey(val tag: String, val messagePrefix: String, val level: LogLevel)
    private val recentEvents = ConcurrentHashMap<EventKey, AtomicInteger>()

    override fun passes(event: LogEvent): Boolean {
        if (event.level >= LogLevel.ERROR) return true  // Errores siempre pasan

        val key     = EventKey(event.tag, event.message.take(50), event.level)
        val counter = recentEvents.getOrPut(key) { AtomicInteger(0) }
        return counter.incrementAndGet() <= maxSameEventPerWindow
    }
}
```

### 16.3 Compresión de Payloads

Para conexiones de bajo ancho de banda (TV con WiFi lento, 3G), el transport comprime el batch con **gzip** antes de enviar:

```kotlin
internal class CompressedSupabaseTransport(
    private val delegate: SupabaseTransport
) : LogTransport {

    override suspend fun send(events: List<LogEvent>): TransportResult {
        val json    = JsonLogFormatter().formatBatch(events)
        val payload = if (json.length > 1024) gzip(json) else json.toByteArray()
        return delegate.sendRaw(payload, isCompressed = json.length > 1024)
    }

    private fun gzip(input: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(input.toByteArray(Charsets.UTF_8)) }
        return bos.toByteArray()
    }
}
```

---

## 17. Referencias y Estándares Consultados

| # | Referencia | Relevancia |
|---|---|---|
| [1] | Android Developers — Logging Best Practices | Base del sistema de logs en Android |
| [2] | Timber by Jake Wharton — GitHub | Patrón Tree para abstracción de destinos |
| [3] | GDPR Article 4 — Definition of Personal Data | Clasificación de datos capturados |
| [4] | LGPD (Lei 13.709/2018) — Brasil | Requisito para apps en mercado latinoamericano |
| [5] | OpenTelemetry Specification — Logs | Estándar de campos en eventos de log |
| [6] | Firebase Crashlytics Architecture | Crash handler + flush pre-muerte |
| [7] | JitPack.io Documentation | Publicación de librerías desde GitHub |
| [8] | Maven Central Publishing Guide | Publicación estándar en ecosistema JVM |
| [9] | LMAX Disruptor — Technical Paper | Ring buffer de alta velocidad (referencia conceptual) |
| [10] | Android WorkManager Documentation | Background processing en Android |
| [11] | Kotlin Coroutines — Channel API | Comunicación asíncrona productor/consumidor |
| [12] | RFC 7519 — JWT | Seguridad en la API key de transporte |
| [13] | Kotlin Multiplatform Documentation — kotlinlang.org | Arquitectura KMP, targets, expect/actual |
| [14] | SQLDelight 2.0 KMP Guide | Base de datos SQLite multiplataforma |
| [15] | Ktor Client — KMP HTTP | Transport HTTP multiplataforma en commonMain |
| [16] | Apple XCFramework Documentation | Distribución del SDK para iOS |
| [17] | Swift Package Manager Manifest Format | Consumo del SDK en proyectos iOS/Swift |
| [18] | Android Network Security Config | TLS configuration y certificate pinning |
| [19] | Android Debug Bridge (ADB) Reference | Captura de logs via adb logcat |
| [20] | Android ConnectivityManager API | Detección adaptativa de tipo de red |
| [21] | Android API Distribution Dashboard | Base para la decisión de minSdk 22 |
| [22] | gzip RFC 1952 | Compresión de payloads en transport |


---

## 18. Roadmap v2 — Features Futuras

> **Nota del autor:** Las siguientes features fueron identificadas durante la auditoría técnica de la v1 como mejoras de alto valor, pero requieren decisiones de producto antes de implementarse. El SDK actual está completo y production-ready sin ellas. Se documentan aquí para que el equipo las evalúe en el ciclo de planificación de v2.

### A1 — Sampling de eventos por nivel

**Qué es:** Enviar solo un porcentaje configurable de eventos DEBUG e INFO al backend, reduciendo el volumen de datos sin perder visibilidad de errores.

**Por qué importa:** En apps con millones de usuarios, loguear cada evento DEBUG/INFO genera costos de almacenamiento y red desproporcionados. Firebase Performance y Datadog implementan sampling nativo.

**Cómo funcionaría:**
```kotlin
AppLoggerConfig.Builder()
    .samplingRate(LogLevel.DEBUG, 0.1)   // Solo 10% de DEBUG llega al backend
    .samplingRate(LogLevel.INFO, 0.5)    // Solo 50% de INFO
    // ERROR, CRITICAL, METRIC: siempre 100%
    .build()
```

**Decisión de producto requerida:** ¿Qué porcentaje default? ¿El sampling es determinístico (mismo usuario siempre incluido) o aleatorio por evento?

---

### A3 — Clock skew correction (corrección de desfase de reloj)

**Qué es:** Detectar y corregir automáticamente cuando el reloj del dispositivo está desincronizado respecto al servidor, usando la diferencia entre el timestamp del servidor en la respuesta HTTP y el timestamp local.

**Por qué importa:** Dispositivos Android TV y dispositivos de bajo costo frecuentemente tienen el reloj desincronizado varios minutos o incluso horas. Esto hace que los eventos aparezcan en el orden incorrecto en Supabase, dificultando el debugging.

**Cómo funcionaría:**
```kotlin
// El transport detecta el skew en cada respuesta HTTP
val serverTime = response.headers["X-Server-Time"]?.toLong()
val skewMs = serverTime - currentTimeMillis()
// Si |skewMs| > 5000ms, ajusta todos los timestamps del batch
```

**Decisión de producto requerida:** ¿Cuál es el umbral de corrección aceptable? ¿Se corrige silenciosamente o se loguea el skew como métrica?

---

### A10 — traceId / spanId para distributed tracing

**Qué es:** Agregar campos `traceId` y `spanId` a cada `LogEvent` para correlacionar logs del cliente con trazas del servidor, siguiendo el estándar W3C Trace Context (RFC 7230).

**Por qué importa:** Cuando un error en la app corresponde a una llamada de red fallida, hoy no hay forma de correlacionar el log del cliente con el log del servidor. Con `traceId` compartido, el equipo puede hacer un join en Supabase entre `app_logs` y los logs del backend.

**Cómo funcionaría:**
```kotlin
// El desarrollador propaga el traceId desde la capa de red
logger.error("API", "Request failed", extra = mapOf(
    "trace_id" to currentTraceId,
    "span_id" to currentSpanId
))
// O automáticamente via interceptor gRPC/OkHttp
```

**Decisión de producto requerida:** ¿Se integra con OpenTelemetry SDK o se implementa un sistema propio? ¿El traceId se genera en el cliente o se propaga desde el servidor?

---

### D1 — PII scrubbing automático

**Qué es:** Un filtro que detecta y redacta automáticamente datos personales identificables (emails, teléfonos, nombres, tokens) en los campos `message` y `extra` antes de que el evento salga del dispositivo.

**Por qué importa:** GDPR y LGPD requieren que los datos personales no se transmitan sin consentimiento explícito. Hoy el SDK confía en que el desarrollador no loguea PII, pero en equipos grandes esto es difícil de garantizar.

**Cómo funcionaría:**
```kotlin
AppLoggerConfig.Builder()
    .piiScrubbing(PiiScrubbingMode.REDACT)  // Reemplaza con "[REDACTED]"
    .piiPatterns(listOf(
        PiiPattern.EMAIL,
        PiiPattern.PHONE,
        PiiPattern.CREDIT_CARD,
        PiiPattern.custom(Regex("token=[a-zA-Z0-9]+"))
    ))
    .build()
```

**Decisión de producto requerida:** ¿Qué patrones incluir por default? ¿El scrubbing es opt-in o opt-out? ¿Se loguea cuántos campos fueron redactados como métrica?

---

### D2 — Cifrado en reposo para SQLite offline

**Qué es:** Cifrar la base de datos SQLite local (usada por `OfflinePersistenceMode.ALL` y `CRITICAL_ONLY`) con AES-256, de modo que los eventos persistidos no sean legibles si el dispositivo es comprometido.

**Por qué importa:** En apps reguladas (fintech, salud, gobierno), los logs persistidos localmente pueden contener información sensible. SQLCipher es el estándar de la industria para SQLite cifrado en Android/iOS.

**Cómo funcionaría:**
```kotlin
AppLoggerConfig.Builder()
    .offlinePersistenceMode(OfflinePersistenceMode.ALL)
    .offlineEncryption(OfflineEncryptionMode.AES_256)  // Usa SQLCipher
    .build()
```

**Decisión de producto requerida:** ¿Dónde se almacena la clave de cifrado? ¿Android Keystore / iOS Secure Enclave? ¿Qué pasa si la clave se pierde (migración de dispositivo)?

---

### D3 — Rotación de API key en caliente

**Qué es:** Permitir actualizar la API key de Supabase sin reiniciar la app ni reinicializar el SDK, útil cuando una key es comprometida o expira.

**Por qué importa:** Hoy si la anon key de Supabase es comprometida, la única solución es publicar una nueva versión de la app. Con rotación en caliente, el equipo puede invalidar la key comprometida y distribuir la nueva via remote config (Firebase Remote Config, LaunchDarkly, etc.).

**Cómo funcionaría:**
```kotlin
// Desde cualquier punto de la app, sin reiniciar el SDK
AppLoggerSDK.updateApiKey(newKey)
// El SupabaseTransport usa la nueva key en el próximo batch
```

**Decisión de producto requerida:** ¿Cómo se distribuye la nueva key de forma segura? ¿Se integra con Firebase Remote Config o se deja como API genérica?

---

### E1 — Self-telemetry (el SDK reportándose a sí mismo)

**Qué es:** El SDK envía métricas sobre su propio funcionamiento a una tabla separada en Supabase: eventos enviados por minuto, tasa de fallos de transporte, tamaño promedio de batch, tiempo de flush, etc.

**Por qué importa:** Hoy `AppLoggerHealth.snapshot()` da visibilidad en tiempo real, pero no hay historial. Con self-telemetry, el equipo puede detectar degradaciones graduales (ej: la tasa de fallos de transporte subió de 0.1% a 5% en las últimas 24h) que no son visibles en un snapshot puntual.

**Cómo funcionaría:**
```kotlin
// Tabla separada: sdk_telemetry
// Campos: timestamp, events_sent, events_dropped, transport_failures,
//         avg_batch_size, avg_flush_ms, consecutive_failures, sdk_version
```

**Decisión de producto requerida:** ¿Con qué frecuencia se reporta (cada flush, cada minuto, cada hora)? ¿Se usa la misma tabla Supabase o una separada? ¿Cómo se evita que la self-telemetry genere más self-telemetry (loop infinito)?

---

### E3 — Health listeners / callbacks reactivos

**Qué es:** Una API de suscripción que notifica a la app cuando el estado de salud del SDK cambia: transporte se cae, buffer llega al 80% de capacidad, DLQ supera N eventos, etc.

**Por qué importa:** Hoy `AppLoggerHealth.snapshot()` es pull-based (la app pregunta). Para mostrar un banner de "modo offline" o alertar al usuario, la app necesita hacer polling. Con listeners, la app reacciona en tiempo real sin polling.

**Cómo funcionaría:**
```kotlin
AppLoggerSDK.addHealthListener { status ->
    when {
        !status.transportAvailable -> showOfflineBanner()
        status.bufferUtilizationPercentage > 80f -> showHighLoadWarning()
        status.consecutiveFailures > 3 -> alertOpsTeam()
    }
}
```

**Decisión de producto requerida:** ¿Los listeners se ejecutan en el hilo principal o en un dispatcher configurable? ¿Hay un debounce para evitar notificaciones en ráfaga? ¿Se soportan múltiples listeners o solo uno?


---

## 18. Auditoría Forense del CLI — AppLoggers CLI 0.2.0

**Fecha:** 2026-03-23  
**Scope:** `cli/internal/cli/` — todos los archivos Go  
**Metodología:** Revisión línea a línea como ingeniero senior de telemetría corporativa

---

### 18.1 Resumen Ejecutivo

El CLI 0.1.x era funcional pero incompleto para uso corporativo real. Los gaps principales eran:

1. **Inconsistencia con el SDK 0.2.0** — la columna `environment` no existía en ningún SELECT ni filtro.
2. **Sin SSE** — imposible conectar un frontend en tiempo real.
3. **Sin paginación real** — solo `--limit`, sin `--offset`.
4. **Filtros de severidad binarios** — `--severity error` no capturaba `CRITICAL`.
5. **HTTP errors genéricos** — 401, 403, 404, 429 todos producían el mismo mensaje.
6. **Sin retry** — un 429 o 503 fallaba inmediatamente.
7. **Flags duplicados** — los 14 flags de telemetría estaban definidos dos veces.
8. **Sin estadísticas** — no había forma de obtener error rate o top tags sin parsear JSON manualmente.

---

### 18.2 Gaps Identificados y Estado

| ID | Descripción | Severidad | Estado |
|---|---|:---:|:---:|
| G1 | `environment` no incluido en SELECT de logs | Alta | ✅ Resuelto |
| G2 | `environment` no incluido en SELECT de métricas | Alta | ✅ Resuelto |
| G3 | Flag `--environment` inexistente | Alta | ✅ Resuelto |
| G4 | `--aggregate environment` inexistente | Alta | ✅ Resuelto |
| G5 | `--sdk-version` filter inexistente | Media | ✅ Resuelto |
| G6 | `--min-severity` (rango) vs `--severity` (exacto) | Alta | ✅ Resuelto |
| G7 | CLI seleccionaba `metric_tags` pero SDK escribe `tags` — revertido a `tags` | Alta | ✅ Resuelto |
| G8 | `--extra-key/--extra-value` filter inexistente | Media | ✅ Resuelto |
| G9 | Sin paginación real (`--offset`) | Media | ✅ Resuelto |
| G10 | Sin `--order` flag (hardcodeado `desc`) | Baja | ✅ Resuelto |
| G11 | Sin `--output-columns` | Baja | Pospuesto (v3) |
| G12 | SSE completamente ausente | Alta | ✅ Resuelto (`telemetry stream`) |
| G13 | Sin `telemetry stats` | Media | ✅ Resuelto |
| G14 | `health` no verificaba conectividad real | Media | ✅ Resuelto (`--deep`) |
| G15 | Sin `telemetry tail` (follow mode) | Media | ✅ Resuelto |
| G16 | HTTP errors no diferenciados | Alta | ✅ Resuelto |
| G17 | Sin retry en 429/503 | Alta | ✅ Resuelto |
| G18 | `--aggregate` solo soportaba `hour` | Media | ✅ Resuelto (`day`, `week`) |
| G19 | Flags duplicados entre `query` y `agent-response` | Baja | ✅ Resuelto (`addTelemetryFlags`) |
| G20 | `throwable_info` no incluido en SELECT | Media | ✅ Resuelto (`--throwable`) |

---

### 18.3 Arquitectura del CLI 0.2.0

```
cli/internal/cli/
├── telemetry.go              ← Comandos: query, agent-response + addTelemetryFlags()
├── telemetry_client.go       ← HTTP client: SELECT completo, retry, HTTP errors diferenciados
├── telemetry_aggregate.go    ← Agregaciones: hour, day, week, severity, tag, session, name, environment
├── telemetry_agent_response.go ← TOON envelope para agentes
├── telemetry_stream.go       ← stream (SSE), tail (follow), stats
├── health.go                 ← health + --deep probe
├── agent.go                  ← agent schema (contract_version 2.0.0)
├── capabilities.go           ← capabilities (11 entradas)
└── ...
```

---

### 18.4 Diseño del SSE Stream

El comando `telemetry stream` implementa un patrón de polling con cursor:

```
┌─────────────────────────────────────────────────────────┐
│  apploggers telemetry stream                            │
│                                                         │
│  ticker (interval=5s)                                   │
│       │                                                 │
│       ▼                                                 │
│  queryTelemetry(from=lastSeenAt, order=asc)             │
│       │                                                 │
│       ├── count > 0 → emit SSE event:telemetry          │
│       │              advance cursor (lastSeenAt + 1ms)  │
│       └── count = 0 → emit SSE event:heartbeat          │
│                                                         │
│  Output: text/event-stream → stdout                     │
│  Consumer: HTTP proxy → browser EventSource             │
└─────────────────────────────────────────────────────────┘
```

El cursor `lastSeenAt` avanza 1ms después del último `created_at` visto para evitar re-emitir el último evento en el siguiente poll. El stream no crashea ante errores de Supabase — emite un `event: error` y continúa.

---

### 18.5 Retry Strategy

```
Intento 1 → inmediato
Intento 2 → espera 2s (solo si 429 o 503)
Intento 3 → espera 6s (solo si 429 o 503)
Fallo definitivo → error accionable al usuario
```

Los errores 401, 403, 404 no se reintentan — son errores de configuración que el usuario debe resolver.

---

### 18.6 Propuestas para Salir del Alpha (CLI v3)

| ID | Propuesta | Impacto |
|---|---|:---:|
| P1 | `--output-columns` para selección de columnas | Medio |
| P2 | `telemetry export --format csv` para exportación masiva | Alto |
| P3 | `telemetry watch --alert-on error_rate>20` — alertas en tiempo real | Alto |
| P4 | Supabase Realtime (WebSocket) en lugar de polling para SSE | Alto |
| P5 | `--aggregate minute` para granularidad sub-hora | Medio |
| P6 | Cache local de resultados (TTL configurable) para reducir requests | Medio |
| P7 | `telemetry diff --from A --to B` — comparar dos ventanas de tiempo | Alto |
| P8 | Plugin system para transportes alternativos (BigQuery, Datadog) | Alto |

---

### 18.7 Consistencia SDK ↔ CLI

| Campo SDK | Columna DB | CLI SELECT | CLI Filter | CLI Aggregate |
|---|---|:---:|:---:|:---:|
| `environment` | `environment` | ✅ | ✅ `--environment` | ✅ `--aggregate environment` |
| `level` | `level` | ✅ | ✅ `--severity` / `--min-severity` | ✅ `--aggregate severity` |
| `tag` | `tag` | ✅ | ✅ `--tag` | ✅ `--aggregate tag` |
| `sessionId` | `session_id` | ✅ | ✅ `--session-id` | ✅ `--aggregate session` |
| `deviceId` | `device_id` | ✅ | ✅ `--device-id` | — |
| `userId` | `user_id` | ✅ | ✅ `--user-id` | — |
| `sdkVersion` | `sdk_version` | ✅ | ✅ `--sdk-version` | — |
| `extra` | `extra` (JSONB) | ✅ | ✅ `--package`, `--error-code`, `--extra-key/value` | — |
| `anomalyType` | `anomaly_type` (top-level, nullable hasta SDK 0.3.0) | ✅ | ✅ `--anomaly-type` | — |
| `throwableInfo` | `throwable_type`, `throwable_msg`, `stack_trace` | ✅ (con `--throwable`) | — | — |
| `metric.name` | `name` | ✅ | ✅ `--name` | ✅ `--aggregate name` |
| `metric.tags` | `tags` (JSONB — `SupabaseMetricEntry.tags`) | ✅ | — | — |

---

## 19. Auditoría Forense CLI — Ronda 2 (2026-03-23)

### 19.1 Contexto

Auditoría forense completa del CLI 0.2.0 post-implementación. Objetivo: detectar gaps de funcionalidad, calidad de código, integración y consistencia antes de declarar el CLI production-ready.

### 19.2 Gaps Identificados y Resueltos

| ID | Categoría | Descripción | Severidad | Estado |
|---|---|---|:---:|:---:|
| F1 | Funcionalidad | `stats` no usaba `addTelemetryFlags()` — sin filtros estándar | CRÍTICO | ✅ |
| F3 | Funcionalidad | Cursor SSE usaba `RFC3339Nano` — frágil con timestamps de Supabase | MEDIO | ✅ |
| F4 | Funcionalidad | `tail` no soportaba `--output json` | MEDIO | ✅ |
| F6 | Funcionalidad | Leak de contexto en `health --deep` — `cancel()` no se llamaba | BAJO | ✅ |
| F7 | Funcionalidad | `severityRank` incluía `"metric"` — `--min-severity error` incluía METRIC incorrectamente | MEDIO | ✅ |
| Q1 | Calidad | `http.Client` nuevo en cada request — sin reutilización de TCP pool | MEDIO | ✅ |
| Q2 | Calidad | `marshalJSON` función muerta en `root.go` | BAJO | ✅ |
| Q3 | Calidad | Lógica de cursor duplicada en `stream` y `tail` | BAJO | ✅ |
| Q4 | Calidad | SSE helpers usaban interfaz inline en vez de `io.Writer` | BAJO | ✅ |
| Q7 | Calidad | `downloadURL` sin límite de body — riesgo de OOM en binarios maliciosos | BAJO | ✅ |
| I2 | Integración | `stream` y `tail` no validaban `--output` | MEDIO | ✅ |
| I3 | Integración | `stats` sin tests de `--output agent` ni de filtros | BAJO | ✅ |
| I5/I6 | Integración | `upgrade` ausente en `agent schema` y `capabilities` | BAJO | ✅ |

### 19.3 Parches Aplicados

**F1 — `stats` con todos los filtros:**
`newTelemetryStatsCommand()` refactorizado para usar `addTelemetryFlags()`. Antes tenía solo `--source`, `--from`, `--to`, `--environment`. Ahora tiene los mismos 20+ flags que `query`.

**F3 + Q3 — Cursor unificado:**
`advanceCursor(rows, current)` y `latestTimestamp(rows)` extraídos como helpers compartidos. Usan `time.RFC3339` (no Nano). Tanto `stream` como `tail` los consumen.

**F4 — `tail --output json`:**
Cuando `--output json`, `tail` emite el `telemetryQueryResponse` completo como JSON por cada poll. Cuando `--output text` (default), imprime filas formateadas con columna de entorno.

**F6 — `health --deep` sin leak:**
`defer cancel()` agregado inmediatamente después de `context.WithTimeout()`.

**F7 — `severityRank` limpio:**
`"metric"` eliminado del mapa. `severityAtOrAbove("error")` retorna `["ERROR", "CRITICAL"]` — correcto.

**Q1 — HTTP client centralizado:**
`supabaseHTTPClient(cfg)` crea el cliente una vez por invocación de comando. `doQuery()` lo recibe como parámetro. TCP connection pool reutilizado en todos los reintentos.

**Q2 — Código muerto eliminado:**
`marshalJSON` removida de `root.go`. No tenía callers.

**Q4 — `io.Writer` en SSE helpers:**
`writeSSEEvent`, `writeSSEHeartbeat`, `writeSSEError`, `printTailRow` reciben `io.Writer` — testables sin capturar stdout.

**Q7 — `downloadURL` con límite:**
`io.LimitReader(resp.Body, 32*1024*1024)` — máximo 32MB. Protege contra binarios maliciosos o respuestas inesperadamente grandes.

**I2 — Validación de `--output`:**
`stream` llama `validateOutputFormat()` al inicio. `tail` también. Errores de typo en `--output` se detectan antes de conectar a Supabase.

**I3 — Tests nuevos:**
6 tests agregados a `contract_test.go`:
- `TestStatsHasAllTelemetryFlags` — verifica que `stats` tiene `--environment`, `--min-severity`, `--session-id`
- `TestTailSupportsOutputJSON` — verifica que `tail --output json` no retorna error de uso
- `TestStreamValidatesOutput` — verifica que `stream --output invalid` retorna exit code 2
- `TestSeverityRankExcludesMetric` — verifica que `severityAtOrAbove("error")` no incluye METRIC
- `TestAdvanceCursorRFC3339` — verifica que `advanceCursor` produce timestamps RFC3339 válidos
- `TestCapabilitiesIncludesUpgrade` — verifica que `capabilities --output json` incluye `upgrade`

**I5/I6 — `upgrade` en schema y capabilities:**
`agent schema` incluye `upgrade` en `Commands[]`. `capabilities` incluye `upgrade` en `Capabilities[]`. `plugin-metadata.yaml` actualizado.

### 19.4 Estado Post-Ronda 2

- `go build ./...` — limpio
- `go vet ./...` — limpio
- `go test ./...` — 41 tests, todos pasan
- Consistencia SDK ↔ DB ↔ CLI — verificada (ver sección 18.7)

---

## 20. Auditoría Forense — Ronda 3: Migraciones ↔ SDK ↔ CLI + Calidad CLI (2026-03-23)

### 20.1 Contexto

Auditoría forense completa de consistencia entre las 8 migraciones SQL, el SDK (`SupabaseTransport.kt`) y el CLI. Objetivo: detectar cualquier desalineación entre lo que el SDK escribe, lo que la DB espera y lo que el CLI lee. Adicionalmente, auditoría de calidad del CLI post-ronda 2.

### 20.2 Matriz de Consistencia SDK ↔ DB ↔ CLI (verificada)

#### app_logs

| Columna DB | SDK escribe | CLI lee | Estado |
|---|---|---|---|
| `id` | auto (gen_random_uuid) | — | ✅ |
| `created_at` | auto (NOW()) | cursor, `--from`, `--to` | ✅ |
| `level` | `level.name` (DEBUG/INFO/WARN/ERROR/CRITICAL) | `--severity`, `--min-severity` | ✅ |
| `tag` | `tag` | `--tag` | ✅ |
| `message` | `message` | `--contains` | ✅ |
| `throwable_type` | `throwableInfo?.type` | `--throwable` | ✅ |
| `throwable_msg` | `throwableInfo?.message` | `--throwable` | ✅ |
| `stack_trace` | `throwableInfo?.stackTrace` | `--throwable` | ✅ |
| `device_info` | `Map<String,String>` JSONB | — | ✅ |
| `api_level` | `deviceInfo.apiLevel` | — | ✅ |
| `sdk_version` | `sdkVersion` | `--sdk-version` | ✅ |
| `session_id` | `sessionId` | `--session-id` | ✅ |
| `device_id` | `deviceId` | `--device-id` | ✅ |
| `user_id` | `userId` (nullable) | `--user-id` | ✅ |
| `extra` | `JsonObject` JSONB | `--extra-key/--extra-value` | ✅ |
| `environment` | `environment` (migración 007) | `--environment` | ✅ |
| `anomaly_type` | **NO escribe** (deuda SDK futuro) | `--anomaly-type` (nullable) | ✅ documentado |

#### app_metrics

| Columna DB | SDK escribe | CLI lee | Estado |
|---|---|---|---|
| `id` | auto | — | ✅ |
| `created_at` | auto | cursor, `--from`, `--to` | ✅ |
| `name` | `metricName ?: tag` | `--name` | ✅ |
| `value` | `metricValue ?: 0.0` | — | ✅ |
| `unit` | `metricUnit ?: "count"` | — | ✅ |
| `tags` | `metricTags ?: emptyMap()` | SELECT `tags` | ✅ |
| `device_id` | `deviceId` | `--device-id` | ✅ |
| `session_id` | `sessionId` | `--session-id` | ✅ |
| `sdk_version` | `sdkVersion` | `--sdk-version` | ✅ |
| `environment` | `environment` (migración 008) | `--environment` | ✅ |

### 20.3 Gaps Identificados

| ID | Área | Descripción | Severidad | Estado |
|---|---|---|---|---|
| M1 | Migración 001 | Comentario en `extra` listaba `anomaly_type` como campo JSONB — ya es columna top-level desde migración 007 | BAJO | ✅ Corregido |
| M2 | Migración (nueva) | Sin índice en `app_logs.sdk_version` — CLI filtra con `--sdk-version` sin índice | MEDIO | ✅ Migración 009 |
| M3 | Migración (nueva) | Sin índice en `app_metrics.sdk_version` — mismo problema | MEDIO | ✅ Migración 009 |
| M4 | Migración (nueva) | Sin índice en `app_logs.user_id` — CLI filtra con `--user-id` sin índice | BAJO | ✅ Migración 009 |
| M5 | Migración (nueva) | Sin índice en `app_metrics.device_id` — CLI filtra con `--device-id` sin índice | BAJO | ✅ Migración 009 |
| C2 | CLI calidad | `telemetry stats` sin `--from` agrega todos los datos históricos sin advertencia | MEDIO | ✅ Warning en stderr |
| C6 | CLI seguridad | `writeExampleConfigIfAbsent` creaba `cli.json` con permisos `0644` — contiene `service_role key` | MEDIO | ✅ Corregido a `0600` |

### 20.4 Deuda Planificada (no bugs)

| Item | Descripción | Resolución planificada |
|---|---|---|
| `anomaly_type` en SDK | El SDK no escribe esta columna. La migración 007 la creó nullable. El CLI puede filtrar pero todos los valores serán NULL. | SDK futuro |
| `telemetry stats --limit` semántico | Con `--limit` bajo, las estadísticas son sobre una muestra parcial, no sobre el universo completo. Documentado en BEST_PRACTICES. | Aceptado — comportamiento documentado |

### 20.5 Orden de Ejecución de Migraciones

Las migraciones son idempotentes (`IF NOT EXISTS`, `IF EXISTS`, `CREATE OR REPLACE`). Ejecutar en orden:

```
001_create_app_logs.sql
002_create_app_metrics.sql
003_create_indexes.sql
004_rls_policies.sql
005_retention_policy.sql
006_harden_authenticated_read_policies.sql
007_add_environment_anomaly_type.sql
008_add_metrics_environment.sql
009_add_missing_indexes.sql
```

### 20.6 Estado Post-Ronda 3

- `go build ./...` — limpio
- `go vet ./...` — limpio
- `go test ./...` — 41 tests, todos pasan
- Consistencia SDK ↔ DB ↔ CLI — verificada (tabla completa en sección 20.2)
- Migraciones 001–009 — idempotentes, ejecutables en orden
