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
    implementation("com.github.devzucca.appLoggers:logger-core:v0.1.1-alpha.2")
    implementation("com.github.devzucca.appLoggers:logger-transport-supabase:v0.1.1-alpha.2")

    testImplementation("com.github.devzucca.appLoggers:logger-test:v0.1.1-alpha.2")
}
```

## Required permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## local.properties policy

If the project uses `local.properties`:

1. Check whether these keys already exist: `appLogger.url`, `appLogger.anonKey`, `appLogger.debug`.
2. Add only missing keys.
3. Do not edit, remove, or rename any unrelated existing variable.

Example append-only update:

```properties
appLogger.url=https://YOUR-PROJECT.supabase.co
appLogger.anonKey=YOUR_ANON_KEY
appLogger.debug=false
```

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