# AppLogger

[![JitPack](https://jitpack.io/v/zuccadev-labs/appLoggers.svg)](https://jitpack.io/#zuccadev-labs/appLoggers)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1+-purple.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-23+-brightgreen.svg)](https://developer.android.com/about/versions/marshmallow)

SDK de telemetría técnica estructurada para Kotlin — Android Mobile · Android TV · JVM.

Captura errores, crashes y métricas de performance de forma segura, sin impactar la UI ni comprometer la privacidad de los usuarios.

---

## ¿Por qué AppLogger?

| Problema | Solución |
|---|---|
| El tester dice "se cerró" sin contexto | Crash capturado + metadatos técnicos + sesión |
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
    implementation("com.github.zuccadev-labs.appLoggers:logger-core:0.1.1-alpha.7")
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
                .debugMode(BuildConfig.LOGGER_DEBUG)
                .consoleOutput(BuildConfig.LOGGER_DEBUG)
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
| [Guía de Integración](../desarrollo/integration-guide.md) | Cómo integrar el SDK en una app Android/iOS, transportes custom y Android TV |
| [Arquitectura del Paquete](architecture.md) | Traits, módulos, pipeline y extensibilidad real del SDK |
| [Testing](testing.md) | Tests unitarios, FakeTransport, casos de resiliencia |
| [Publicación](publishing.md) | JitPack, GitHub Packages tras release etiquetada, Maven Central y CI/CD |
| [CONTRIBUTING](CONTRIBUTING.md) | Guía para contribuir al proyecto |
| [CHANGELOG](CHANGELOG.md) | Historial de versiones |

---

## Diseño por Traits — Arquitectura Extensible

AppLogger está construido sobre interfaces (traits) independientes. Cada componente es reemplazable:

```
AppLoggerSDK
    └── AppLoggerImpl
            ├── DeviceInfoProvider  →  AndroidDeviceInfoProvider (o custom)
            ├── LogFilter           →  RateLimitFilter (o chain de filtros)
            ├── LogBuffer           →  InMemoryBuffer (1000 Mobile / 100 TV+WearOS)
            ├── LogFormatter        →  JsonLogFormatter (o protobuf, etc.)
            ├── LogTransport        →  SupabaseTransport (o implementación custom)
            └── CrashHandler        →  AndroidCrashHandler
```

---

## Privacidad por Defecto

AppLogger no captura datos personales identificables (PII):

- ✅ Modelo del dispositivo, versión del SO, versión de app, tipo de conexión.
- ✅ `session_id`: UUID efímero por sesión. No persistente. No correlacionable.
- ❌ Nombre, email, número de teléfono — nunca.
- ❌ Ubicación GPS — nunca.
- ❌ Contenido de payloads de negocio o credenciales — nunca.
- ⚙️ `user_id` anónimo — solo si el desarrollador lo activa con consentimiento explícito.

---

## Licencia

MIT License — ver [LICENSE](LICENSE) para detalle.

---

## Contribuir

Ver [CONTRIBUTING.md](CONTRIBUTING.md).
