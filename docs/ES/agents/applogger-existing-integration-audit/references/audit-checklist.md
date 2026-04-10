# Audit Checklist

## Initialization

1. Is AppLogger initialized exactly once?
2. Does initialization happen before services, workers, or servers start logging?
3. Is `transport` passed explicitly?
4. Is `config.validate()` used in development?

## Version alignment

1. Are `logger-core`, `logger-transport-supabase`, and `logger-test` aligned?
2. Does the consumer use the current approved version?

## Identity and privacy

1. Is pseudonymous device correlation established after initialization?
2. Does auth update runtime identity cleanly?
3. Are user IDs, emails, JWTs, or tokens printed in log messages?
4. Is raw `ANDROID_ID` avoided?

## Instrumentation quality

1. Are tags centralized?
2. Are metrics using stable dimensions?
3. Are anomalies modeled with structured fields?
4. Are messages queryable and operationally stable?

## Runtime durability

1. Is `AppLoggerHealth.snapshot()` checked at startup or shutdown?
2. Is `flush()` called on controlled shutdown?
3. Are buffer/flush settings appropriate for the app lifetime model?

## Config hygiene

1. Are AppLogger credentials mapped from one authoritative source?
2. Are the same values duplicated in packaged assets without need?
3. Are production debug flags disabled reliably?