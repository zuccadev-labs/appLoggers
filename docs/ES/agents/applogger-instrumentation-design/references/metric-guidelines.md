# Metric Guidelines

## Reglas fundamentales

1. Siempre incluir unidad (`unit`).
2. Nombres de métricas en `snake_case` estables — no cambiar entre versiones.
3. Medir tiempos sensibles a p95 donde sea posible.
4. Agregar tags contextuales solo cuando ayudan al triage — no añadir tags de alta cardinalidad.

---

## API de métricas

### `AppLogger.metric()` — API base

```kotlin
AppLoggerSDK.metric(
    name = "screen_load_time",
    value = 320.0,
    unit = "ms",
    tags = mapOf("screen" to "HomeScreen")
)
```

### `AppLogger.logM()` — Shorthand

```kotlin
// Equivalente a metric() con unit="count" por defecto
logger.logM("frame_drop", 3.0, "count", mapOf("screen" to "PlayerScreen"))
logger.logM("api_latency", 450.0, "ms")
```

### `Any.logM()` — Con tag de fuente automático

```kotlin
class HomeScreen(private val logger: AppLogger) {
    fun onResume() {
        // Añade automáticamente "source" -> "HomeScreen" a los tags
        this.logM(logger, "screen_load_time", 320.0, "ms")
    }
}
```

### `AppLogger.timed{}` — Medir latencia automáticamente

```kotlin
// Mide el tiempo de ejecución del bloque y registra la métrica
val user = logger.timed("db_query_user", "ms", mapOf("table" to "users")) {
    userDao.findById(id)
}
```

### `Any.timed{}` — Con tag de fuente automático

```kotlin
class SearchRepository(private val logger: AppLogger) {
    suspend fun search(query: String) = this.timed(logger, "search_latency", "ms") {
        // "source" -> "SearchRepository" se añade automáticamente
        api.search(query)
    }
}
```

---

## Catálogo de métricas recomendadas

### Performance de pantallas

| Nombre | Unidad | Tags recomendados |
|---|---|---|
| `screen_load_time` | `ms` | `screen` |
| `screen_render_time` | `ms` | `screen` |
| `time_to_interactive` | `ms` | `screen` |

### Performance de red

| Nombre | Unidad | Tags recomendados |
|---|---|---|
| `api_latency` | `ms` | `endpoint`, `method` |
| `api_payload_size` | `bytes` | `endpoint` |
| `retry_count` | `count` | `endpoint` |

### Performance de reproducción (media apps)

| Nombre | Unidad | Tags recomendados |
|---|---|---|
| `startup_duration` | `ms` | `cold_start` |
| `buffer_time` | `ms` | `content_id`, `quality` |
| `frame_drop` | `count` | `screen`, `codec` |
| `bitrate` | `kbps` | `content_id`, `quality` |

### Performance de base de datos

| Nombre | Unidad | Tags recomendados |
|---|---|---|
| `db_query_time` | `ms` | `table`, `operation` |
| `db_write_time` | `ms` | `table` |

### Negocio

| Nombre | Unidad | Tags recomendados |
|---|---|---|
| `checkout_duration` | `ms` | `payment_method` |
| `search_latency` | `ms` | `query_type` |
| `content_load_time` | `ms` | `content_type`, `cdn_region` |

---

## Tags — buenas prácticas

```kotlin
// ✅ Tags de baja cardinalidad — valores finitos y conocidos
tags = mapOf(
    "screen" to "HomeScreen",
    "method" to "GET",
    "cold_start" to "true"
)

// ❌ Tags de alta cardinalidad — valores únicos por evento
tags = mapOf(
    "user_id" to userId,        // millones de valores únicos
    "request_id" to requestId,  // UUID único por request
    "timestamp" to timestamp    // nunca en tags
)
```

Los tags de alta cardinalidad explotan el número de series en Supabase y dificultan las queries de agregación. Poner valores únicos en `extra`, no en `tags`.

---

## Ejemplo completo — clase con métricas

```kotlin
class ContentRepository(private val logger: AppLogger) {

    private val log = logger.withTag("ContentRepository")

    suspend fun loadContent(id: String): Content {
        return this.timed(logger, "content_load_time", "ms", mapOf("content_id" to id)) {
            val result = this.logCatching(logger, "load content $id") {
                api.getContent(id)
            }
            result ?: throw ContentNotFoundException(id)
        }
    }

    fun trackQuality(bitrate: Int, contentId: String) {
        log.metric("bitrate", bitrate.toDouble(), "kbps", mapOf("content_id" to contentId))
    }

    fun trackBuffering(durationMs: Long) {
        log.metric("buffer_time", durationMs.toDouble(), "ms")
    }
}
```
