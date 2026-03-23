# AppLogger — Arquitectura del Paquete

**Paradigma:** Trait-based design · Clean Architecture · SOLID  
**Lenguaje:** Kotlin Multiplatform 2.0 — Android (Mobile + TV) · iOS · JVM

---

## Índice

1. [Filosofía de Diseño](#1-filosofía-de-diseño)
2. [Estructura de Módulos — KMP](#2-estructura-de-módulos--kmp)
3. [Mapa de Traits (Interfaces)](#3-mapa-de-traits-interfaces)
4. [Implementación de Cada Trait](#4-implementación-de-cada-trait)
5. [Ensamblaje — Entry Points por Plataforma](#5-ensamblaje--entry-points-por-plataforma)
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
| **I** — Segregación de Interfaces | `AppLogger` y `AppLoggerHealthProvider` son interfaces separadas. El caller solo conoce lo que necesita |
| **D** — Inversión de Dependencias | `AppLoggerImpl` depende de `LogTransport` (abstracción), no de `SupabaseTransport` (concreción) |

---

## 2. Estructura de Módulos — KMP

```
appLoggers/
├── logger-core/                         ← Módulo KMP principal
│   └── src/
│       ├── commonMain/kotlin/
│       │   └── com/applogger/core/
│       │       ├── AppLogger.kt             (interfaz pública)
│       │       ├── AppLoggerExtensions.kt   (logD/I/W/E/C, withTag, timed, logCatching)
│       │       ├── AppLoggerConfig.kt       (Builder + validate() + enums)
│       │       ├── AppLoggerHealth.kt       (HealthStatus, AppLoggerHealthProvider, AppLoggerHealth)
│       │       ├── LogTransport.kt          (interfaz + TransportResult)
│       │       ├── model/
│       │       │   ├── LogEvent.kt
│       │       │   ├── LogLevel.kt
│       │       │   ├── DeviceInfo.kt
│       │       │   └── ThrowableInfo.kt
│       │       └── internal/
│       │           ├── AppLoggerImpl.kt
│       │           ├── BatchProcessor.kt    (Mutex anti-solapamiento, retryAfterMs)
│       │           ├── InMemoryBuffer.kt
│       │           ├── RateLimitFilter.kt
│       │           ├── SessionManager.kt
│       │           └── JsonLogFormatter.kt
│       │
│       ├── androidMain/kotlin/
│       │   └── com/applogger/core/
│       │       ├── AppLoggerSDK.kt               (entry point Android — singleton)
│       │       ├── AppLoggerLifecycleObserver.kt
│       │       ├── AndroidDeviceInfoProvider.kt
│       │       ├── AndroidCrashHandler.kt
│       │       ├── PlatformDetector.kt
│       │       └── internal/
│       │           └── SqliteOfflineStorage.kt
│       │
│       ├── iosMain/kotlin/
│       │   └── com/applogger/core/
│       │       ├── AppLoggerIos.kt               (entry point iOS — shared singleton)
│       │       ├── IosDeviceInfoProvider.kt
│       │       └── IosCrashHandler.kt
│       │
│       └── jvmMain/kotlin/
│           └── com/applogger/core/
│               └── Platform.jvm.kt
│
├── logger-transport-supabase/           ← Módulo de transporte (KMP, intercambiable)
│   └── src/commonMain/kotlin/
│       └── com/applogger/transport/supabase/
│           └── SupabaseTransport.kt
│
└── logger-test/                         ← Utilidades de testing para consumidores del SDK
    └── src/commonMain/kotlin/
        └── com/applogger/test/
            ├── NoOpTestLogger.kt        (logger que descarta todo — alias público para tests)
            ├── InMemoryLogger.kt        (logger que guarda en memoria — para assertions)
            └── FakeTransport.kt         (transport mock con control total)
```

### 2.1 Configuración `build.gradle.kts` del módulo core

```kotlin
kotlin {
    androidTarget { compilations.all { kotlinOptions { jvmTarget = "11" } } }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework { baseName = "AppLogger"; isStatic = true }
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

---

## 3. Mapa de Traits (Interfaces)

```
AppLoggerSDK (Android) / AppLoggerIos.shared (iOS)
       │
       ▼
AppLoggerImpl (implements AppLogger)
       │
       ├── DeviceInfoProvider ──▶ AndroidDeviceInfoProvider / IosDeviceInfoProvider
       ├── LogFilter          ──▶ ChainedLogFilter → RateLimitFilter
       ├── InMemoryBuffer     ──▶ buffer FIFO con política de overflow configurable
       ├── LogFormatter       ──▶ JsonLogFormatter
       ├── LogTransport       ──▶ SupabaseTransport / NoOpTransport / Custom
       ├── CrashHandler       ──▶ AndroidCrashHandler / IosCrashHandler
       └── OfflineStorage     ──▶ SqliteOfflineStorage / NoOpOfflineStorage

AppLoggerHealth (object) implements AppLoggerHealthProvider
       └── snapshot() ──▶ lee BatchProcessor + InMemoryBuffer + LogTransport
```

---

## 4. Implementación de Cada Trait

### 4.1 `AppLogger` — Contrato Público

```kotlin
interface AppLogger {
    fun debug(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun info(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun warn(tag: String, message: String, throwable: Throwable? = null, anomalyType: String? = null, extra: Map<String, Any>? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun critical(tag: String, message: String, throwable: Throwable? = null, extra: Map<String, Any>? = null)
    fun metric(name: String, value: Double, unit: String, tags: Map<String, String>? = null)
    fun flush()

    // Contexto global — se adjunta a todos los eventos subsiguientes
    fun addGlobalExtra(key: String, value: String)
    fun removeGlobalExtra(key: String)
    fun clearGlobalExtra()
}
```

Garantías del contrato:
- Ninguna llamada bloquea el hilo del llamador.
- Ninguna llamada lanza excepciones al llamador.
- `debug()` es no-op en producción (`isDebugMode = false`).
- `addGlobalExtra()` adjunta el par a **todos** los eventos posteriores hasta que se llame `removeGlobalExtra()` o `clearGlobalExtra()`.

### 4.1.1 `AppLoggerExtensions` — Extension Functions (commonMain)

Disponibles en todos los targets (Android, iOS, JVM) sin dependencias adicionales.

```kotlin
// ── Shorthands sobre AppLogger (tag explícito) ──────────────────────────────
fun AppLogger.logD(tag, message, throwable?, extra?)
fun AppLogger.logI(tag, message, throwable?, extra?)
fun AppLogger.logW(tag, message, throwable?, anomalyType?, extra?)
fun AppLogger.logE(tag, message, throwable?, extra?)
fun AppLogger.logC(tag, message, throwable?, extra?)
fun AppLogger.logM(name, value, unit = "count", tags?)   // métrica shorthand

// ── Extensión sobre Any (tag inferido del nombre de clase) ──────────────────
fun Any.logTag(): String                                  // → simpleName ?: "Anonymous"
fun Any.logD(logger, message, throwable?, extra?)
fun Any.logI(logger, message, throwable?, extra?)
fun Any.logW(logger, message, throwable?, anomalyType?, extra?)
fun Any.logE(logger, message, throwable?, extra?)
fun Any.logC(logger, message, throwable?, extra?)
fun Any.logM(logger, name, value, unit = "count", tags?) // añade "source" automáticamente

// ── Companion object helper ──────────────────────────────────────────────────
inline fun <reified T : Any> loggerTag(): String          // → T::class.simpleName

// ── TaggedLogger — tag fijo para toda la clase ───────────────────────────────
fun AppLogger.withTag(tag: String): TaggedLogger
fun AppLogger.withTag(receiver: Any): TaggedLogger        // infiere tag del receiver

// ── Timed metric block ────────────────────────────────────────────────────────
inline fun <T> AppLogger.timed(name, unit = "ms", tags?, block: () -> T): T
inline fun <T> Any.timed(logger, name, unit = "ms", tags?, block: () -> T): T

// ── Safe execution con auto-logging ──────────────────────────────────────────
inline fun <T> AppLogger.logCatching(tag, context = "operation", extra?, block: () -> T): T?
inline fun <T> Any.logCatching(logger, context = "operation", extra?, block: () -> T): T?
```

**Ejemplos de uso:**

```kotlin
// Tag inferido automáticamente
class PlayerController(private val logger: AppLogger) {
    fun onError(t: Throwable) = this.logE(logger, "Playback failed", throwable = t)
    fun onStart() = logger.logI("PLAYER", "Playback started")
}

// TaggedLogger — elimina repetición del tag
class PaymentRepository(logger: AppLogger) {
    private val log = logger.withTag("PaymentRepository")
    fun charge() {
        log.i("Charging card")
        log.e("Charge failed", ex)
        log.metric("charge_latency", 120.0, "ms")
    }
}

// timed — mide latencia automáticamente
val user = logger.timed("db_query_user", "ms", mapOf("table" to "users")) {
    userDao.findById(id)
}

// logCatching — captura y loguea excepciones sin try/catch
val result = logger.logCatching("NetworkClient", "fetch user") {
    api.getUser(id)
}

// loggerTag<T>() — para companion objects
class NetworkRepository(private val logger: AppLogger) {
    companion object { val TAG = loggerTag<NetworkRepository>() }
    fun fetch() = logger.logI(TAG, "Fetching data")
}
```

### 4.2 `LogTransport` — Contrato de Transporte

```kotlin
interface LogTransport {
    suspend fun send(events: List<LogEvent>): TransportResult
    fun isAvailable(): Boolean
}

sealed class TransportResult {
    data object Success : TransportResult()
    data class Failure(
        val reason: String,
        val retryable: Boolean,
        val retryAfterMs: Long? = null,   // respeta Retry-After del servidor (ej. HTTP 429)
        val cause: Throwable? = null
    ) : TransportResult()
}
```

Contrato:
- `send()` es suspend — ejecutar en coroutine context adecuado.
- `send()` **nunca** lanza excepciones; capturar internamente y retornar `Failure`.
- `isAvailable()` debe ser rápido (sin I/O). Usa estado de red en caché.
- Cuando `Failure.retryAfterMs` está presente, `BatchProcessor` lo respeta en lugar del backoff exponencial por defecto.

### 4.3 `LogBuffer` — Almacenamiento Temporal

`InMemoryBuffer` implementa el buffer FIFO con política de overflow configurable:

```kotlin
// Política de overflow (configurable en AppLoggerConfig)
enum class BufferOverflowPolicy { DISCARD_OLDEST, DISCARD_NEWEST, PRIORITY_AWARE }

// Estrategia de tamaño del buffer
enum class BufferSizeStrategy { FIXED, ADAPTIVE_TO_RAM, ADAPTIVE_TO_LOG_RATE }
```

- `DISCARD_OLDEST` (default): cuando el buffer está lleno, descarta el evento más antiguo.
- `DISCARD_NEWEST`: descarta el evento entrante si el buffer está lleno.
- `PRIORITY_AWARE`: preserva eventos de mayor severidad ante overflow.
- `ADAPTIVE_TO_RAM`: calcula la capacidad del buffer en función de la RAM disponible del dispositivo.

### 4.4 `LogFilter` — Filtrado y Rate Limiting

```kotlin
interface LogFilter {
    fun passes(event: LogEvent): Boolean
}

class ChainedLogFilter(private val filters: List<LogFilter>) : LogFilter {
    override fun passes(event: LogEvent): Boolean = filters.all { it.passes(event) }
}
```

`RateLimitFilter` limita eventos por tag por minuto. Los eventos `ERROR` y `CRITICAL` siempre pasan, independientemente del rate limit.

### 4.5 `AppLoggerHealthProvider` — Interfaz de Health

```kotlin
interface AppLoggerHealthProvider {
    fun snapshot(): HealthStatus
}
```

Inyectable en tests para simular estados de salud sin depender del SDK real:

```kotlin
class FakeHealthProvider(private val status: HealthStatus) : AppLoggerHealthProvider {
    override fun snapshot() = status
}
```

---

## 5. Ensamblaje — Entry Points por Plataforma

### 5.1 Android — `AppLoggerSDK`

Singleton thread-safe con inicialización idempotente. Delega todas las llamadas a `AppLoggerImpl` una vez que `initialize()` tiene éxito; antes de eso, todas las llamadas son no-ops.

```kotlin
// Application.onCreate()
val transport = SupabaseTransport(
    endpoint = BuildConfig.LOGGER_URL,
    apiKey = BuildConfig.LOGGER_KEY,
    networkAvailabilityProvider = androidNetworkAvailabilityProvider(this)
)

AppLoggerSDK.initialize(
    context = this,
    config = AppLoggerConfig.Builder()
        .endpoint(BuildConfig.LOGGER_URL)
        .apiKey(BuildConfig.LOGGER_KEY)
        .environment("production")          // filtra QA vs prod en Supabase
        .debugMode(BuildConfig.LOGGER_DEBUG)
        .minLevel(LogMinLevel.INFO)          // descarta DEBUG en producción
        .batchSize(20)
        .flushIntervalSeconds(30)
        .build(),
    transport = transport
)
```

Métodos adicionales de `AppLoggerSDK` (no en la interfaz `AppLogger`):

```kotlin
AppLoggerSDK.setAnonymousUserId(userId)   // adjunta user_id a todos los eventos
AppLoggerSDK.clearAnonymousUserId()
AppLoggerSDK.setDeviceId(deviceId)        // sobreescribe el device_id calculado por el SDK
AppLoggerSDK.clearDeviceId()
AppLoggerSDK.newSession()                 // fuerza inicio de nueva sesión (login/logout)
```

### 5.2 iOS — `AppLoggerIos.shared`

Entry point KMP para iOS. Distinto del singleton Android.

```kotlin
// iosMain — Kotlin
AppLoggerIos.shared.initialize(config = config, transport = transport)

// Métodos adicionales (equivalentes a Android):
AppLoggerIos.shared.setAnonymousUserId(userId)
AppLoggerIos.shared.clearAnonymousUserId()
AppLoggerIos.shared.setDeviceId(deviceId)
AppLoggerIos.shared.clearDeviceId()
AppLoggerIos.shared.newSession()
```

### 5.3 Debug mode via variable de entorno

**Android** — leer `APPLOGGER_DEBUG` del manifest meta-data sin cambiar código:

```xml
<!-- AndroidManifest.xml -->
<meta-data android:name="APPLOGGER_DEBUG" android:value="${APPLOGGER_DEBUG}" />
```

```groovy
// build.gradle
manifestPlaceholders = [APPLOGGER_DEBUG: System.getenv("APPLOGGER_DEBUG") ?: "false"]
```

**iOS** — leer `APPLOGGER_DEBUG` de `Info.plist`:

```xml
<!-- Info.plist -->
<key>APPLOGGER_DEBUG</key>
<string>true</string>
```

En ambas plataformas, si el flag está presente y es `"true"`, el SDK activa `isDebugMode = true` automáticamente, sin necesidad de cambiar código.

### 5.4 `NoOpLogger` — Seguridad antes de la inicialización

`NoOpLogger` es la implementación por defecto del SDK antes de `initialize()`. Garantiza que llamadas tempranas (en `ContentProvider`, etc.) no crasheen. Es `internal` — no se usa directamente en tests de consumidores; para eso existe `NoOpTestLogger` en el módulo `logger-test`.

---

## 6. Pipeline del Evento — Flujo Completo

```
Llamador (cualquier hilo)
    │
    ▼
AppLoggerSDK / AppLoggerIos.shared
    │  delega a
    ▼
AppLoggerImpl.process()
    ├── [1] Guard minLevel: descarta eventos por debajo del nivel mínimo configurado
    ├── [2] Guard debug: descarta DEBUG si isDebugMode=false
    ├── [3] Consola: imprime a logcat/NSLog si isDebugMode=true && consoleOutput=true
    ├── [4] Construye LogEvent (solo en memoria — operación ligera)
    │         ├── id (UUID v4)
    │         ├── timestamp (epoch millis)
    │         ├── level, tag, message
    │         ├── throwableInfo (stack trace truncado a maxStackTraceLines)
    │         ├── deviceInfo (snapshot del dispositivo)
    │         ├── deviceId, sessionId, userId
    │         ├── environment (de AppLoggerConfig)
    │         ├── extra (Map<String, JsonElement> — tipos nativos preservados)
    │         └── metricName/Value/Unit/Tags (solo si level == METRIC)
    ├── [5] Mezcla globalExtra: los pares de addGlobalExtra() se fusionan en extra
    │         (per-call extra tiene precedencia sobre global en colisión de clave)
    ├── [6] LogFilter.passes(): RateLimitFilter (ERROR/CRITICAL siempre pasan)
    └── [7] processor.enqueue() — Channel.trySend() — NO BLOQUEA
            │
            ▼
        BatchProcessor (Dispatchers.IO + SupervisorJob)
            ├── InMemoryBuffer.push(event)
            ├── Si level >= ERROR → sendBatch() inmediato
            ├── Si buffer.size() >= batchSize → sendBatch()
            └── Flush periódico cada flushIntervalSeconds
                    │
                    ▼
                sendBatch()
                    ├── Mutex (sendMutex) — previene solapamiento de envíos concurrentes
                    ├── Si !transport.isAvailable() → OfflineStorage.persist(batch)
                    ├── transport.send(batch)
                    │       ├── Success → lastSuccessfulFlushTimestamp = now
                    │       │            consecutiveFailures = 0
                    │       └── Failure(retryable=true)
                    │               ├── Si retryAfterMs presente → espera ese tiempo
                    │               ├── Si no → backoff exponencial con jitter
                    │               ├── consecutiveFailures++
                    │               └── Tras maxRetries → DeadLetterQueue
                    └── Failure(retryable=false) → DeadLetterQueue directo
```

### 6.1 `LogEvent` — Modelo de Datos

```kotlin
@Serializable
data class LogEvent(
    val id: String,                          // UUID v4
    val timestamp: Long,                     // epoch millis
    val level: LogLevel,                     // DEBUG/INFO/WARN/ERROR/CRITICAL/METRIC
    val tag: String,                         // máx 100 chars
    val message: String,                     // máx 10 000 chars
    val throwableInfo: ThrowableInfo? = null,
    val deviceInfo: DeviceInfo,
    val deviceId: String = "",
    val sessionId: String,
    val userId: String? = null,
    val environment: String = "production",  // de AppLoggerConfig.environment
    val extra: Map<String, JsonElement>? = null,  // tipos nativos: Int, Long, Double, Boolean, String
    val sdkVersion: String = AppLoggerVersion.NAME,
    // Solo cuando level == METRIC:
    val metricName: String? = null,
    val metricValue: Double? = null,
    val metricUnit: String? = null,
    val metricTags: Map<String, String>? = null   // auto-enriquecido con platform, app_version, device_model
)
```

**Nota sobre `extra`:** Los valores son `JsonElement` nativos, no strings. Esto permite queries directas en Supabase JSONB:

```sql
SELECT * FROM app_logs WHERE (extra->>'retry_count')::int > 2;
SELECT * FROM app_logs WHERE (extra->>'is_cached')::boolean = true;
SELECT * FROM app_logs WHERE environment = 'production' AND level = 'ERROR';
```

### 6.2 `BatchProcessor` — Corazón del Pipeline

```kotlin
internal class BatchProcessor(
    private val buffer: InMemoryBuffer,
    private val transport: LogTransport,
    private val formatter: JsonLogFormatter,
    private val config: AppLoggerConfig,
    private val offlineStorage: OfflineStorage = NoOpOfflineStorage
) {
    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("AppLogger-Processor")
    )
    private val sendMutex = Mutex()   // anti-solapamiento: un solo sendBatch() activo a la vez

    // consecutiveFailures NO se resetea hasta que un envío tenga éxito completo
    // lastSuccessfulFlushTimestamp se actualiza solo en TransportResult.Success
}
```

---

## 7. Gestión del Ciclo de Vida

**Android** — `ProcessLifecycleOwner` registra dos observers:

1. `AppLoggerLifecycleObserver` — en `onStop()`: llama `impl.flush()` y `sessionManager.onBackground()`.
2. `AppLoggerForegroundObserver` — en `onStart()`: llama `sessionManager.onForeground()`.

**iOS** — el consumidor debe llamar `AppLoggerIos.shared.flush()` en `applicationDidEnterBackground`.

**Crash Handler** — solo se instala en producción (`isDebugMode = false`). Usa `runBlocking` justificado: el proceso está terminando y el flush síncrono es la única garantía de que el evento de crash llega al backend.

---

## 8. Configuración — Builder Pattern

```kotlin
val config = AppLoggerConfig.Builder()
    .endpoint("https://xyz.supabase.co")     // HTTPS obligatorio en producción
    .apiKey("eyJ...")                         // Supabase anon key
    .environment("production")               // "production" | "staging" | "development"
    .debugMode(false)                         // o leer de APPLOGGER_DEBUG automáticamente
    .consoleOutput(true)                      // logcat/NSLog (solo activo si isDebugMode=true)
    .minLevel(LogMinLevel.INFO)              // descarta DEBUG antes del pipeline
    .batchSize(20)                            // 1–100 (default 20)
    .flushIntervalSeconds(30)                 // 5–300 (default 30)
    .maxStackTraceLines(50)                   // 1–100 (default 50)
    .flushOnlyWhenIdle(false)                 // true en TV: flush solo en background
    .verboseTransportLogging(false)           // imprime cada batch en logcat (solo debug)
    .bufferSizeStrategy(BufferSizeStrategy.FIXED)
    .bufferOverflowPolicy(BufferOverflowPolicy.DISCARD_OLDEST)
    .offlinePersistenceMode(OfflinePersistenceMode.NONE)
    .build()
```

**Validación post-build:**

```kotlin
val issues = config.validate()
if (issues.isNotEmpty()) {
    issues.forEach { Log.w("AppLogger", "Config issue: $it") }
}
```

`validate()` detecta: endpoint en blanco, endpoint sin HTTPS en producción, apiKey en blanco, apiKey sin formato JWT, environment en blanco, combinación batchSize/flushInterval problemática, y `isDebugMode=true` con `environment="production"`.

**`LogMinLevel`** — tabla de filtrado:

| Valor | Eventos procesados |
|---|---|
| `DEBUG` | Todos (default) |
| `INFO` | INFO, WARN, ERROR, CRITICAL, METRIC |
| `WARN` | WARN, ERROR, CRITICAL, METRIC |
| `ERROR` | ERROR, CRITICAL, METRIC |
| `CRITICAL` | Solo CRITICAL y METRIC |

METRIC siempre pasa independientemente del nivel configurado.

**`OfflinePersistenceMode`:**

| Valor | Comportamiento |
|---|---|
| `NONE` | Solo memoria (default) |
| `CRITICAL_ONLY` | ERROR y CRITICAL se guardan en SQLite |
| `ALL` | Todos los eventos se guardan en SQLite |

---

## 9. Extensibilidad — Implementar Transportes Propios

Para usar un backend diferente a Supabase (Firebase, Datadog, HTTP propio, etc.):

```kotlin
class FirebaseTransport(private val database: FirebaseDatabase) : LogTransport {

    override suspend fun send(events: List<LogEvent>): TransportResult {
        return try {
            val ref = database.getReference("app_logs")
            withContext(Dispatchers.IO) {
                events.forEach { event -> ref.push().setValue(event.toMap()) }
            }
            TransportResult.Success
        } catch (e: Exception) {
            TransportResult.Failure(
                reason = e.message ?: "Firebase write failed",
                retryable = true,
                cause = e
            )
        }
    }

    override fun isAvailable(): Boolean = true
}

// Inyección en initialize():
AppLoggerSDK.initialize(
    context = this,
    config = AppLoggerConfig.Builder()
        .endpoint("https://...")
        .apiKey("eyJ...")
        .environment("production")
        .build(),
    transport = FirebaseTransport(FirebaseDatabase.getInstance())
)
```

---

## 10. Concurrencia y Thread Safety

| Componente | Thread Safety | Mecanismo |
|---|---|---|
| `AppLoggerSDK` | ✅ | `@Volatile` + `AtomicBoolean` en inicialización |
| `AppLoggerImpl.process()` | ✅ | `Channel.trySend()` es thread-safe |
| `BatchProcessor.enqueue()` | ✅ | `Channel` de Kotlin Coroutines |
| `BatchProcessor.sendBatch()` | ✅ | `Mutex` (sendMutex) — un solo envío activo |
| `InMemoryBuffer` | ✅ | `ConcurrentLinkedQueue` |
| `RateLimitFilter` | ✅ | `ConcurrentHashMap` + `AtomicInteger` |
| `SessionManager` | ✅ | Inmutable tras creación |
| `AppLoggerConfig` | ✅ | `data class` inmutable |
| `AppLoggerHealth.snapshot()` | ✅ | Lectura de referencias `@Volatile` |

**Coroutine Scope del SDK:**

```kotlin
private val scope = CoroutineScope(
    Dispatchers.IO          // No bloquea el hilo principal
    + SupervisorJob()       // Fallos individuales no cancelan el scope
    + CoroutineName("AppLogger-Processor")
)
```

`SupervisorJob()` garantiza que si un batch falla, el job del coroutine no se cancela. El scope es tan longevo como la app.

---

## 11. KMP — Arquitectura Multiplataforma

### 11.1 El Patrón `expect` / `actual`

Los providers de plataforma son `expect` en `commonMain` y tienen implementación `actual` en cada target:

| Componente | commonMain | androidMain | iosMain |
|---|---|---|---|
| `DeviceInfoProvider` | `expect interface` | `AndroidDeviceInfoProvider` | `IosDeviceInfoProvider` |
| `CrashHandler` | `expect interface` | `AndroidCrashHandler` | `IosCrashHandler` |
| `currentTimeMillis()` | `expect fun` | `System.currentTimeMillis()` | `NSDate.timeIntervalSince1970` |

### 11.2 Diferencias de comportamiento por plataforma

| Comportamiento | Android Mobile | Android TV | iOS |
|---|---|---|---|
| Entry point | `AppLoggerSDK` | `AppLoggerSDK` | `AppLoggerIos.shared` |
| Debug flag | `AndroidManifest meta-data` | `AndroidManifest meta-data` | `Info.plist` |
| Buffer capacity | 1000 eventos | 100 eventos (low resource) | 1000 eventos |
| Rate limit | 120 eventos/tag/min | 30 eventos/tag/min | 120 eventos/tag/min |
| Stack trace lines | 50 (default) | 5 (auto-reducido) | 50 (default) |
| Flush trigger | Lifecycle + periódico | Solo idle | Manual (`flush()`) |
| Crash handler | `AndroidCrashHandler` | `AndroidCrashHandler` | `IosCrashHandler` |
| Offline storage | SQLite (Android Room) | SQLite (Android Room) | No disponible aún |
| `connectionType` | `ConnectivityManager` | `ConnectivityManager` | `Network.framework` |

### 11.3 `AppLoggerHealth` — Monitoreo en Tiempo Real

```kotlin
val health: HealthStatus = AppLoggerHealth.snapshot()

// Campos disponibles:
health.isInitialized                    // true tras initialize() exitoso
health.transportAvailable               // true si el transporte tiene conectividad
health.bufferedEvents                   // eventos en buffer pendientes de envío
health.deadLetterCount                  // eventos fallidos permanentemente
health.consecutiveFailures              // fallos consecutivos del transporte
health.eventsDroppedDueToBufferOverflow // total descartados por overflow
health.bufferUtilizationPercentage      // ocupación del buffer (0-100)
health.sdkVersion                       // versión del SDK
health.snapshotTimestamp                // epoch millis cuando se tomó el snapshot
health.lastSuccessfulFlushTimestamp     // epoch millis del último flush exitoso (0 si ninguno)

// Detectar outage silencioso:
if (health.isStale(maxAgeMs = 10_000L)) {
    // El snapshot tiene más de 10 segundos — puede no reflejar el estado actual
}

// Detectar que el SDK lleva tiempo sin enviar:
val minutesSinceFlush = (currentTime - health.lastSuccessfulFlushTimestamp) / 60_000
if (minutesSinceFlush > 5) alertSRE("AppLogger no ha enviado en $minutesSinceFlush minutos")
```

`AppLoggerHealth` implementa `AppLoggerHealthProvider`, lo que permite inyectar un fake en tests:

```kotlin
class FakeHealthProvider(private val status: HealthStatus) : AppLoggerHealthProvider {
    override fun snapshot() = status
}

// En tests:
val provider: AppLoggerHealthProvider = FakeHealthProvider(
    HealthStatus(isInitialized = true, transportAvailable = false, bufferedEvents = 42, ...)
)
myViewModel = MyViewModel(healthProvider = provider)
```
