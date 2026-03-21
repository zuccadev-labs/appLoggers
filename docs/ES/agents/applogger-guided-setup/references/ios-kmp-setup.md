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
                implementation("com.github.zuccadev-labs.appLoggers:logger-core:v0.1.1-alpha.5")
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("com.github.zuccadev-labs.appLoggers:logger-transport-supabase:v0.1.1-alpha.5")
            }
        }
    }
}
```

## Initialization pattern

Preferred initialization point: Kotlin code in `iosMain`.

```kotlin
val config = AppLoggerConfig.Builder()
    .endpoint(url)
    .apiKey(anonKey)
    .debugMode(debugMode)
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
```

## local.properties policy

If `local.properties` is present:

1. Verify AppLogger keys first.
2. Add only missing AppLogger keys.
3. Keep all unrelated variables untouched.

Suggested keys:

```properties
appLogger_url=https://YOUR-PROJECT.supabase.co
appLogger_anonKey=YOUR_ANON_KEY
appLogger_debug=false
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
