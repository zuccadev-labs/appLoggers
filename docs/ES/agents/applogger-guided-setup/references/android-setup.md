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
    // Reemplazar <latest-version> con la última release: https://github.com/zuccadev-labs/appLoggers/releases
    implementation("com.github.zuccadev-labs.appLoggers:logger-core:<latest-version>")
    implementation("com.github.zuccadev-labs.appLoggers:logger-transport-supabase:<latest-version>")

    testImplementation("com.github.zuccadev-labs.appLoggers:logger-test:<latest-version>")
}
```

## Required permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## local.properties policy

If the project uses `local.properties`:

1. Check whether these keys already exist: `APPLOGGER_URL`, `APPLOGGER_ANON_KEY`, `APPLOGGER_DEBUG`.
2. Add only missing keys.
3. Do not edit, remove, or rename any unrelated existing variable.

Example append-only update:

```properties
APPLOGGER_URL=https://YOUR-PROJECT.supabase.co
APPLOGGER_ANON_KEY=YOUR_ANON_KEY
APPLOGGER_DEBUG=false
```

## BuildConfig mapping (required if using `BuildConfig.LOGGER_*`)

```kotlin
import java.util.Properties

val appLoggerLocalProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    buildFeatures { buildConfig = true }

    defaultConfig {
        val loggerUrl   = appLoggerLocalProps.getProperty("APPLOGGER_URL", "")
        val loggerKey   = appLoggerLocalProps.getProperty("APPLOGGER_ANON_KEY", "")
        val loggerDebug = appLoggerLocalProps.getProperty("APPLOGGER_DEBUG", "false").toBoolean()

        buildConfigField("String",  "LOGGER_URL",   "\"$loggerUrl\"")
        buildConfigField("String",  "LOGGER_KEY",   "\"$loggerKey\"")
        buildConfigField("boolean", "LOGGER_DEBUG", loggerDebug.toString())
    }
}
```

## Debug mode via APPLOGGER_DEBUG (sin cambiar código)

El SDK lee `APPLOGGER_DEBUG` del manifest meta-data automáticamente. Si está presente y es `"true"`, activa `isDebugMode = true` sin necesidad de cambiar código:

```xml
<!-- AndroidManifest.xml -->
<application ...>
    <meta-data
        android:name="APPLOGGER_DEBUG"
        android:value="${APPLOGGER_DEBUG}" />
</application>
```

```groovy
// build.gradle (app module)
android {
    defaultConfig {
        manifestPlaceholders = [
            APPLOGGER_DEBUG: System.getenv("APPLOGGER_DEBUG") ?: "false"
        ]
    }
}
```

Regla efectiva: Logcat output ocurre solo cuando `isDebugMode=true` **y** `consoleOutput=true`. En producción (`isDebugMode=false`), Logcat está **siempre suprimido** sin importar `consoleOutput`.

## Canonical imports (Android)

```kotlin
import com.applogger.core.AppLoggerConfig
import com.applogger.core.AppLoggerHealth
import com.applogger.core.AppLoggerSDK
import com.applogger.core.BufferOverflowPolicy
import com.applogger.core.BufferSizeStrategy
import com.applogger.core.LogMinLevel
import com.applogger.core.OfflinePersistenceMode
import com.applogger.transport.supabase.SupabaseTransport
```

Do not use `com.applogger.sdk.*` imports.

## Initialization pattern

Preferred initialization point: custom `Application.onCreate()`.

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val transport = SupabaseTransport(
            endpoint = BuildConfig.LOGGER_URL,
            apiKey = BuildConfig.LOGGER_KEY,
            networkAvailabilityProvider = androidNetworkAvailabilityProvider(this)
        )

        AppLoggerSDK.initialize(
            context = this,
            config = AppLoggerConfig.Builder()
                .endpoint(BuildConfig.LOGGER_URL)
                .apiKey(BuildConfig.LOGGER_KEY)
                .environment("production")          // "production" | "staging" | "development"
                .debugMode(BuildConfig.LOGGER_DEBUG)
                .consoleOutput(BuildConfig.LOGGER_DEBUG)
                .minLevel(LogMinLevel.INFO)          // descarta DEBUG en producción
                .batchSize(20)
                .flushIntervalSeconds(30)
                .build(),
            transport = transport
        )

        // Validar configuración en debug
        if (BuildConfig.DEBUG) {
            AppLoggerConfig.Builder()
                .endpoint(BuildConfig.LOGGER_URL)
                .apiKey(BuildConfig.LOGGER_KEY)
                .build()
                .validate()
                .forEach { android.util.Log.w("AppLogger", "Config issue: $it") }
        }
    }
}
```

`androidNetworkAvailabilityProvider(context)` devuelve una lambda que usa `ConnectivityManager` con estado en caché — sin I/O en el hilo llamador.

## Session and user identity

```kotlin
// Al hacer login — nueva sesión + user ID
AppLoggerSDK.setAnonymousUserId(anonymousUUID)
AppLoggerSDK.newSession()

// Al hacer logout — limpiar identidad
AppLoggerSDK.clearAnonymousUserId()
AppLoggerSDK.newSession()

// Global extra — adjunta a todos los eventos posteriores
AppLoggerSDK.addGlobalExtra("ab_test", "checkout_v2")
AppLoggerSDK.addGlobalExtra("experiment", "group_b")
AppLoggerSDK.removeGlobalExtra("ab_test")
AppLoggerSDK.clearGlobalExtra()
```

## Minimal verification

```kotlin
AppLoggerSDK.info("BOOT", "AppLogger initialized")

val health = AppLoggerHealth.snapshot()
println("initialized=${health.isInitialized}, transport=${health.transportAvailable}, buffered=${health.bufferedEvents}")
```

## Guardrails

1. Do not log PII (names, emails, phone numbers, device IMEI).
2. Do not log tokens or API keys.
3. Keep `debugMode=false` in production builds.
4. Always set `environment` to distinguish production from staging data.
5. Call `validate()` during development to catch config issues early.
