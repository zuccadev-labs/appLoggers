# Event Taxonomy

## Niveles y cuándo usarlos

| Nivel | Cuándo | Ejemplos |
|---|---|---|
| `debug` | Flujos internos durante desarrollo — suprimido en producción | Estado de variables, puntos de control |
| `info` | Eventos relevantes del flujo productivo | Usuario inicia reproducción, completa pago |
| `warn` | Anomalías recuperables | Reintento de red, degradación de calidad |
| `error` | Fallos que el usuario probablemente nota | Fallo de API, error de parseo, timeout |
| `critical` | Fallos que bloquean la app | Corrupción de estado, fallo de inicialización |
| `metric` | Datos cuantitativos de performance | Tiempos de carga, uso de memoria, buffer |

---

## Taxonomía mínima recomendada

### 1. Startup lifecycle

```kotlin
// App inicializada correctamente
AppLoggerSDK.info("BOOT", "Application started", extra = mapOf(
    "cold_start" to true,
    "app_version" to BuildConfig.VERSION_NAME
))

// SDK inicializado
AppLoggerSDK.info("BOOT", "AppLogger initialized", extra = mapOf(
    "transport" to "supabase",
    "environment" to "production"
))

// Fallo crítico en startup
AppLoggerSDK.critical("BOOT", "Database initialization failed", throwable = e)
```

### 2. Authentication outcomes

```kotlin
// Login exitoso
AppLoggerSDK.info("AUTH", "User authenticated", extra = mapOf(
    "method" to "oauth",
    "provider" to "google"
))

// Login fallido
AppLoggerSDK.warn("AUTH", "Authentication failed", throwable = e, anomalyType = "AUTH_FAILURE", extra = mapOf(
    "attempt" to 2,
    "method" to "password"
))

// Token expirado
AppLoggerSDK.warn("AUTH", "Token expired — refreshing", anomalyType = "TOKEN_EXPIRED")

// Refresh fallido — crítico
AppLoggerSDK.critical("AUTH", "Token refresh failed completely", throwable = e)
```

### 3. Network anomalies

```kotlin
// Timeout
AppLoggerSDK.warn("NETWORK", "Request timeout", throwable = e, anomalyType = "TIMEOUT", extra = mapOf(
    "endpoint" to "/api/v1/content",
    "timeout_ms" to 5000
))

// Retry
AppLoggerSDK.warn("NETWORK", "Retrying request", anomalyType = "RETRY", extra = mapOf(
    "attempt" to 2,
    "max_attempts" to 3
))

// Error de servidor
AppLoggerSDK.error("NETWORK", "Server error", extra = mapOf(
    "status_code" to 503,
    "endpoint" to "/api/v1/content"
))
```

### 4. Critical business failures

```kotlin
// Fallo de pago
AppLoggerSDK.error("PAYMENT", "Transaction failed", throwable = e, extra = mapOf(
    "error_code" to "INSUFFICIENT_FUNDS",
    "amount_cents" to 9900
))

// Fallo de reproducción
AppLoggerSDK.error("PLAYER", "Playback failed", throwable = e, extra = mapOf(
    "content_id" to "movie_123",
    "codec" to "h264",
    "segment_index" to 42
))
```

### 5. Core performance metrics

```kotlin
// Tiempo de carga de pantalla
AppLoggerSDK.metric("screen_load_time", 320.0, "ms", tags = mapOf("screen" to "HomeScreen"))

// Latencia de API
AppLoggerSDK.metric("api_latency", 450.0, "ms", tags = mapOf(
    "endpoint" to "/api/v1/content",
    "method" to "GET"
))

// Tiempo de startup
AppLoggerSDK.metric("startup_duration", 1200.0, "ms", tags = mapOf("cold_start" to "true"))

// Frame drops
AppLoggerSDK.metric("frame_drop", 3.0, "count", tags = mapOf("screen" to "PlayerScreen"))
```

---

## Uso con extension functions

```kotlin
class PlayerController(private val logger: AppLogger) {

    // Tag inferido automáticamente del nombre de clase
    fun onPlaybackStarted(contentId: String) =
        this.logI(logger, "Playback started", extra = mapOf("content_id" to contentId))

    fun onPlaybackFailed(t: Throwable, contentId: String) =
        this.logE(logger, "Playback failed", throwable = t, extra = mapOf("content_id" to contentId))

    // Medir latencia automáticamente
    suspend fun loadContent(id: String) = this.timed(logger, "content_load_time", "ms") {
        contentRepository.load(id)
    }

    // Capturar excepciones sin try/catch
    suspend fun submitRating(rating: Int) = this.logCatching(logger, "submit rating") {
        api.submitRating(rating)
    }
}
```

---

## Buenas prácticas de contenido

```kotlin
// ✅ Contexto técnico, no datos del usuario
AppLoggerSDK.error("STREAM", "HLS segment fetch failed", extra = mapOf(
    "segment_index" to 42,
    "cdn_region" to "us-east-1",
    "retry_count" to 2
))

// ✅ Tags consistentes en todo el módulo
object LogTags {
    const val PLAYER   = "PLAYER"
    const val NETWORK  = "NETWORK"
    const val AUTH     = "AUTH"
    const val PAYMENT  = "PAYMENT"
    const val BOOT     = "BOOT"
}

// ❌ Nunca loguear datos del usuario
AppLoggerSDK.error("AUTH", "Error for user: ${user.email}")  // INCORRECTO

// ❌ Nunca loguear tokens o credenciales
AppLoggerSDK.debug("AUTH", "Token: $accessToken")  // NUNCA
```

### 6. Beta tester events (optional)

```kotlin
// Activar modo beta tester (email del auth flow del desarrollador)
AppLoggerSDK.setBetaTester("tester@example.com")

// Evento normal — automáticamente incluye is_beta_tester=true y beta_tester_email
AppLoggerSDK.error("PLAYER", "Playback failed on beta build", throwable = e, extra = mapOf(
    "content_id" to "movie_123",
    "build_type" to "beta"
))

// Desactivar modo beta tester
AppLoggerSDK.clearBetaTester()
```

El extra `is_beta_tester` y `beta_tester_email` se inyectan como global extras — aparecen en TODOS los eventos mientras el modo esté activo.

En Supabase, el trigger `trg_correlate_beta_tester` auto-correlaciona el email del frontend al backend en el mismo dispositivo via `device_id → email` mapping en `beta_tester_devices`.

### 7. Remote config events (automáticos)

```kotlin
// El SDK loguea automáticamente cuando aplica remote config
// Tag: REMOTE_CONFIG, Level: DEBUG
// No es necesario instrumentar manualmente
```

Para consultar eventos filtrados por remote config:

```bash
# Filtrar beta testers en CLI
apploggers telemetry query --source logs --extra-key is_beta_tester --extra-value true --output json

# Filtrar por device fingerprint
apploggers telemetry query --source logs --fingerprint "abc123sha256" --output json
```
