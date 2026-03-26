# ConsentLevel — Guía de referencia

## El enum

```kotlin
import com.applogger.core.ConsentLevel

enum class ConsentLevel {
    STRICT,       // Errores críticos únicamente, anonimizados
    PERFORMANCE,  // + métricas y performance
    MARKETING     // Telemetría completa (default)
}
```

## Qué pasa con cada nivel

| Nivel | Eventos que pasan | `user_id` | `device_id` | Breadcrumbs | User props |
|---|---|---|---|---|---|
| `STRICT` | Solo CRITICAL y ERROR | `null` (suprimido) | SHA-256 pseudonimizado | No | No |
| `PERFORMANCE` | + METRIC y WARN | Opcional | Normal | No | No |
| `MARKETING` | Todos | Presente | Normal | Sí | Sí |

## Inferencia automática de consentimiento

El SDK infiere el consentimiento requerido por cada evento sin que el desarrollador tenga que especificarlo:

| LogLevel del evento | Nivel de consentimiento requerido |
|---|---|
| `CRITICAL` | `STRICT` (siempre pasa) |
| `ERROR` | `STRICT` (siempre pasa) |
| `METRIC` | `PERFORMANCE` |
| `WARN` | `PERFORMANCE` |
| `INFO` | `MARKETING` |
| `DEBUG` | `MARKETING` |

Si el nivel activo es `STRICT`, solo pasan CRITICAL y ERROR. Si es `PERFORMANCE`, también METRIC y WARN.

## Base legal (GDPR/CCPA)

| Nivel | Base legal |
|---|---|
| `STRICT` | Art. 6(1)(f) GDPR — interés legítimo / operabilidad de la app. Sin opt-in requerido. |
| `PERFORMANCE` | "Service Improvement" — requiere T&C aceptados. |
| `MARKETING` | Art. 7 GDPR / CCPA § 1798.100 — opt-in explícito del usuario. |

## API

### Cambiar nivel en runtime

```kotlin
import com.applogger.core.ConsentLevel

// Android
AppLoggerSDK.setConsent(ConsentLevel.MARKETING)   // opt-in recibido
AppLoggerSDK.setConsent(ConsentLevel.STRICT)       // opt-out o sin consentimiento

// iOS KMP
AppLoggerIos.shared.setConsent(ConsentLevel.MARKETING)
AppLoggerIos.shared.setConsent(ConsentLevel.STRICT)
```

### Leer nivel actual

```kotlin
val level: ConsentLevel = AppLoggerSDK.getConsent()
val level: ConsentLevel = AppLoggerIos.shared.getConsent()
```

### Persistencia

- **Android**: `SharedPreferences` — persiste entre reinicios de la app
- **iOS**: `NSUserDefaults` — persiste entre reinicios de la app

### Configurar nivel por defecto al inicializar

```kotlin
AppLoggerConfig.Builder()
    .defaultConsentLevel(ConsentLevel.STRICT)       // iniciar sin consentimiento
    .dataMinimizationEnabled(true)                   // default true — pseudonimizar en STRICT
    .build()
```

## Patrones de uso

### Patrón 1 — App con consent dialog al inicio

```kotlin
// En Application.onCreate() — sin consentimiento inicial
AppLoggerSDK.initialize(
    context = this,
    config = AppLoggerConfig.Builder()
        .defaultConsentLevel(ConsentLevel.STRICT)  // solo errores hasta obtener consent
        .build(),
    transport = transport
)

// Cuando el usuario acepta en el consent dialog:
AppLoggerSDK.setConsent(ConsentLevel.MARKETING)

// Cuando el usuario rechaza:
AppLoggerSDK.setConsent(ConsentLevel.STRICT)

// Cuando el usuario acepta solo T&C pero no marketing:
AppLoggerSDK.setConsent(ConsentLevel.PERFORMANCE)
```

### Patrón 2 — Scope con consent override

```kotlin
// Datos de performance que siempre son PERFORMANCE, aunque el nivel global sea STRICT
val perfLog = AppLoggerSDK.newScope(
    "component" to "network",
    consentLevel = ConsentLevel.PERFORMANCE
)
perfLog.info("NETWORK", "Request latency: 120ms")
// → emite incluso en STRICT global porque el scope requiere PERFORMANCE explícitamente
// ATENCIÓN: solo funciona si el scope consent >= nivel activo global
```

## Data minimization (GDPR Art. 5(1)(c))

Cuando `dataMinimizationEnabled = true` (default) y el nivel activo es `STRICT`:
- `user_id` → `null` automáticamente
- `device_id` → `sha256Hex(device_id)` pseudonimización irreversible

```kotlin
// Desactivar solo si tienes tu propio mecanismo de minimización:
AppLoggerConfig.Builder()
    .dataMinimizationEnabled(false)
    .build()
```
