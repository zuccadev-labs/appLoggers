# local.properties Live Checklist

## Read phase

1. Verify if `local.properties` exists.
2. If missing, inspect `local.properties.example`.
3. Check existing keys without exposing values.

## Required keys

1. `APPLOGGER_URL`
2. `APPLOGGER_ANON_KEY`
3. `APPLOGGER_DEBUG` — in the standard mapping, set `true` to enable `debugMode` and `consoleOutput` together (Logcat output)

## Edit rules

1. Append only missing AppLogger keys.
2. Preserve unrelated variables.
3. Keep file formatting stable.

## Validation

1. Build config fields map keys correctly.
2. SDK initializes with endpoint/apiKey.
3. Smoke log call compiles.
4. Android imports resolve under `com.applogger.core.*` and `com.applogger.transport.supabase.*`.
5. If Logcat behavior is required, verify both flags: `isDebugMode=true` and `consoleOutput=true`.
