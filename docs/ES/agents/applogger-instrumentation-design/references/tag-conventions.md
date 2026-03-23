# Tag Conventions

## Reglas fundamentales

1. Tags en `UPPER_SNAKE_CASE` estables — no cambiar entre versiones.
2. Mantener el conjunto de tags finito y documentado.
3. Evitar valores dinámicos en tags — poner contexto variable en `extra`.
4. Un tag por dominio funcional — no por clase ni por método.

---

## Tags estándar por dominio

| Tag | Dominio | Ejemplos de uso |
|---|---|---|
| `BOOT` | Startup y ciclo de vida de la app | Inicialización, crash en startup |
| `AUTH` | Autenticación y autorización | Login, logout, token refresh |
| `NETWORK` | Comunicación de red | Timeouts, retries, errores HTTP |
| `PAYMENT` | Pagos y transacciones | Checkout, fallo de cobro |
| `PLAYER` | Reproducción de media | Playback, buffering, frame drops |
| `DB` | Base de datos local | Queries, migraciones, corrupción |
| `STORAGE` | Almacenamiento de archivos | Lectura/escritura, espacio insuficiente |
| `PUSH` | Notificaciones push | Registro, recepción, acción |
| `ANALYTICS` | Eventos de negocio | Conversiones, funnels |
| `PERF` | Performance general | Métricas de UI, memoria |

---

## Formas de pasar el tag

### 1. Tag explícito — API base

```kotlin
AppLoggerSDK.error("PAYMENT", "Transaction failed", throwable = e)
AppLoggerSDK.info("PLAYER", "Playback started")
```

### 2. `loggerTag<T>()` — Para companion objects

Evita repetir el nombre de la clase como string literal:

```kotlin
class NetworkRepository(private val logger: AppLogger) {
    companion object {
        val TAG = loggerTag<NetworkRepository>()  // → "NetworkRepository"
    }

    fun fetch() = logger.logI(TAG, "Fetching data")
    fun onError(e: Exception) = logger.logE(TAG, "Fetch failed", throwable = e)
}
```

### 3. `Any.logTag()` — Tag inferido del nombre de clase

```kotlin
class PlayerController(private val logger: AppLogger) {
    fun onError(t: Throwable) {
        // Tag inferido automáticamente → "PlayerController"
        this.logE(logger, "Playback failed", throwable = t)
    }
}
```

### 4. `AppLogger.withTag()` — TaggedLogger con tag fijo

Elimina la repetición del tag en clases que siempre loguean bajo el mismo dominio:

```kotlin
class PaymentRepository(logger: AppLogger) {
    // Tag fijo para toda la clase
    private val log = logger.withTag("PaymentRepository")

    fun charge(amount: Double) {
        log.i("Charging card", extra = mapOf("amount_cents" to (amount * 100).toInt()))
        log.e("Charge failed", throwable = e)
        log.metric("charge_latency", 120.0, "ms")
    }
}
```

También acepta el receiver para inferir el tag:

```kotlin
class AuthViewModel(logger: AppLogger) {
    private val log = logger.withTag(this)  // tag = "AuthViewModel"
}
```

### 5. Shorthands `logD/I/W/E/C` — Con tag explícito

```kotlin
logger.logI("PLAYER", "Playback started")
logger.logW("NETWORK", "Timeout", anomalyType = "TIMEOUT")
logger.logE("AUTH", "Login failed", throwable = e)
```

---

## Definir tags como constantes

Para proyectos grandes, centralizar los tags evita typos y facilita búsquedas:

```kotlin
object LogTags {
    const val BOOT     = "BOOT"
    const val AUTH     = "AUTH"
    const val NETWORK  = "NETWORK"
    const val PAYMENT  = "PAYMENT"
    const val PLAYER   = "PLAYER"
    const val DB       = "DB"
    const val STORAGE  = "STORAGE"
    const val PUSH     = "PUSH"
    const val PERF     = "PERF"
}

// Uso:
AppLoggerSDK.error(LogTags.PAYMENT, "Transaction failed", throwable = e)
```

---

## Qué va en `tag` vs `extra`

| Información | Dónde |
|---|---|
| Dominio funcional (`PAYMENT`, `PLAYER`) | `tag` |
| Contexto variable (`content_id`, `amount`) | `extra` |
| Clasificación de anomalía (`TIMEOUT`, `RETRY`) | `anomalyType` (en `warn`) |
| Datos cuantitativos | `metric()` |

```kotlin
// ✅ Correcto
AppLoggerSDK.error(
    tag = "PLAYER",
    message = "Segment fetch failed",
    extra = mapOf(
        "content_id" to "movie_123",
        "segment_index" to 42,
        "cdn_region" to "us-east-1"
    )
)

// ❌ Incorrecto — tag dinámico
AppLoggerSDK.error(
    tag = "PLAYER_movie_123",   // tag con valor dinámico
    message = "Segment fetch failed"
)
```
