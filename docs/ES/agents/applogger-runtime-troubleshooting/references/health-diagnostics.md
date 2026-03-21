# Health Diagnostics

Use `AppLoggerHealth.snapshot()` and evaluate:

1. `isInitialized`
2. `transportAvailable`
3. `bufferedEvents`
4. `eventsDroppedDueToBufferOverflow`
5. `bufferUtilizationPercentage`

Interpretation:

1. `isInitialized=false`: bootstrap path not executed.
2. `transportAvailable=false`: endpoint or connectivity issue.
3. `bufferedEvents` increasing continuously: delivery blocked.
4. high drop counters: overflow policy and capacity issue.
