# CONNECT reconnect policy

This module computes bounded retry decisions only. It does not reconnect or schedule network operations.

- Retries transient NETWORK, TIMEOUT, SERVER failures only.
- Authentication, invalid configuration, and user-cancelled failures are not retried.
- Backoff: 1, 2, 4, 8, 16 seconds; maxAttempts configurable (0–10).
- Production engine/controller must explicitly consume decisions and cancel timers on disconnect.
