# Common Failures

1. Wrong endpoint format.
2. Invalid API key.
3. Non-HTTPS production endpoint.
4. Missing network permissions on Android.
5. Initialization never called.
6. AppLogger keys missing in `local.properties`.

Fix policy for `local.properties`:

1. Add only missing keys.
2. Preserve unrelated keys exactly as they are.