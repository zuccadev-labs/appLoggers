# DataBudgetManager — Guía de referencia

## Qué es

`DataBudgetManager` es un subsistema interno del SDK que controla cuántos bytes se envían por día. El usuario lo configura exclusivamente via `AppLoggerConfig.Builder().dailyDataLimitMb(n)`.

## Activación

```kotlin
AppLoggerConfig.Builder()
    .dailyDataLimitMb(50)   // 50 MB/día en datos móviles
    // 0 (default) = sin límite
    .build()
```

El `DataBudgetManager` se instancia internamente por el SDK al inicializar — no es parte de la API pública.

## Comportamiento completo

### Cuando `dailyDataLimitMb = 0` (default)

El presupuesto está desactivado. Todos los eventos pasan sin restricción.

### Cuando `dailyDataLimitMb > 0`

El SDK calcula el límite efectivo según el tipo de red:

```
Datos móviles → límite efectivo = dailyDataLimitMb MB
WiFi          → límite efectivo = dailyDataLimitMb × 2 MB
```

Cuando `bytes_enviados_hoy ≥ límite_efectivo`:
- `shouldShedLowPriority = true`
- El `BatchProcessor` descarta todos los eventos no críticos
- **ERROR y CRITICAL nunca se descartan**

### Reset diario

El contador de bytes enviados se resetea automáticamente a las 00:00 UTC de cada día. El índice del día se calcula como `(epoch_ms / 86400000) % 365` — determinista y sin zona horaria local.

### Persistencia del contador

El SDK persiste el contador entre reinicios del proceso:
- **Android**: `SharedPreferences` (clave `applogger_budget_bytes` / `applogger_budget_day`)
- **iOS**: `NSUserDefaults`

Esto garantiza que si la app se reinicia 5 veces en el mismo día, el límite se respeta acumulativamente.

### Overflow protection

Si `bytesUsedToday + estimatedBytes` desborda un `Long`, el SDK lo clampea a `Long.MAX_VALUE` en lugar de wrappear a negativo.

---

## Valores recomendados por caso de uso

| Caso de uso | `dailyDataLimitMb` recomendado | Justificación |
|---|---|---|
| App de usuario final (datos móviles) | `20–50` | Respeta plan de datos del usuario |
| App enterprise (WiFi garantizado) | `0` o `200+` | Sin restricción o muy permisivo |
| Beta build con debug intensivo | `0` | No restringir durante testing |
| App con sampling < 20% | `0` | Poco volumen, presupuesto innecesario |
| App con métricas de alta frecuencia | `50–100` | Controlar costos de Supabase |

---

## Verificar el presupuesto en producción

El SDK no expone `DataBudgetManager` directamente como API pública. Para monitorear el impacto del shedding, verifica en CLI si hay gaps de eventos esperados:

```bash
# Ver cuántos eventos llegan vs los esperados por hora
apploggers telemetry query --source logs \
    --aggregate hour \
    --environment production \
    --output json

# Comparar con días sin shedding para detectar reducción de volumen
apploggers telemetry query --source logs \
    --aggregate day \
    --from "2026-01-01T00:00:00Z" \
    --to "2026-01-07T00:00:00Z" \
    --output json
```

Si el volumen de logs cae abruptamente a mediodía, es señal de que el presupuesto se agota antes del día.

---

## Interacción con remote config

El remote config `sampling_rate` y `DataBudgetManager` son mecanismos independientes:

- `sampling_rate` (0.0–1.0): porcentaje aleatorio de eventos enviados — aplicado antes del BatchProcessor.
- `dailyDataLimitMb`: límite acumulativo de bytes por día — aplicado en el BatchProcessor.

Ambos pueden estar activos simultáneamente. Si quieres controlar costos, prefer `sampling_rate` sobre `dailyDataLimitMb` para una distribución uniforme a lo largo del día.

---

## Acceptance gate #16

El gate de aceptación #16 verifica:

1. `recordBytesSent` respeta el límite diario → verificar que eventos no críticos dejan de llegar cuando se simula el límite.
2. `shouldShedLowPriority` se activa cuando se excede → los eventos ERROR/CRITICAL siguen llegando.

Para simular el shedding en desarrollo:

```kotlin
// Configurar un límite muy pequeño para testing
AppLoggerConfig.Builder()
    .dailyDataLimitMb(1)  // 1 MB — fácil de exceder en pruebas
    .build()
```
