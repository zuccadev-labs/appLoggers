# Runtime Tuning Baseline

## Configuración baseline recomendada

```kotlin
AppLoggerConfig.Builder()
    .endpoint("https://tu-proyecto.supabase.co")
    .apiKey("eyJ...")
    .environment("production")                          // siempre etiquetar el entorno
    .debugMode(false)                                   // nunca true en producción
    .minLevel(LogMinLevel.INFO)                         // descarta DEBUG antes del pipeline
    .batchSize(20)                                      // 1–100 (default 20)
    .flushIntervalSeconds(30)                           // 5–300 (default 30)
    .maxStackTraceLines(50)                             // 1–100 (default 50)
    .bufferSizeStrategy(BufferSizeStrategy.FIXED)       // capacidad fija de 1000 eventos
    .bufferOverflowPolicy(BufferOverflowPolicy.DISCARD_OLDEST)
    .offlinePersistenceMode(OfflinePersistenceMode.NONE)
    .build()
```

Ajustar valores después de observar el comportamiento de health y entrega.

---

## Parámetros y su impacto

### `minLevel` — Filtrado antes del pipeline

Descarta eventos por debajo del nivel configurado **antes** de entrar al pipeline. Sin coste de serialización ni red.

| Valor | Recomendado para |
|---|---|
| `DEBUG` | Solo desarrollo local |
| `INFO` | Apps con volumen moderado de logs |
| `WARN` | Apps con alto volumen — reduce ruido |
| `ERROR` | Apps con restricciones de ancho de banda |
| `CRITICAL` | Solo incidentes críticos + métricas |

METRIC siempre pasa independientemente del nivel configurado.

### `environment` — Separación QA vs producción

Siempre configurar `environment` para poder filtrar en Supabase:

```sql
-- Solo errores de producción
SELECT * FROM app_logs WHERE environment = 'production' AND level = 'ERROR';

-- Comparar staging vs producción
SELECT environment, count(*) FROM app_logs GROUP BY environment;
```

Valores recomendados: `"production"`, `"staging"`, `"development"`.

### `batchSize` — Tamaño del batch

| Escenario | Valor recomendado |
|---|---|
| Producción normal | 20 |
| Alto volumen de logs | 50 |
| Android TV / low-resource | 5 (auto-aplicado por el SDK) |
| Debug / desarrollo | 1 (cada evento dispara un envío) |

> `batchSize=1` desactiva el batching — cada evento genera una request de red. Solo para debugging.

### `flushIntervalSeconds` — Intervalo de flush periódico

| Escenario | Valor recomendado |
|---|---|
| Producción normal | 30 s |
| Apps con sesiones cortas | 15 s |
| Android TV | 60 s (auto-aplicado) |
| Baja conectividad | 60–120 s |

### `bufferSizeStrategy` — Estrategia de tamaño del buffer

| Valor | Comportamiento |
|---|---|
| `FIXED` | Capacidad fija de 1000 eventos (default) |
| `ADAPTIVE_TO_RAM` | Calcula capacidad según RAM disponible (0.1% de RAM total, entre 50 y 5000) |
| `ADAPTIVE_TO_LOG_RATE` | Reservado para uso futuro |

Usar `ADAPTIVE_TO_RAM` en apps que corren en dispositivos con RAM muy variable (ej. gama baja vs alta).

### `bufferOverflowPolicy` — Política ante overflow

| Valor | Comportamiento | Recomendado para |
|---|---|---|
| `DISCARD_OLDEST` | Descarta el evento más antiguo (default) | La mayoría de apps |
| `DISCARD_NEWEST` | Descarta el evento entrante | Apps donde el historial importa más |
| `PRIORITY_AWARE` | Preserva eventos de mayor severidad | Apps reguladas, banca, salud |

### `offlinePersistenceMode` — Persistencia offline

