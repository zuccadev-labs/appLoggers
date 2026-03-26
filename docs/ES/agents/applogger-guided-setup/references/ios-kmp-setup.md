# iOS KMP Setup Reference

## Scope

This SDK uses a Kotlin Multiplatform-first iOS flow. The entry point is `AppLoggerIos.shared` defined in `iosMain`. Do not use `AppLoggerSDK` in iOS KMP modules — that singleton is Android-only.

## Dependency setup

In the shared KMP module:

```kotlin
kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Reemplazar <latest-version> con la última release: https://github.com/zuccadev-labs/appLoggers/releases
                implementation("com.github.zuccadev-labs.appLoggers:logger-core:<latest-version>")
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("com.github.zuccadev-labs.appLoggers:logger-transport-supabase:<latest-version>")
            }
        }
    }
}
```

## Debug mode via Info.plist (sin cambiar código)

El SDK lee `APPLOGGER_DEBUG` de `Info.plist` automáticamente. Si está presente y es `"true"`, activa `isDebugMode = true` sin necesidad de cambiar código:

```xml
<!-- Info.plist -->
<key>APPLOGGER_DEBUG</key>
<string>true</string>
```

Para producción, omitir la clave o establecerla en `"false"`. El SDK la lee en `initialize()` y aplica el flag antes de construir el pipeline.

## Initialization pattern

Preferred initialization point: Kotlin code in `iosMain`.

```kotlin
import com.applogger.core.AppLoggerConfig
import com.applogger.core.AppLoggerIos
import com.applogger.core.LogMinLevel
import com.applogger.transport.supabase.SupabaseTransport

object IosLoggerBootstrap {
    fun initialize(url: String, anonKey: String, debugMode: Boolean = false) {
        val config = AppLoggerConfig.Builder()
            .endpoint(url)
            .apiKey(anonKey)
            .environment("production")          // "production" | "staging" | "development"
            .debugMode(debugMode)
            .consoleOutput(debugMode)
            .minLevel(LogMinLevel.INFO)          // descarta DEBUG en producción
            .batchSize(20)
            .flushIntervalSeconds(30)
            .build()

        val transport = SupabaseTransport(
            endpoint = url,
            apiKey = anonKey
        )

        AppLoggerIos.shared.initialize(
            config = config,
            transport = transport
        )
    }
}
```

Compile guard:
1. Do not use `AppLoggerSDK` in iOS KMP modules.
2. Keep initialization in Kotlin (`iosMain` or shared Kotlin bootstrap), not in ad-hoc Swift host wrappers.

## Session and user identity

```kotlin
// Al hacer login — nueva sesión + user ID
AppLoggerIos.shared.setAnonymousUserId(anonymousUUID)
AppLoggerIos.shared.newSession()

// Al hacer logout — limpiar identidad
AppLoggerIos.shared.clearAnonymousUserId()
AppLoggerIos.shared.newSession()

// Global extra — adjunta a todos los eventos posteriores
AppLoggerIos.shared.addGlobalExtra("ab_test", "checkout_v2")
AppLoggerIos.shared.removeGlobalExtra("ab_test")
AppLoggerIos.shared.clearGlobalExtra()
```

## Device fingerprint

El SDK calcula automáticamente un fingerprint SHA-256 pseudonymizado del dispositivo al inicializar:

```kotlin
// Fórmula: sha256Hex("$idfv:$bundleId")
// Donde idfv = UIDevice.currentDevice.identifierForVendor
// y bundleId = NSBundle.mainBundle.bundleIdentifier

val fingerprint = AppLoggerIos.shared.getDeviceFingerprint()
// → "a3f8c2e1..." (64 hex chars)
```

El fingerprint se inyecta automáticamente en `extra.device_fingerprint` de cada evento. El CLI puede filtrar con `--fingerprint`.

## Remote config

Polling periódico a la tabla `device_remote_config` de Supabase para ajustar el comportamiento del SDK sin redesplegar:

```kotlin
// Iniciar polling (default: 300 segundos)
AppLoggerIos.shared.startRemoteConfig(intervalSeconds = 300)

// El SDK aplica automáticamente:
// - minLevel → descarta eventos por debajo del nivel configurado
// - sampling → probabilidad de envío (0.0 a 1.0)
// - debugMode → activa/desactiva logs locales
// - tagFilter → solo envía tags específicos

// Detener polling
AppLoggerIos.shared.stopRemoteConfig()
```

