# Smoke Tests

1. Verify initialization runs once.
2. Emit one `info`, one `warn`, one `error`, one `metric`.
3. Trigger manual flush.
4. Verify health snapshot before and after flush.
5. Confirm events arrive in backend.