| Valor | Comportamiento | Recomendado para |
|---|---|---|
| `NONE` | Solo memoria (default) | La mayoría de apps |
| `CRITICAL_ONLY` | ERROR y CRITICAL se guardan en SQLite | Apps reguladas |
| `ALL` | Todos los eventos en SQLite | Auditoría completa, outages prolongados |

> `CRITICAL_ONLY` y `ALL` requieren el driver SQLite en Android. No disponible en iOS aún.

---

## Perfiles de configuración por caso de uso

### Producción estándar (mobile)

```kotlin
.minLevel(LogMinLevel.INFO)
.batchSize(20)
.flushIntervalSeconds(30)
.bufferSizeStrategy(BufferSizeStrategy.FIXED)
.bufferOverflowPolicy(BufferOverflowPolicy.DISCARD_OLDEST)
.offlinePersistenceMode(OfflinePersistenceMode.NONE)
```

### App regulada (banca, salud)

```kotlin
.minLevel(LogMinLevel.WARN)
.batchSize(10)
.flushIntervalSeconds(15)
.bufferSizeStrategy(BufferSizeStrategy.ADAPTIVE_TO_RAM)
.bufferOverflowPolicy(BufferOverflowPolicy.PRIORITY_AWARE)
.offlinePersistenceMode(OfflinePersistenceMode.CRITICAL_ONLY)
```

### Android TV / low-resource (auto-aplicado por el SDK)

```kotlin
// El SDK aplica estos valores automáticamente al detectar TV via PlatformDetector
.batchSize(5)
.flushIntervalSeconds(60)
.maxStackTraceLines(5)
.flushOnlyWhenIdle(true)
```

### Alto volumen de logs

```kotlin
.minLevel(LogMinLevel.WARN)   // descarta DEBUG e INFO
.batchSize(50)
.flushIntervalSeconds(60)
.bufferSizeStrategy(BufferSizeStrategy.ADAPTIVE_TO_RAM)
.bufferOverflowPolicy(BufferOverflowPolicy.PRIORITY_AWARE)
```

---

## Validar la configuración

Siempre llamar `validate()` en debug para detectar problemas antes de producción:

```kotlin
val config = AppLoggerConfig.Builder()
    // ... tu configuración
    .build()

val issues = config.validate()
if (issues.isNotEmpty()) {
    issues.forEach { Log.w("AppLogger", "Config issue: $it") }
}
```

`validate()` detecta: endpoint en blanco, endpoint sin HTTPS en producción, apiKey en blanco o sin formato JWT, environment en blanco, combinación batchSize/flushInterval problemática, y `isDebugMode=true` con `environment="production"`.

---

## Remote Config — Tuning del polling

El SDK puede recibir overrides remotos desde la tabla `device_remote_config` de Supabase. El polling se configura en el builder:

```kotlin
AppLoggerConfig.Builder()
    .remoteConfigIntervalSeconds(300)  // 30–3600, default 300
    .build()
```

| Escenario | Intervalo recomendado |
|---|---|
| Producción normal | 300 s (5 min) |
| Debugging activo de un dispositivo | 60 s (1 min) |
| Apps con bajo volumen | 600 s (10 min) |
| Apps reguladas (mínima latencia de activación) | 30 s |

El remote config permite activar/desactivar remotamente:

- `debugEnabled` — habilita debug logging sin rebuild
- `minLevel` — cambia el nivel mínimo de log
- `tagsAllow` / `tagsBlock` — filtra tags específicos
- `samplingRate` — 0.0–1.0 porcentaje de eventos que pasan

> ERROR y CRITICAL siempre pasan independientemente del remote config.

### Device fingerprint en remote config

El CLI envía el fingerprint SHA-256 al crear un remote config entry. El SDK compara su propio fingerprint contra los registros de la tabla para aplicar configs device-specific o globales (fingerprint=NULL).

Prioridad: config con fingerprint específico > config global (fingerprint=NULL).
