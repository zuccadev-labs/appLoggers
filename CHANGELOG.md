# Changelog

All notable changes to AppLogger are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and the project adheres to [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Planned
- `logger-transport-firebase` module — transport to Firebase Realtime Database
- Support for `logger-transport-grpc` — direct delivery via gRPC to a custom server
- Wear OS support in `PlatformDetector`
- Web dashboard for real-time log visualization

---

## [SDK 0.2.0-alpha.9 + CLI 0.1.5] — 2026-03-24

### Added — SDK

**iOS KMP: paridad completa de features (100% Kotlin Multiplatform, cero Swift/Ruby):**
- **Device fingerprint** — `sha256Hex("$idfv:$bundleId")` via `UIDevice.currentDevice.identifierForVendor`. Automático al inicializar, consultable con `getDeviceFingerprint()`.
- **Remote config polling** — `startRemoteConfig(intervalSeconds)` / `stopRemoteConfig()`. Polling via `CoroutineScope(Dispatchers.Default + SupervisorJob())` con `delay` loop. HTTP via `NSURLSession` con `dispatch_semaphore`.
- **Beta tester** — `setBetaTester(email)` / `clearBetaTester()` inyecta `beta_tester_email` en global extras.
- **Consent management** — `setConsent(true/false)` persiste en `NSUserDefaults.standardUserDefaults`. Sin consentimiento el SDK no envía eventos.
- **Distributed tracing** — `setTraceId(id)` / `clearTraceId()` correlaciona eventos cross-device (mobile → TV → backend).
- **Breadcrumbs** — `recordBreadcrumb(label)` registra trail de navegación adjunto a cada evento posterior.
- **Scoped logger** — `scopedLogger(tag)` retorna logger con tag fijo para módulos o clases.
- **Session variant** — `setVariant(name)` / `clearVariant()` etiqueta la sesión para A/B testing.
- **Coroutine exception handler** — `AppLoggerExceptionHandler` captura excepciones no manejadas en coroutines.
- **System snapshot** — `NSProcessInfo.thermalState` + `lowPowerModeEnabled` en device info.
- **User properties** — `setUserId()` / `clearUserId()` para identificación opcional.

**Batch manifest metadata:**
- `BatchManifestCapable.storeBatchManifest()` ahora incluye `environment` y `sdkVersion` (parámetros con default, backward compatible).
- `BatchProcessor` extrae `environment` y `sdkVersion` del primer evento del batch y los propaga al manifest.
- `SupabaseTransport` envía `environment` y `sdk_version` en el payload de `log_batches`.

### Added — CLI

- **`--fingerprint` flag** en `telemetry query` — filtra logs por device fingerprint SHA-256 pseudonymizado via PostgREST JSONB: `extra->>device_fingerprint=eq.VALUE`. Solo disponible para `--source logs`.
- **`health --deep` probes ampliados** — ahora verifica 5 tablas: `app_logs`, `app_metrics`, `log_batches`, `device_remote_config`, `beta_testers`. Helper `probeTable()` reutiliza `queryTelemetry` con tabla overrideada.
- **`remote-config list`** — listar configuraciones remotas activas, filtrar por `--environment` y `--fingerprint`. Soporta `--output text|json|csv`.
- **`remote-config set`** — crear/actualizar configuración remota por fingerprint o global. Control de `minLevel`, `sampling`, `debugMode`, filtros de tags.
- **`remote-config delete`** — eliminar configuración remota por `--id` o `--fingerprint`.
- **`erase`** — borrado GDPR por `--user-id` o `--device-id` con limpieza de batch manifests. Soporta `--output text|json|agent`.
- **Agent schema actualizado** — `contract_version: 2.0.0` con `remote-config list/set/delete` y `erase` en la lista de comandos.

### Added — Migraciones

- **010** (`010_session_variant.sql`) — columna `variant` en `app_logs` para A/B testing.
- **011** (`011_batch_integrity.sql`) — tabla `log_batches` con `batch_id`, `batch_hash`, `event_count`, `environment`, `sdk_version`.
- **012** (`012_enterprise_indexes_views.sql`) — índices y vistas empresariales.
- **013** (`013_device_remote_config.sql`) — tablas `device_remote_config` y `device_fingerprints`.
- **014** (`014_beta_tester_correlation.sql`) — tabla `beta_testers` y correlación de eventos.

### Changed — Documentation

- **README.md**: sección "Características" ampliada con 13 nuevas features. Tabla de migraciones actualizada con 010-014. iOS quick start expandido con todas las nuevas APIs. Sección CLI ampliada con `remote-config` y `erase`.
- **CHANGELOG.md**: nueva entrada con todos los cambios de esta versión.
- **ios-kmp-setup.md**: secciones de fingerprint, beta tester, remote config, consent, tracing, breadcrumbs, scoped logger.
- **ios-kmp-patterns.md**: patrones de integración para las nuevas features.
- **checklist-ios-kmp.md**: checklist expandido con verificaciones de todas las nuevas features.
- **cli/README.md**: comandos `remote-config` y `erase`, flag `--fingerprint`.
- **14 skill/reference files actualizados** en sesión anterior con remote config, fingerprint, beta tester, auto-correlation.

---

## [Docs patch — 2026-03-23 auditoría completa]

### Fixed
- **README.md**: tabla "Backend — Supabase Setup" actualizada con migraciones 007, 008, 009. Sección "Documentación → Backend" actualizada con las mismas.
- **docs/ES/cli/README.md**: columnas `environment` y `anomaly_type` agregadas como top-level en tabla de `app_logs`. `anomaly_type` removido de `extra` (es columna top-level desde migración 007). Tabla de flags completada con `--min-severity`, `--environment`, `--sdk-version`, `--throwable`, `--extra-key/--extra-value`, `--offset`, `--order`. Modos de agregación `day`, `week`, `environment` agregados. Secciones `stream`, `tail`, `stats` agregadas con referencia completa de flags y campos de respuesta. Nota sobre `--throwable` en columnas sin flag CLI.
- **docs/ES/cli/SUPABASE_CONFIGURATION.md**: orden de migraciones actualizado con 007, 008, 009.
- **docs/ES/agents/applogger-supabase-mcp-configuration/references/mcp-configuration-flow.md**: migración 009 agregada al orden de aplicación.
- **docs/ES/agents/applogger-cli-live-configuration/references/cli-live-setup-runbook.md**: validación `health --deep` actualizada para mencionar migración 009.

---

## [CLI patch — 2026-03-23 ronda 4]

### Fixed
- **B1**: `supabaseHTTPClient` se creaba en cada intento del retry loop — el cliente ahora se crea una vez en `doQueryWithRetry` y se pasa a `doQuery`. El TCP connection pool se reutiliza entre reintentos.
- **B2**: Migración 009 creaba `idx_app_metrics_device_id` que ya existía desde migración 002 — eliminado el índice duplicado.
- **B3**: Override del default de `--limit` en `telemetry stats` usaba `cmd.Flags().Set()` con `_ = err` suprimiendo el error — reemplazado por acceso directo al `Flag.Value` con nil-check.
- **B4**: `doQueryWithRetry` no respetaba el header `Retry-After` de HTTP 429 — `classifyHTTPError` ahora retorna `retryAfterDuration` y el loop de retry lo aplica si es mayor que el backoff por defecto. Consistente con el comportamiento del SDK (`SupabaseTransport`).

---

## [CLI patch — 2026-03-23 ronda 3]

### Fixed
- **M1**: Comentario en migración 001 decía que `anomaly_type` era un campo JSONB conocido en `extra` — corregido para reflejar que fue promovido a columna top-level en migración 007.
- **C6**: `writeExampleConfigIfAbsent` creaba `cli.json` con permisos `0644` — corregido a `0600`. El archivo contiene `service_role key` y solo debe ser legible por el propietario.
- **C2**: `telemetry stats` sin `--from` ahora emite advertencia en stderr indicando que se están agregando todos los datos históricos y sugiriendo un rango temporal.

### Added
- **Migración 009** (`009_add_missing_indexes.sql`): índices faltantes para filtros frecuentes del CLI:
  - `idx_app_logs_sdk_version` — soporta `--sdk-version` en logs (partial index, excluye `0.0.0`)
  - `idx_app_logs_user_id` — soporta `--user-id` en logs (partial index, excluye NULLs)
  - `idx_app_metrics_sdk_version` — soporta `--sdk-version` en métricas (partial index)
  - `idx_app_metrics_device_id` — soporta `--device-id` en métricas
- **BEST_PRACTICES.md**: nota sobre permisos `0600` automáticos en `cli.json`; advertencia sobre `--from` obligatorio en `telemetry stats`; checklist actualizado a migraciones 001–009.

---

## [CLI 0.2.0] — 2026-03-23

### Added

**Nuevos comandos:**
- **`telemetry stream`** — emite un flujo continuo en formato `text/event-stream` (SSE) para consumo por frontends vía `EventSource`. Polling configurable (1..300s), cursor automático para evitar re-emisión de eventos, heartbeat entre polls, evento `error` sin crashear el stream.
- **`telemetry tail`** — modo follow en tiempo real, equivalente a `tail -f`. Imprime nuevos eventos en formato legible por humanos con columna de entorno. Polling configurable (1..60s).
- **`telemetry stats`** — resumen estadístico rápido: `total_events`, `error_rate_pct`, `by_severity`, `by_tag`, `by_hour`, `by_environment`, `by_name` (métricas). Útil para dashboards y agentes que necesitan contexto rápido.
- **`health --deep`** — deep probe real contra Supabase: verifica conectividad, mide latencia (`latency_ms`), confirma accesibilidad de `app_logs` y `app_metrics`. Retorna `status: degraded` si alguna tabla falla.

**Nuevos flags en `telemetry query` y `telemetry agent-response`:**
- **`--environment`** — filtra por entorno (`production|staging|development`) en logs y métricas. Columna `environment` ahora incluida en `SELECT` de ambas tablas.
- **`--min-severity`** — filtra logs en el nivel especificado o superior (ej: `--min-severity error` captura `ERROR` y `CRITICAL`). Mutuamente excluyente con `--severity`.
- **`--extra-key / --extra-value`** — filtro JSONB genérico ad-hoc sobre cualquier campo de `extra` (ej: `--extra-key screen_name --extra-value PlayerScreen`). Deben usarse juntos.
- **`--sdk-version`** — filtra eventos por versión del SDK que los generó (ej: `--sdk-version 0.2.0`).
- **`--throwable`** — incluye columnas `throwable_type`, `throwable_msg` y `stack_trace` en la respuesta (logs únicamente).
- **`--offset`** — paginación real basada en offset (ej: `--offset 100` para la segunda página). Permite exportar datasets > 1000 eventos.
- **`--order desc|asc`** — controla el orden de `created_at`. Default: `desc`. `asc` es el default interno para `stream` y `tail`.

**Nuevos modos de agregación:**
- **`--aggregate day`** — agrupa por día UTC (ej: `2026-03-23`).
- **`--aggregate week`** — agrupa por semana (lunes de la semana, ej: `2026-03-23`).
- **`--aggregate environment`** — agrupa por entorno (`production`, `staging`, `development`).

**Columnas añadidas al SELECT:**
- `app_logs`: `environment`, `anomaly_type` (columna top-level, nullable hasta SDK 0.3.0), `throwable_type`, `throwable_msg`, `stack_trace` (con `--throwable`).
- `app_metrics`: `environment` (columna nueva, migración 008), `tags` (columna existente — nombre correcto que el SDK escribe).

**Resiliencia:**
- Retry automático en HTTP 429 y 503 con backoff (0s → 2s → 6s, máximo 3 intentos).
- Errores HTTP diferenciados con mensajes accionables: 401 (credenciales), 403 (RLS), 404 (tabla no existe), 429 (rate limit), 503 (servicio caído).

**Refactoring:**
- Flags de telemetría extraídos a `addTelemetryFlags(cmd, flags)` — elimina la duplicación que existía entre `query` y `agent-response`.
- `agent schema` actualizado a `contract_version: 2.0.0` con todos los nuevos comandos documentados.
- `capabilities` actualizado con las 5 nuevas capacidades.

### Fixed (ronda 1)
- `anomaly_type` era filtrado via `extra->>anomaly_type`; corregido a columna top-level (requiere migración 007).
- `app_metrics` select corregido: el SDK escribe `tags` (no `metric_tags`) — el CLI ahora selecciona `tags` consistentemente con `SupabaseMetricEntry`.

### Fixed (ronda 2 — auditoría forense)
- **F1**: `stats` no usaba `addTelemetryFlags()` — ahora tiene todos los filtros estándar (`--environment`, `--min-severity`, `--session-id`, etc.).
- **F3**: Cursor SSE usaba `time.RFC3339Nano` — corregido a `time.RFC3339`. Lógica extraída a `advanceCursor()` helper compartido por `stream` y `tail`.
- **F4**: `tail` no soportaba `--output json` — ahora emite el response completo como JSON por poll.
- **F6**: `health --deep` tenía leak de contexto — `cancel()` ahora se llama explícitamente con `defer`.
- **F7**: `severityRank` incluía `"metric"` incorrectamente — eliminado. `--min-severity error` ya no incluye `METRIC`.
- **Q1**: `http.Client` se creaba en cada request — centralizado en `supabaseHTTPClient(cfg)` para reutilizar TCP connection pool.
- **Q2**: `marshalJSON` función muerta en `root.go` — eliminada.
- **Q3**: Lógica de cursor duplicada en `stream` y `tail` — extraída a `advanceCursor()` + `latestTimestamp()`.
- **Q4**: `writeSSEEvent`, `writeSSEHeartbeat`, `writeSSEError`, `printTailRow` usan `io.Writer` en lugar de interfaz inline.
- **Q7**: `downloadURL` en `upgrade.go` sin límite de body — protegido con `io.LimitReader(32MB)`.
- **I2**: `stream` y `tail` validan `--output` con `validateOutputFormat()`.
- **I3**: 6 tests nuevos en `contract_test.go`: `TestStatsHasAllTelemetryFlags`, `TestTailSupportsOutputJSON`, `TestStreamValidatesOutput`, `TestSeverityRankExcludesMetric`, `TestAdvanceCursorRFC3339`, `TestCapabilitiesIncludesUpgrade`.
- **I5/I6**: `upgrade` aparece en `agent schema` y `capabilities` — visible para agentes IA.

---

## [SDK 0.2.0] — 2026-03-23

### Added

**Core pipeline — nuevas capacidades:**
- **`AppLogger.addGlobalExtra(key, value)` / `removeGlobalExtra(key)` / `clearGlobalExtra()`** — adjunta pares clave-valor a todos los eventos posteriores. Per-call `extra` tiene precedencia en colisión de clave.
- **`AppLoggerConfig.environment`** — etiqueta de entorno (`"production"`, `"staging"`, `"development"`) adjunta a cada evento. Permite filtrar QA vs producción en Supabase JSONB.
- **`AppLoggerConfig.minLevel` (`LogMinLevel`)** — descarta eventos por debajo del nivel configurado antes del pipeline, sin coste de serialización ni red. METRIC siempre pasa.
- **`AppLoggerConfig.validate()`** — retorna lista de problemas de configuración accionables: endpoint en blanco, sin HTTPS en producción, apiKey sin formato JWT, environment en blanco, combinación batchSize/flushInterval problemática, `isDebugMode=true` con `environment="production"`.
- **`AppLoggerConfig.bufferSizeStrategy` (`BufferSizeStrategy`)** — `FIXED` (default), `ADAPTIVE_TO_RAM`, `ADAPTIVE_TO_LOG_RATE`.
- **`AppLoggerConfig.bufferOverflowPolicy` (`BufferOverflowPolicy`)** — `DISCARD_OLDEST` (default), `DISCARD_NEWEST`, `PRIORITY_AWARE`.
- **`AppLoggerConfig.offlinePersistenceMode` (`OfflinePersistenceMode`)** — `NONE` (default), `CRITICAL_ONLY`, `ALL`.
- **`AppLoggerHealthProvider` interface** — contrato inyectable para `AppLoggerHealth`. Permite `FakeHealthProvider` en tests sin depender del singleton.
- **`HealthStatus.lastSuccessfulFlushTimestamp`** — epoch millis del último flush exitoso. Detecta outages silenciosos.
- **`HealthStatus.snapshotTimestamp`** — epoch millis cuando se tomó el snapshot.
- **`HealthStatus.isStale(maxAgeMs)`** — retorna `true` si el snapshot tiene más de `maxAgeMs` ms.
- **`HealthStatus.consecutiveFailures`** — se resetea solo en flush completamente exitoso (no en éxito parcial).
- **`LogEvent.environment`** — campo de entorno en cada evento, propagado desde `AppLoggerConfig`.
- **`LogEvent.deviceId`** — identificador estable del dispositivo, separado del `userId` opcional.
- **`LogEvent.extra` como `Map<String, JsonElement>`** — tipos nativos (Int, Long, Double, Boolean) preservados como primitivos JSON. Elimina la conversión a String y habilita queries tipadas en Supabase JSONB.
- **`LogEvent.metricName/Value/Unit/Tags`** — campos tipados para eventos de tipo METRIC.
- **`TransportResult.Failure.retryAfterMs`** — delay mínimo antes del reintento. `BatchProcessor` lo respeta en lugar del backoff exponencial cuando está presente (ej. HTTP 429 con `Retry-After`).
- **`BatchProcessor` con `Mutex` (sendMutex)** — previene solapamiento de envíos concurrentes. Un solo `sendBatch()` activo a la vez.
- **`AppLoggerSDK.newSession()`** — fuerza inicio de nueva sesión inmediatamente (login/logout).
- **`AppLoggerSDK.setDeviceId()` / `clearDeviceId()`** — sobreescribe el device_id calculado por el SDK.
- **`AppLoggerIos.shared.newSession()`** — equivalente iOS de `newSession()`.
- **`AppLoggerIos.shared.addGlobalExtra/removeGlobalExtra/clearGlobalExtra()`** — global extra en iOS.
- **`androidNetworkAvailabilityProvider(context)`** — función pública que retorna una lambda de conectividad basada en `ConnectivityManager`. Pasar a `SupabaseTransport` para evitar retry loops offline.
- **iOS `connectionType` via `Network.framework`** — `IosDeviceInfoProvider` reporta el tipo de conexión real (WiFi, Cellular, etc.).
- **`APPLOGGER_DEBUG` en `Info.plist` (iOS)** — equivalente al manifest meta-data de Android. Activa debug mode sin cambiar código.
- **`SqliteOfflineStorage`** — campo `environment` persistido y restaurado correctamente en el schema SQLite.

**Extension functions (`AppLoggerExtensions.kt` — commonMain):**
- **`AppLogger.withTag(tag: String): TaggedLogger`** — wrapper con tag fijo para toda la clase.
- **`AppLogger.withTag(receiver: Any): TaggedLogger`** — infiere el tag del receiver.
- **`TaggedLogger`** — clase con métodos `d/i/w/e/c/metric/flush` que fijan el tag.
- **`AppLogger.timed(name, unit, tags, block): T`** — mide latencia del bloque y registra métrica.
- **`Any.timed(logger, name, unit, tags, block): T`** — igual con "source" tag automático.
- **`AppLogger.logCatching(tag, context, extra, block): T?`** — ejecuta bloque, loguea excepción como ERROR, retorna null si falla.
- **`Any.logCatching(logger, context, extra, block): T?`** — igual con tag inferido.
- **`AppLogger.logM(name, value, unit, tags)`** — shorthand para `metric()`.
- **`Any.logM(logger, name, value, unit, tags)`** — igual con "source" tag automático.
- **`inline fun <reified T : Any> loggerTag(): String`** — tag desde companion objects.

**Módulo `logger-test`:**
- **`InMemoryLogger.addGlobalExtra()`** — funcional (antes era no-op). Mezcla global extra en cada evento almacenado.
- **`FakeTransport.retryAfterMs`** — expone el campo para simular respuestas 429 con `Retry-After`.
- **`NoOpTestLogger`** — alias público documentado de `NoOpLogger` interno.

**Correcciones de bugs:**
- **`SqliteOfflineStorage`** — campo `environment` no se persistía ni restauraba. Corregido en schema `.sq` y en `persist()`/`drain()`.
- **`SampleApplication`** — credenciales hardcodeadas reemplazadas con `BuildConfig.LOGGER_URL` / `BuildConfig.LOGGER_KEY`.
- **`AppLoggerConfig.validate()`** — detecta `isDebugMode=true` con `environment="production"` como error de configuración.
- **HTTP 429/503/4xx/5xx diferenciados** en `SupabaseTransport` — 429 respeta `Retry-After`, 4xx no retryable, 5xx retryable.
- **`consecutiveFailures`** — no se resetea prematuramente; solo en flush completamente exitoso.
- **`APPLOGGER_DEBUG`** en Android — leído de `NSBundle.mainBundle.infoDictionary` en iOS (B1).

### Changed
- `AppLoggerConfig.Builder` — nuevos métodos: `environment()`, `minLevel()`, `bufferSizeStrategy()`, `bufferOverflowPolicy()`, `offlinePersistenceMode()`.
- `AppLoggerSDK.initialize()` — acepta `transport: LogTransport?` como parámetro (antes construía el transporte internamente).
- `AppLoggerHealth` — implementa `AppLoggerHealthProvider`. Todos los campos de `HealthStatus` disponibles.
- `LogEvent.extra` — tipo cambiado de `Map<String, String>?` a `Map<String, JsonElement>?`.

### Documentation
- `docs/ES/paquete/architecture.md` — reescritura completa (600 líneas): refleja SDK real al 100%.
- `docs/ES/desarrollo/integration-guide.md` — secciones 3.5, 4.1, 5, 6.3, 8, 9.2, 11.1 actualizadas.
- `docs/ES/paquete/testing.md` — reescritura completa: `NoOpTestLogger`, `FakeTransport` con `retryAfterMs`, `InMemoryLogger` con global extra, `FakeHealthProvider`.
- `docs/ES/agents/applogger-runtime-troubleshooting/references/health-diagnostics.md` — todos los campos de `HealthStatus`, `lastSuccessfulFlushTimestamp`, `isStale()`.
- `docs/ES/agents/applogger-production-hardening/references/runtime-tuning.md` — `minLevel`, `environment`, `offlinePersistenceMode`, perfiles por caso de uso.
- `docs/ES/agents/applogger-guided-setup/references/android-setup.md` — `environment()`, `validate()`, `androidNetworkAvailabilityProvider`, `newSession()`, `addGlobalExtra()`.
- `docs/ES/agents/applogger-guided-setup/references/ios-kmp-setup.md` — `APPLOGGER_DEBUG` en `Info.plist`, `newSession()`, `addGlobalExtra()`, `flush()` manual.
- `docs/ES/agents/applogger-instrumentation-design/references/event-taxonomy.md` — ejemplos reales con API actual, `timed{}`, `logCatching{}`.
- `docs/ES/agents/applogger-instrumentation-design/references/metric-guidelines.md` — `timed{}`, `logM`, `withTag`, catálogo completo.
- `docs/ES/agents/applogger-instrumentation-design/references/tag-conventions.md` — `loggerTag<T>()`, `withTag()`, `logTag()`, `TaggedLogger`.
- SKILL.md de `guided-setup`, `instrumentation-design`, `production-hardening`, `runtime-troubleshooting` — constraints actualizadas.

---

## [CLI 0.1.3] — 2026-03-22

### Added
- **Binary renamed to `apploggers`**: CLI binary, install scripts, and all references updated from `applogger-cli` to `apploggers`.
- **Bootstrap `~/.apploggers/cli.json`**: On first run the CLI now creates `~/.apploggers/` and writes a fully-commented example `cli.json` template (cross-platform: Windows, Linux, macOS).
- **Config auto-read**: CLI reads `~/.apploggers/cli.json` automatically; no `--config` flag required for the default path.
- **Updated install scripts**: `install.sh` and `install.ps1` both create the config dir, write the example config, and install the binary as `apploggers` / `apploggers.exe`.
- **Updated upgrade command**: `apploggers upgrade` now resolves tags with prefix `apploggers-v*`.
- **Updated package manager manifests**: Homebrew formula `Apploggers`, Scoop `apploggers.json`, Winget `DevZucca.AppLoggers`.

### Changed
- **CLI version bump**: Updated from v0.1.2 to v0.1.3.
- **Env vars for installer**: Renamed `APPLOGGER_CLI_*` → `APPLOGGERS_*` in both install scripts.
- **Documentation**: Updated `docs/ES/cli/README.md`, `docs/ES/cli/INSTALLATION.md`, and `docs/ES/agents/applogger-cli-agent-operator/SKILL.md` to reflect new binary name and version.

---

## [0.1.2] — 2026-03-22

### Added
- **Automatic `.apploggers` directory creation**: CLI now automatically creates the user config directory (`~/.apploggers`) on all platforms (Windows, Linux, macOS) during first run.
- **Enhanced directory creation logic**: Added `ensureConfigDir()` function with proper error handling and permissions (0755) for cross-platform compatibility.

### Changed
- **CLI version bump**: Updated from v0.1.1 to v0.1.2 across all configuration files.
- **Plugin metadata version**: Updated `plugin-metadata.yaml` to reflect v0.1.2.
- **Documentation updates**: Updated CLI README and agent documentation to reflect v0.1.2.

### Fixed
- **Config directory initialization**: Resolved issue where `.apploggers` directory was not created automatically, causing config file access failures on fresh installations.
- **Cross-platform compatibility**: Ensured directory creation works correctly on Windows (PowerShell/CMD), Linux (bash), and macOS (bash/zsh).

---

## [0.1.1-alpha.7] — 2026-03-22

### Changed
- **SDK version bump**: Updated from v0.1.1-alpha.6 to v0.1.1-alpha.7.
- **CI/CD workflows**: Fixed deprecated action versions (checkout@v4, setup-java@v4, setup-gradle@v4, upload-artifact@v4, codeql@v3).
- **Release workflow**: Added tag validation against gradle.properties, prerelease flag, and body_path for SDK releases.

---

## [0.1.1-alpha.6] — 2026-03-22

### Added
- **SDK identity contract extension**: logs and metrics now include `device_id` separately from optional anonymous `user_id`.
- **Anonymous ID normalization**: non-UUID anonymous IDs are normalized to deterministic UUID-compatible values in the SDK.
- **CLI segmentation filters**: telemetry query now supports `--device-id`, `--user-id`, `--package`, `--error-code`, and `--contains`.
- **Cross-platform user config bootstrap**: installers now create a standard user config directory (`~/.apploggers`) and initialize `cli.json` when missing.

### Changed
- **Default CLI project config path**: switched from `~/.applogger-cli/cli.json` to `~/.apploggers/cli.json` with legacy fallback support preserved.
- **MCP/agent guidance**: agent schema recommendations now explicitly require deterministic discovery flow (`capabilities -> agent schema -> health`) before telemetry queries.
- **Telemetry docs and skills alignment**: SDK/CLI docs now reflect flexible identity fields and advanced filtering for operational triage and user segmentation.
- **AppLogger local config naming**: references were normalized to uppercase keys (`APPLOGGER_URL`, `APPLOGGER_ANON_KEY`, `APPLOGGER_DEBUG`) across templates/docs/skills.

### Fixed
- **CLI contract coverage**: integration tests now validate identity filters and advanced log filters (`package`, `error_code`, `contains`) including source guardrails.

### Security
- **Installer baseline hardening preserved**: checksum verification remains mandatory while introducing config bootstrap behavior.


---

## [0.1.0-alpha.1] — 2026-03-17

### Added
- **`AppLogger` interface** — unified logging contract for Kotlin (Android / JVM / iOS).
- **`LogTransport` interface** — swappable transport abstraction (REST, gRPC, stdio).
- **`LogBuffer` interface** — temporary event storage with configurable overflow policy.
- **`LogFilter` interface** — event filtering with chain-of-responsibility support.
- **`LogFormatter` interface** — `LogEvent` serialization (JSON implementation included).
- **`DeviceInfoProvider` interface** — technical device metadata without PII.
- **`CrashHandler` interface** — `UncaughtException` capture with synchronous pre-death flush.
- **`AppLoggerImpl`** — core event pipeline with `Channel<LogEvent>` from Kotlin Coroutines.
- **`BatchProcessor`** — batch processor with size-based, time-based, and severity-based flush.
- **`RateLimitFilter`** — per-tag rate limiter with auto-bypass for ERROR and CRITICAL.
- **`NoOpLogger`** — empty implementation for pre-init state and tests.
- **`InMemoryLogger`** — test implementation with built-in assertions.
- **`FakeTransport`** — transport mock with success/failure control for tests.
- **`SupabaseTransport`** — Supabase (PostgreSQL) transport with `anon key` auth via Ktor.
- **`PlatformDetector`** — automatic detection of ANDROID_MOBILE, ANDROID_TV, WEAR_OS, JVM.
- **`SqliteOfflineBuffer`** — persistent FIFO buffer in SQLite for offline operation on Android TV.
- **`AppLoggerLifecycleObserver`** — automatic flush when app backgrounds.
- **`AppLoggerConfig.Builder`** — typed configuration builder with adaptive defaults per platform.
- **`AppLoggerSDK`** — public entry singleton (Android), idempotent initialization.
- **`AppLoggerIos`** — public iOS entry singleton for Kotlin Multiplatform (`iosMain`).
- **`logger-test` module** — testing utilities: `NoOpTestLogger`, `InMemoryLogger`, `FakeTransport`.
- **Privacy by design**: no PII captured, ephemeral `session_id`, optional `user_id` with consent.
- **Crash handler chaining**: SDK chains the previous handler, never replaces it.
- **Mandatory TLS**: builder rejects HTTP endpoints in production mode.
- **Complete documentation**: `docs/ES/desarrollo/`, `docs/ES/paquete/`.
- **SQL migrations**: `docs/ES/migraciones/001` a `docs/ES/migraciones/005` for PostgreSQL / Supabase.
- **CI/CD with GitHub Actions**: test workflows on PRs and automated release on tags.
- **Monorepo structure**: `sdk/`, `docs/ES/`, `docs/EN/`, `frontend/`, `cli/`.
- **JitPack publication**: all 3 modules with 6 KMP platform variants.
- **GitHub Packages publication**: automated via `release.yml` workflow on `v*` tags.
- **Professional README**: configuration guide, CI/CD docs, branching model.
- **`local.properties.example`**: onboarding template for new contributors.

### Security
- API keys never hardcoded: injected via `BuildConfig` from `local.properties` or CI env vars.
- Row Level Security in Supabase: `anon` role only has `INSERT` permission on `app_logs`.
- Production endpoint requires `https://` — validated at `Config` build time.
- **CodeQL security scanning** in CI workflow.
- **Dependency submission** to GitHub for vulnerability alerts.
