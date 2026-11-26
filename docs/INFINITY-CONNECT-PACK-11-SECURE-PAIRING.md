# INFINITY CONNECT — PACK 11: Secure Pairing Foundation

## Objective

Prepare the Android side of device pairing before implementing the Windows companion service and the authenticated network channel.

## Included

- Cryptographically random 256-bit, URL-safe pairing nonce.
- Unique request identifier.
- Five-minute challenge lifetime with strict expiry validation.
- SHA-256 certificate fingerprint normalization and strict validation.
- Constant-time fingerprint comparison using `MessageDigest.isEqual`.
- Unit tests for challenge creation, expiry, malformed input and fingerprint matching.

## Security boundary

This pack is a **local pairing foundation only**. It does not yet pair two devices, transmit a challenge, provision a certificate, pin a live server certificate, create a TLS session, authenticate a Windows user, or open an RDP session. A fingerprint must only be accepted after the user independently verifies it through a trusted channel. The next pack must implement the Windows companion identity and an authenticated TLS 1.3 channel with certificate pinning; do not send Windows credentials before that verification succeeds.

## Network policy

- Do not expose TCP/3389 directly to the public Internet.
- Prefer a private VPN overlay for devices on different networks.
- Keep Android cleartext traffic disabled.
- Never treat a TCP port probe as proof of peer identity or a remote desktop session.

## Acceptance checks

- Debug and Release unit tests pass.
- Android lint passes.
- Debug and signed Release APKs build.
- Release APK signature verifies.
- Physical tablet/Windows interoperability remains a separate acceptance gate.
