# AppLoggerConfig.Builder — Referencia completa

Todos los métodos del Builder con sus defaults, rangos y notas.

```kotlin
AppLoggerConfig.Builder()
    // required
    .endpoint("https://YOUR_PROJECT.supabase.co")
    .apiKey("eyJhbGci...")
    // optional — todos tienen defaults razonables
    .build(): AppLoggerConfig
```

---

## Opciones requeridas

| Método | Tipo | Default | Notas |
|---|---|---|---|
| `endpoint(url: String)` | String | `""` | URL del proyecto Supabase. HTTPS obligatorio en producción. |
| `apiKey(key: String)` | String | `""` | Supabase anon key (JWT). La clave empieza con `eyJ`. |

---

## Identidad y entorno

| Método | Tipo | Default | Notas |
|---|---|---|---|
| `environment(env: String)` | String | `"production"` | Etiqueta de entorno. String libre — convenio: `"production"`, `"staging"`, `"development"`. Adjunto a todos los eventos. Blanco → `"production"`. |

---

## Debug y logging

| Método | Tipo | Default | Notas |
|---|---|---|---|
| `debugMode(debug: Boolean)` | Boolean | `false` | Activa logging debug. En Android: también auto-activado si `APPLOGGER_DEBUG=true` en manifest. En iOS: auto-activado si `APPLOGGER_DEBUG=true` en Info.plist. Relaja el requisito HTTPS. |
| `consoleOutput(enabled: Boolean)` | Boolean | `true` | Imprime eventos a logcat/consola cuando `true` **Y** `debugMode=true`. Siempre suprimido en producción. |
| `verboseTransportLogging(v: Boolean)` | Boolean | `false` | Loguea detalles del transporte (HTTP requests, responses). Solo para debugging de integración. |

---

## Filtrado y niveles

| Método | Tipo | Default | Rango | Notas |
|---|---|---|---|---|
| `minLevel(level: LogMinLevel)` | LogMinLevel | `DEBUG` | N/A | Nivel mínimo para procesar. Eventos por debajo se descartan **antes** del pipeline. METRIC siempre pasa. Valores: `DEBUG`, `INFO`, `WARN`, `ERROR`, `CRITICAL`. En producción: recomendar `INFO`. |

---

## Batching y flush

| Método | Tipo | Default | Rango | Notas |
|---|---|---|---|---|
| `batchSize(size: Int)` | Int | `20` | 1–100 | Eventos por batch. Coerced al rango en build. Valores grandes reducen requests HTTP pero aumentan latencia. |
| `flushIntervalSeconds(sec: Int)` | Int | `30` | 5–300 | Segundos entre flushes periódicos. Coerced al rango. |
| `flushOnlyWhenIdle(idle: Boolean)` | Boolean | `false` | N/A | Android TV optimization — demora el flush hasta que el dispositivo esté idle. Reducir batería en TV. |

---

## Stack traces

| Método | Tipo | Default | Rango | Notas |
|---|---|---|---|---|
| `maxStackTraceLines(lines: Int)` | Int | `50` | 1–100 | Máximo de líneas del stack trace por evento. Coerced al rango. Reducir para events más pequeños. |

---

## Buffer y overflow

| Método | Tipo | Default | Notas |
|---|---|---|---|
| `bufferSizeStrategy(strategy: BufferSizeStrategy)` | BufferSizeStrategy | `FIXED` | Estrategia de capacidad del buffer. `FIXED` = fija. `ADAPTIVE_TO_RAM` = escala con RAM disponible. `ADAPTIVE_TO_LOG_RATE` = deprecated, igual a FIXED. |
| `bufferOverflowPolicy(policy: BufferOverflowPolicy)` | BufferOverflowPolicy | `DISCARD_OLDEST` | Qué hacer cuando el buffer está lleno. `DISCARD_OLDEST` = FIFO, descarta el más antiguo. `DISCARD_NEWEST` = descarta el entrante. `PRIORITY_AWARE` = descarta el de menor prioridad (mejor para producción). |

---

## Persistencia offline

| Método | Tipo | Default | Notas |
|---|---|---|---|
| `offlinePersistenceMode(mode: OfflinePersistenceMode)` | OfflinePersistenceMode | `NONE` | Cómo persistir eventos cuando no hay red. `NONE` = sin persistencia. `CRITICAL_ONLY` = solo ERROR/CRITICAL a SQLite. `ALL` = todos los eventos a SQLite. |

---

## Deduplicación

| Método | Tipo | Default | Rango | Notas |
|---|---|---|---|---|
| `deduplicationWindowMs(ms: Long)` | Long | `10_000` | 0–∞ | Ventana de deduplicación en ms. `0` = desactivado. Activo por defecto (10 s). |

**Comportamiento exacto de la deduplicación:**

- Solo aplica a eventos **WARN, ERROR, CRITICAL** que tienen **throwable** adjunto. INFO, DEBUG, METRIC y eventos sin throwable pasan siempre sin deduplicar.
- **Clave de dedup**: `level | tag | message (primeros 200 chars) | throwable type`. Dos eventos son idénticos cuando estos cuatro campos coinciden.
- **Primera ocurrencia** → pasa inmediatamente, inicia ventana.
- **Duplicados dentro de la ventana** → suprimidos, contador interno incrementado.
- **Cuando llega un evento diferente tras expirar la ventana** → el SDK emite el primer evento enriquecido con `occurrence_count = N` (total de ocurrencias de la ventana).
- **En flush/shutdown** → `drainAggregated()` emite cualquier evento pendiente con `occurrence_count`.
- `occurrence_count` solo aparece en el evento emitido si hubo 2+ duplicados. Si solo hubo 1, el evento sale sin ese campo.

