# iOS KMP Setup Reference

## Scope

This SDK uses a Kotlin Multiplatform-first iOS flow. The entry point is `AppLoggerIos.shared` defined in `iosMain`. Do not use `AppLoggerSDK` in iOS KMP modules — that singleton is Android-only.

## Dependency setup

In the shared KMP module:

```kotlin
kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Reemplazar <latest-version> con la última release: https://github.com/zuccadev-labs/appLoggers/releases
                implementation("com.github.zuccadev-labs.appLoggers:logger-core:<latest-version>")
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("com.github.zuccadev-labs.appLoggers:logger-transport-supabase:<latest-version>")
            }
        }
    }
}
```

## Debug mode via Info.plist (sin cambiar código)

El SDK lee `APPLOGGER_DEBUG` de `Info.plist` automáticamente. Si está presente y es `"true"`, activa `isDebugMode = true` sin necesidad de cambiar código:

```xml
<!-- Info.plist -->
<key>APPLOGGER_DEBUG</key>
<string>true</string>
```

Para producción, omitir la clave o establecerla en `"false"`. El SDK la lee en `initialize()` y aplica el flag antes de construir el pipeline.

## Initialization pattern

Preferred initialization point: Kotlin code in `iosMain`.

```kotlin
import com.applogger.core.AppLoggerConfig
import com.applogger.core.AppLoggerIos
import com.applogger.core.LogMinLevel
import com.applogger.transport.supabase.SupabaseTransport

object IosLoggerBootstrap {
    fun initialize(url: String, anonKey: String, debugMode: Boolean = false) {
        val config = AppLoggerConfig.Builder()
            .endpoint(url)
            .apiKey(anonKey)
            .environment("production")          // "production" | "staging" | "development"
            .debugMode(debugMode)
            .consoleOutput(debugMode)
            .minLevel(LogMinLevel.INFO)          // descarta DEBUG en producción
            .batchSize(20)
            .flushIntervalSeconds(30)
            .build()

        val transport = SupabaseTransport(
            endpoint = url,
            apiKey = anonKey
        )

        AppLoggerIos.shared.initialize(
            config = config,
            transport = transport
        )
    }
}
```

Compile guard:
1. Do not use `AppLoggerSDK` in iOS KMP modules.
2. Keep initialization in Kotlin (`iosMain` or shared Kotlin bootstrap), not in ad-hoc Swift host wrappers.

## Session and user identity

```kotlin
// Al hacer login — nueva sesión + user ID
AppLoggerIos.shared.setAnonymousUserId(anonymousUUID)
AppLoggerIos.shared.newSession()

// Al hacer logout — limpiar identidad
AppLoggerIos.shared.clearAnonymousUserId()
AppLoggerIos.shared.newSession()

// Global extra — adjunta a todos los eventos posteriores
AppLoggerIos.shared.addGlobalExtra("ab_test", "checkout_v2")
AppLoggerIos.shared.removeGlobalExtra("ab_test")
AppLoggerIos.shared.clearGlobalExtra()
```

## Flush en background

iOS no tiene lifecycle observer automático. Llamar `flush()` manualmente al entrar en background:

```kotlin
// En el delegate de ciclo de vida de la app (iosMain)
AppLoggerIos.shared.flush()
```

## local.properties policy

If `local.properties` is present:

1. Verify AppLogger keys first.
2. Add only missing AppLogger keys.
3. Keep all unrelated variables untouched.

Suggested keys:

```properties
APPLOGGER_URL=https://YOUR-PROJECT.supabase.co
APPLOGGER_ANON_KEY=YOUR_ANON_KEY
APPLOGGER_DEBUG=false
```

## Minimal verification

```kotlin
AppLoggerIos.shared.info("BOOT", "AppLogger initialized")

val health = AppLoggerHealth.snapshot()
println("initialized=${health.isInitialized}, transport=${health.transportAvailable}, buffered=${health.bufferedEvents}")
```

## Guardrails

1. Do not use `AppLoggerSDK` in iOS — use `AppLoggerIos.shared`.
2. Do not propose native Swift host setup outside KMP.
3. Keep secrets outside versioned files.
4. Always set `environment` to distinguish production from staging data.
5. Call `flush()` manually before the app enters background.
