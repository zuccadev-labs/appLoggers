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

Controla el nivel de procesamiento de datos. El SDK filtra eventos según el nivel activo.

### API — Android y iOS KMP

```kotlin
import com.applogger.core.ConsentLevel

// Android
AppLoggerSDK.setConsent(ConsentLevel.MARKETING)   // telemetría completa (requiere opt-in)
AppLoggerSDK.setConsent(ConsentLevel.PERFORMANCE) // + métricas y timing (requiere T&C)
AppLoggerSDK.setConsent(ConsentLevel.STRICT)       // solo errores críticos, anonimizados

val level: ConsentLevel = AppLoggerSDK.getConsent()

// iOS KMP — mismo API
AppLoggerIos.shared.setConsent(ConsentLevel.MARKETING)
AppLoggerIos.shared.setConsent(ConsentLevel.STRICT)
val level = AppLoggerIos.shared.getConsent()
```

### Niveles de consentimiento

| Nivel | Eventos que pasan | `user_id` | `device_id` | Base legal (GDPR) |
|---|---|---|---|---|
| `STRICT` | Solo CRITICAL/ERROR | Suprimido | SHA-256 pseudonimizado | Art. 6(1)(f) — interés legítimo |
| `PERFORMANCE` | + METRIC y WARN | Opcional | Normal | "Service Improvement" consent |
| `MARKETING` | Todos (default) | Presente | Normal | Art. 7 — opt-in explícito |

### Inferencia automática de consentimiento

El SDK infiere el nivel requerido por cada evento:
- CRITICAL/ERROR → requiere `STRICT` mínimo (siempre pasan)
- METRIC/WARN → requieren `PERFORMANCE` mínimo
- INFO/DEBUG → requieren `MARKETING`

### Override por scope

```kotlin
// Scope que siempre requiere mínimo PERFORMANCE, aunque el evento sea INFO
val perfLog = AppLoggerSDK.newScope(
    "component" to "network",
    consentLevel = ConsentLevel.PERFORMANCE
)
perfLog.info("TAG", "Request latency: 120ms")  // pasa incluso en modo PERFORMANCE
```

### Configurar consent por defecto al inicializar

```kotlin
AppLoggerConfig.Builder()
    .defaultConsentLevel(ConsentLevel.STRICT)  // iniciar en STRICT hasta obtener opt-in
    .dataMinimizationEnabled(true)              // default true — anonimizar en STRICT
    .build()
```

---

## Distributed Tracing — Correlación entre dispositivos

Correlaciona eventos de múltiples dispositivos (mobile → TV → backend) usando un `trace_id` compartido.

### API — Android y iOS KMP

```kotlin
// Android
AppLoggerSDK.setTraceId("order-abc-123")
// ...
AppLoggerSDK.clearTraceId()

// iOS KMP
AppLoggerIos.shared.setTraceId("order-abc-123")
// ...
AppLoggerIos.shared.clearTraceId()
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

Registra un historial de acciones/pantallas visitadas antes de un error.

### API — Android y iOS KMP

```kotlin
// Signature completa: recordBreadcrumb(action, screen?, metadata?)
AppLoggerSDK.recordBreadcrumb("tap_play")
AppLoggerSDK.recordBreadcrumb("screen_enter", screen = "PlayerScreen")
AppLoggerSDK.recordBreadcrumb("api_call", screen = "HomeScreen", metadata = mapOf("endpoint" to "/feed"))

// iOS KMP — mismo API
AppLoggerIos.shared.recordBreadcrumb("tap_play")
AppLoggerIos.shared.recordBreadcrumb("screen_enter", screen = "PlayerScreen")
```

El SDK retiene los últimos `breadcrumbCapacity` breadcrumbs (default: 10). Configurable:
```kotlin
AppLoggerConfig.Builder()
    .breadcrumbCapacity(20)  // 0 = desactivado
    .build()
```

### Por qué es útil

Cuando llega un error, `extra.breadcrumbs` muestra exactamente qué pantallas visitó el usuario
antes del fallo — sin necesidad de reproducir el problema manualmente.

---

## Session Variant — A/B Testing

Etiqueta la sesión para distinguir variantes de experimentos.

### API — Android y iOS KMP

```kotlin
// Android — setSessionVariant acepta String?, null para limpiar
AppLoggerSDK.setSessionVariant("checkout_v2")
// Todos los eventos llevan "variant" como campo top-level
AppLoggerSDK.setSessionVariant(null)  // limpiar

