# AppLogger — Guía de Integración

**Plataforma:** Android (Mobile + TV) · iOS · JVM

> Para la versión actual del SDK consultá las [releases del repositorio](https://github.com/zuccadev-labs/appLoggers/releases).

Documentación para desarrolladores que consumen `AppLogger` en sus aplicaciones.

---

## Índice

1. [Inicio Rápido — 5 minutos](#1-inicio-rápido--5-minutos)
2. [Instalación y Dependencias](#2-instalación-y-dependencias)
3. [Configuración por Entorno](#3-configuración-por-entorno)
4. [Inicialización en Application](#4-inicialización-en-application)
5. [Uso del Logger en la App](#5-uso-del-logger-en-la-app)
6. [Transporte Custom — Implementar LogTransport](#6-transporte-custom--implementar-logtransport)
7. [Configuración para Android TV](#7-configuración-para-android-tv)
8. [Integración en iOS (KMP puro)](#8-integración-en-ios-kmp-puro)
9. [Matriz de Compatibilidad de Plataformas](#9-matriz-de-compatibilidad-de-plataformas)
10. [App de Monitoreo Externo](#10-app-de-monitoreo-externo)
11. [Modo Debug vs Producción](#11-modo-debug-vs-producción)
12. [User ID Opcional — Con Consentimiento](#12-user-id-opcional--con-consentimiento)
13. [Preguntas Frecuentes](#13-preguntas-frecuentes)

---

## 1. Inicio Rápido — 5 minutos

```kotlin
// 1. En Application.kt
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
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
                .environment("production")
                .debugMode(BuildConfig.DEBUG)
                .build(),
            transport = transport
        )
    }
}

// 2. En cualquier lugar de la app — loguear un error
AppLoggerSDK.error("PAYMENT", "Transaction failed", throwable)

// 3. En cualquier lugar — loguear información
AppLoggerSDK.info("PLAYER", "Playback started", extra = mapOf("content_id" to "movie_123"))
```

Eso es todo. La librería se encarga del batching, la transmisión asíncrona y la captura de crashes.

---

## 2. Instalación y Dependencias

### 2.1 Añadir el repositorio JitPack

En `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2.2 Añadir la dependencia

En el `build.gradle.kts` del módulo `app`:

```kotlin
dependencies {
    // Core del logger (obligatorio)
    // Reemplazar <latest-version> con la última release: https://github.com/zuccadev-labs/appLoggers/releases
    implementation("com.github.zuccadev-labs.appLoggers:logger-core:<latest-version>")

    // Módulo de transporte Supabase (opcional — incluido en el core)
    // implementation("com.github.zuccadev-labs.appLoggers:logger-transport-supabase:<latest-version>")
}
```

### 2.3 Permisos en AndroidManifest.xml

AppLogger solo requiere internet para el modo producción:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

No se declaran otros permisos. La librería **no solicita** localización, contactos, almacenamiento externo ni ningún permiso sensitivo.

---

## 3. Configuración por Entorno

### 3.1 Variables de configuración completas

Todas las variables se colocan en `local.properties` (no commiteable) y se mapean a `BuildConfig`:

| Variable | Tipo | Valor por defecto | Descripción |
|---|---|---|---|
| `APPLOGGER_URL` | String | `""` | Endpoint del backend (Supabase URL o URL propia) |
| `APPLOGGER_ANON_KEY` | String | `""` | API key de autenticación (anon key de Supabase) |
| `APPLOGGER_DEBUG` | Boolean | `false` | Modo debug: logs van a Logcat en vez de backend |
| `appLogger_logToConsole` | Boolean | `true` | Mostrar logs en Logcat (solo en debug) |
| `appLogger_batchSize` | Int | `20` | Número de eventos por batch antes de enviar (1-100) |
| `appLogger_flushIntervalSeconds` | Int | `30` | Intervalo máximo en segundos antes de flush automático (5-300) |
| `appLogger_maxStackTraceLines` | Int | `50` | Líneas máximas de stack trace (Mobile: 50, TV: 5) |
| `appLogger_lowStorageMode` | Boolean | `false` | Reduce buffer local y stack traces (para TV o dispositivos low-RAM) |
| `appLogger_verboseTransport` | Boolean | `false` | Log detallado de cada batch enviado (solo debug) |
| `appLogger_userId` | String | `null` | UUID anónimo del usuario (solo con consentimiento explícito) |
| `appLogger_bufferSizeStrategy` | String | `FIXED` | Estrategia de tamaño: `FIXED`, `ADAPTIVE_TO_RAM`, `ADAPTIVE_TO_LOG_RATE` |
| `appLogger_bufferOverflowPolicy` | String | `DISCARD_OLDEST` | Política ante overflow: `DISCARD_OLDEST`, `DISCARD_NEWEST`, `PRIORITY_AWARE` |
| `appLogger_offlinePersistenceMode` | String | `NONE` | Persistencia offline: `NONE`, `CRITICAL_ONLY`, `ALL` |

### 3.2 Ejemplo completo de `local.properties`

```properties
# AppLogger — NUNCA commitear este archivo
APPLOGGER_URL=https://tu-proyecto.supabase.co
APPLOGGER_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.tu_anon_key_aqui
APPLOGGER_DEBUG=true
appLogger_logToConsole=true
appLogger_batchSize=20
appLogger_flushIntervalSeconds=30
appLogger_maxStackTraceLines=50
appLogger_lowStorageMode=false
appLogger_verboseTransport=false
```

> **Verificar que `local.properties` está en `.gitignore`:**
> ```
> # .gitignore
> local.properties
> ```

### 3.3 Mapear en `build.gradle.kts`

```kotlin
import java.util.Properties

android {
    buildFeatures { buildConfig = true }

    defaultConfig {
        val props = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) load(file.inputStream())
        }

        buildConfigField("String",  "LOGGER_URL",            "\"${props["APPLOGGER_URL"] ?: ""}\"")
        buildConfigField("String",  "LOGGER_KEY",            "\"${props["APPLOGGER_ANON_KEY"] ?: ""}\"")
        buildConfigField("Boolean", "LOGGER_DEBUG_MODE",     "${props["APPLOGGER_DEBUG"] ?: false}")
        buildConfigField("Boolean", "LOGGER_CONSOLE_OUTPUT", "${props["appLogger_logToConsole"] ?: true}")
        buildConfigField("Int",     "LOGGER_BATCH_SIZE",     "${props["appLogger_batchSize"] ?: 20}")
        buildConfigField("Int",     "LOGGER_FLUSH_INTERVAL", "${props["appLogger_flushIntervalSeconds"] ?: 30}")
        buildConfigField("Int",     "LOGGER_MAX_STACK",      "${props["appLogger_maxStackTraceLines"] ?: 50}")
        buildConfigField("Boolean", "LOGGER_LOW_STORAGE",    "${props["appLogger_lowStorageMode"] ?: false}")
        buildConfigField("Boolean", "LOGGER_VERBOSE",        "${props["appLogger_verboseTransport"] ?: false}")
        buildConfigField("String",  "LOGGER_BUFFER_STRATEGY", "\"${props["appLogger_bufferSizeStrategy"] ?: "FIXED"}\"")
        buildConfigField("String",  "LOGGER_OVERFLOW_POLICY", "\"${props["appLogger_bufferOverflowPolicy"] ?: "DISCARD_OLDEST"}\"")
        buildConfigField("String",  "LOGGER_PERSISTENCE_MODE", "\"${props["appLogger_offlinePersistenceMode"] ?: "NONE"}\"")
    }
}
```

### 3.4 Variables en CI/CD (GitHub Actions)

Para el pipeline de producción, las credenciales se inyectan como secrets:

```yaml
# .github/workflows/release.yml
- name: Build Release APK
  env:
    LOGGER_URL: ${{ secrets.LOGGER_URL }}
    LOGGER_KEY: ${{ secrets.LOGGER_ANON_KEY }}
  run: |
    echo "APPLOGGER_URL=$LOGGER_URL" >> local.properties
    echo "APPLOGGER_ANON_KEY=$LOGGER_KEY" >> local.properties
    echo "APPLOGGER_DEBUG=false" >> local.properties
    echo "appLogger_logToConsole=false" >> local.properties
    ./gradlew assembleRelease
```

### 3.5 Comportamiento según configuración

| `APPLOGGER_DEBUG` | `appLogger_logToConsole` | Resultado |
|---|---|---|
| `true` | `true` | Logs a Logcat **y** backend (ambos destinos activos) |
| `true` | `false` | Solo a backend (debug mode sin consola) |
| `false` | `true` | Solo a backend — Logcat suprimido en producción independientemente de `consoleOutput` |
| `false` | `false` | Solo a backend (producción normal) |

> En producción (`isDebugMode=false`), Logcat está **siempre suprimido** sin importar el valor de `consoleOutput`. El flag `consoleOutput` solo tiene efecto cuando `isDebugMode=true`.

Para desactivar completamente el envío de datos (modo offline-only), no configurar `APPLOGGER_URL` o dejarlo vacío. El SDK operará solo con SQLite local.

---

## 4. Inicialización en Application

### 4.1 Inicialización estándar

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        AppLoggerSDK.initialize(
            context = this,
            config = AppLoggerConfig.Builder()
                .endpoint(BuildConfig.LOGGER_URL)
                .apiKey(BuildConfig.LOGGER_KEY)
                .debugMode(BuildConfig.LOGGER_DEBUG_MODE)
                .consoleOutput(BuildConfig.LOGGER_CONSOLE_OUTPUT)
                .environment("production")              // filtra QA vs prod en Supabase
                .minLevel(LogMinLevel.INFO)             // descarta DEBUG en producción
                .batchSize(BuildConfig.LOGGER_BATCH_SIZE)
                .flushIntervalSeconds(BuildConfig.LOGGER_FLUSH_INTERVAL)
                .maxStackTraceLines(BuildConfig.LOGGER_MAX_STACK)
                // Opciones avanzadas:
                .bufferSizeStrategy(
                    when (BuildConfig.LOGGER_BUFFER_STRATEGY) {
                        "ADAPTIVE_TO_RAM" -> BufferSizeStrategy.ADAPTIVE_TO_RAM
                        "ADAPTIVE_TO_LOG_RATE" -> BufferSizeStrategy.ADAPTIVE_TO_LOG_RATE
                        else -> BufferSizeStrategy.FIXED
                    }
                )
                .bufferOverflowPolicy(
                    when (BuildConfig.LOGGER_OVERFLOW_POLICY) {
                        "DISCARD_NEWEST" -> BufferOverflowPolicy.DISCARD_NEWEST
                        "PRIORITY_AWARE" -> BufferOverflowPolicy.PRIORITY_AWARE
                        else -> BufferOverflowPolicy.DISCARD_OLDEST
                    }
                )
                .offlinePersistenceMode(
                    when (BuildConfig.LOGGER_PERSISTENCE_MODE) {
                        "CRITICAL_ONLY" -> OfflinePersistenceMode.CRITICAL_ONLY
                        "ALL" -> OfflinePersistenceMode.ALL
                        else -> OfflinePersistenceMode.NONE
                    }
                )
                .build(),
            transport = SupabaseTransport(
                endpoint = BuildConfig.LOGGER_URL,
                apiKey = BuildConfig.LOGGER_KEY,
                networkAvailabilityProvider = androidNetworkAvailabilityProvider(this)
            )
        )

        // Validar configuración en debug — detecta problemas antes de producción
        if (BuildConfig.DEBUG) {
            val issues = AppLoggerConfig.Builder()
                .endpoint(BuildConfig.LOGGER_URL)
                .apiKey(BuildConfig.LOGGER_KEY)
                .build()
                .validate()
            issues.forEach { Log.w("AppLogger", "Config issue: $it") }
        }
    }
}
```

### 4.2 Declarar en AndroidManifest.xml

```xml
<application
    android:name=".MyApp"
    ... >
```

### 4.3 ¿Qué hace `initialize()` internamente?

Al llamar a `initialize()`, el SDK:

1. Construye el `DeviceInfoProvider` con los metadatos del dispositivo.
2. Inicia el `Channel<LogEvent>` en memoria para recibir eventos.
3. Lanza coroutines en `Dispatchers.Default` para procesar el canal y gestionar el flush periódico.
4. Instala el `UncaughtExceptionHandler` (en modo producción).
5. Registra un `LifecycleObserver` en `ProcessLifecycleOwner` para flush automático en background.

Todo esto ocurre **fuera del hilo principal**.

---

## 5. Uso del Logger en la App

### 5.1 API Pública

```kotlin
// Debug — solo visible en modo debug (Logcat)
AppLoggerSDK.debug("TAG", "Mensaje de depuración")
AppLoggerSDK.debug("TAG", "Con datos extra", extra = mapOf("key" to "value"))
AppLoggerSDK.debug("TAG", "Estado inesperado", throwable = e)               // stacktrace opcional

// Info — flujos normales de la app
AppLoggerSDK.info("PLAYER", "Playback started")
AppLoggerSDK.info("PLAYER", "Buffering", extra = mapOf("buffer_ms" to 500))
AppLoggerSDK.info("PLAYER", "Recovery attempt", throwable = e)              // stacktrace opcional

// Warn — comportamiento inesperado pero no fatal
AppLoggerSDK.warn("NETWORK", "Slow response detected", anomalyType = "HIGH_LATENCY")
AppLoggerSDK.warn("NETWORK", "Slow response", throwable = e, anomalyType = "HIGH_LATENCY") // con stacktrace

// Error — fallos que el usuario probablemente nota
AppLoggerSDK.error("PAYMENT", "Transaction failed", throwable = exception)

// Critical — fallos que impiden el uso de la app
AppLoggerSDK.critical("AUTH", "Token refresh failed completely", throwable = exception)

// Metric — datos de performance
AppLoggerSDK.metric("screen_load_time", 1234.0, "ms", tags = mapOf("screen" to "HomeScreen"))
```

### 5.2 Extension Functions — Tag Automático

El módulo `logger-core` incluye extension functions en `commonMain` que eliminan el tag manual en clases con logger inyectado. El tag se infiere del nombre simple de la clase (`this::class.simpleName`).

```kotlin
import com.applogger.core.logD
import com.applogger.core.logI
import com.applogger.core.logW
import com.applogger.core.logE
import com.applogger.core.logC
import com.applogger.core.logTag

class PlaybackRepository(private val logger: AppLogger) {

    fun load(id: String) {
        this.logI(logger, "Loading content", extra = mapOf("content_id" to id))
        // Tag automático → "PlaybackRepository"
    }

    fun handleError(t: Throwable) {
        this.logE(logger, "Content load failed", throwable = t)
    }

    fun warnRetry(t: Throwable) {
        this.logW(logger, "Retrying segment", throwable = t, anomalyType = "SEGMENT_RETRY")
    }
}
```

También se pueden usar los shorthands directos sobre `AppLogger` cuando el tag es explícito:

```kotlin
logger.logE("PLAYER", "Segment failed", throwable = e)
logger.logW("NETWORK", "Timeout", anomalyType = "TIMEOUT")
```

| Nivel | Cuándo usarlo | Ejemplos |
|---|---|---|
| `debug` | Flujos internos durante desarrollo | Estado de variables, puntos de control |
| `info` | Eventos relevantes del flujo productivo | Usuario empieza reproducción, completa pago |
| `warn` | Anomalías recuperables | Reintento de red, degradación de calidad |
| `error` | Fallos que afectan al usuario | Fallo de API, error de parseo, timeout |
| `critical` | Fallos que bloquean la app | Corrupción de estado, fallo de inicialización |
| `metric` | Datos cuantitativos de performance | Tiempos de carga, uso de memoria, buffer |

### 5.4 Helpers avanzados — `withTag`, `timed`, `logCatching`

```kotlin
// withTag — TaggedLogger con tag fijo para toda la clase
class PaymentRepository(logger: AppLogger) {
    private val log = logger.withTag("PaymentRepository")

    fun charge(amount: Double) {
        log.i("Charging card", extra = mapOf("amount_cents" to (amount * 100).toInt()))
        log.e("Charge failed", throwable = e)
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
// result es null si lanzó excepción; la excepción ya está logueada como ERROR
```

### 5.5 Global Extra — Contexto adjunto a todos los eventos

```kotlin
// Adjuntar contexto global (ej. A/B test, feature flag)
AppLoggerSDK.addGlobalExtra("ab_test", "checkout_v2")
AppLoggerSDK.addGlobalExtra("experiment", "group_b")

// Todos los eventos posteriores incluirán estos pares en extra
AppLoggerSDK.info("PLAYER", "Playback started")  // extra incluye ab_test y experiment

// Remover una clave específica
AppLoggerSDK.removeGlobalExtra("ab_test")

// Limpiar todo el contexto global
AppLoggerSDK.clearGlobalExtra()
```

Los valores per-call en `extra` tienen precedencia sobre los globales en caso de colisión de clave.

### 5.6 Buenas Prácticas de Contenido

```kotlin
// ✅ Loguear contexto técnico, no datos del usuario
AppLoggerSDK.error("STREAM", "HLS segment fetch failed",
    extra = mapOf("segment_index" to 42, "cdn_region" to "us-east-1", "retry_count" to 2))

// ✅ Usar tags consistentes en todo el módulo
object LogTags {
    const val PLAYER   = "PLAYER"
    const val NETWORK  = "NETWORK"
    const val AUTH     = "AUTH"
    const val PAYMENT  = "PAYMENT"
}

// ❌ Nunca loguear datos del usuario
AppLoggerSDK.error("AUTH", "Error for user: ${user.email}")  // Incorrecto

// ❌ Nunca loguear tokens o credenciales
AppLoggerSDK.debug("AUTH", "Token: $accessToken")  // NUNCA
```

---

## 6. Transporte Custom — Implementar LogTransport

AppLogger separa la lógica de logging del transporte. `LogTransport` es el único punto de integración con cualquier backend. Para usar un backend diferente a Supabase (Firebase, Datadog, HTTP propio, etc.) implementa la interfaz:

### 6.1 Implementar LogTransport

```kotlin
import com.applogger.core.LogTransport
import com.applogger.core.TransportResult
import com.applogger.core.model.LogEvent

class MyBackendTransport(
    private val endpoint: String,
    private val apiKey: String
) : LogTransport {

    override suspend fun send(events: List<LogEvent>): TransportResult {
        return try {
            // Serializar los eventos y enviarlos a tu backend.
            // events contiene: level, tag, message, deviceInfo, sessionId, throwableInfo, extra...
            val payload = events.map { serialize(it) }
            myHttpClient.post(endpoint, payload, headers = mapOf("X-Api-Key" to apiKey))
            TransportResult.Success
        } catch (e: Exception) {
            TransportResult.Failure(
                reason = e.message ?: "Transport error",
                retryable = true, // true → el SDK reintenta con backoff exponencial
                cause = e
            )
        }
    }

    override fun isAvailable(): Boolean {
        // false → el SDK mantiene eventos en buffer y reintenta cuando isAvailable() vuelva a true.
        return endpoint.isNotBlank() && apiKey.isNotBlank()
    }
}
```

### 6.2 Inyectar al inicializar

```kotlin
val transport = MyBackendTransport(
    endpoint = BuildConfig.LOGGER_URL,
    apiKey = BuildConfig.LOGGER_KEY
)

AppLoggerSDK.initialize(
    context = this,
    config = AppLoggerConfig.Builder()
        .endpoint(BuildConfig.LOGGER_URL)
        .apiKey(BuildConfig.LOGGER_KEY)
        .debugMode(BuildConfig.DEBUG)
        .build(),
    transport = transport
)
```

### 6.3 Campos disponibles en `LogEvent`

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | `String` | UUID único del evento |
| `timestamp` | `Long` | Unix millis del momento del log |
| `level` | `LogLevel` | DEBUG, INFO, WARN, ERROR, CRITICAL, METRIC |
| `tag` | `String` | Etiqueta del módulo (máx 100 chars) |
| `message` | `String` | Mensaje del log (máx 10 000 chars) |
| `throwableInfo` | `ThrowableInfo?` | Tipo, mensaje y stack trace (si aplica) |
| `deviceInfo` | `DeviceInfo` | Marca, modelo, OS, API level, plataforma, conexión |
| `deviceId` | `String` | Identificador estable del dispositivo (SDK-generado por defecto) |
| `sessionId` | `String` | UUID efímero de la sesión |
| `userId` | `String?` | UUID anónimo opcional (solo con consentimiento) |
| `environment` | `String` | Entorno de despliegue: "production", "staging", "development" |
| `extra` | `Map<String, JsonElement>?` | Metadatos adicionales — valores nativos (Int, Long, Double, Boolean, String) |
| `sdkVersion` | `String` | Versión del SDK que generó el evento |
| `metricName` | `String?` | Nombre de la métrica (solo cuando level == METRIC) |
| `metricValue` | `Double?` | Valor numérico de la métrica |
| `metricUnit` | `String?` | Unidad de la métrica (ej. "ms", "count", "bytes") |
| `metricTags` | `Map<String, String>?` | Tags contextuales de la métrica (auto-enriquecidos con platform, app_version, device_model) |

> `extra` es `Map<String, JsonElement>?` — no `Map<String, String>?`. Los valores Int, Long, Double y Boolean se preservan como primitivos JSON nativos, habilitando queries directas en Supabase JSONB:
> ```sql
> SELECT * FROM app_logs WHERE (extra->>'retry_count')::int > 2;
> SELECT * FROM app_logs WHERE environment = 'staging';
> ```

### 6.4 Comportamiento de retry

Si `send()` retorna `TransportResult.Failure(retryable = true)`, el SDK aplica **backoff exponencial con jitter**:

- Máx. 5 reintentos por batch.
- Delay: aleatorio entre `base/2` y `min(base × 2ⁿ, 30 s)` (base = 1 s).
- Al agotar reintentos: eventos enviados al `DeadLetterQueue` en memoria.

---

## 7. Configuración para Android TV

En Android TV, el SDK detecta automáticamente la plataforma y ajusta su comportamiento. Sin embargo, hay configuraciones adicionales recomendadas.

### 7.1 El SDK detecta Android TV automáticamente

No es necesario indicar explícitamente que la app es para TV. El `PlatformDetector` interno usa:

```kotlin
// Detección automática — no requiere configuración manual
packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)  // → ANDROID_TV
```

### 7.2 Valores por defecto en Android TV

> El SDK detecta automáticamente TV y aplica estos valores via `resolveForLowResource()`. Solo sobreescribelos si necesitas ajustar los defaults.

```kotlin
AppLoggerSDK.initialize(
    context = this,
    config = AppLoggerConfig.Builder()
        .endpoint(BuildConfig.LOGGER_URL)
        .apiKey(BuildConfig.LOGGER_KEY)
        .debugMode(BuildConfig.LOGGER_DEBUG_MODE)
        // Valores aplicados automáticamente para TV (override solo si es necesario):
        .batchSize(5)                 // Auto en TV: 5 eventos/batch (mobile: 20)
        .flushIntervalSeconds(60)     // Auto en TV: flush cada 60 s (mobile: 30 s)
        .maxStackTraceLines(5)        // Auto en TV: 5 líneas max (mobile: 50)
        .flushOnlyWhenIdle(true)      // Auto en TV: flush solo en background
        .build()
)
```

### 7.3 Comportamiento automático en TV

- **Buffer en memoria FIFO (100 eventos)**: cuando el buffer está lleno, el evento más antiguo se descarta para dejar espacio al nuevo.
- **Stack traces truncados**: máximo 5 líneas por excepción (vs 50 en mobile) para reducir el payload.
- **Rate limiting reforzado**: máximo 30 eventos por tag por minuto (vs 120 en mobile).
- **Flush idle**: flush solo al pasar a background (`flushOnlyWhenIdle = true`), minimizando actividad en foreground.

---

## 8. Integración en iOS (KMP puro)

En esta auditoría, iOS se trata como target KMP puro: configuración, inicialización y uso desde Kotlin (`commonMain` + `iosMain`).

### 8.1 Inicialización en `iosMain` (Kotlin)

El entry point iOS es `AppLoggerIos.shared` (definido en `iosMain`). Es distinto del singleton Android `AppLoggerSDK`.

```kotlin
// iosMain
import com.applogger.core.AppLoggerConfig
import com.applogger.core.AppLoggerIos
import com.applogger.core.LogMinLevel
import com.applogger.transport.supabase.SupabaseTransport

fun initializeLoggerIos(url: String, apiKey: String, debugMode: Boolean = false) {
    val config = AppLoggerConfig.Builder()
        .endpoint(url)
        .apiKey(apiKey)
        .environment("production")          // siempre etiquetar el entorno
        .debugMode(debugMode)
        .minLevel(LogMinLevel.INFO)          // descarta DEBUG en producción
        .batchSize(20)
        .flushIntervalSeconds(30)
        .maxStackTraceLines(50)
        .build()

    val transport = SupabaseTransport(endpoint = url, apiKey = apiKey)

    AppLoggerIos.shared.initialize(
        config = config,
        transport = transport
    )
}
```

> **Debug mode en iOS:** El SDK lee `APPLOGGER_DEBUG` de `Info.plist` automáticamente. Si la clave está presente y es `"true"`, activa `isDebugMode = true` sin cambiar código.

### 8.2 Uso en iOS desde Kotlin

```kotlin
AppLoggerIos.shared.info("PLAYER", "Playback started")
AppLoggerIos.shared.error("PAYMENT", "Transaction failed", throwable = null)
AppLoggerIos.shared.metric("buffer_time", 420.0, "ms")
AppLoggerIos.shared.flush()  // llamar manualmente al entrar en background
```

### 8.3 Sesión e identidad en iOS

```kotlin
// Al hacer login
AppLoggerIos.shared.setAnonymousUserId(anonymousUUID)
AppLoggerIos.shared.newSession()

// Al hacer logout
AppLoggerIos.shared.clearAnonymousUserId()
AppLoggerIos.shared.newSession()

// Global extra — adjunta a todos los eventos posteriores
AppLoggerIos.shared.addGlobalExtra("ab_test", "checkout_v2")
AppLoggerIos.shared.removeGlobalExtra("ab_test")
AppLoggerIos.shared.clearGlobalExtra()
```

### 8.4 Configuración avanzada

```kotlin
val config = AppLoggerConfig.Builder()
    .endpoint("https://tu-proyecto.supabase.co")
    .apiKey("eyJ...")
    .environment("production")
    .debugMode(false)
    .minLevel(LogMinLevel.INFO)
    .bufferSizeStrategy(BufferSizeStrategy.FIXED)
    .bufferOverflowPolicy(BufferOverflowPolicy.DISCARD_OLDEST)
    .offlinePersistenceMode(OfflinePersistenceMode.NONE)
    .build()
```

---

## 9. Manejo de Desconexión y Métricas de Salud

### 9.1 Comportamiento ante pérdida de conectividad

El SDK está diseñado para operar en redes inestables típicas de dispositivos móviles:

- **Buffer local en memoria**: Los eventos se encolan en un buffer FIFO mientras no haya conectividad.
- **Reintento inteligente**: Si el transporte reporta `isAvailable() = false`, el batch se reintenta con backoff exponencial + jitter (máx 5 reintentos).
- **Dead Letter Queue**: Si se agotan los reintentos, los eventos se mueven a una cola de cartas muertas (en memoria) para diagnóstico posterior.
- **Flush automático en reconexión**: Cuando `isAvailable()` vuelve a `true`, se envía automáticamente el buffer pendiente.

### 9.2 Monitoreo de salud del SDK

Puedes consultar el estado interno del logger en tiempo real:

```kotlin
val health = AppLoggerHealth.snapshot()
if (!health.transportAvailable) {
    // Mostrar banner "offline" en UI
}
if (health.bufferUtilizationPercentage > 80) {
    // Alertar a SRE: buffer cerca de overflow
}
if (health.eventsDroppedDueToBufferOverflow > 0) {
    // Reportar métrica de pérdida de logs
}

// Detectar outage silencioso — el SDK lleva tiempo sin enviar
val minutesSinceFlush = (System.currentTimeMillis() - health.lastSuccessfulFlushTimestamp) / 60_000
if (health.lastSuccessfulFlushTimestamp > 0 && minutesSinceFlush > 5) {
    Log.w("AppLogger", "Sin flush exitoso en $minutesSinceFlush minutos")
}

// Verificar que el snapshot no está desactualizado
if (health.isStale(maxAgeMs = 60_000L)) {
    // El snapshot tiene más de 1 minuto — volver a llamar snapshot()
}
```

**Campos expuestos por `HealthStatus`:**

| Campo | Tipo | Descripción |
|---|---|---|
| `isInitialized` | `Boolean` | `true` tras `initialize()` exitoso |
| `transportAvailable` | `Boolean` | `true` si el transporte tiene conectividad |
| `bufferedEvents` | `Int` | Eventos en el buffer pendientes de envío |
| `deadLetterCount` | `Int` | Eventos fallidos permanentemente (para análisis) |
| `consecutiveFailures` | `Int` | Fallos consecutivos del transporte (se resetea solo en éxito) |
| `eventsDroppedDueToBufferOverflow` | `Long` | Total de eventos descartados por overflow del buffer |
| `bufferUtilizationPercentage` | `Float` | Porcentaje de ocupación del buffer (0-100) |
| `sdkVersion` | `String` | Versión del SDK |
| `snapshotTimestamp` | `Long` | Epoch millis cuando se tomó este snapshot |
| `lastSuccessfulFlushTimestamp` | `Long` | Epoch millis del último flush exitoso (0 si ninguno aún) |

`AppLoggerHealth` implementa `AppLoggerHealthProvider`, lo que permite inyectar un fake en tests:

```kotlin
class FakeHealthProvider(private val status: HealthStatus) : AppLoggerHealthProvider {
    override fun snapshot() = status
}
```

### 9.3 Objetivos operativos de telemetría

Con la configuración por defecto (`bufferSize = 1000`, `DISCARD_OLDEST`), el SDK está diseñado para:

- Minimizar pérdida de eventos en conectividad intermitente.
- Evitar bloqueo del hilo de UI mediante envío asíncrono.
- Exponer métricas de salud para detectar degradación (`eventsDroppedDueToBufferOverflow`, `bufferUtilizationPercentage`).

Para requisitos más estrictos (ej. banca), se recomienda:

- Aumentar `bufferSize` a 5000-10000 según pruebas de carga propias.
- Usar `bufferOverflowPolicy = PRIORITY_AWARE` para preservar errores críticos.
- Considerar `offlinePersistenceMode = CRITICAL_ONLY` para retención forzada de incidentes graves.

---

## 10. Matriz de Compatibilidad de Plataformas

| Plataforma | Mínimo soportado | Recomendado | Notas |
|---|---|---|---|
| Android Mobile | API 23 (Android 6.0) | API 26+ | API 21 queda fuera por estabilidad operativa en dispositivos low-RAM |
| Android TV | API 23 (Android 6.0 TV) | API 28+ | Mismo sourceSet que Android Mobile (`androidMain`) |
| iOS | iOS 14 | iOS 16+ | Distribución via XCFramework generado por Gradle KMP |
| JVM | JDK 11 | JDK 17 | Soporte para herramientas internas y runners |

---

## 11. Modo Debug vs Producción

### 11.1 Diferencias de comportamiento

| Comportamiento | Debug (`debugMode = true`) | Producción (`debugMode = false`) |
|---|---|---|
| Destino de los logs | Logcat **y** backend (ambos activos) | Solo base de datos remota (Supabase) |
| `UncaughtExceptionHandler` | No se instala | Sí se instala |
| Flush automático | Sí (batching activo en ambos modos) | Sí (batching + intervalo de tiempo) |
| Nivel de verbosidad | Todos los niveles (DEBUG incluido) | Configurable via `minLevel` (default: todos) |
| SQLite local (fallback) | Según `offlinePersistenceMode` | Según `offlinePersistenceMode` |
| Logcat | Activo si `consoleOutput=true` | **Siempre suprimido** |

### 11.2 Control desde BuildConfig

```kotlin
// El valor de LOGGER_DEBUG_MODE viene de local.properties → build.gradle
// En desarrollo: APPLOGGER_DEBUG=true → consola
// En release: la variable no existe o es false → remoto

AppLoggerConfig.Builder()
    .debugMode(BuildConfig.LOGGER_DEBUG_MODE)
    // ...
```

---

## 12. User ID Opcional — Con Consentimiento

Por defecto, el `user_id` en todos los logs es `null`. Solo tiene sentido activarlo cuando el usuario ha dado consentimiento explícito para que sus logs sean correlacionables.

Desde esta version, AppLogger separa identidad de dispositivo e identidad de usuario:

- `device_id`: se calcula automaticamente desde huellas tecnicas del dispositivo y puede sobreescribirse.
- `user_id`: es opcional y anonimo; cuando llamas `setAnonymousUserId(...)`, el SDK normaliza internamente el valor a un formato UUID-compatible deterministico si no recibe ya un UUID canonico.

```kotlin
// Paso 1: El usuario acepta la política de privacidad
fun onPrivacyPolicyAccepted() {
    val anonymousId = getOrCreateAnonymousId()
    AppLoggerSDK.setAnonymousUserId(anonymousId)
}

// Paso 2: Generar/recuperar ID anónimo (NO usar el ID real del usuario)
// El SDK acepta UUID o texto libre y lo normaliza a UUID-compatible.
private fun getOrCreateAnonymousId(): String {
    val prefs = getSharedPreferences("app_logger_prefs", Context.MODE_PRIVATE)
    return prefs.getString("anon_user_id", null)
        ?: UUID.randomUUID().toString().also { id ->
            prefs.edit().putString("anon_user_id", id).apply()
        }
}

// Paso 3 (opcional): fijar un device_id estable propio
fun onDeviceIdAvailable(deviceFingerprint: String) {
    AppLoggerSDK.setDeviceId(deviceFingerprint)
}

// Para revocar el consentimiento (derecho al olvido):
fun onPrivacyPolicyRevoked() {
    AppLoggerSDK.clearAnonymousUserId()
    // Opcionalmente: borrar datos del servidor
}

// Para restaurar el device_id calculado automaticamente por el SDK:
fun onResetDeviceIdentity() {
    AppLoggerSDK.clearDeviceId()
}
```

---

## 13. Preguntas Frecuentes

**¿La librería puede hacer que mi app crashee?**  
No. El SDK captura silenciosamente todas las excepciones internas. Usa `Channel.trySend()` (no bloqueante) para recibir eventos. Si el canal está al límite o el transporte falla, los eventos se descartan o reintentan — la app nunca se ve afectada.

**¿Afecta al rendimiento de la UI?**  
No. Todas las operaciones de red y disco ocurren en `Dispatchers.IO`. El hilo principal solo ejecuta `Channel.trySend()`, que es una operación de microsegundos.

**¿Qué pasa si no hay internet?**  
Los logs se mantienen en el buffer en memoria (FIFO circular). Cuando el transporte vuelve a estar disponible (`isAvailable() = true`), el `BatchProcessor` los envía con retry automático (backoff exponencial).

**¿Puedo usar AppLogger sin Supabase?**  
Sí. Implementa la interfaz `LogTransport` para cualquier backend (ver [sección 6](#6-transporte-custom--implementar-logtransport) y [architecture.md](../paquete/architecture.md)).

**¿Los logs de DEBUG se envían a producción?**  
No. En modo producción (`debugMode = false`), los eventos de nivel `DEBUG` son filtrados automáticamente y no abandonan el dispositivo.

**¿Cómo verifico que los logs están llegando a Supabase?**  
En modo debug puedes habilitar el logging verbose del SDK:
```kotlin
AppLoggerConfig.Builder()
    .verboseTransportLogging(true)  // Solo en debug — imprime en Logcat cada batch enviado
```
