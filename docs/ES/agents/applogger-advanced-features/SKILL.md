---
name: applogger-advanced-features
description: Use advanced AppLogger SDK features beyond basic logging — OperationTrace spans, DataBudgetManager, consent management, distributed tracing, breadcrumbs, session variants, and coroutine exception handling. Use when the user needs performance instrumentation, bandwidth control, or cross-device correlation.
---

# AppLogger Advanced Features

## When to use this skill

Use this skill when the integration is already working and the user needs:

1. Span-based performance tracing (`OperationTrace`).
2. Daily data budget control (`DataBudgetManager` via `dailyDataLimitMb`).
3. Consent management (`setConsent`).
4. Cross-device correlation (`setTraceId`).
5. Navigation breadcrumbs (`recordBreadcrumb`).
6. A/B test variant tagging (`setVariant`).
7. Unhandled coroutine exception capture (`AppLoggerExceptionHandler`).

Examples:

1. "Instrumenta el tiempo de carga de video con spans"
2. "Limita cuántos datos envía el SDK por día"
3. "Agrega breadcrumbs a los pantallas para debugging"
4. "Correla eventos del frontend y backend del mismo dispositivo"
5. "Captura todas las excepciones de coroutines automáticamente"

---

## OperationTrace — Span API

`OperationTrace` mide el tiempo de cualquier operación y emite una métrica `trace.<operationName>` al finalizar.

### Entrada pública

`AppLogger.startTrace` es una extension function — disponible en `AppLoggerSDK` (Android) y `AppLoggerIos.shared` (iOS KMP).

### Android — flujo completo

```kotlin
import com.applogger.core.AppLoggerSDK
import com.applogger.core.OperationTrace

// 1. Abrir span
val trace = AppLoggerSDK.startTrace("video_load", "content_id" to contentId)

// 2. Añadir atributos durante la operación
trace.tag("quality", "4K")
     .tag("drm", "widevine")

// 3a. Cerrar con éxito — emite métrica trace.video_load con duration_ms
trace.end(mapOf("buffer_ms" to bufferTimeMs))

// 3b. Cerrar con error — emite evento ERROR-level
trace.endWithError(exception, failureReason = "drm_error")
```

### iOS KMP — mismo patrón, distinto entry point

```kotlin
val trace = AppLoggerIos.shared.startTrace("video_load", "content_id" to contentId)
trace.tag("quality", "4K")
// ...
trace.end()
```

### API completa de OperationTrace

| Método | Descripción |
|---|---|
| `tag(key, value)` | Añade atributo al span. Fluent. |
| `bytes(count: Long)` | Registra bytes transferidos → auto-calcula `throughput_mbps`. |
| `withTimeout(ms: Long)` | Establece deadline. Si se supera, `timed_out=true` al cerrar. |
| `isExpired` | `true` si el timeout ya pasó. |
| `end(extraAttributes?)` | Cierra con éxito. Emite `trace.<name>` metric. |
| `endWithError(error, failureReason?, extraAttributes?)` | Cierra con error. Emite evento ERROR-level. |

### Qué emite cada closure

**`end()`** → metric `trace.<operationName>`:
```
name     = "trace.video_load"
value    = 1250.0
unit     = "ms"
tags     = { duration_ms: 1250, trace_id: "uuid", success: true,
             content_id: "movie_123", quality: "4K" }
```

**`endWithError(e)`** → log `ERROR`:
```
tag      = "Trace.video_load"
message  = "trace.video_load failed after 5001ms"
throwable = <exception>
extra    = { duration_ms: 5001, trace_id: "uuid", success: false,
             failure_reason: "drm_error", timed_out: true, timeout_ms: 5000 }
```

### Patrones de uso recomendados

```kotlin
// Patrón A — Operación simple con try/finally
val trace = AppLoggerSDK.startTrace("checkout_flow")
try {
    val result = paymentApi.charge(amount)
    trace.end(mapOf("payment_method" to result.method))
} catch (e: Exception) {
    trace.endWithError(e, failureReason = "payment_rejected")
}

// Patrón B — Con timeout y verificación
val trace = AppLoggerSDK.startTrace("api_call", "endpoint" to url)
    .withTimeout(5000)
// ... operación ...
if (trace.isExpired) trace.endWithError(TimeoutException(), failureReason = "timeout")
else trace.end()

// Patrón C — Medir bytes transferidos (para cálculo de throughput)
val trace = AppLoggerSDK.startTrace("file_upload", "filename" to filename)
trace.bytes(fileBytes.size.toLong())
// ... upload ...
trace.end()
// → emitirá throughput_mbps automáticamente
```

