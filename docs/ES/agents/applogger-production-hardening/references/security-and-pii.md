# Security and PII Rules

1. Never log PII directly.
2. Never log access tokens, API keys, or secrets.
3. Keep `debugMode=false` for non-local builds.
4. Use HTTPS endpoints in production.

`local.properties` rule:

1. Add missing AppLogger keys only.
2. Do not alter unrelated existing keys.