```kotlin
// Si el mismo error ocurre 50 veces en 10 segundos:
// → Solo se envía 1 evento con occurrence_count = 50
// → En lugar de 50 filas en Supabase
```

---

## Breadcrumbs

| Método | Tipo | Default | Rango | Notas |
|---|---|---|---|---|
| `breadcrumbCapacity(n: Int)` | Int | `10` | 0–∞ | Máximo de breadcrumbs retenidos. Los más antiguos se descartan cuando se llena. `0` = desactivado. |

---

## Consentimiento y privacidad

| Método | Tipo | Default | Notas |
|---|---|---|---|
| `defaultConsentLevel(level: ConsentLevel)` | ConsentLevel | `MARKETING` | Nivel de consentimiento al inicializar (antes de que el usuario acepte/rechace). Para apps con consent dialog: usar `STRICT`. |
| `dataMinimizationEnabled(enabled: Boolean)` | Boolean | `true` | GDPR Art. 5(1)(c). En modo `STRICT`: `user_id → null`, `device_id → SHA-256`. Desactivar solo si tienes tu propio mecanismo. |

---

## Integridad de batches (HMAC)

| Método | Tipo | Default | Notas |
|---|---|---|---|
| `integritySecret(secret: String)` | String | `""` | Secreto para HMAC-SHA256 batch integrity. Blanco = desactivado. **NUNCA usar la anon key de Supabase.** Generar un secreto dedicado. Cuando activo: cada batch tiene un hash HMAC verificable vía `apploggers verify`. |

---

## Data budget

| Método | Tipo | Default | Notas |
|---|---|---|---|
| `dailyDataLimitMb(mb: Int)` | Int | `0` | Límite de bytes enviados por día. `0` = desactivado. En WiFi: límite efectivo = `mb × 2` (WiFi multiplier = 2×). Cuando se alcanza: eventos no críticos se descartan hasta el siguiente día UTC. ERROR/CRITICAL nunca se descartan. |

---

## Remote config

| Método | Tipo | Default | Rango | Notas |
|---|---|---|---|---|
| `remoteConfigEnabled(enabled: Boolean)` | Boolean | `false` | N/A | Activa polling a `device_remote_config` en Supabase. Requiere migración 013. |
| `remoteConfigIntervalSeconds(sec: Int)` | Int | `300` | 30–3600 | Segundos entre polls. Coerced al rango. Default: 5 minutos. |

---

## Validación

```kotlin
val config = AppLoggerConfig.Builder()
    .endpoint(url)
    .apiKey(key)
    .environment("production")
    .build()

// Verificar en desarrollo — retorna lista de warnings/errors
config.validate().forEach { issue ->
    Log.w("AppLogger", "Config issue: $issue")
}
```

`validate()` detecta:
- Endpoint vacío o no HTTPS en producción
- Endpoint con formato inválido (no empieza con `http://` ni `https://`)
- `apiKey` vacío
- `apiKey` que no empieza con `"eyJ"` (no parece un JWT de Supabase)
- `environment` vacío (eventos sin etiqueta de entorno)
- `isDebugMode=true` con `environment="production"` (no debe estar en producción)
- `batchSize >= 50` con `flushIntervalSeconds <= 10` (posibles bursts de red)
- `batchSize == 1` (deshabilita batching — cada evento es un request HTTP)

---

## Auto-ajuste en dispositivos de bajos recursos (Android TV / low-RAM)

En Android, el SDK detecta automáticamente dispositivos de bajos recursos (Android TV, baja RAM) y sobreescribe la config con valores más conservadores:

| Parámetro | Valor auto-ajustado | Por qué |
|---|---|---|
| `batchSize` | `min(config, 5)` | Reducir presión de red |
| `flushIntervalSeconds` | `max(config, 60)` | Espaciar flushes |
| `maxStackTraceLines` | `min(config, 5)` | Reducir tamaño de payload |
| `flushOnlyWhenIdle` | `true` | Ahorro de batería en TV |
| `breadcrumbCapacity` | `min(config, 5)` | Reducir uso de memoria |

Esto ocurre automáticamente — no hay que configurarlo. Si el comportamiento en TV difiere del esperado, es probable que el auto-ajuste esté activo. No se puede desactivar vía config.

---

## Ejemplo de config producción completa

```kotlin
AppLoggerConfig.Builder()
    .endpoint(BuildConfig.LOGGER_URL)
    .apiKey(BuildConfig.LOGGER_KEY)
    .environment("production")
    .debugMode(false)
    .consoleOutput(false)
    .minLevel(LogMinLevel.INFO)
    .batchSize(20)
    .flushIntervalSeconds(30)
    .bufferOverflowPolicy(BufferOverflowPolicy.PRIORITY_AWARE)
    .offlinePersistenceMode(OfflinePersistenceMode.CRITICAL_ONLY)
    .deduplicationWindowMs(10_000)
    .breadcrumbCapacity(10)
    .defaultConsentLevel(ConsentLevel.STRICT)         // sin consentimiento por defecto
    .dataMinimizationEnabled(true)
    .integritySecret(BuildConfig.LOGGER_HMAC_SECRET)  // si HMAC está activo
    .dailyDataLimitMb(50)                              // si control de costos es necesario
    .remoteConfigEnabled(true)
    .remoteConfigIntervalSeconds(300)
    .build()
```
