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
