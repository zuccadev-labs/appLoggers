# AppLogger

SDK de telemetría técnica estructurada para Kotlin Multiplatform — Android Mobile · Android TV · iOS · JVM.

Captura errores, crashes y métricas de performance de forma segura, sin impactar la UI ni comprometer la privacidad de los usuarios.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1+-purple.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-23+-brightgreen.svg)](https://android-arsenal.com/api?level=23)

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

## Estructura del Proyecto

```
app-logger/
├── logger-core/                    ← Módulo KMP principal (commonMain + androidMain + iosMain + jvmMain)
├── logger-transport-supabase/      ← Transporte a Supabase (KMP, intercambiable)
├── logger-test/                    ← Utilidades de testing (NoOpLogger, InMemoryLogger, FakeTransport)
├── migrations/                     ← Scripts SQL para Supabase/PostgreSQL
├── scripts/                        ← Scripts de publicación Gradle
├── .github/workflows/              ← CI/CD (test en PRs, release en tags)
├── docs-investigation/             ← Documentación de investigación
├── docs-develop/                   ← Guías de integración
└── docs-package/                   ← Arquitectura, testing, publicación
```

---

## Instalación

### Opción 1: JitPack (recomendado para comenzar)

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
    implementation("com.github.zuccadev.app-logger:logger-core:0.1.1")

    // Transporte Supabase (opcional — usar si tu backend es Supabase)
    implementation("com.github.zuccadev.app-logger:logger-transport-supabase:0.1.1")

    // Utilidades de testing (solo para tests)
    testImplementation("com.github.zuccadev.app-logger:logger-test:0.1.1")
}
```

### Opción 2: GitHub Packages

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/zuccadev/app-logger")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.zuccadev:logger-core:0.1.1")
    implementation("com.github.zuccadev:logger-transport-supabase:0.1.1")
}
```

### iOS (Swift Package Manager)

El módulo `logger-core` genera un XCFramework (`AppLogger.framework`) que se puede distribuir vía:
- GitHub Releases (descargar el .xcframework del release)
- Repositorio SPM dedicado

```swift
import AppLogger

// Inicialización en Swift
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
<!-- AndroidManifest.xml — ya incluidos en el SDK, solo verificar merge -->
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

// En tu AppDelegate o @main struct
AppLoggerIos.shared.initialize(config: ...)

// Uso
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

## Configuración

```properties
# local.properties (NO commitear)
appLogger.url=https://tu-proyecto.supabase.co
appLogger.anonKey=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
appLogger.debug=true
appLogger.logToConsole=true
appLogger.batchSize=20
appLogger.flushIntervalSeconds=30
appLogger.maxStackTraceLines=50
appLogger.lowStorageMode=false
```

### Mapear a BuildConfig

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

## Backend — Supabase Setup

1. Crea un proyecto en [supabase.com](https://supabase.com)
2. Ejecuta las migraciones SQL en orden:

```bash
# En el SQL Editor de Supabase, ejecutar en orden:
migrations/001_create_app_logs.sql
migrations/002_create_app_metrics.sql
migrations/003_create_indexes.sql
migrations/004_rls_policies.sql
migrations/005_retention_policy.sql
```

3. Copia la **URL del proyecto** y la **anon key** a tu `local.properties`

---

## Publicar el SDK

### Paso 1: Crear un release tag

```bash
git tag -a v0.1.1 -m "Release 0.1.1"
git push origin v0.1.1
```

### Paso 2: JitPack (automático)

1. Ve a [jitpack.io](https://jitpack.io)
2. Busca `zuccadev/app-logger`
3. Haz clic en **Get it** junto al tag `v0.1.1`
4. JitPack construye el artefacto automáticamente

### Paso 3: GitHub Packages (CI/CD)

El workflow `.github/workflows/release.yml` publica automáticamente al crear un tag `v*`:

```bash
git tag -a v0.1.1 -m "Release 0.1.1"
git push origin v0.1.1
# → GitHub Actions ejecuta tests + publica a GitHub Packages
```

### Paso 4: Maven Central (cuando esté estable)

```bash
# Requiere cuenta en Sonatype + claves GPG
export OSSRH_USERNAME="tu-usuario"
export OSSRH_PASSWORD="tu-password"
export GPG_SIGNING_KEY="tu-clave-gpg"
export GPG_SIGNING_PASSWD="tu-passphrase"

