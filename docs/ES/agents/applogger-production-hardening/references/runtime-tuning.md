# Runtime Tuning Baseline

Suggested baseline:

1. `batchSize=20`
2. `flushIntervalSeconds=30`
3. `bufferSizeStrategy=FIXED`
4. `bufferOverflowPolicy=DISCARD_OLDEST`
5. `offlinePersistenceMode=NONE`

Tune values after observing health and delivery behavior.
