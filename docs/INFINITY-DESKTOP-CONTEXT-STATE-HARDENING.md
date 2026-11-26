# Desktop context state hardening

Persisted icon-size values and refresh counters are normalized before reducer transitions. Non-finite sizes fall back to 48dp, finite sizes are clamped to 24–96dp, negative refresh counters reset to zero, and refresh saturates at Long.MAX_VALUE rather than overflowing. Added regression tests for corrupted persisted values and counter saturation.