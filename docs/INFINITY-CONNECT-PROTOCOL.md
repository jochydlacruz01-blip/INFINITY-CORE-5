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

### Local Windows device approval commands

Run the Companion executable from an elevated Windows administrator console:

- `Infinity.WindowsCompanion.exe --approve-client-cert C:\\path\\android-client.cer` validates a current client-auth certificate and adds its SHA-256 fingerprint to the machine authorization registry.
- `Infinity.WindowsCompanion.exe --revoke-client-cert <SHA256>` removes the exact fingerprint. The next TLS handshake is rejected.

The current approval operation is local-console only. It does not expose an unauthenticated network enrollment endpoint. It approves a certificate already issued by a trusted enrollment process; automated Android CSR issuance and certificate delivery remain a separate required stage.

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
| Windows TLS 1.3 mTLS endpoint | Kestrel identity-only endpoint; client certificate validated against machine-scoped approved fingerprint registry on every handshake | Implemented in source; physical Android interoperability test still required |
| Windows client certificate approval/revocation | AuthorizedDeviceStore.cs; local elevated console commands; HKLM registry allowlist | Implemented in source; Windows integration and ACL verification still required |\n| Windows certificate pin, validity, EKU and key-usage checks | CertificatePinning.cs | Implemented in source with unit tests; digitalSignature is required when Key Usage is present |
| Windows identity endpoint | /v1/identity | Identity only |
| Remote screen capture/input | no implementation verified | Not implemented |
| RDP engine | adapter contract only; FreeRDP not integrated | Not implemented |

## 13. Immediate next implementation steps

1. Add Android certificate enrollment/key lifecycle with explicit user confirmation and Android Keystore-backed private key where supported. The current PACK 16 alias validator is covered by local JVM unit tests; it does not yet exercise the Android Keystore provider on a physical device.
2. Add an Android TLS 1.3 client transport that validates the pinned Windows server certificate and presents the enrolled client certificate.
3. Complete Android CSR/certificate issuance and secure certificate delivery; the Windows local approval store is now present but does not issue certificates.
4. Add interoperability tests against the real Windows service before adding remote display or input capabilities.

## 14. PACK26-PACK27 local enrollment implementation

The Windows Companion now includes a strict PKCS#10 validation path for ECDSA P-256 / SHA-256:
- DER is bounded to 16 KiB.
- Only ECDSA-with-SHA256 and an exact P-256 subject public key are accepted.
- The CertificationRequestInfo signature is verified as proof of possession.
- CSR attributes are rejected by this initial profile.
- The device ID must match the exact locally confirmed subject CN.

A local Windows enrollment CA can be initialized from an elevated console:
- `Infinity.WindowsCompanion.exe --initialize-enrollment-ca`
- `Infinity.WindowsCompanion.exe --export-enrollment-ca C:\\path\\infinity-enrollment-ca.cer`
- `Infinity.WindowsCompanion.exe --issue-client-csr C:\\path\\android.csr C:\\path\\android-chain.der infinity-device-01`
- Review the printed SHA-256 pins. The command writes the leaf certificate to `android-chain.der.client.cer` and the concatenated leaf + CA chain to `android-chain.der`.
- Only after reviewing the identity, run the existing elevated `--approve-client-cert` command with the leaf `.cer`.

The CA private key is imported into the Windows LocalMachine personal certificate store as a non-exportable machine key. Initialization and issuance require an elevated local administrator. The issuance command is local-console only; it does not expose a network CSR endpoint. The CSR proof-of-possession signature is checked before signing. Client certificates are limited to 90 days and contain clientAuth EKU, digitalSignature key usage, and CA:false basic constraints.

Android additions:
- `InfinityConnectIdentityKeyStore.createCertificateSigningRequest` builds a PKCS#10 request from the existing non-exportable Android Keystore P-256 key.
- `InfinityConnectClientCertificateInstaller` validates the returned client certificate public key against the Keystore key, clientAuth EKU, validity, digitalSignature usage, issuer chain signatures, CA constraints, and a user-supplied CA SHA-256 pin before installing the chain onto the existing key alias.
- `InfinityConnectMtlsTransport` creates a TLS 1.3 client context using Android Keystore key managers, pins the Windows server leaf certificate, validates serverAuth/validity/chain signatures/CA constraints, relies on HTTPS hostname verification, and calls only `/v1/identity`. Redirects are disabled and the response is bounded to 8 KiB.

