# AppLoggers

Monorepo de telemetría técnica — **SDK** · Frontend · CLI.

[![CI](https://github.com/devzucca/appLoggers/actions/workflows/ci.yml/badge.svg)](https://github.com/devzucca/appLoggers/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/devzucca/appLoggers.svg)](https://jitpack.io/#devzucca/appLoggers)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1+-purple.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/Android_API-23+-brightgreen.svg)](https://developer.android.com/about/versions/marshmallow)

---

## Índice

- [Estructura del Repositorio](#estructura-del-repositorio)
- [Características](#características)
- [⚙️ Configuración del Entorno](#configuración-del-entorno)
- [Instalación](#instalación)
- [Inicio Rápido](#inicio-rápido)
- [Backend — Supabase Setup](#backend--supabase-setup)
- [CI/CD — Automatización](#cicd--automatización)
- [Flujo de Ramas (Branching)](#flujo-de-ramas-branching)
- [Testing](#testing)
- [Publicar el SDK](#publicar-el-sdk)
- [Documentación](#documentación)
- [Plataformas Soportadas](#plataformas-soportadas)
- [Dependencias](#dependencias)
- [Licencia](#licencia)

---

## Estructura del Repositorio

```
appLoggers/
├── sdk/                            ← SDK Kotlin Multiplatform (Android · iOS · JVM)
│   ├── logger-core/                ← Módulo principal
│   ├── logger-transport-supabase/  ← Transporte a Supabase
│   ├── logger-test/                ← Utilidades de testing
│   ├── sample/                     ← App Android de ejemplo
│   └── build.gradle.kts            ← Build raíz del SDK
├── docs/
│   ├── ES/                         ← Documentación en español
│   │   ├── desarrollo/             ← Guías de integración
│   │   ├── paquete/                ← Arquitectura, testing, publicación
│   │   └── migraciones/            ← Scripts SQL para Supabase/PostgreSQL
│   └── EN/                         ← (Próximamente) Documentación en inglés
├── .github/workflows/              ← CI/CD (lint · test · e2e · security · release)
├── frontend/                       ← (Próximamente) Dashboard de monitoreo
└── cli/                            ← (Próximamente) Herramienta de línea de comandos
```

---

## Características

- **Kotlin Multiplatform** — un codebase para Android, iOS y JVM
- **Trait-based architecture** — interfaces intercambiables, Clean Code, SOLID
- **Fire-and-forget** — no bloquea el hilo llamador
- **Adaptativo** — detecta Mobile vs TV y ajusta automáticamente
- **Privacidad por diseño** — GDPR, LGPD, CCPA compliant
- **gRPC y WebSocket** — interceptores para flujos de alta velocidad
- **Offline-first** — SQLite circular FIFO cuando no hay red
- **Batería inteligente** — adapta flush según tipo de red y nivel de batería

---

## ⚙️ Configuración del Entorno

> **Esta sección es obligatoria antes de compilar o contribuir al proyecto.**

### Prerrequisitos

| Herramienta | Versión mínima | Verificar con |
|---|---|---|
| JDK | 17 (Temurin recomendado) | `java -version` |
| Android SDK | API 35 (compileSdk) | Android Studio → SDK Manager |
| Gradle | 8.10.2 (usa el wrapper) | `cd sdk && ./gradlew --version` |
| Git | 2.30+ | `git --version` |

### Paso 1 — Clonar el repositorio

```bash
git clone https://github.com/devzucca/appLoggers.git
cd appLoggers
```

### Paso 2 — Crear `local.properties`

El archivo `local.properties` contiene configuración local y **secrets** que nunca deben commitearse.  
Un template está incluido en el repositorio:

```bash
cp local.properties.example local.properties
```

Luego abrí `local.properties` y completá los valores:

```properties
# ── Android SDK ──────────────────────────────────────────────────────────
# Android Studio lo configura automáticamente.
# Solo modificar si tu SDK está en una ruta custom.
sdk.dir=C:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk

# ── Supabase (backend de logs) ──────────────────────────────────────────
# Obtener de: https://supabase.com/dashboard → Settings → API
appLogger.url=https://TU-PROYECTO.supabase.co
appLogger.anonKey=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

# ── Modo Debug ──────────────────────────────────────────────────────────
# true  → logs en Logcat + envío al backend (desarrollo)
# false → solo envío al backend, sin output local (producción)
appLogger.debug=true
```

| Variable | Obligatoria | Dónde obtenerla |
|---|:---:|---|
| `sdk.dir` | ✅ | Android Studio lo autocompleta, o ver `ANDROID_HOME` |
| `appLogger.url` | ✅ | [Supabase Dashboard](https://supabase.com/dashboard) → Settings → API → Project URL |
| `appLogger.anonKey` | ✅ | Supabase Dashboard → Settings → API → `anon` `public` key |
| `appLogger.debug` | ❌ | `true` para desarrollo, `false` para producción (default: `false`) |

> ⚠️ **`local.properties` está en `.gitignore`** — nunca se sube al repositorio.  
> Si clonás el repo y no existe, copiá desde `local.properties.example`.

### Paso 3 — Compilar y verificar

```bash
cd sdk
./gradlew check      # Lint (Detekt) + Tests unitarios
./gradlew assemble   # Compilar todos los módulos
```

### Mapear secrets a BuildConfig (para apps Android)

Si usás el SDK en tu propia app, mapeá las variables de `local.properties` a `BuildConfig`:

```kotlin
// app/build.gradle.kts
import java.util.Properties

android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        val props = Properties().apply {
            val file = rootProject.file("local.properties")
            if (file.exists()) load(file.inputStream())
        }
        buildConfigField("String",  "LOGGER_URL",  "\"${props["appLogger.url"] ?: ""}\"")
        buildConfigField("String",  "LOGGER_KEY",  "\"${props["appLogger.anonKey"] ?: ""}\"")
        buildConfigField("Boolean", "LOGGER_DEBUG", "${props["appLogger.debug"] ?: false}")
    }
}
```

---

## Instalación

### Opción 1: JitPack (recomendado)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    // Core del logger (obligatorio)
    implementation("com.github.devzucca.appLoggers:logger-core:v0.1.0-alpha.1")

    // Transporte Supabase (opcional — si tu backend es Supabase)
    implementation("com.github.devzucca.appLoggers:logger-transport-supabase:v0.1.0-alpha.1")

    // Utilidades de testing (solo para tests)
    testImplementation("com.github.devzucca.appLoggers:logger-test:v0.1.0-alpha.1")
}
```

### Opción 2: GitHub Packages

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/devzucca/appLoggers")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.devzucca:logger-core:0.1.0-alpha.1")
    implementation("com.github.devzucca:logger-transport-supabase:0.1.0-alpha.1")
}
```

### iOS (Swift Package Manager)

`logger-core` genera un XCFramework (`AppLogger.framework`) distribuible vía GitHub Releases o repositorio SPM.

```swift
import AppLogger

AppLoggerIos.shared.initialize(
    config: AppLoggerConfig.Builder()
        .endpoint(endpoint: "https://tu-proyecto.supabase.co")
        .apiKey(key: "tu_anon_key")
        .debugMode(debug: false)
        .build()
)
```

### Permisos Android

```xml
<!-- Ya incluidos en el SDK — solo verificar que el manifest merge funcione -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## Inicio Rápido

### Android

```kotlin
// 1. Application.kt — inicializar una sola vez
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val transport = SupabaseTransport(
            endpoint = BuildConfig.LOGGER_URL,
            apiKey = BuildConfig.LOGGER_KEY
        )

        AppLoggerSDK.initialize(
            context = this,
            config = AppLoggerConfig.Builder()
                .endpoint(BuildConfig.LOGGER_URL)
                .apiKey(BuildConfig.LOGGER_KEY)
                .debugMode(BuildConfig.DEBUG)
                .batchSize(20)
                .flushIntervalSeconds(30)
                .build(),
            transport = transport
        )
    }
}

// 2. Declarar en AndroidManifest.xml
// <application android:name=".MyApp" ... >

// 3. Usar en cualquier lugar — fire-and-forget
AppLoggerSDK.error("PAYMENT", "Transaction failed", throwable)
AppLoggerSDK.info("PLAYER", "Playback started", extra = mapOf("content_id" to "movie_123"))
AppLoggerSDK.warn("NETWORK", "Slow response", anomalyType = "HIGH_LATENCY")
AppLoggerSDK.metric("screen_load_time", 1234.0, "ms", tags = mapOf("screen" to "Home"))
AppLoggerSDK.debug("TAG", "Solo visible en debug")
```

### iOS (Swift)

```swift
import AppLogger

AppLoggerIos.shared.initialize(config: ...)

AppLoggerIos.shared.error(tag: "PLAYER", message: "Playback failed")
AppLoggerIos.shared.metric(name: "buffer_time", value: 420.0, unit: "ms")
```

### gRPC — Interceptor automático

```kotlin
val channel = ManagedChannelBuilder
    .forAddress("api.tuapp.com", 443)
    .useTransportSecurity()
    .intercept(
        GrpcLoggingInterceptor(
            logger = AppLoggerSDK,
            latencyThresholdMs = 500
        )
    )
    .build()
```

### WebSocket — Listener wrapper

```kotlin
val loggingListener = LoggingWebSocketListener(
    delegate = tuWebSocketListener,
    logger = AppLoggerSDK,
    tag = "WS_STREAM"
)
okHttpClient.newWebSocket(request, loggingListener)
```

---

## Backend — Supabase Setup

1. Creá un proyecto en [supabase.com](https://supabase.com)
2. Ejecutá las migraciones SQL en orden desde el **SQL Editor**:

| Orden | Archivo | Descripción |
|:---:|---|---|
| 1 | `docs/ES/migraciones/001_create_app_logs.sql` | Tabla principal de logs |
| 2 | `docs/ES/migraciones/002_create_app_metrics.sql` | Tabla de métricas |
| 3 | `docs/ES/migraciones/003_create_indexes.sql` | Índices de performance |
| 4 | `docs/ES/migraciones/004_rls_policies.sql` | Políticas de seguridad (RLS) |
| 5 | `docs/ES/migraciones/005_retention_policy.sql` | Retención automática de datos |

3. Copiá la **URL del proyecto** y la **anon key** a tu `local.properties`

---

## CI/CD — Automatización

El proyecto tiene automatización completa. **Cada push ejecuta el pipeline automáticamente.**

### ¿Qué pasa cuando hago `git push`?

| Evento | Pipeline | Jobs que se ejecutan |
|---|---|---|
| **Push a `main`** | CI completo | `lint` → `test` → `e2e` → `security` |
| **Push a `dev`** | CI sin E2E | `lint` → `test` → `security` |
| **Pull Request** | CI sin E2E | `lint` → `test` → `security` |
| **Push tag `v*`** | Release | `test` → `publish` → GitHub Release |

### Detalle de cada job

| Job | Qué hace | Duración aprox. |
|---|---|---|
| **lint** | Detekt — análisis estático de código | ~2 min |
| **test** | Tests unitarios + cobertura (JaCoCo + Codecov) + build | ~5 min |
| **e2e** | Tests de integración contra Supabase real | ~3 min |
| **security** | CodeQL + análisis de dependencias | ~4 min |
| **publish** | Publica a GitHub Packages + genera Dokka docs + crea Release | ~5 min |

### GitHub Secrets requeridos

Para que el pipeline funcione al 100%, configurá estos secrets en **GitHub → Settings → Secrets and variables → Actions**:

| Secret | Requerido por | Dónde obtenerlo |
|---|---|---|
| `APPLOGGER_SUPABASE_URL` | Job `e2e` | Supabase Dashboard → Settings → API → Project URL |
| `APPLOGGER_SUPABASE_ANON_KEY` | Job `e2e` | Supabase Dashboard → Settings → API → `anon` `public` key |
| `APPLOGGER_SUPABASE_SERVICE_KEY` | Job `e2e` | Supabase Dashboard → Settings → API → `service_role` key |
| `CODECOV_TOKEN` | Job `test` (opcional) | [codecov.io](https://codecov.io) → Settings → Token |

> Si los secrets de Supabase no están configurados, los jobs `lint`, `test` y `security` pasan normalmente — solo `e2e` fallará.

---

## Flujo de Ramas (Branching)

```
main      ← Código estable. Releases se tagean desde acá.
  └── dev ← Desarrollo activo. CI corre en cada push.
       ├── feature/nueva-funcionalidad
       ├── fix/corregir-bug
       └── docs/mejorar-readme
```

| Rama | Propósito | Push directo | CI |
|---|---|:---:|---|
| `main` | Producción / releases | ❌ Solo vía PR desde `dev` | Completo (lint + test + e2e + security) |
| `dev` | Desarrollo activo | ✅ | lint + test + security |
| `feature/*` | Nuevas funcionalidades | ✅ | CI al abrir PR contra `dev` |
| `fix/*` | Correcciones | ✅ | CI al abrir PR contra `dev` |

### Flujo de release

```bash
# 1. Trabajo diario en dev
git checkout dev
git push origin dev              # → CI: lint + test + security

# 2. Cuando dev esté estable → PR a main
# GitHub → New Pull Request → dev → main

# 3. Después del merge → crear tag para release
git checkout main
git pull
git tag -a v0.2.0 -m "Release 0.2.0"
git push origin v0.2.0          # → Release pipeline: test + publish + GitHub Release
```

---

## Testing

El módulo `logger-test` provee utilidades para testing sin red ni dispositivo real.

```kotlin
// Verificar que un componente loguea correctamente
val logger = InMemoryLogger()
myComponent.doSomething()
assertEquals(1, logger.errorCount)
logger.assertLogged(LogLevel.ERROR, tag = "PAYMENT")
logger.assertNotLogged(LogLevel.DEBUG)

// FakeTransport para verificar envíos al backend
val transport = FakeTransport(shouldSucceed = true)
assertEquals(3, transport.sentEvents.size)

// Simular fallo de red
val failingTransport = FakeTransport(shouldSucceed = false, retryable = true)

// NoOpTestLogger para tests donde el logger no es el foco
val logger = NoOpTestLogger()
```

### Correr tests

```bash
cd sdk

# Tests unitarios (sin red ni dispositivo)
./gradlew check

# Solo E2E (requiere Supabase configurado en local.properties o env vars)
./gradlew :logger-transport-supabase:jvmTest
```

Ver documentación completa en [docs/ES/paquete/testing.md](docs/ES/paquete/testing.md).

---

## Publicar el SDK

### JitPack (automático con tags)

JitPack construye automáticamente cuando se crea un tag o cuando alguien solicita el artefacto:

```kotlin
// Usar en cualquier proyecto
implementation("com.github.devzucca.appLoggers:logger-core:v0.1.0-alpha.1")
```

### GitHub Packages (CI/CD)

El workflow `release.yml` publica automáticamente al crear un tag `v*`:

```bash
git tag -a v0.1.0-alpha.1 -m "Release 0.1.0-alpha.1"
git push origin v0.1.0-alpha.1
# → GitHub Actions: tests + publish + GitHub Release
```

### Maven Central (futuro)

Cuando el SDK esté estable, se publicará a Maven Central para distribución sin repositorios custom.

---

## Documentación

| Documento | Descripción |
|---|---|
| [Guía de Integración](docs/ES/desarrollo/integration-guide.md) | Cómo integrar el SDK en Android e iOS |
| [App de Monitoreo](docs/ES/desarrollo/monitoring-app.md) | App externa para visualizar logs |
| [Compatibilidad](docs/ES/desarrollo/api-compatibility.md) | Matriz de versiones soportadas |
| [Arquitectura](docs/ES/paquete/architecture.md) | Traits, módulos KMP, pipeline |
| [Testing](docs/ES/paquete/testing.md) | Estrategia de tests, FakeTransport |
| [Publicación](docs/ES/paquete/publishing.md) | JitPack, GitHub Packages, Maven Central |
| [Contribuir](docs/ES/paquete/CONTRIBUTING.md) | Guía para contribuir al proyecto |
| [Changelog](CHANGELOG.md) | Historial de versiones |

---

## Plataformas Soportadas

| Plataforma | Mínimo | Target KMP | Estado |
|---|---|---|---|
| Android Mobile | API 23 (6.0) | `androidMain` | ✅ |
| Android TV | API 23 (6.0) | `androidMain` | ✅ |
| iOS | iOS 15+ | `iosMain` → XCFramework | ✅ |
| JVM | JDK 11+ | `jvmMain` | ✅ |

---

## Dependencias

| Dependencia | Versión | Notas |
|---|---|---|
| Kotlin | 2.1.0 | KMP con K2 compiler |
| AGP | 8.7.3 | Compatible con Gradle 8.10+ |
| Coroutines | 1.9.0 | Channel, Dispatchers.IO |
| Serialization | 1.7.3 | kotlinx.serialization.json |
| Ktor | 2.3.12 | HTTP client multiplataforma |
| SQLDelight | 2.0.2 | SQLite multiplataforma |
| Lifecycle | 2.8.7 | ProcessLifecycleOwner |
| Gradle | 8.10.2 | Wrapper incluido |

Ver catálogo completo en [`sdk/gradle/libs.versions.toml`](sdk/gradle/libs.versions.toml).

---

## Licencia

[MIT](LICENSE) — DevZucca
