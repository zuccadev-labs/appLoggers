# Changelog

Todos los cambios notables de AppLogger se documentan aquí.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es/1.0.0/) y el proyecto adhiere a [Versionado Semántico](https://semver.org/lang/es/).

---

## [Unreleased]

### Planned
- Módulo `logger-transport-firebase` — transporte a Firebase Realtime Database
- Soporte para `logger-transport-grpc` — envío directo vía gRPC a un servidor custom
- Soporte Wear OS en `PlatformDetector`
- Dashboard web de visualización de logs en tiempo real

---

## [0.1.0-alpha.1] — 2026-03-17

### Added
- **`AppLogger` trait** — contrato público unificado de logging para Kotlin (Android / JVM).
- **`LogTransport` trait** — abstracción de transporte intercambiable (REST, gRPC, stdio).
- **`LogBuffer` trait** — almacenamiento temporal de eventos con política de overflow configurable.
- **`LogFilter` trait** — filtrado de eventos con soporte de cadena de responsabilidad.
- **`LogFormatter` trait** — serialización de `LogEvent` (implementación JSON incluida).
- **`DeviceInfoProvider` trait** — metadatos técnicos del dispositivo sin PII.
- **`CrashHandler` trait** — captura de `UncaughtException` con flush síncrono pre-muerte.
- **`AppLoggerImpl`** — implementación core del pipeline de eventos con `Channel<LogEvent>` de Kotlin Coroutines.
- **`BatchProcessor`** — procesador de batches con flush por tamaño, por tiempo y por criticidad.
- **`RateLimitFilter`** — limitador de rate por tag con bypass automático para `ERROR` y `CRITICAL`.
- **`NoOpLogger`** — implementación vacía para estado pre-inicialización y tests.
- **`InMemoryLogger`** — implementación de test con assertions integradas.
- **`FakeTransport`** — mock de transporte con control de éxito/fallo para tests.
- **`SupabaseTransport`** — transporte a Supabase (PostgreSQL) con autenticación por `anon key`.
- **`GrpcLoggingInterceptor`** — interceptor de `ClientInterceptor` para captura automática de anomalías gRPC.
- **`LoggingWebSocketListener`** — wrapper de `WebSocketListener` para captura de fallos de WebSocket.
- **`PlatformDetector`** — detección automática de `ANDROID_MOBILE`, `ANDROID_TV`, `WEAR_OS`, `JVM`.
- **`SqliteOfflineBuffer`** — buffer persistente FIFO en SQLite para funcionamiento offline en Android TV.
- **`AppLoggerLifecycleObserver`** — flush automático cuando la app entra en background.
- **`AppLoggerConfig.Builder`** — constructor de configuración tipado con valores por defecto adaptativos por plataforma.
- **`AppLoggerSDK`** — objeto singleton de entrada pública (Android), con inicialización idempotente.
- **Módulo `logger-test`** — utilidades de testing: `NoOpLogger`, `InMemoryLogger`, `FakeTransport`.
- **Política de privacidad por diseño**: sin captura de PII, `session_id` efímero, `user_id` opcional con consentimiento.
- **Retrocompatibilidad con crash handlers existentes**: el SDK encadena el handler previo, no lo reemplaza.
- **Soporte TLS obligatorio**: el builder rechaza endpoints HTTP en modo producción.
- **Documentación completa**: `docs/ES/desarrollo/`, `docs/ES/paquete/`.
- **Scripts SQL de migración**: `docs/ES/migraciones/001` a `docs/ES/migraciones/005` para PostgreSQL / Supabase.
- **CI/CD con GitHub Actions**: workflows de test en PRs y release automático en tags.

### Changed
— (primera versión)

### Deprecated
— (primera versión)

### Removed
— (primera versión)

### Fixed
— (primera versión)

### Security
- API keys nunca hardcodeadas: se inyectan vía `BuildConfig` desde `local.properties` o variables de entorno CI.
- Row Level Security en Supabase: el rol `anon` solo tiene permiso `INSERT` en `app_logs`.
- El endpoint de producción requiere `https://` — validado en tiempo de construcción del `Config`.

---

## Formato de Entradas

Para versiones futuras, cada entrada de cambio debe seguir este formato:

```markdown
## [X.Y.Z] — YYYY-MM-DD

### Added
- Nueva funcionalidad que se añadió.

### Changed
- Cambio en funcionalidad existente (no breaking).

### Deprecated
- Funcionalidad que será eliminada en la próxima versión mayor.

### Removed
- Funcionalidad eliminada en esta versión.

### Fixed
- Bug corregido. Referencia al issue: (#123)

### Security
- Corrección de vulnerabilidad de seguridad.
  NOTA: Los breaking changes deben marcarse con **BREAKING:** al inicio de la línea.
```

---

[Unreleased]: https://github.com/devzucca/appLoggers/compare/v0.1.0-alpha.1...HEAD
[0.1.0-alpha.1]: https://github.com/devzucca/appLoggers/releases/tag/v0.1.0-alpha.1
