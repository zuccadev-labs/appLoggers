# iOS KMP Integration Patterns

## Good places to initialize

1. Shared Kotlin bootstrap used by the iOS app startup.
2. A dedicated `iosMain` initializer object.
3. Shared telemetry facade exposed from Kotlin.

## Good first logging points

1. Shared module initialization completed.
2. API failure surfaced through shared code.
3. Critical user flow failure handled in Kotlin.

## Health verification

Use `AppLoggerHealth.snapshot()` from Kotlin after initialization and after one emitted event.

## Device fingerprint integration

```kotlin
// El fingerprint se calcula automáticamente al inicializar
// SHA-256 de IDFV:bundleId — estable por app+dispositivo
val fingerprint = AppLoggerIos.shared.getDeviceFingerprint()

// Se inyecta en extra.device_fingerprint de cada evento
// El CLI filtra con: --fingerprint "sha256..."
```

## Remote config integration

```kotlin
// Iniciar después de initialize() — permite control remoto del SDK
AppLoggerIos.shared.startRemoteConfig(intervalSeconds = 300)

// El SDK consulta device_remote_config en Supabase por fingerprint
// Ajusta: minLevel, sampling, debugMode, tagFilter
// Detener en shutdown:
AppLoggerIos.shared.stopRemoteConfig()
```

## Consent flow

```kotlin
// Mostrar UI de consentimiento al primer lanzamiento
// Persistir decisión:
AppLoggerIos.shared.setConsent(userAccepted)

// Verificar antes de habilitar features avanzadas
// Sin consentimiento, el SDK descarta eventos silenciosamente
```

## Beta tester setup

```kotlin
// Marcar dispositivo como beta tester (para filtrado en CLI/dashboard)
AppLoggerIos.shared.setBetaTester("qa-team@company.com")

// Remover al salir de beta
AppLoggerIos.shared.clearBetaTester()
```

## Distributed tracing pattern

```kotlin
// Al iniciar un flujo cross-device (ej: casting desde mobile a TV)
val traceId = generateTraceId() // UUID o ID de negocio
AppLoggerIos.shared.setTraceId(traceId)

// Compartir traceId con el otro dispositivo via API
// Ambos dispositivos emiten eventos con el mismo trace_id
// CLI: --extra-key trace_id --extra-value "order-abc-123"

// Al finalizar el flujo
AppLoggerIos.shared.clearTraceId()
```

## Breadcrumb pattern

```kotlin
// Registrar navegación del usuario
AppLoggerIos.shared.recordBreadcrumb("HomeScreen")
AppLoggerIos.shared.recordBreadcrumb("SearchScreen → query='action movies'")
AppLoggerIos.shared.recordBreadcrumb("PlayerScreen → contentId=movie_123")

// Cuando ocurre un error, los breadcrumbs se adjuntan al evento
// Facilita debugging: ver el camino del usuario antes del crash
```

## Scoped logger pattern

```kotlin
// En módulos grandes, usar scoped logger para consistencia de tags
class PaymentManager {
    private val log = AppLoggerIos.shared.scopedLogger("PAYMENT")

    fun processPayment() {
        log.info("Payment started")
        // ...
        log.error("Payment failed", throwable = e)
    }
}
```

## A/B testing pattern

```kotlin
// Asignar variante al inicio de sesión
AppLoggerIos.shared.setVariant("checkout_v2")

// Todos los eventos de la sesión incluyen variant="checkout_v2"
// Analizar en CLI: --extra-key variant --extra-value "checkout_v2"
```

## Common review questions

1. Is the project truly KMP-first for iOS? (Must be — no Swift implementation files)
2. Where are remote endpoint and keys injected?
3. Should logging happen directly in `iosMain` or in shared abstractions?
4. Is consent requested before initializing the logger?
5. Is remote config started after initialization?
6. Are breadcrumbs recorded at screen transitions?
7. Is flush() called when the app enters background?
