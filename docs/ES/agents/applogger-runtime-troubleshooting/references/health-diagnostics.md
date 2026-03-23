# Health Diagnostics

## Obtener el snapshot

```kotlin
val health = AppLoggerHealth.snapshot()
```

`AppLoggerHealth` implementa `AppLoggerHealthProvider`. En tests, inyectar un `FakeHealthProvider` en lugar de usar el singleton directamente.

---

## Campos de `HealthStatus`

| Campo | Tipo | Descripción |
|---|---|---|
| `isInitialized` | `Boolean` | `true` tras `initialize()` exitoso |
| `transportAvailable` | `Boolean` | `true` si el transporte reporta conectividad |
| `bufferedEvents` | `Int` | Eventos en buffer pendientes de envío |
| `deadLetterCount` | `Int` | Eventos fallidos permanentemente (agotaron reintentos) |
| `consecutiveFailures` | `Int` | Fallos consecutivos del transporte — se resetea **solo** en éxito completo |
| `eventsDroppedDueToBufferOverflow` | `Long` | Total de eventos descartados por overflow del buffer |
| `bufferUtilizationPercentage` | `Float` | Ocupación del buffer (0–100) |
| `sdkVersion` | `String` | Versión del SDK |
| `snapshotTimestamp` | `Long` | Epoch millis cuando se tomó este snapshot |
| `lastSuccessfulFlushTimestamp` | `Long` | Epoch millis del último flush exitoso (0 si ninguno aún) |

---

## Interpretación y acciones

### 1. `isInitialized = false`
El path de bootstrap no se ejecutó. Verificar que `AppLoggerSDK.initialize()` (Android) o `AppLoggerIos.shared.initialize()` (iOS) se llama en `Application.onCreate()` antes de cualquier uso del SDK.

### 2. `transportAvailable = false`
Problema de endpoint o conectividad. Verificar:
- El dispositivo tiene acceso a internet.
- El endpoint en `AppLoggerConfig` es correcto y usa HTTPS.
- `SupabaseTransport` recibe un `networkAvailabilityProvider` válido.

### 3. `bufferedEvents` creciendo continuamente
La entrega está bloqueada. Causas posibles:
- `transportAvailable = false` — sin red.
- `consecutiveFailures` alto — el transporte falla repetidamente.
- `flushIntervalSeconds` muy alto — el flush periódico tarda demasiado.

### 4. `consecutiveFailures` alto
El transporte está fallando. Verificar:
- Credenciales Supabase válidas (apiKey empieza con `eyJ`).
- El endpoint responde correctamente.
- No hay rate limiting activo (HTTP 429) — si `lastSuccessfulFlushTimestamp` es reciente pero `consecutiveFailures` sube, puede ser throttling.

### 5. `eventsDroppedDueToBufferOverflow` > 0
El buffer se está llenando más rápido de lo que se vacía. Acciones:
- Aumentar `batchSize` para enviar más eventos por flush.
- Reducir `flushIntervalSeconds` para flushear más frecuentemente.
- Cambiar `bufferOverflowPolicy` a `PRIORITY_AWARE` para preservar errores críticos.
- Considerar `bufferSizeStrategy = ADAPTIVE_TO_RAM`.

### 6. `deadLetterCount` > 0
Eventos que agotaron todos los reintentos. Indica fallos persistentes del transporte. Revisar logs del servidor Supabase y verificar que las tablas `app_logs` y `app_metrics` existen con el schema correcto.

### 7. `lastSuccessfulFlushTimestamp` — Detectar outage silencioso

```kotlin
val health = AppLoggerHealth.snapshot()
val minutesSinceFlush = (System.currentTimeMillis() - health.lastSuccessfulFlushTimestamp) / 60_000

if (health.lastSuccessfulFlushTimestamp > 0 && minutesSinceFlush > 5) {
    // El SDK lleva más de 5 minutos sin enviar — posible outage silencioso
    alertSRE("AppLogger sin flush exitoso en $minutesSinceFlush minutos")
}
```

### 8. `isStale()` — Snapshot desactualizado

```kotlin
if (health.isStale(maxAgeMs = 60_000L)) {
    // El snapshot tiene más de 1 minuto — volver a llamar snapshot()
    val fresh = AppLoggerHealth.snapshot()
}
```

Umbrales recomendados:
- Dashboards de monitoreo: `maxAgeMs = 60_000` (1 minuto).
- Alertas de producción: `maxAgeMs = 10_000` (10 segundos).

---

## Ejemplo de diagnóstico completo

```kotlin
fun diagnoseAppLogger() {
    val health = AppLoggerHealth.snapshot()

    if (!health.isInitialized) {
        Log.e("Diagnostics", "SDK no inicializado")
        return
    }

    Log.d("Diagnostics", buildString {
        appendLine("=== AppLogger Health ===")
        appendLine("Transport: ${if (health.transportAvailable) "OK" else "OFFLINE"}")
        appendLine("Buffer: ${health.bufferedEvents} eventos (${health.bufferUtilizationPercentage.toInt()}%)")
        appendLine("Consecutive failures: ${health.consecutiveFailures}")
        appendLine("Dead letters: ${health.deadLetterCount}")
        appendLine("Dropped (overflow): ${health.eventsDroppedDueToBufferOverflow}")
        val minSinceFlush = (System.currentTimeMillis() - health.lastSuccessfulFlushTimestamp) / 60_000
        appendLine("Last flush: ${if (health.lastSuccessfulFlushTimestamp > 0) "${minSinceFlush}m ago" else "never"}")
        appendLine("SDK: ${health.sdkVersion}")
    })
}
```