./gradlew publish
```

---

## Testing

El módulo `logger-test` provee utilidades para testeo sin red ni dispositivo real.

```kotlin
// Verificar que un componente loguea correctamente
val logger = InMemoryLogger()
myComponent.doSomething()
assertEquals(1, logger.errorCount)
logger.assertLogged(LogLevel.ERROR, tag = "PAYMENT")
logger.assertNotLogged(LogLevel.DEBUG) // debug no se envía en producción

// FakeTransport para verificar envíos al backend
val transport = FakeTransport(shouldSucceed = true)
// ... usar transport en configuración de tests ...
assertEquals(3, transport.sentEvents.size)

// Simular fallo de red
val failingTransport = FakeTransport(shouldSucceed = false, retryable = true)

// NoOpTestLogger para tests donde el logger no es el foco
val logger = NoOpTestLogger()
```

### Correr tests

```bash
# Tests unitarios (sin red ni dispositivo)
./gradlew check

# Solo tests de integración (requiere Supabase staging)
./gradlew jvmTest -Pintegration \
  -PSUPABASE_STAGING_URL="https://staging.supabase.co" \
  -PSUPABASE_STAGING_ANON_KEY="eyJ..."
```

Ver documentación completa de testing en [docs-package/testing.md](docs-package/testing.md).

---

## Documentación

| Documento | Descripción |
|---|---|
| [Investigación Técnica](docs-investigation/investigation.md) | Decisiones arquitectónicas, estándares, diseño KMP |
| [Base de Datos](docs-investigation/db-migration.md) | Esquema PostgreSQL, RLS, retención, migraciones |
| [Privacidad](docs-investigation/privacy-compliance.md) | GDPR/LGPD/CCPA, clasificación de datos |
| [Guía de Integración](docs-develop/integration-guide.md) | Cómo integrar el SDK en Android e iOS |
| [App de Monitoreo](docs-develop/monitoring-app.md) | App externa para visualizar logs |
| [Compatibilidad](docs-develop/api-compatibility.md) | Matriz de versiones soportadas |
| [Arquitectura](docs-package/architecture.md) | Traits, módulos KMP, pipeline |
| [Testing](docs-package/testing.md) | Estrategia de tests, FakeTransport |
| [Publicación](docs-package/publishing.md) | JitPack, GitHub Packages, Maven Central |
| [Contribuir](docs-package/CONTRIBUTING.md) | Guía para contribuir al proyecto |
| [Changelog](docs-package/CHANGELOG.md) | Historial de versiones |

## Plataformas Soportadas

| Plataforma | Mínimo | Compilación KMP | Estado |
|---|---|---|---|
| Android Mobile | API 23 (6.0) | `androidMain` | ✅ Soportado |
| Android TV | API 23 (6.0) | `androidMain` | ✅ Soportado |
| iOS | iOS 15 | `iosMain` → XCFramework | ✅ Soportado |
| JVM | JDK 11 | `jvmMain` | ✅ Soportado |

## Versiones de Dependencias

| Dependencia | Versión | Notas |
|---|---|---|
| Kotlin | 2.1.0 | KMP con K2 compiler |
| AGP | 8.7.3 | Compatible con Gradle 8.10+ |
| Coroutines | 1.9.0 | Channel, Dispatchers.IO |
| Serialization | 1.7.3 | kotlinx.serialization.json |
| Ktor | 2.3.12 | HTTP client multiplataforma |
| SQLDelight | 2.0.2 | SQLite multiplataforma |
| Lifecycle | 2.8.7 | ProcessLifecycleOwner (flush en background) |
| Gradle | 8.10.2 | Wrapper distribuido |

Ver todas las versiones en [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Licencia

[MIT](LICENSE)
