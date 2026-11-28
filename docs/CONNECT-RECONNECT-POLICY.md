# INFINITY CONNECT — reconnect contract and current implementation boundary

## Purpose

This document distinguishes transport recovery from an RDP desktop session. A successful TLS/WebSocket reconnect is not proof that the original RDP session survived.

## Three separate outcomes

1. **Transport recovery** — establish a new authenticated TLS 1.3/mTLS WebSocket tunnel using a still-valid session grant. This restores only the transport channel.
2. **Existing RDP session recovery** — continue the same RDP protocol connection and Windows logon without creating a new RDP connection. This requires the underlying TCP/RDP session to have survived. The current Android tunnel closes its accepted loopback socket and listener when the WebSocket closes or fails; therefore the existing RDP TCP connection is not preserved across that failure. This outcome is **NOT IMPLEMENTED**.
3. **New RDP session** — after a transport interruption, establish a new RDP connection through a newly opened local tunnel. This is a new RDP connection, even if Windows later restores the same user profile or desktop. It must be labeled as a new session and requires a currently valid grant, authorization and credentials supplied again according to policy. Never describe it as resumption of the prior RDP session.

## Current source behavior

- `InfinityConnectReconnectPolicy` is a pure decision helper. It does not open sockets, schedule retries, recreate a tunnel, or call FreeRDP.
- It permits only transient NETWORK, TIMEOUT and SERVER failures; authentication, invalid configuration and user cancellation are terminal.
- Backoff is bounded (1, 2, 4, 8, 16 seconds); the caller must enforce the maximum attempt count and cancel pending timers on teardown.
- `InfinityConnectRdpTunnel.open` performs one mTLS WebSocket handshake, then accepts one loopback TCP client for the relay.
- On WebSocket close/failure, the implementation closes the local socket and local server socket. The tunnel handle is therefore single-use.
- `InfinityFreeRdpClient.connect` creates one FreeRDP session. Its `OnReconnecting` callback is a FreeRDP event notification, not evidence that the INFINITY transport can rebuild itself.
- The current session controller and CI tests do not demonstrate reconnect against a real Windows host.

## Required implementation before claiming reconnect

- Introduce an explicit lifecycle state machine with mutually exclusive states for transport connecting/connected, RDP connecting/connected, interrupted, retry waiting, authorization required, terminal failure and closed.
- Make every retry generation-scoped. A stale callback from an older attempt must not replace or close a newer connection.
- Revalidate grant expiry, device identity, certificate validity, capabilities and revocation state before every new transport handshake. Never retry authentication or authorization failures.
- Recreate the tunnel and local loopback listener only through an owned controller; ensure one active tunnel/FreeRDP instance at a time.
- Do not reuse a revoked/expired bearer token. An offline revocation request is pending until the server confirms it; local queueing is not remote revocation confirmation.
- If the RDP TCP connection has been lost, close and dispose the old FreeRDP instance. Obtain fresh authorization and credentials before creating a new RDP connection. Do not persist or log an RDP password to enable silent reconnection.
- Surface the distinction to the user: “Transport restored; desktop reconnecting” versus “Previous desktop session ended; authorize a new desktop session.”
- Add deterministic tests for network loss at each lifecycle phase, concurrent disconnect/retry/revoke, stale callbacks, expiry during backoff, user cancellation, retry exhaustion and credential clearing.
- Add a controlled Windows integration test for transport restoration and a separate test proving whether the original RDP connection survived. If it did not, verify that the app requests a new session rather than claiming resume.

## Acceptance and status

| Capability | Status |
|---|---|
| Bounded retry decision helper | IMPLEMENTED; unit/CI coverage must be checked at the exact PR head |
| Automatic transport retry orchestration | NOT IMPLEMENTED |
| Automatic recreation of the local tunnel/listener | NOT IMPLEMENTED |
| Recovery of the same existing RDP TCP session after tunnel loss | NOT IMPLEMENTED with current socket lifecycle |
| Explicitly authorized new RDP session after interruption | NOT IMPLEMENTED as an end-to-end recovery flow |
| Physical tablet ↔ Windows LTE/VPN reconnect test | NOT RUN |

Do not enable remote-desktop feature gates based on this document or on CI compilation. Physical acceptance remains mandatory.
