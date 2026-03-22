# Integration Playbook

## What to inspect first

1. Root and module Gradle files.
2. Android `Application` class or DI bootstrap.
3. Shared KMP module entry points.
4. Existing logging, analytics, or crash SDKs.
5. Local configuration sources such as `local.properties`, env files, or `BuildConfig`.

## Preferred initialization points

1. Android: `Application.onCreate()` or the main DI/bootstrap layer.
2. KMP iOS: shared Kotlin bootstrap invoked from app startup code.
3. Shared business logic: use `AppLoggerExtensions` (`Any.logD/I/W/E/C`) in `commonMain` for repeated usage — tag is inferred automatically from the class name.

## First-pass integration policy

1. Add dependencies.
2. Initialize once.
3. Add one startup log.
4. Add one network or error log.
5. Add one health-check path.

## local.properties handling rule

1. Detect whether the project uses `local.properties` for runtime config.
2. Verify required AppLogger keys.
3. Add only missing AppLogger keys.
4. Do not change unrelated keys, comments, ordering, or existing values.

Required keys:

1. `APPLOGGER_URL`
2. `APPLOGGER_ANON_KEY`
3. `APPLOGGER_DEBUG`

## Canonical imports and packages

Use only these package roots in Android integration code:

1. `com.applogger.core.AppLoggerSDK`
2. `com.applogger.core.AppLoggerConfig`
3. `com.applogger.core.AppLoggerHealth`
4. `com.applogger.transport.supabase.SupabaseTransport`

Do not use `com.applogger.sdk.*` imports.

Platform API mapping (must match SDK source):

1. Android entry point: `com.applogger.core.AppLoggerSDK`
2. iOS KMP entry point: `com.applogger.core.AppLoggerIos.shared`
3. Shared health snapshot: `com.applogger.core.AppLoggerHealth.snapshot()`

Do not cross these entry points between platforms.

## Canonical initialization snippet (Android)

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

Before applying this snippet, verify that `BuildConfig.LOGGER_URL`, `BuildConfig.LOGGER_KEY`, and `BuildConfig.LOGGER_DEBUG` exist. If they do not exist, map values from your current config source first.

## Canonical initialization snippet (iOS KMP)

```kotlin
val config = AppLoggerConfig.Builder()
 .endpoint(url)
 .apiKey(anonKey)
 .debugMode(debugMode)
 .consoleOutput(debugMode)
 .batchSize(20)
 .flushIntervalSeconds(30)
 .build()

val transport = SupabaseTransport(endpoint = url, apiKey = anonKey)

AppLoggerIos.shared.initialize(config = config, transport = transport)
```

Logcat visibility rule: output is shown only when `isDebugMode=true` and `consoleOutput=true`.

## What not to do on first pass

1. Do not replace every existing logger call in the whole codebase.
2. Do not add PII to logs.
3. Do not add AppLogger to unrelated layers without a reason.
4. Do not introduce platform-specific iOS host code if the project is KMP.
