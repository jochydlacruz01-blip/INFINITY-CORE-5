# PACK14 — Remote Frame Pipeline

## Delivered components

### Encoded frame packet boundary
- Immutable remote-frame packet with defensive payload copies.
- Validates sequence, dimensions, pixel count, timestamp, payload length and RGBA byte length before a decoder can receive data.
- A synchronized single-slot mailbox retains the newest encoded packet and rejects stale/duplicate sequence numbers.
- Counters expose accepted/replaced frames for diagnostics; clearing the mailbox supports deterministic teardown.

### Decoded FreeRDP frame dispatch
- `InfinityLatestFrameMailbox<T>` is a one-slot, generic mailbox for already-decoded frames.
- `InfinityFreeRdpClient` uses it to coalesce Bitmap updates before posting to the Android main thread.
- At most one UI drain is scheduled at a time; newer frames replace pending frames while Compose is busy.
- The mailbox is cleared on framebuffer replacement and session teardown.

## Why latest-frame wins

Remote desktop is latency-sensitive. An unbounded FIFO of Handler callbacks can accumulate old frames when rendering falls behind, making pointer and keyboard interaction feel delayed. The decoded-frame mailbox limits pending UI work to one freshest frame. The encoded-packet mailbox provides the same latest-frame policy at the transport/decoder boundary.

## Integration boundary

FreeRDP decodes RDP graphics updates directly into Android Bitmap surfaces; those decoded frames use `InfinityLatestFrameMailbox`. The separate `InfinityRemoteFramePacket`/`InfinityRemoteFrameMailbox` pair remains an engine-neutral boundary for encoded frames from other transports or future decoders. It is not itself an image decoder or RDP transport.

## Security and implementation limits

- Frame dimensions and payloads are bounded before allocation/decoding; the FreeRDP adapter separately caps bitmap dimensions/pixels.
- The Windows tunnel relays bounded binary RDP frames over mTLS and only to `127.0.0.1:3389`.
- No frame payloads, passwords, private keys or session tokens are written to audit logs.
- No public RDP port should be exposed to the Internet; use a private VPN/overlay and authenticated encrypted transport.
- The remote-desktop UI remains disabled by the Android feature gate until physical Samsung tablet ↔ Windows PC validation passes.

## Validation

Unit tests cover encoded packet ownership/bounds, stale sequence rejection, latest-frame replacement, mailbox reset, decoded-frame drain coalescing and rescheduling. CI must pass on the current PR head. Actual screen rendering, latency, memory behavior and input responsiveness still require physical-device validation.

## Credential vault and profile binding — implementation update

- CONNECT profiles now carry a stable random profile ID persisted with the profile record. Existing records without an ID receive one and are rewritten on load.
- Credential storage v2 uses a separate Android Keystore AES-256 key alias and AES/GCM/NoPadding with a 96-bit random IV and a 128-bit authentication tag.
- Every v2 encryption and decryption supplies length-prefixed AAD containing the domain/schema context, stable profile ID and canonical endpoint. Copying a ciphertext between profiles or changing the endpoint causes authenticated decryption to fail.
- Preference keys are derived from a SHA-256 digest of the stable profile ID; credential bytes are never stored as plaintext. Byte buffers containing cleartext, IVs and ciphertext are wiped after use where the JVM permits.
- Existing v1 endpoint-keyed values are read only through the legacy Keystore alias and migrated to v2 on successful retrieval. A failed migration fails closed and does not return an unprotected value.
- JVM regression tests cover same-profile round-trip using the AAD contract, cross-profile substitution, endpoint substitution and ciphertext tampering. Android instrumentation tests cover actual Android Keystore persistence and profile/endpoint isolation; they are intentionally deferred to final device/simulation acceptance under the current project instruction.

### Scope boundary

This package secures locally stored profile credentials; it does not establish a remote session, authenticate a Windows host, prove certificate identity, or make raw RDP reachable across networks. TCP probing remains diagnostics only. The remote session stays gated until the authenticated pairing/tunnel and native FreeRDP path are integrated and validated.
