# AppLogger — Guía de Integración

**Versión SDK:** 0.1.0-alpha.1  
**Plataforma:** Android (Mobile + TV) · iOS · JVM  
**Fecha:** 2026-03-17

Documentación para desarrolladores que consumen `AppLogger` en sus aplicaciones.

---

## Índice

1. [Inicio Rápido — 5 minutos](#1-inicio-rápido--5-minutos)
2. [Instalación y Dependencias](#2-instalación-y-dependencias)
3. [Configuración por Entorno](#3-configuración-por-entorno)
4. [Inicialización en Application](#4-inicialización-en-application)
5. [Uso del Logger en la App](#5-uso-del-logger-en-la-app)
6. [Integración con gRPC](#6-integración-con-grpc)
7. [Integración con WebSockets](#7-integración-con-websockets)
8. [Configuración para Android TV](#8-configuración-para-android-tv)
9. [Integración en iOS (Swift)](#9-integración-en-ios-swift)
10. [Matriz de Compatibilidad de Plataformas](#10-matriz-de-compatibilidad-de-plataformas)
11. [App de Monitoreo Externo](#11-app-de-monitoreo-externo)
12. [Modo Debug vs Producción](#12-modo-debug-vs-producción)
13. [User ID Opcional — Con Consentimiento](#13-user-id-opcional--con-consentimiento)
14. [Preguntas Frecuentes](#14-preguntas-frecuentes)

---

## 1. Inicio Rápido — 5 minutos

```kotlin
// 1. En Application.kt
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLoggerSDK.initialize(
            context = this,
            config = AppLoggerConfig.Builder()
                .endpoint(BuildConfig.LOGGER_URL)
                .apiKey(BuildConfig.LOGGER_KEY)
                .debugMode(BuildConfig.DEBUG)
                .build()
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
    implementation("com.github.devzucca.appLoggers:logger-core:v0.1.0-alpha.1")

    // Módulo de transporte Supabase (opcional — incluido en el core)
    // implementation("com.github.devzucca.appLoggers:logger-transport-supabase:v0.1.0-alpha.1")
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
| `appLogger.url` | String | `""` | Endpoint del backend (Supabase URL o URL propia) |
| `appLogger.anonKey` | String | `""` | API key de autenticación (anon key de Supabase) |
| `appLogger.debug` | Boolean | `false` | Modo debug: logs van a Logcat en vez de backend |
| `appLogger.logToConsole` | Boolean | `true` | Mostrar logs en Logcat (solo en debug) |
| `appLogger.batchSize` | Int | `20` | Número de eventos por batch antes de enviar (1-100) |
| `appLogger.flushIntervalSeconds` | Int | `30` | Intervalo máximo en segundos antes de flush automático (5-300) |
| `appLogger.maxStackTraceLines` | Int | `50` | Líneas máximas de stack trace (Mobile: 50, TV: 5) |
| `appLogger.lowStorageMode` | Boolean | `false` | Reduce buffer local y stack traces (para TV o dispositivos low-RAM) |
| `appLogger.verboseTransport` | Boolean | `false` | Log detallado de cada batch enviado (solo debug) |
| `appLogger.userId` | String | `null` | UUID anónimo del usuario (solo con consentimiento explícito) |

### 3.2 Ejemplo completo de `local.properties`

```properties
# AppLogger — NUNCA commitear este archivo
appLogger.url=https://tu-proyecto.supabase.co
appLogger.anonKey=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.tu_anon_key_aqui
appLogger.debug=true
appLogger.logToConsole=true
appLogger.batchSize=20
appLogger.flushIntervalSeconds=30
appLogger.maxStackTraceLines=50
appLogger.lowStorageMode=false
appLogger.verboseTransport=false
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

        buildConfigField("String",  "LOGGER_URL",            "\"${props["appLogger.url"] ?: ""}\"")
        buildConfigField("String",  "LOGGER_KEY",            "\"${props["appLogger.anonKey"] ?: ""}\"")
        buildConfigField("Boolean", "LOGGER_DEBUG_MODE",     "${props["appLogger.debug"] ?: false}")
        buildConfigField("Boolean", "LOGGER_CONSOLE_OUTPUT", "${props["appLogger.logToConsole"] ?: true}")
        buildConfigField("Int",     "LOGGER_BATCH_SIZE",     "${props["appLogger.batchSize"] ?: 20}")
        buildConfigField("Int",     "LOGGER_FLUSH_INTERVAL", "${props["appLogger.flushIntervalSeconds"] ?: 30}")
        buildConfigField("Int",     "LOGGER_MAX_STACK",      "${props["appLogger.maxStackTraceLines"] ?: 50}")
        buildConfigField("Boolean", "LOGGER_LOW_STORAGE",    "${props["appLogger.lowStorageMode"] ?: false}")
        buildConfigField("Boolean", "LOGGER_VERBOSE",        "${props["appLogger.verboseTransport"] ?: false}")
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
    echo "appLogger.url=$LOGGER_URL" >> local.properties
    echo "appLogger.anonKey=$LOGGER_KEY" >> local.properties
    echo "appLogger.debug=false" >> local.properties
    echo "appLogger.logToConsole=false" >> local.properties
    ./gradlew assembleRelease
```

### 3.5 Comportamiento según configuración

| `appLogger.debug` | `appLogger.logToConsole` | Resultado |
|---|---|---|
| `true` | `true` | Logs a Logcat + backend (doble envío) |
| `true` | `false` | Solo a Logcat (desarrollo sin red) |
| `false` | `true` | Solo a backend (producción con verbose) |
| `false` | `false` | Solo a backend (producción normal) |

Para desactivar completamente el envío de datos (modo offline-only), no configurar `appLogger.url` o dejarlo vacío. El SDK operará solo con SQLite local.

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
                .batchSize(BuildConfig.LOGGER_BATCH_SIZE)
                .flushIntervalSeconds(BuildConfig.LOGGER_FLUSH_INTERVAL)
                .build()
        )
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
3. Lanza un coroutine en `Dispatchers.IO` que procesa el canal.
4. Instala el `UncaughtExceptionHandler` (en modo producción).
5. Registra un `LifecycleObserver` en `ProcessLifecycleOwner` para flush automático en background.

Todo esto ocurre en `Dispatchers.IO`, **nunca en el hilo principal**.

---

## 5. Uso del Logger en la App

### 5.1 API Pública

```kotlin
// Debug — solo visible en modo debug (Logcat)
AppLoggerSDK.debug("TAG", "Mensaje de depuración")
AppLoggerSDK.debug("TAG", "Con datos extra", extra = mapOf("key" to "value"))

// Info — flujos normales de la app
AppLoggerSDK.info("PLAYER", "Playback started")
AppLoggerSDK.info("PLAYER", "Buffering", extra = mapOf("buffer_ms" to 500))

// Warn — comportamiento inesperado pero no fatal
AppLoggerSDK.warn("NETWORK", "Slow response detected", anomalyType = "HIGH_LATENCY")

// Error — fallos que el usuario probablemente nota
AppLoggerSDK.error("PAYMENT", "Transaction failed", throwable = exception)

// Critical — fallos que impiden el uso de la app
AppLoggerSDK.critical("AUTH", "Token refresh failed completely", throwable = exception)

// Metric — datos de performance
AppLoggerSDK.metric("screen_load_time", 1234.0, "ms", tags = mapOf("screen" to "HomeScreen"))
```

### 5.2 Qué loguear en cada nivel

| Nivel | Cuándo usarlo | Ejemplos |
|---|---|---|
| `debug` | Flujos internos durante desarrollo | Estado de variables, puntos de control |
| `info` | Eventos relevantes del flujo productivo | Usuario empieza reproducción, completa pago |
| `warn` | Anomalías recuperables | Reintento de red, degradación de calidad |
| `error` | Fallos que afectan al usuario | Fallo de API, error de parseo, timeout |
| `critical` | Fallos que bloquean la app | Corrupción de estado, fallo de inicialización |
| `metric` | Datos cuantitativos de performance | Tiempos de carga, uso de memoria, buffer |

### 5.3 Buenas Prácticas de Contenido

```kotlin
// ✅ Loguear contexto técnico, no datos del usuario
AppLoggerSDK.error("STREAM", "HLS segment fetch failed",
    extra = mapOf("segment_index" to 42, "cdn_region" to "us-east-1"))

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

## 6. Integración con gRPC

No llames al logger manualmente en cada llamada gRPC. Usa el interceptor proporcionado por el SDK:

### 6.1 Añadir el interceptor al canal gRPC

```kotlin
import io.grpc.ManagedChannelBuilder
import com.tuorg.applogger.interceptors.GrpcLoggingInterceptor

val channel = ManagedChannelBuilder
    .forAddress("api.tuapp.com", 443)
    .useTransportSecurity()
    .intercept(
        GrpcLoggingInterceptor(
            logger = AppLoggerSDK,
            latencyThresholdMs = 500  // Solo loguea si la llamada tarda más de 500ms
        )
    )
    .build()
```

### 6.2 El interceptor captura automáticamente

- Llamadas que fallan con cualquier `status` distinto de `OK`.
- Llamadas que superan el umbral de latencia configurado.
- **No** loguea llamadas exitosas dentro del tiempo normal (sin overhead).
- **No** loguea el contenido de los mensajes protobuf (solo el nombre del método y el status).

### 6.3 Configuración avanzada del interceptor

```kotlin
GrpcLoggingInterceptor(
    logger               = AppLoggerSDK,
    latencyThresholdMs   = 1000,   // Umbral de latencia en ms (default: 500)
    logSuccessfulCalls   = false,  // Log de llamadas exitosas — default: false
    excludeMethods       = setOf("HealthCheck/Check")  // Métodos a excluir del log
)
```

---

## 7. Integración con WebSockets

Envuelve tu `WebSocketListener` con el `LoggingWebSocketListener` del SDK:

### 7.1 Con OkHttp

```kotlin
import com.tuorg.applogger.interceptors.LoggingWebSocketListener

val originalListener = object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
        // Tu lógica de negocio
    }
    override fun onOpen(webSocket: WebSocket, response: Response) {
        // Tu lógica de negocio
    }
}

// Envolver con el listener del SDK
val loggingListener = LoggingWebSocketListener(
    delegate = originalListener,
    logger   = AppLoggerSDK,
    tag      = "WS_STREAM"
)

// Usar el listener envuelto al conectar
val request = Request.Builder().url("wss://tu-api.com/stream").build()
okHttpClient.newWebSocket(request, loggingListener)
```

### 7.2 Qué captura el LoggingWebSocketListener

| Evento | ¿Se loguea? | Nivel |
|---|---|---|
| `onOpen` | No | — |
| `onMessage` | No (nunca el contenido) | — |
| `onClosing` con código 1000 (normal) | No | — |
| `onClosing` con código ≠ 1000 (anormal) | Sí | `WARN` |
| `onFailure` | Sí | `ERROR` |
| `onClosed` | No | — |

---

## 8. Configuración para Android TV

En Android TV, el SDK detecta automáticamente la plataforma y ajusta su comportamiento. Sin embargo, hay configuraciones adicionales recomendadas.

### 8.1 El SDK detecta Android TV automáticamente

No es necesario indicar explícitamente que la app es para TV. El `PlatformDetector` interno usa:

```kotlin
// Detección automática — no requiere configuración manual
packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)  // → ANDROID_TV
```

### 8.2 Ajustes recomendados para TV en el Builder

```kotlin
AppLoggerSDK.initialize(
    context = this,
    config = AppLoggerConfig.Builder()
        .endpoint(BuildConfig.LOGGER_URL)
        .apiKey(BuildConfig.LOGGER_KEY)
        .debugMode(BuildConfig.LOGGER_DEBUG_MODE)
        // Ajustes específicos para TV
        .batchSize(5)                    // Batch pequeño — menos RAM
        .flushIntervalSeconds(60)        // Flush cada minuto (no cada 30s)
        .maxStackTraceLines(5)           // Solo primeras 5 líneas del stack trace
        .flushOnlyWhenIdle(true)         // Solo hace flush cuando la app está en pausa
        .build()
)
```

### 8.3 Comportamiento automático en TV

- **Buffer SQLite FIFO**: si no hay internet, los logs se almacenan localmente. Máximo 100 registros. Al llegar al 101, se descarta el más antiguo.
- **Stack traces truncados**: para ahorrar ancho de banda en conexiones de TV (frecuentemente limitadas a la velocidad del router doméstico).
- **Retry con WiFi**: el SDK solo reintenta el envío cuando detecta conexión WiFi o Ethernet, nunca agota el plan de datos de un router con límite.

---

## 9. Integración en iOS (Swift)

El SDK KMP se expone a iOS como XCFramework. La forma recomendada de consumo es Swift Package Manager.

### 9.1 Inicialización en Swift

```swift
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
```

### 9.2 Uso desde Swift

```swift
AppLoggerSDK.shared.info(tag: "PLAYER", message: "Playback started")
AppLoggerSDK.shared.error(tag: "PAYMENT", message: "Transaction failed")
AppLoggerSDK.shared.metric(name: "buffer_time", value: 420.0, unit: "ms")
```

---

## 10. Matriz de Compatibilidad de Plataformas

| Plataforma | Mínimo soportado | Recomendado | Notas |
|---|---|---|---|
| Android Mobile | API 23 (Android 6.0) | API 26+ | API 21 queda fuera por estabilidad operativa en dispositivos low-RAM |
| Android TV | API 23 (Android 6.0 TV) | API 28+ | Mismo sourceSet que Android Mobile (`androidMain`) |
| iOS | iOS 15 | iOS 16+ | Distribución via XCFramework / SwiftPM |
| JVM | JDK 11 | JDK 17 | Soporte para herramientas internas y runners |

---

## 11. App de Monitoreo Externo

> Documentación completa de la app de monitoreo en [monitoring-app.md](monitoring-app.md).

El SDK solo escribe datos. La visualización se realiza desde una aplicación externa separada que consulta Supabase con credenciales de solo lectura.

---

## 12. Modo Debug vs Producción

### 12.1 Diferencias de comportamiento

| Comportamiento | Debug (`debugMode = true`) | Producción (`debugMode = false`) |
|---|---|---|
| Destino de los logs | Logcat (consola Android) | Base de datos remota (Supabase) |
| `UncaughtExceptionHandler` | No se instala | Sí se instala |
| Flush automático | No (logs son inmediatos en Logcat) | Sí (batching + intervalo de tiempo) |
| Nivel de verbosidad | Todos los niveles | Solo INFO, WARN, ERROR, CRITICAL, METRIC |
| SQLite local (fallback) | No | Sí |

### 12.2 Control desde BuildConfig

```kotlin
// El valor de LOGGER_DEBUG_MODE viene de local.properties → build.gradle
// En desarrollo: appLogger.debug=true → consola
// En release: la variable no existe o es false → remoto

AppLoggerConfig.Builder()
    .debugMode(BuildConfig.LOGGER_DEBUG_MODE)
    // ...
```

---

## 13. User ID Opcional — Con Consentimiento

Por defecto, el `user_id` en todos los logs es `null`. Solo tiene sentido activarlo cuando el usuario ha dado consentimiento explícito para que sus logs sean correlacionables.

```kotlin
// Paso 1: El usuario acepta la política de privacidad
fun onPrivacyPolicyAccepted() {
    val anonymousId = getOrCreateAnonymousId()
    AppLoggerSDK.setAnonymousUserId(anonymousId)
}

// Paso 2: Generar/recuperar UUID anónimo (NO usar el ID real del usuario)
private fun getOrCreateAnonymousId(): String {
    val prefs = getSharedPreferences("app_logger_prefs", Context.MODE_PRIVATE)
    return prefs.getString("anon_user_id", null)
        ?: UUID.randomUUID().toString().also { id ->
            prefs.edit().putString("anon_user_id", id).apply()
        }
}

// Para revocar el consentimiento (derecho al olvido):
fun onPrivacyPolicyRevoked() {
    AppLoggerSDK.clearAnonymousUserId()
    // Opcionalmente: borrar datos del servidor
}
```

---

## 14. Preguntas Frecuentes

**¿La librería puede hacer que mi app crashee?**  
No. El SDK está diseñado para ser infalible: todas las excepciones internas son capturadas silenciosamente. Usa `Channel.trySend()` (never-blocking) para recibir eventos. Si el transporte falla, los logs se encoloan en SQLite o se descartan — la app nunca se ve afectada.

**¿Afecta al rendimiento de la UI?**  
No. Todas las operaciones de red y disco ocurren en `Dispatchers.IO`. El hilo principal solo ejecuta `Channel.trySend()`, que es una operación de microsegundos.

**¿Qué pasa si no hay internet?**  
Los logs se almacenan en SQLite local (buffer circular FIFO). Cuando vuelve la conectividad, el SDK los envía automáticamente.

**¿Puedo usar AppLogger sin Supabase?**  
Sí. La arquitectura basada en traits permite implementar un `LogTransport` personalizado para cualquier backend. Ver la arquitectura de `LogTransport` en [architecture.md](../paquete/architecture.md).

**¿Los logs de DEBUG se envían a producción?**  
No. En modo producción (`debugMode = false`), los eventos de nivel `DEBUG` son filtrados automáticamente y no abandonan el dispositivo.

**¿Cómo verifico que los logs están llegando a Supabase?**  
En modo debug puedes habilitar el logging verbose del SDK:
```kotlin
AppLoggerConfig.Builder()
    .verboseTransportLogging(true)  // Solo en debug — imprime en Logcat cada batch enviado
```
