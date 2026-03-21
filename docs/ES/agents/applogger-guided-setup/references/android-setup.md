# Android Setup Reference

## Dependency setup

Add JitPack in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add dependencies in the app module:

```kotlin
dependencies {
    implementation("com.github.zuccadev-labs.appLoggers:logger-core:v0.1.1-alpha.4")
    implementation("com.github.zuccadev-labs.appLoggers:logger-transport-supabase:v0.1.1-alpha.4")

    testImplementation("com.github.zuccadev-labs.appLoggers:logger-test:v0.1.1-alpha.4")
}
```

## Required permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## local.properties policy

If the project uses `local.properties`:

1. Check whether these keys already exist: `appLogger_url`, `appLogger_anonKey`, `appLogger_debug`.
2. Add only missing keys.
3. Do not edit, remove, or rename any unrelated existing variable.

Example append-only update:

```properties
appLogger_url=https://YOUR-PROJECT.supabase.co
appLogger_anonKey=YOUR_ANON_KEY
appLogger_debug=false
```

## Debug output behavior

- Effective rule: Logcat output happens only when `isDebugMode=true` **and** `consoleOutput=true`.
- `appLogger_debug=true` usually enables Logcat because most setups map it to `debugMode` (and often to `consoleOutput`).
- No additional Logcat configuration, tag setup, or Android logger wrapper is needed.
- `appLogger_debug=false` (production default) disables Logcat output in the standard setup; no code change required.
- Do **not** set `debug=true` in production builds.

## Canonical imports (Android)

```kotlin
import com.applogger.core.AppLoggerConfig
import com.applogger.core.AppLoggerHealth
import com.applogger.core.AppLoggerSDK
import com.applogger.transport.supabase.SupabaseTransport
```

Do not use `com.applogger.sdk.*` imports.

SDK source references (anti-hallucination):

1. `sdk/logger-core/src/commonMain/kotlin/com/applogger/core/internal/AppLoggerImpl.kt` — Logcat/console output guard.
2. `sdk/logger-core/src/commonMain/kotlin/com/applogger/core/AppLoggerConfig.kt` — default `consoleOutput=true` in Builder.

## Initialization pattern

Preferred initialization point: custom `Application`.

```kotlin
val transport = SupabaseTransport(
    endpoint = BuildConfig.LOGGER_URL,
    apiKey = BuildConfig.LOGGER_KEY
)

AppLoggerSDK.initialize(
    context = this,
    config = AppLoggerConfig.Builder()
        .endpoint(BuildConfig.LOGGER_URL)
        .apiKey(BuildConfig.LOGGER_KEY)
        .debugMode(BuildConfig.LOGGER_DEBUG)
        .consoleOutput(BuildConfig.LOGGER_DEBUG)
        .batchSize(20)
        .flushIntervalSeconds(30)
        .build(),
    transport = transport
)
```

## Minimal verification

```kotlin
AppLoggerSDK.info("BOOT", "AppLogger initialized")

val health = AppLoggerHealth.snapshot()
println("initialized=${health.isInitialized}, buffered=${health.bufferedEvents}")
```

## Guardrails

1. Do not log PII.
2. Do not log tokens or API keys.
3. Keep `debugMode=false` outside local development.