Implementación interna: `NSURLSession.sharedSession.dataTaskWithRequest` con `dispatch_semaphore` para fetch síncrono dentro de la coroutine del polling.

## Beta tester

Marcar un dispositivo como beta tester para filtrado dedicado:

```kotlin
// Activar — inyecta beta_tester_email en global extras
AppLoggerIos.shared.setBetaTester("tester@company.com")

// Desactivar
AppLoggerIos.shared.clearBetaTester()
```

El CLI puede filtrar beta testers con `--extra-key beta_tester_email`.

## Consent management

Controla el nivel de procesamiento de datos. Persiste en `NSUserDefaults`.

```kotlin
import com.applogger.core.ConsentLevel

// Telemetría completa — requiere opt-in explícito del usuario
AppLoggerIos.shared.setConsent(ConsentLevel.MARKETING)

// Solo métricas y performance — requiere T&C aceptados
AppLoggerIos.shared.setConsent(ConsentLevel.PERFORMANCE)

// Solo errores críticos, anonimizados — sin opt-in requerido
AppLoggerIos.shared.setConsent(ConsentLevel.STRICT)

// Leer nivel actual
val level: ConsentLevel = AppLoggerIos.shared.getConsent()
```

El SDK filtra eventos según el nivel activo:
- `STRICT`: solo CRITICAL/ERROR pasan, `user_id` suprimido, `device_id` pseudonimizado
- `PERFORMANCE`: + METRIC y WARN pasan
- `MARKETING`: todos los eventos pasan (default)

## Distributed tracing

Correlacionar eventos entre dispositivos (mobile → TV → backend):

```kotlin
// Establecer trace ID (compartir entre dispositivos via API)
AppLoggerIos.shared.setTraceId("order-abc-123")

// El trace_id se adjunta a cada evento posterior
// Query en CLI: --extra-key trace_id --extra-value "order-abc-123"

// Limpiar
AppLoggerIos.shared.clearTraceId()
```

## Breadcrumbs

Trail de navegación adjunto a cada evento posterior:

```kotlin
// Registrar breadcrumbs al navegar — signature completa:
// recordBreadcrumb(action: String, screen: String? = null, metadata: Map<String, String>? = null)
AppLoggerIos.shared.recordBreadcrumb("tap_play")
AppLoggerIos.shared.recordBreadcrumb("screen_enter", screen = "PlayerScreen")
AppLoggerIos.shared.recordBreadcrumb("api_call", screen = "HomeScreen", metadata = mapOf("endpoint" to "/feed"))

// Los breadcrumbs se incluyen en extra.breadcrumbs de cada evento
// Útil para debugging: ver qué pantallas visitó el usuario antes del error
```

## Scoped logger (TaggedLogger — withTag)

Logger con tag fijo para un módulo o clase. Usa `withTag()` — extension function en `AppLogger`:

```kotlin
// Crear logger con tag fijo
val playerLog = AppLoggerIos.shared.withTag("PLAYER")
playerLog.i("Playback started")
playerLog.w("Buffer low", anomalyType = "buffer_underrun")
playerLog.e("Playback failed", throwable = e)
// Todos los eventos usan tag = "PLAYER"

// withTag también acepta receiver para inferir el tag del nombre de clase
class PlayerController {
    private val log = AppLoggerIos.shared.withTag(this)  // tag = "PlayerController"
}
```

## Scoped logger (ScopedAppLogger — newScope)

Logger que pre-inyecta atributos contextuales en todos los eventos:

```kotlin
// Scope para una sesión de reproducción específica
val sessionLog = AppLoggerIos.shared.newScope(
    "content_id" to "movie_123",
    "quality" to "4K",
    "drm" to "fairplay"
)
sessionLog.error("PLAYER", "Stall detected", stallException)
// → event incluye content_id, quality, drm automáticamente

// Child scope — hereda atributos del padre y añade más
val segLog = sessionLog.childScope("segment_id" to "seg_042", "offset_ms" to 72000)
segLog.warn("SEGMENT", "Buffer underrun", anomalyType = "buffer_underrun")

// Combinar scope + tag fijo — patrón recomendado para clases
val log = AppLoggerIos.shared.newScope("content_id" to contentId).withTag("PlayerController")
log.e("Codec error", throwable = e)
```