### Consultar spans en CLI

```bash
# Ver todas las métricas de un span específico
apploggers telemetry query --source metrics --name trace.video_load --output json

# Spans fallidos (ERROR-level)
apploggers telemetry query --source logs --tag "Trace.video_load" --severity error

# Agregar por día para detectar degradación de performance
apploggers telemetry query --source metrics --name trace.video_load --aggregate day
```

---

## DataBudgetManager — Control de ancho de banda diario

Controla cuántos bytes envía el SDK por día. Cuando se alcanza el límite, el SDK descarta eventos no críticos hasta el siguiente día UTC. ERROR y CRITICAL nunca se descartan.

### Activar mediante AppLoggerConfig

```kotlin
// Android
AppLoggerSDK.initialize(
    context = this,
    config = AppLoggerConfig.Builder()
        .endpoint(BuildConfig.LOGGER_URL)
        .apiKey(BuildConfig.LOGGER_KEY)
        .environment("production")
        .dailyDataLimitMb(50)          // 50 MB por día en datos móviles
        // En WiFi: límite efectivo = 50 × 2 = 100 MB (wifiMultiplier = 2 por defecto)
        .build(),
    transport = transport
)
```

```kotlin
// iOS KMP — mismo patrón
val config = AppLoggerConfig.Builder()
    .endpoint(url)
    .apiKey(anonKey)
    .environment("production")
    .dailyDataLimitMb(50)
    .build()
AppLoggerIos.shared.initialize(config = config, transport = transport)
```

### Comportamiento

| Condición | Comportamiento |
|---|---|
| `dailyDataLimitMb = 0` (default) | Sin límite — el presupuesto está desactivado |
| Bytes < límite diario | Todos los eventos pasan normalmente |
| Bytes ≥ límite diario | Eventos no críticos se descartan (`shouldShedLowPriority = true`) |
| Red WiFi | Límite efectivo = `dailyDataLimitMb × 2` (más permisivo en WiFi) |
| Nuevo día UTC | Contador se resetea automáticamente |
| ERROR / CRITICAL | Nunca se descartan, independientemente del presupuesto |

### Persistencia

El contador de bytes enviados persiste entre reinicios del proceso:
- **Android**: `SharedPreferences`
- **iOS**: `NSUserDefaults`

Esto garantiza que el límite diario se respeta incluso si la app se reinicia varias veces en el mismo día.

### Cuándo configurarlo

| Caso | Valor recomendado |
|---|---|
| Apps de usuario final con datos móviles | `20–50 MB/día` |
| Apps enterprise con WiFi garantizado | sin límite (0) o `200+ MB/día` |
| Beta builds con debugging intensivo | sin límite (0) |
| Apps con sampling < 0.5 y poco tráfico | sin límite (0) |

---

## Consent Management

Controla si el SDK envía eventos. Sin consentimiento, los eventos se retienen (no se envían).

### iOS KMP

```kotlin
// Otorgar consentimiento (persiste en NSUserDefaults)
AppLoggerIos.shared.setConsent(true)

// Revocar consentimiento — eventos dejan de enviarse
AppLoggerIos.shared.setConsent(false)
```

### Android

El consentimiento en Android se gestiona mediante `minLevel` y `debugMode` en `AppLoggerConfig`.
Para apps que requieren consentimiento GDPR explícito:

```kotlin
// Patrón: inicializar con minLevel CRITICAL, subir a INFO tras consentimiento
if (userHasConsented) {
    AppLoggerSDK.setMinLevel(LogMinLevel.INFO)
} else {
    AppLoggerSDK.setMinLevel(LogMinLevel.CRITICAL)
}
```

### Niveles de consentimiento

| Nivel | Comportamiento |
|---|---|
| `STRICT` | Solo errores críticos. `user_id` suprimido. `device_id` pseudonimizado. |
| `MARKETING` | Telemetría completa habilitada incluyendo `user_id`. |

