# Release Checklist

1. Endpoint is HTTPS.
2. Keys load from non-committed source.
3. `debugMode=false` in release.
4. One startup event arrives in backend.
5. Health snapshot stable after smoke flow.