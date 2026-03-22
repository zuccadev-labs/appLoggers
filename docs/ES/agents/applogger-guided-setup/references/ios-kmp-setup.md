# iOS KMP Setup Reference

## Scope

This SDK uses a Kotlin Multiplatform-first iOS flow. Do not recommend Swift-host integration for new setups.

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
                implementation("com.github.zuccadev-labs.appLoggers:logger-core:v0.1.1-alpha.6")
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("com.github.zuccadev-labs.appLoggers:logger-transport-supabase:v0.1.1-alpha.6")
            }
        }
    }
}
```

## Initialization pattern

Preferred initialization point: Kotlin code in `iosMain`.

```kotlin
import com.applogger.core.AppLoggerConfig
import com.applogger.core.AppLoggerIos
import com.applogger.transport.supabase.SupabaseTransport

object IosLoggerBootstrap {
    fun initialize(url: String, anonKey: String, debugMode: Boolean) {
        val config = AppLoggerConfig.Builder()
            .endpoint(url)
            .apiKey(anonKey)
            .debugMode(debugMode)
            .consoleOutput(debugMode)
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
println("initialized=${health.isInitialized}, buffered=${health.bufferedEvents}")
```

## Guardrails

1. Do not propose native host setup outside KMP.
2. Keep secrets outside versioned files.
3. Validate connectivity and HTTPS when the transport is remote.