## Session variant

Etiquetar la sesión para A/B testing:

```kotlin
// setSessionVariant acepta String? — pasar null para limpiar
AppLoggerIos.shared.setSessionVariant("checkout_v2")
// El variant se adjunta como campo top-level en cada evento

AppLoggerIos.shared.setSessionVariant(null)  // limpiar
```

## Coroutine exception handler

Capturar excepciones no manejadas en coroutines:

```kotlin
// exceptionHandler es una propiedad de AppLoggerIos — NO una importación separada
val scope = CoroutineScope(
    Dispatchers.Default + AppLoggerIos.shared.exceptionHandler
)
// Las excepciones no manejadas se loguean como CRITICAL automáticamente
```

## Flush en background

iOS no tiene lifecycle observer automático. Llamar `flush()` manualmente al entrar en background:

```kotlin
// En el delegate de ciclo de vida de la app (iosMain)
AppLoggerIos.shared.flush()
```

## local.properties policy

If `local.properties` is present:

1. Verify AppLogger keys first.
2. Add only missing AppLogger keys.
3. Keep all unrelated variables untouched.

Suggested keys:

```properties
APPLOGGER_URL=https://YOUR-PROJECT.supabase.co
APPLOGGER_ANON_KEY=YOUR_ANON_KEY
APPLOGGER_DEBUG=false
```

## Minimal verification

```kotlin
AppLoggerIos.shared.info("BOOT", "AppLogger initialized")

val health = AppLoggerHealth.snapshot()
println("initialized=${health.isInitialized}, transport=${health.transportAvailable}, buffered=${health.bufferedEvents}")
```

## OperationTrace — Spans de performance

Mide el tiempo de cualquier operación en código commonMain o iosMain:

```kotlin
import com.applogger.core.AppLoggerIos
import com.applogger.core.OperationTrace

// Abrir span
val trace = AppLoggerIos.shared.startTrace("sync_operation", "source" to "cloud")

try {
    syncManager.sync()
    trace.end()
    // → emite métrica trace.sync_operation con duration_ms
} catch (e: Exception) {
    trace.endWithError(e, failureReason = "sync_failed")
    // → emite evento ERROR Trace.sync_operation
}
```

Fluent API:
```kotlin
AppLoggerIos.shared.startTrace("upload", "filename" to filename)
    .bytes(fileBytes.size.toLong())
    .withTimeout(30_000)
    .end()
// → emite trace.upload con throughput_mbps calculado
```

Ver skill `applogger-advanced-features` para el API completa.

## DataBudgetManager — Límite diario de datos

```kotlin
val config = AppLoggerConfig.Builder()
    .endpoint(url)
    .apiKey(anonKey)
    .environment("production")
    .dailyDataLimitMb(50)   // 50 MB/día. 0 (default) = sin límite
    .build()
AppLoggerIos.shared.initialize(config = config, transport = transport)
```

El límite persiste en `NSUserDefaults` entre reinicios. En WiFi, el límite efectivo es `50 × 2 = 100 MB`.
ERROR y CRITICAL nunca se descartan independientemente del límite.

## Guardrails

1. Do not use `AppLoggerSDK` in iOS — use `AppLoggerIos.shared`.
2. Do not propose native Swift host setup outside KMP. The project is 100% KMP — no Swift/Ruby implementation files.
3. Keep secrets outside versioned files.
4. Always set `environment` to distinguish production from staging data.
5. Call `flush()` manually before the app enters background.
6. Always call `setConsent(true)` before expecting events to be sent.
7. Remote config interval must be between 30 and 3600 seconds.
8. Device fingerprint is SHA-256 pseudonymized — never store raw device identifiers.
9. Always call `trace.end()` or `trace.endWithError()` — unclosed spans emit nothing.
10. `dailyDataLimitMb = 0` disables the budget (default). Persistence via NSUserDefaults survives app restarts within the same UTC day.
