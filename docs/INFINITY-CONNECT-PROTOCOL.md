# INFINITY CONNECT — Protocol and Trust Contract

**Status:** Contract specification; not yet a wire-protocol implementation.
**Scope:** Android ↔ Windows Companion identity and future remote-session transport.
**Security posture:** fail closed; no public RDP exposure.

## 1. Scope and non-goals

This contract defines how the Android client and Windows Companion are expected to establish device trust before any remote capability is offered.

The current Windows Companion exposes an identity-only HTTPS endpoint protected by TLS 1.3 and mandatory client-certificate authentication. The Android app currently has local pairing-challenge and certificate-fingerprint primitives, but does not yet provision a client certificate, perform the mutual-TLS request, or verify a live Windows identity.

This document does not claim that pairing or remote desktop is operational. It does not authorize screen capture, keyboard/mouse injection, file transfer, shell access, or an RDP proxy.

## 2. Trust model

- Each Windows Companion has a stable device ID and a server certificate with server-authentication EKU.
- Each authorized Android installation has its own client certificate with client-authentication EKU and a private key protected by Android Keystore where the selected key algorithm/provider supports the required TLS use.
- The Windows service accepts only explicitly enrolled Android client certificate fingerprints.
- The Android client validates the Windows certificate fingerprint against a user-confirmed trust record. Unknown, changed, expired, revoked, or malformed certificates fail closed.
- A successful TCP connection is not identity verification. A successful TLS handshake is not authorization for remote-control capabilities.
- Certificate enrollment is a separate, explicit, user-confirmed operation. Do not silently trust the first certificate observed.

## 3. Required protocol versioning

Every application-level request/response must carry a protocol version. The initial planned version is 1.
- Reject unsupported major versions.
- Permit compatible minor-version negotiation only when explicitly documented.
- Never infer capabilities from a version string alone.
- The server must return a bounded capability list; Android must intersect it with locally supported and user-authorized capabilities.

## 4. Device identity response

The current /v1/identity endpoint returns a small identity document. Before it is treated as a stable contract, its schema should be extended with:
- protocolVersion
- deviceId (stable GUID)
- product
- companionVersion
- capabilities (initially identity only)
- serverCertificateSha256
- issuedAtUtc and a bounded response/request correlation identifier

Do not return host usernames, Windows account identifiers, filesystem paths, installed software lists, or other unnecessary personal information.

The server certificate is validated by TLS and a separately user-confirmed pin. A certificate fingerprint included in JSON is informational only and must never replace TLS certificate validation.

## 5. Pairing challenge lifecycle

The intended enrollment sequence is:
1. User opens Add Device on Android and explicitly starts pairing.
2. Android creates a cryptographically random 256-bit nonce and a unique request ID with a five-minute expiry.
3. Android registers the challenge in a bounded, single-use registry.
4. A short-lived pairing code or QR representation transfers only necessary enrollment request data; it must not contain a private key or reusable password.
5. Windows Companion presents the request for local user confirmation. No unattended enrollment.
6. Both sides bind the approved enrollment to the device ID, request ID, nonce, protocol version, and certificate fingerprints.
7. The response is accepted only once, before expiry, over an authenticated channel.
8. On success, Android stores the Windows trust pin and the client key/certificate reference using platform-protected storage. On cancellation, timeout, mismatch, or service restart, pending challenges are invalidated.

### Replay and resource controls
- Challenges are single-use.
- Nonces are compared in constant time after strict decoding.
- Expired challenges are purged.
- Outstanding challenges are bounded; registration fails closed when capacity is reached.
- Duplicate request IDs are rejected.
- Invalid nonce attempts do not consume a legitimate challenge, avoiding a trivial unauthenticated denial of service.
- The current Android registry is process-local. It prevents replay only within that registry's lifetime; it is not yet connected to a network enrollment endpoint or persistent cross-process state.

## 6. TLS and certificate rules

- TLS 1.3 only for the current Companion endpoint.
- Mutual TLS is mandatory.
- Windows Companion requires its server certificate to be within its validity window, have a private key, contain server-authentication EKU, and permit digitalSignature when a Key Usage extension is present. A missing Key Usage extension remains permitted; duplicate or malformed usage extensions fail closed.
- Android client certificate must be within its validity window and contain client-authentication EKU.
- Windows accepts only explicitly configured/enrolled Android client pins.
- Fingerprint comparison must use strict SHA-256 parsing and constant-time byte comparison.
- Unknown, malformed, expired, wrong-EKU, revoked, or mismatched certificates are rejected.
- Certificate renewal requires an authenticated, user-visible trust update. Do not silently replace pins.
- Private keys and certificate passwords must never be committed, logged, or embedded in app preferences.

## 7. Session state model

The client must distinguish these states:

