# Scoped Logger — Guía de referencia

## Dos tipos de logger con scope

| Tipo | Crea | Para qué sirve |
|---|---|---|
| `TaggedLogger` | `logger.withTag("TAG")` | Tag fijo para todas las llamadas de una clase |
| `ScopedAppLogger` | `logger.newScope("key" to val)` | Atributos pre-inyectados en todos los eventos |

Ambos son `AppLogger` — aceptan todas las extension functions (`logD/I/W/E/C`, `timed`, `logCatching`, `startTrace`).

---

## TaggedLogger — `withTag()`

Crea un wrapper que fija el `tag` para todas las llamadas. Elimina repetir el tag en cada log.

### Crear

```kotlin
// Por string literal
val log = AppLoggerSDK.withTag("PaymentRepository")
val log = AppLoggerIos.shared.withTag("PaymentRepository")

// Por receiver (infiere tag del nombre de clase)
class AuthViewModel(logger: AppLogger) {
    private val log = logger.withTag(this)  // tag = "AuthViewModel"
}
```

### API de TaggedLogger

```kotlin
val log = AppLoggerSDK.withTag("PLAYER")

log.d("Playback started")                           // debug
log.i("Content loaded", extra = mapOf("id" to id)) // info
log.w("Buffer low", anomalyType = "buffer_low")     // warn
log.e("Playback failed", throwable = e)             // error
log.c("Critical crash", throwable = e)              // critical
log.metric("frame_drop", 3.0, "count")              // metric
log.flush()
```

### Ejemplo completo — clase con TaggedLogger

```kotlin
class ContentRepository(logger: AppLogger) {
    private val log = logger.withTag("ContentRepository")

    suspend fun fetch(id: String): Content {
        return log.timed("content_fetch_time", "ms", mapOf("content_id" to id)) {
            log.logCatching(this, "fetch content $id") {
                api.getContent(id)
            } ?: throw ContentNotFoundException(id)
        }
    }
}
```

---

## ScopedAppLogger — `newScope()`

Crea un logger que pre-inyecta atributos fijos en todos los eventos. Ideal cuando tienes contexto de sesión/operación que quieres adjuntar a muchos eventos.

### Crear

```kotlin
// Android
val log = AppLoggerSDK.newScope(
    "content_id" to contentId,
    "user_tier"  to "premium",
    "quality"    to "4K"
)

// iOS KMP
val log = AppLoggerIos.shared.newScope(
    "content_id" to contentId,
    "user_tier"  to "premium"
)
```

### Usar

```kotlin
log.error("PLAYER", "Stall detected", stallException)
// → event.extra incluye content_id, user_tier, quality + throwable automáticamente

log.metric("buffer_time", 2500.0, "ms")
// → metric.tags incluye content_id, user_tier, quality
```

### Priority chain (menor → mayor)

```
globalExtra → scope attributes → per-call extra
```

Per-call `extra` siempre gana. Scope overrides globalExtra en colisión de keys.

```kotlin
// Scope tiene "quality" = "4K"
log.error("PLAYER", "Stall", extra = mapOf("quality" to "720p"))
// → extra.quality = "720p" (per-call wins)
```

### childScope — herencia de atributos

```kotlin
val sessionLog = AppLoggerSDK.newScope("session_id" to sessionId)

// Child hereda session_id y añade content_id
val playerLog = sessionLog.childScope("content_id" to contentId)
playerLog.error("PLAYER", "Codec error", e)
// → extra incluye session_id + content_id

// Child de child — herencia transitiva
val segLog = playerLog.childScope("segment_id" to segId)
// → extra incluye session_id + content_id + segment_id
```

Child values override parent values en colisión de keys.

### Consent override por scope

```kotlin
// Este scope siempre requiere mínimo PERFORMANCE
val perfLog = AppLoggerSDK.newScope(
    "component" to "network_monitor",
    consentLevel = ConsentLevel.PERFORMANCE
)
perfLog.info("NETWORK", "Latency: 120ms")
// → pasa aunque el nivel global sea PERFORMANCE (no solo MARKETING)
```

### Combinar ScopedAppLogger + withTag

```kotlin
// Patrón recomendado para clases que siempre tienen un scope y un tag fijo
val log = AppLoggerSDK
    .newScope("content_id" to contentId, "quality" to "4K")
    .withTag("PlayerController")

log.e("Stall detected", throwable = e)
// tag = "PlayerController", extra incluye content_id y quality
```

---

## `loggerTag<T>()` — Para companion objects

Evita strings duplicados cuando el tag coincide con el nombre de la clase:

```kotlin
class NetworkRepository(private val logger: AppLogger) {
    companion object {
        val TAG = loggerTag<NetworkRepository>()  // → "NetworkRepository"
    }

    fun fetch() = logger.logI(TAG, "Fetching data")
    fun onError(e: Exception) = logger.logE(TAG, "Fetch failed", throwable = e)
}
```

---

## Cuándo usar qué

| Situación | Solución |
|---|---|
| Clase que siempre loguea bajo el mismo tag | `logger.withTag("MyClass")` |
| Operación que tiene contexto fijo (content_id, session_id) | `logger.newScope("key" to val)` |
| Scope + tag fijo en una clase | `logger.newScope(...).withTag("MyClass")` |
| Contexto global para toda la app | `AppLoggerSDK.addGlobalExtra("key", val)` |
| Jerarquía de contextos (sesión → reproducción → segmento) | `newScope` + `childScope` |
| Datos compartidos en companion object | `loggerTag<T>()` |
