# AppLogger

[![JitPack](https://jitpack.io/v/TuOrganizacion/app-logger.svg)](https://jitpack.io/#TuOrganizacion/app-logger)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-22+-brightgreen.svg)](https://android-arsenal.com/api?level=22)

SDK de telemetría técnica estructurada para Kotlin — Android Mobile · Android TV · JVM.

Captura errores, crashes y métricas de performance de forma segura, sin impactar la UI ni comprometer la privacidad de los usuarios.

---

## ¿Por qué AppLogger?

| Problema | Solución |
|---|---|
| El tester dice "se cerró" sin contexto | Crash capturado + metadatos técnicos + sesión |
| El logger satura la red en gRPC/WebSocket | Interceptores con umbrales de anomalía, sin log de llamadas normales |
| El SDK mata la app en Android TV | Buffer adaptativo, batch pequeño, flush solo en idle |
| Cambiar de Supabase a otro backend | Implementar `LogTransport` — la app no se entera |
| GDPR / LGPD | Sin PII por defecto, session_id efímero, user_id opcional con consentimiento |

---

## Instalación

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.TuOrganizacion:app-logger:0.1.1")
}
```

---

## Inicio Rápido

```kotlin
// Application.kt
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLoggerSDK.initialize(
            context = this,
            config = AppLoggerConfig.Builder()
                .endpoint(BuildConfig.LOGGER_URL)
                .apiKey(BuildConfig.LOGGER_KEY)
                .debugMode(BuildConfig.DEBUG)
                .build()
        )
    }
}

// Cualquier lugar de la app
AppLoggerSDK.error("PAYMENT", "Transaction failed", throwable)
AppLoggerSDK.info("PLAYER", "Playback started", extra = mapOf("content_id" to "movie_123"))
AppLoggerSDK.metric("screen_load_time", 1234.0, "ms")
```

---

## Documentación

| Documento | Descripción |
|---|---|
| [Investigación Técnica](docs-investigation/investigation.md) | Contexto, estándares de referencia, decisiones arquitectónicas |
| [Base de Datos y Migraciones](docs-investigation/db-migration.md) | Esquema PostgreSQL/Supabase, RLS, retención, consultas |
| [Privacidad y Cumplimiento](docs-investigation/privacy-compliance.md) | GDPR/LGPD, clasificación de datos, checklist |
| [Guía de Integración](docs-develop/integration-guide.md) | Cómo integrar el SDK en una app, gRPC, WebSocket, Android TV |
| [Arquitectura del Paquete](docs-package/architecture.md) | Traits, módulos, pipeline, extensibilidad |
| [Testing](docs-package/testing.md) | Tests unitarios, FakeTransport, casos de resiliencia |
| [Publicación](docs-package/publishing.md) | JitPack, GitHub Packages, Maven Central, CI/CD |
| [CONTRIBUTING](docs-package/CONTRIBUTING.md) | Guía para contribuir al proyecto |
| [CHANGELOG](docs-package/CHANGELOG.md) | Historial de versiones |

---

## Diseño por Traits — Arquitectura Extensible

AppLogger está construido sobre interfaces (traits) independientes. Cada componente es reemplazable:

```
AppLoggerSDK
    └── AppLoggerImpl
            ├── DeviceInfoProvider  →  AndroidDeviceInfoProvider (o custom)
            ├── LogFilter           →  RateLimitFilter (o chain de filtros)
            ├── LogBuffer           →  InMemoryBuffer / SqliteOfflineBuffer
            ├── LogFormatter        →  JsonLogFormatter (o protobuf, etc.)
            ├── LogTransport        →  SupabaseTransport (o Firebase, Datadog, gRPC)
            └── CrashHandler        →  AndroidCrashHandler
```

---

## Privacidad por Defecto

AppLogger no captura datos personales identificables (PII):

- ✅ Modelo del dispositivo, versión del SO, versión de app, tipo de conexión.
- ✅ `session_id`: UUID efímero por sesión. No persistente. No correlacionable.
- ❌ Nombre, email, número de teléfono — nunca.
- ❌ Ubicación GPS — nunca.
- ❌ Contenido de mensajes gRPC/WebSocket — nunca.
- ⚙️ `user_id` anónimo — solo si el desarrollador lo activa con consentimiento explícito.

---

## Licencia

MIT License — ver [LICENSE](LICENSE) para detalle.

---

## Contribuir

Ver [docs-package/CONTRIBUTING.md](docs-package/CONTRIBUTING.md).