---

## Distributed Tracing — Correlación entre dispositivos

Correlaciona eventos de múltiples dispositivos (mobile → TV → backend) usando un `trace_id` compartido.

### iOS KMP

```kotlin
// Establecer trace ID compartido (viene de tu API)
AppLoggerIos.shared.setTraceId("order-abc-123")

// El trace_id se adjunta a cada evento posterior
AppLoggerIos.shared.clearTraceId()
```

### Android

```kotlin
AppLoggerSDK.addGlobalExtra("trace_id", "order-abc-123")
// ...
AppLoggerSDK.removeGlobalExtra("trace_id")
```

### Consultar en CLI

```bash
apploggers telemetry query --source logs \
    --extra-key trace_id \
    --extra-value "order-abc-123" \
    --output json
```

---

## Breadcrumbs — Trail de navegación

Registra un historial de pantallas o eventos visitados antes de un error.

### iOS KMP

```kotlin
AppLoggerIos.shared.recordBreadcrumb("HomeScreen")
AppLoggerIos.shared.recordBreadcrumb("SearchScreen")
AppLoggerIos.shared.recordBreadcrumb("PlayerScreen")

// Los breadcrumbs aparecen en extra.breadcrumbs de cada evento posterior
```

### Android — Patrón equivalente

```kotlin
AppLoggerSDK.addGlobalExtra("breadcrumbs", listOf("HomeScreen", "SearchScreen"))
```

### Por qué es útil

Cuando llega un error, `extra.breadcrumbs` muestra exactamente qué pantallas visitó el usuario
antes del fallo — sin necesidad de reproducir el problema manualmente.

---

## Session Variant — A/B Testing

Etiqueta la sesión para distinguir variantes de experimentos.

### iOS KMP

```kotlin
AppLoggerIos.shared.setVariant("checkout_v2")
// Todos los eventos de esta sesión llevan el variant
AppLoggerIos.shared.clearVariant()
```

### Android

```kotlin
AppLoggerSDK.addGlobalExtra("variant", "checkout_v2")
AppLoggerSDK.removeGlobalExtra("variant")
```

### Consultar resultados de A/B en Supabase

```sql
-- Comparar tasa de error entre variantes
SELECT extra->>'variant' AS variant,
       level,
       COUNT(*) AS total
FROM app_logs
WHERE extra->>'variant' IS NOT NULL
GROUP BY variant, level
ORDER BY variant, level;
```

---

## Coroutine Exception Handler

Captura automáticamente todas las excepciones no manejadas en coroutines.

```kotlin
import com.applogger.core.AppLoggerExceptionHandler

// Usar en cualquier CoroutineScope
val scope = CoroutineScope(Dispatchers.Default + AppLoggerExceptionHandler)

// Las excepciones no capturadas se loguean como CRITICAL automáticamente
// No necesitas try/catch en cada coroutine
```

Este handler es especialmente útil en `viewModelScope`, `lifecycleScope`, y scopes de servicio.

---

## Mandatory constraints

1. `startTrace` acepta `vararg attributes: Pair<String, Any>` como contexto inicial — úsalo para capturar IDs relevantes al inicio del span.
2. Siempre llamar `end()` o `endWithError()` — los spans no cerrados no emiten ningún evento.
3. `end()` y `endWithError()` son idempotentes — llamadas posteriores son no-op.
4. `dailyDataLimitMb = 0` desactiva el presupuesto (default). No poner `0` cuando se quiere límite.
5. El WiFi multiplier es `2×` por defecto — no se puede cambiar vía config actual.
6. Nunca usar `AppLoggerSDK` en iOS KMP — usar `AppLoggerIos.shared`.
7. Los breadcrumbs se acumulan durante la sesión — limpiarlos en logout si contienen rutas sensibles.

## References bundled with this skill

1. `references/operation-trace-guide.md`
2. `references/data-budget-guide.md`

## Output standard

1. Mostrar el span/budget configurado con parámetros exactos.
2. Incluir cómo verificar el evento emitido en CLI.
3. Señalar qué eventos son inmunes a DataBudget shedding (ERROR/CRITICAL).
