# Acceptance Gates

All gates must pass:

1. SDK initializes correctly.
2. Events are delivered.
3. No forbidden sensitive data in logs.
4. Health snapshot does not indicate persistent degradation.
5. local.properties policy is respected (only missing keys added; unrelated keys untouched).