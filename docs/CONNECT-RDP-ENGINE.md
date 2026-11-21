# INFINITY CONNECT — RDP Engine Integration Boundary

## Current integration
- Profiles and TCP reachability probe remain separate from an authenticated remote desktop session.
- Optional saved secrets use AES-GCM encryption with Android Keystore.
- Session state transitions and an engine adapter contract have been introduced.
- The adapter contract is **not** an RDP implementation and does not claim to render a remote desktop.

## Native engine decision
FreeRDP requires an Android/native integration path (JNI, native build, Android-compatible dependencies, graphics/input bridge, lifecycle handling). Before introducing it, validate upstream licensing, supported Android ABI packages, build reproducibility, and security/certificate behavior. Do not ship a stub or TCP probe as if it were RDP.

## Adapter contract
`InfinityConnectEngine` accepts a target, emits lifecycle events, and supports disconnect. A future implementation should:
1. use a native FreeRDP build and JNI bridge;
2. verify server certificates and expose trust decisions without silently bypassing TLS;
3. translate touch/keyboard input and remote frames;
4. support cancellation, timeouts, reconnect, and disposal;
5. avoid logging passwords and zero temporary secret buffers where feasible;
6. run instrumentation tests against a controlled RDP server before release.

## Status
Architecture boundary and unit tests added. Native engine, rendering, and device-level validation remain outstanding.