// iOS KMP — mismo API
AppLoggerIos.shared.setSessionVariant("checkout_v2")
AppLoggerIos.shared.setSessionVariant(null)
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
// exceptionHandler es una PROPIEDAD del SDK — no una importación separada

// Android
val scope = CoroutineScope(Dispatchers.Default + AppLoggerSDK.exceptionHandler)
// O en ViewModel:
val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + AppLoggerSDK.exceptionHandler)

// iOS KMP
val scope = CoroutineScope(Dispatchers.Default + AppLoggerIos.shared.exceptionHandler)
```

Las excepciones no capturadas se loguean como CRITICAL con `anomaly_type = "coroutine_crash"`.
Útil en `viewModelScope`, `lifecycleScope`, y scopes de servicio/background.

---

## ScopedAppLogger — Scope con atributos pre-inyectados

`newScope()` crea un logger que automáticamente inyecta atributos en todos los eventos — sin boilerplate por llamada.

```kotlin
// Android
val log = AppLoggerSDK.newScope(
    "content_id" to contentId,
    "user_tier"  to "premium",
    "session_id" to sessionId
)
log.error("PLAYER", "Stall detected", stallException)
// → event incluye content_id, user_tier, session_id automáticamente

// iOS KMP
val log = AppLoggerIos.shared.newScope("content_id" to contentId)
log.warn("PLAYER", "Buffer low", anomalyType = "buffer_underrun")
```

### childScope — herencia de atributos

```kotlin
val sessionLog = AppLoggerSDK.newScope("session_id" to sessionId)
// Child scope hereda session_id y añade más:
val playerLog = sessionLog.childScope("content_id" to contentId, "quality" to "4K")
playerLog.error("PLAYER", "Codec error", e)
// → event incluye session_id + content_id + quality
```

### Combinar scope + tag fijo (patrón recomendado)

```kotlin
// ScopedAppLogger implementa AppLogger → withTag funciona
val log = AppLoggerSDK.newScope("content_id" to contentId).withTag("PlayerController")
log.e("Stall detected", throwable = e)  // tag="PlayerController", extra incluye content_id
```

### Consent override por scope

```kotlin
// Solo emitir PERFORMANCE+ para este scope (ignorar inferencia de nivel)
val perfLog = AppLoggerSDK.newScope(
    "component" to "network",
    consentLevel = ConsentLevel.PERFORMANCE
)
```

### Priority chain (menor → mayor prioridad)

`globalExtra` → scope attributes → per-call `extra`

Per-call `extra` siempre gana sobre el scope. Scope attributes ganan sobre globalExtra.

---

## Mandatory constraints

1. `startTrace` acepta `vararg attributes: Pair<String, Any>` como contexto inicial — úsalo para capturar IDs relevantes al inicio del span.
2. Siempre llamar `end()` o `endWithError()` — los spans no cerrados no emiten ningún evento.
3. `end()` y `endWithError()` son idempotentes — llamadas posteriores son no-op.
4. `dailyDataLimitMb = 0` desactiva el presupuesto (default). No poner `0` cuando se quiere límite.
5. El WiFi multiplier es `2×` por defecto — no se puede cambiar vía config actual.
6. Nunca usar `AppLoggerSDK` en iOS KMP — usar `AppLoggerIos.shared`.
7. Los breadcrumbs se acumulan durante la sesión — limpiarlos en logout si contienen rutas sensibles.
8. `setConsent()` acepta `ConsentLevel` enum (STRICT/PERFORMANCE/MARKETING) — NO un Boolean.
9. `setSessionVariant(null)` limpia el variant — no existe `clearVariant()`.
10. `exceptionHandler` es una propiedad del SDK singleton — no es un import separado.
11. `setUserProperty(key, value)` almacena como `user_prop_<key>` en extra — suprimido en STRICT/PERFORMANCE.
12. `newScope()` crea un `ScopedAppLogger` aislado — no contamina el SDK global con `addGlobalExtra`.

## References bundled with this skill

1. `references/operation-trace-guide.md`
2. `references/data-budget-guide.md`
3. `references/consent-guide.md`
4. `references/scoped-logger-guide.md`

## Output standard

1. Mostrar el span/budget configurado con parámetros exactos.
2. Incluir cómo verificar el evento emitido en CLI.
3. Señalar qué eventos son inmunes a DataBudget shedding (ERROR/CRITICAL).
