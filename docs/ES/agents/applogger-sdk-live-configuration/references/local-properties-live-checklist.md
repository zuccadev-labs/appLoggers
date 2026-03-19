# local.properties Live Checklist

## Read phase

1. Verify if `local.properties` exists.
2. If missing, inspect `local.properties.example`.
3. Check existing keys without exposing values.

## Required keys

1. `appLogger.url`
2. `appLogger.anonKey`
3. `appLogger.debug`

## Edit rules

1. Append only missing AppLogger keys.
2. Preserve unrelated variables.
3. Keep file formatting stable.

## Validation

1. Build config fields map keys correctly.
2. SDK initializes with endpoint/apiKey.
3. Smoke log call compiles.