SAVED_PROFILE → RESOLVING → TCP_REACHABLE → TLS_AUTHENTICATED → IDENTITY_VERIFIED → SESSION_AUTHORIZED → CONNECTED

Failure states include INVALID_CONFIGURATION, NETWORK_UNREACHABLE, TLS_REJECTED, CERTIFICATE_MISMATCH, IDENTITY_REJECTED, AUTHORIZATION_DENIED, TIMEOUT, CANCELLED, REMOTE_UNAVAILABLE.

A state may be shown only after the corresponding operation actually succeeds. A TCP probe must never be displayed as connected. A verified identity must not be displayed as an active RDP session.

## 8. Capability authorization

The initial Windows Companion capability is identity only. Future capabilities must be separately designed and authorized:
- remote.display
- remote.input
- file.transfer
- clipboard.sync
- multi.display

Each capability needs its own threat model, explicit session consent, permission checks, bounded message formats, audit events, and tests. No capability is implied by mTLS alone.

## 9. Network deployment

- Bind only to a deliberately configured private LAN or VPN interface.
- Scope Windows Firewall rules to the required private subnet/interface.
- Never expose TCP 3389 or the Companion endpoint directly to the public Internet.
- For Android and PC on different networks, use a private authenticated VPN such as WireGuard or Tailscale.
- Do not add automatic router port forwarding or UPnP.
- Timeouts, message-size limits, rate limits, and bounded concurrent requests are mandatory.

## 10. Privacy and diagnostics

- Do not log passwords, private keys, pairing nonces, full certificate contents, screen data, clipboard data, or user documents.
- Diagnostics may include event category, timestamp, protocol version, and a non-sensitive correlation ID.
- Hostnames and usernames should be redacted by default.
- Keep logs bounded and allow the user to clear them.
- User consent is required before enabling any screen or input capability.

## 11. Required verification before activation

### Android
- Challenge uniqueness, expiry, malformed input, replay rejection, capacity limits, clock overflow.
- Certificate pin parsing, wrong pin, wrong EKU, expiry, and renewal behavior.
- TLS 1.3 mutual-authentication success and failure against the real Windows service.
- No secrets in logs, preferences, crash reports, or clipboard.

### Windows
- Service refuses missing/invalid certificate configuration.
- Service refuses public bind addresses.
- Client certificate missing, expired, wrong EKU, and wrong pin are rejected.
- Only the configured identity route is exposed in the identity-only stage.
- Service restart and certificate rotation behave predictably.

### End-to-end
- Physical Android tablet and Windows PC test on the same private network.
- Physical Android tablet and Windows PC test across a private VPN.
- Unknown/changed server identity is rejected and shown as a trust error.
- No remote input or screen stream is available in the identity-only stage.

## 12. Current implementation ledger

| Capability | Current evidence | Status |
|---|---|---|
| Android challenge generation | InfinityConnectPairing.kt | Implemented local primitive |
| Android challenge validation | expiry, UUID, nonce length and clock bounds | Implemented local primitive |
| Android one-time challenge registry | bounded process-local registry with consume-once semantics | Implemented and unit tests added; not wired to network enrollment |
| Android certificate fingerprint comparison | strict SHA-256 format and constant-time comparison | Implemented local primitive |
| Android X.509 trust policy | bounded DER parsing, SHA-256 pin, validity, EKU and digital-signature key-usage checks | Implemented helper; not yet wired into TLS transport |
| Android identity signing key | non-exportable EC P-256 key generated/stored in Android Keystore; domain-separated transcript signing; aliases restricted to 1–80 ASCII letters/digits/dot/underscore/dash | Key primitive implemented; alias-policy unit tests added; no certificate issuance/enrollment |
| Android client certificate provisioning | no issued client certificate or chain installation verified | Not implemented |
| Android TLS 1.3 mTLS client | no live client transport verified | Not implemented |
| Windows TLS 1.3 mTLS endpoint | Kestrel identity-only endpoint | Implemented in source; interoperability test still required |
| Windows certificate pin, validity, EKU and key-usage checks | CertificatePinning.cs | Implemented in source with unit tests; digitalSignature is required when Key Usage is present |
| Windows identity endpoint | /v1/identity | Identity only |
| Remote screen capture/input | no implementation verified | Not implemented |
| RDP engine | adapter contract only; FreeRDP not integrated | Not implemented |

## 13. Immediate next implementation steps

1. Add Android certificate enrollment/key lifecycle with explicit user confirmation and Android Keystore-backed private key where supported. The current PACK 16 alias validator is covered by local JVM unit tests; it does not yet exercise the Android Keystore provider on a physical device.
2. Add an Android TLS 1.3 client transport that validates the pinned Windows server certificate and presents the enrolled client certificate.
3. Add a Windows-side enrollment mechanism that updates allowed client pins only after local approval.
4. Add interoperability tests against the real Windows service before adding remote display or input capabilities.