These source modules are not yet equivalent to a proven physical-device enrollment. Android Keystore chain replacement behavior, live TLS 1.3 client-certificate presentation, server hostname validation, and Samsung tablet ↔ Windows PC interoperability must pass on the actual devices before this stage is marked complete. Remote display, input, and file transfer remain disabled.


### Operator workflow for the first real enrollment

1. On Windows, configure the Companion server certificate and its private/VPN bind address as documented in the deployment section.
2. From an elevated console in the Companion folder, run `Infinity.WindowsCompanion.exe --initialize-enrollment-ca` once. Keep the displayed Android CA SHA-256 pin.
3. Run `Infinity.WindowsCompanion.exe --server-cert-sha256` in the same configured environment. Record the server SHA-256 pin and the Companion GUID.
4. On Android, open INFINITY CONNECT, enter the Android device ID, and export its CSR.
5. Transfer only the CSR file to Windows. It contains the public key and proof-of-possession signature, never the private key.
6. On Windows, run `Infinity.WindowsCompanion.exe --issue-client-csr <csr-file> <chain-output.der> <android-device-id>`. Transfer the generated `.der` chain back to Android.
7. Review the Android device identity, then run `Infinity.WindowsCompanion.exe --approve-client-cert <chain-output.der.client.cer>` from an elevated console.
8. On Android, enter the CA pin, import the DER chain, enter the PC private/VPN address, Companion port (7443 by default), server certificate pin and expected Companion GUID. Run the mTLS identity check.

The CSR/chain file exchange is intentionally user-mediated for this first release; no unauthenticated enrollment API is exposed. The Android UI does not report a verified PC until the TLS handshake, certificate pin, hostname validation, and expected device ID checks succeed.


## 15. PACK29-PACK30 session authorization and audit

The Windows Companion now exposes a narrowly scoped session authorization API after mTLS:
- `POST /v1/sessions` requires a verified client certificate, explicit `consent: true`, and exactly the `identity.read` capability.
- The server issues a random 256-bit bearer token, stores only its SHA-256 hash, binds it to the client certificate fingerprint, and limits the session to 15 minutes.
- The store is bounded to 256 active sessions, purges expired records, supports individual and device-wide revocation, and clears token hashes from memory when a session is removed.
- `GET /v1/sessions/{sessionId}` validates the bearer token, certificate binding, expiry and capability.
- `DELETE /v1/sessions/{sessionId}` revokes the session.
- Every HTTP request re-checks the client certificate against the persistent device allowlist. If a certificate has been revoked, the next request is rejected and all in-memory sessions for that certificate are removed.
- A bounded JSONL audit log is written under ProgramData. It records UTC timestamp, event code, certificate fingerprint, session ID and outcome; it never records bearer tokens, private keys, screen content or file content.

Android CONNECT now has a separate explicit consent step for an identity-only session. It stores the returned bearer token encrypted through the existing Android Keystore AES-GCM credential vault and offers a session-revoke action.

**Important capability boundary:** the only session capability granted in this pack is `identity.read`. `remote.display`, `remote.input`, `file.transfer`, clipboard, shell and RDP proxy capabilities remain denied and are not mapped to endpoints. Session authorization is not remote desktop functionality.


## 16. PACK31 native RDP engine dependency

The Android native engine is pinned as a Git submodule to the verified FreeRDP 3.33.1 release commit `65bcca1913a28cbdd7eb913599f692bd2b707991`. The module builds only `arm64-v8a`, matching the INFINITY tablet target. Native dependencies are configured with OpenSSL enabled and optional audio/video/image codec bundles disabled for the initial secure transport build.

A separate GitHub Actions workflow builds `:freerdpCore:externalNativeBuildDebug` only. It does not run assemble, package or upload an APK. The app's existing visual layer is not replaced by the FreeRDP sample UI; only the native library and Java client core are included.

**RDP security integration rule:** the upstream `LibFreeRDP.setConnectionInfo(Context, instance, BookmarkBase)` path appends `/cert:ignore` and must not be used by INFINITY. The adapter must use a controlled argument path with certificate verification enabled and accept the RDP server certificate only when its fingerprint matches an explicitly trusted pin. No RDP connection is enabled by this dependency integration alone.

The native library is not yet considered a functioning INFINITY remote session. It must pass native compilation, runtime loading on the Samsung tablet, pinned RDP certificate validation, screen update tests, input gating and the mTLS-authorized loopback tunnel before remote capabilities are granted.
