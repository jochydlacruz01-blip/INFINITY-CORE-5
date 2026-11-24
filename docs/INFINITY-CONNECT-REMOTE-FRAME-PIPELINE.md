# PACK14 — Remote Frame Pipeline

## Delivered boundary
- Adds an immutable remote-frame packet with defensive payload copies.
- Enforces frame dimension, pixel-count, payload-size, timestamp and RGBA byte-length limits before a decoder can receive data.
- Adds a synchronized single-slot mailbox that keeps the newest frame and drops stale/duplicate sequence numbers.
- Exposes lightweight mailbox counters for diagnostics and deterministic teardown.

## Why latest-frame wins
Remote desktop is latency-sensitive. An unbounded FIFO queue can accumulate old frames when rendering falls behind, making pointer and keyboard interaction feel delayed. The mailbox intentionally replaces unread frames instead of allowing backlog growth.

## Security and implementation limits
- This package does not decode arbitrary image bytes and does not claim to implement RDP.
- A future native FreeRDP adapter must validate negotiated dimensions, use a bounded decoder, publish monotonically increasing frame sequence numbers, clear the mailbox on disconnect, and avoid logging frame payloads or credentials.
- No public RDP port should be exposed to the Internet; use a private VPN/overlay and authenticated encrypted transport.

## Validation
Unit tests cover payload ownership, stale-frame rejection, latest-frame replacement, bounds and mailbox reset. CI must pass before the pack is considered accepted. Actual screen rendering and end-to-end RDP streaming remain subsequent integration work.
