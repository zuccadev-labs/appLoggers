# OperationTrace — Guía de referencia

## Qué es

`OperationTrace` es la API de spans del SDK. Mide el tiempo de cualquier operación y emite automáticamente una métrica estructurada al cerrar el span.

## Entry point

`AppLogger.startTrace` es una extension function definida en `commonMain`:

```kotlin
fun AppLogger.startTrace(operation: String, vararg attributes: Pair<String, Any>): OperationTrace
```

Disponible en:
- **Android**: `AppLoggerSDK.startTrace(...)`
- **iOS KMP**: `AppLoggerIos.shared.startTrace(...)`

## Ciclo de vida de un span

```
startTrace() → [tag/bytes/withTimeout opcionales] → end() o endWithError()
```

Solo se puede cerrar una vez. Llamadas adicionales a `end()` / `endWithError()` son no-op.

## API completa

### `startTrace(operation, vararg attributes)`

```kotlin
// Sin atributos iniciales
val trace = AppLoggerSDK.startTrace("screen_load")

// Con atributos iniciales (vararg de Pair<String, Any>)
val trace = AppLoggerSDK.startTrace("video_load",
    "content_id" to contentId,
    "quality" to "4K"
)
```

### `tag(key, value): OperationTrace`

Añade o actualiza un atributo durante la operación. Fluent — devuelve `this`.

```kotlin
trace.tag("cdn_region", "us-east-1").tag("retry_count", 2)
```

### `bytes(count: Long): OperationTrace`

Registra bytes transferidos. Habilita cálculo automático de `throughput_mbps` en `end()`.

```kotlin
trace.bytes(responseBody.size.toLong())
```

### `withTimeout(ms: Long): OperationTrace`

Establece un deadline. Si `end()` se llama después del deadline, el span se emite con `timed_out=true`.

```kotlin
trace.withTimeout(5_000)  // 5 segundos
```

### `isExpired: Boolean`

Propiedad de solo lectura. `true` si el timeout fue establecido y ya expiró.

```kotlin
if (trace.isExpired) {
    trace.endWithError(TimeoutException(), failureReason = "timeout")
}
```

### `end(extraAttributes?)`

Cierra con éxito. Emite métrica `trace.<operationName>` con `unit = "ms"`.

```kotlin
trace.end()
trace.end(mapOf("response_code" to 200, "cache_hit" to false))
```

### `endWithError(error, failureReason?, extraAttributes?)`

Cierra con error. Emite log ERROR-level (no una métrica).

```kotlin
trace.endWithError(e)
trace.endWithError(e, failureReason = "rate_limited", extraAttributes = mapOf("retry_after" to 60))
```

---

## Campos emitidos por `end()`

La métrica `trace.<operationName>` lleva estos tags:

| Campo | Tipo | Siempre presente | Descripción |
|---|---|---|---|
| `duration_ms` | Long | ✅ | Duración en milisegundos |
| `trace_id` | String | ✅ | UUID del span |
| `success` | Boolean | ✅ | `true` si no hubo timeout ni error |
| `timed_out` | Boolean | Solo si timeout | `true` cuando duración ≥ `timeoutMs` |
| `timeout_ms` | Long | Solo si timeout | Valor configurado en `withTimeout` |
| `bytes_transferred` | Long | Solo si `bytes()` llamado | Bytes registrados |
| `throughput_mbps` | Double | Solo si `bytes()` y `duration > 0` | MB/s calculado automáticamente |
| Atributos personalizados | Any | Si se añadieron | Via `tag()` o `startTrace(vararg)` o `extraAttributes` |

## Campos emitidos por `endWithError()`

Evento ERROR-level con estos campos:

| Campo | Descripción |
|---|---|
| `tag` | `"Trace.<operationName>"` |
| `message` | `"trace.<operationName> failed after <duration>ms"` |
| `throwable` | La excepción pasada |
| `extra.duration_ms` | Duración en ms |
| `extra.trace_id` | UUID del span |
| `extra.success` | `false` |
| `extra.failure_reason` | Si se pasó `failureReason` |
| `extra.timed_out` | `true` si expiró el timeout |

---

## Patrones de referencia

### Patrón 1 — Operación suspendida con try/finally

```kotlin
suspend fun loadContent(id: String): Content {
    val trace = AppLoggerSDK.startTrace("content_load", "content_id" to id)
    return try {
        val content = contentApi.fetch(id)
        trace.end(mapOf("content_type" to content.type))
        content
    } catch (e: Exception) {
        trace.endWithError(e, failureReason = "fetch_failed")
        throw e
    }
}
```

### Patrón 2 — Con timeout explícito

```kotlin
suspend fun callApi(url: String): Response {
    val trace = AppLoggerSDK.startTrace("api_call", "endpoint" to url)
        .withTimeout(10_000)

    val response = httpClient.get(url)

    return if (trace.isExpired) {
        trace.endWithError(TimeoutException("Response arrived too late"), failureReason = "timeout")
        throw TimeoutException()
    } else {
        trace.end(mapOf("status_code" to response.statusCode))
        response
    }
}
```

### Patrón 3 — Upload con throughput

```kotlin
suspend fun uploadFile(file: ByteArray, name: String) {
    val trace = AppLoggerSDK.startTrace("file_upload", "filename" to name)
    trace.bytes(file.size.toLong())

    try {
        storageApi.upload(file)
        trace.end()
        // → emite trace.file_upload con throughput_mbps calculado
    } catch (e: Exception) {
        trace.endWithError(e, failureReason = "upload_failed")
    }
}
```

### Patrón 4 — iOS KMP (mismo patrón, distinto entry point)

```kotlin
// En iosMain o commonMain con expect/actual
val trace = AppLoggerIos.shared.startTrace("sync_operation", "source" to "cloud")
try {
    syncManager.sync()
    trace.end()
} catch (e: Exception) {
    trace.endWithError(e)
}
```

---

## Queries CLI para spans

```bash
# Todos los spans de una operación
apploggers telemetry query --source metrics --name trace.content_load --output json

# Spans lentos (> 2s) — filtrar en cliente por duration_ms
apploggers telemetry query --source metrics --name trace.content_load \
    --aggregate day --output json

# Spans fallidos (vienen como logs ERROR)
apploggers telemetry query --source logs \
    --tag "Trace.content_load" \
    --severity error \
    --output json

# Buscar por trace_id específico (correlación entre spans)
apploggers telemetry query --source metrics \
    --extra-key trace_id \
    --extra-value "uuid-del-span" \
    --output json
```

---

## Acceptance gate #15

El gate de aceptación #15 verifica:

```bash
# Emitir un span y verificar que aparece la métrica trace.*
apploggers telemetry query --source metrics --name trace.test_operation --limit 1 --output json
```

Debe retornar un objeto con `duration_ms` presente en los tags.
