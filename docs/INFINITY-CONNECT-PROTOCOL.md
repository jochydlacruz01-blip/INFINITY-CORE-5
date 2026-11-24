# INFINITY CONNECT — Protocol and Trust Contract

**Status:** Source implementation present; physical interoperability and release acceptance pending.
**Scope:** Android ↔ Windows Companion identity, capability-bound sessions, RDP tunnel and secure file transfer.
**Security posture:** fail closed; no public RDP exposure.

## 1. Scope and non-goals

This contract documents the source-level implementation for device trust, short-lived sessions, the loopback RDP tunnel and file transfer. Android includes Keystore CSR generation/chain installation, TLS 1.3 mTLS, session authorization, a WebSocket tunnel and a FreeRDP adapter. Windows includes local certificate issuance/approval, mTLS, capability sessions, bounded file transfer and a loopback-only RDP relay.

This is not a claim of physical-device operation. Android remote-desktop and file-transfer feature gates remain false, and Windows capability switches default to deny until the tablet/PC interoperability and security checklist passes. No public RDP exposure is allowed.

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

The current `/v1/identity` response contains `deviceId`, `product`, `version`, `transportSecurity`, `capability` and `allowedCapabilities`. It is bounded and protected by TLS 1.3/mTLS. Protocol versioning and a request correlation ID remain candidates for a future compatible schema revision; a certificate fingerprint in JSON must not replace TLS pin validation.

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

The Windows Companion source implements `identity.read`, `remote.display`, `remote.input` and `file.transfer`. RDP display/input must be granted together; file transfer is independent. The administrator policy is default-deny and requires explicit environment switches. Each capability is bound to a short-lived certificate-specific session and requires explicit Android consent. Clipboard, shell and multi-display remain unavailable. mTLS alone never grants a capability.

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
- Remote endpoints reject requests unless the matching capability is explicitly enabled by Windows administrator policy and granted to the certificate-bound session.
- Service restart and certificate rotation behave predictably.

### End-to-end
- Physical Android tablet and Windows PC test on the same private network.
- Physical Android tablet and Windows PC test across a private VPN.
- Unknown/changed server identity is rejected and shown as a trust error.
- With default policy, remote input, screen stream and file transfer are denied. After explicit administrator configuration, verify capability authorization and revocation on physical devices.

### Local Windows device approval commands

Run the Companion executable from an elevated Windows administrator console:

- `Infinity.WindowsCompanion.exe --approve-client-cert C:\\path\\android-client.cer` validates a current client-auth certificate and adds its SHA-256 fingerprint to the machine authorization registry.
- `Infinity.WindowsCompanion.exe --revoke-client-cert <SHA256>` removes the exact fingerprint. The next TLS handshake is rejected.

The current approval operation is local-console only. It does not expose an unauthenticated network enrollment endpoint. It approves a certificate already issued by a trusted enrollment process; automated Android CSR issuance and certificate delivery remain a separate required stage.

## 12. Current implementation ledger

| Capability | Source evidence | Status |
|---|---|---|
| Android identity key and CSR | `InfinityConnectIdentityKeyStore`, `InfinityConnectCsr` | Implemented in source; physical Keystore behavior pending |
| Windows local CA and CSR issuance | `LocalEnrollmentCertificateAuthority`, `CertificateSigningRequestValidator` | Implemented; local elevated operator workflow |
| Android client certificate installation | `InfinityConnectClientCertificateInstaller` | Implemented with chain, EKU, validity, public-key and CA-pin checks |
| TLS 1.3 mTLS identity | `InfinityConnectMtlsTransport`, `/v1/identity` | Implemented in source; live device handshake pending |
| Capability-bound sessions | `AuthorizedSessionStore`, `POST/GET/DELETE /v1/sessions` | Implemented; 15-minute lifetime, certificate binding, explicit consent and revocation |
| Administrator capability policy | `RemoteCapabilityPolicy` | Implemented; remote and file capabilities default-deny |
| Android remote RDP relay | `InfinityConnectRdpTunnel` | Implemented in source; loopback-only client socket and mTLS WebSocket |
| Windows RDP relay | `GET /v1/tunnel/rdp` | Implemented in source; forwards only to `127.0.0.1:3389`, rechecks authorization |
| FreeRDP Android adapter | `InfinityFreeRdpClient`, pinned FreeRDP 3.33.1 submodule | Integrated in source; current native build/runtime validation pending |
| Remote screen and input UI | `InfinityConnectRemoteDesktopPanel` | Implemented but client feature gate remains false |
| Secure file transfer | `InfinityConnectFileTransferPanel`, `SecureFileTransferStore` | Implemented in source; 50 MiB/file, quotas and SHA-256 checks; client gate false |
| Windows Companion unit tests | `Infinity.WindowsCompanion.Tests` | CI has passed on earlier/current runs; final current-head result tracked separately |
| Physical Android ↔ Windows validation | Samsung tablet + Windows PC | Pending; mandatory before enabling capabilities |

## 13. Immediate next implementation steps

1. Complete the current-head Android source compilation, unit tests, lint and FreeRDP arm64-v8a native build without generating APKs.
2. Complete the current-head Windows Companion build and unit tests.
3. Perform physical Android Keystore enrollment, mTLS handshake, hostname/pin verification and certificate revocation tests.
4. Perform physical NLA/RDP tests covering screen updates, keyboard, pointer, reconnect, disconnect, certificate rejection and immediate session revocation.
5. Perform bidirectional SAF file transfer, integrity, interruption, quota, per-device isolation and revocation tests.
6. Only after those tests pass, review and deliberately change both Windows administrator policy and Android feature gates. Keep TCP/3389 loopback-only; use a private VPN between different networks.
7. Add protocol version/correlation fields only with compatibility tests; do not claim full protocol version negotiation until implemented.

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
- `InfinityConnectMtlsTransport` creates a TLS 1.3 client context using Android Keystore key managers, pins the Windows server certificate, validates serverAuth/validity/chain signatures/CA constraints, relies on HTTPS hostname verification, and exposes identity, session, revocation and file-transfer requests with bounded responses.

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

**Important capability boundary:** session creation accepts only capabilities allowed by `RemoteCapabilityPolicy`. The default policy grants `identity.read` only. When explicitly enabled by the Windows administrator, `remote.display` and `remote.input` must be requested together, while `file.transfer` is independently granted. Tokens are short-lived, certificate-bound and revocable.


## 16. PACK31 native RDP engine dependency

The Android native engine is pinned as a Git submodule to the verified FreeRDP 3.33.1 release commit `65bcca1913a28cbdd7eb913599f692bd2b707991`. The module builds only `arm64-v8a`, matching the INFINITY tablet target. Native dependencies are configured with OpenSSL enabled and optional audio/video/image codec bundles disabled for the initial secure transport build.

A separate GitHub Actions workflow builds `:freerdpCore:externalNativeBuildDebug` only. It does not run assemble, package or upload an APK. The app's existing visual layer is not replaced by the FreeRDP sample UI; only the native library and Java client core are included.

**RDP security integration rule:** the upstream `LibFreeRDP.setConnectionInfo(Context, instance, BookmarkBase)` path appends `/cert:ignore` and must not be used by INFINITY. The adapter must use a controlled argument path with certificate verification enabled and accept the RDP server certificate only when its fingerprint matches an explicitly trusted pin. No RDP connection is enabled by this dependency integration alone.

The Android source now integrates the FreeRDP adapter with the mTLS WebSocket-to-loopback tunnel, NLA and a pinned RDP certificate. The native library still requires a current-head build and runtime loading on the Samsung tablet; screen updates, input, credential handling, disconnect and revocation must be validated physically before remote capabilities are enabled.


## 17. PACK32 loopback RDP tunnel (capability-gated)

Windows now has a WebSocket tunnel endpoint at `GET /v1/tunnel/rdp`:
- Requires mTLS, a valid bearer session, and both `remote.display` and `remote.input`.
- Opens only `127.0.0.1:3389`; the client cannot select an arbitrary destination or port.
- Allows one active RDP tunnel at a time.
- Relays bounded 32 KiB binary frames in both directions.
- Revalidates the approved client certificate and both session capabilities every two seconds; revocation closes the tunnel.
- Records tunnel start, denial, revocation and end events without recording RDP payloads or credentials.

Android now has a companion relay that binds a temporary TCP listener only to `127.0.0.1`, uses the pinned TLS 1.3 mTLS context for the WebSocket, requires both remote capabilities before opening, bounds its queues, and closes both sides when the tunnel ends.

**The route is implemented but remains disabled by default:** Windows refuses `remote.display`/`remote.input` unless both administrator switches are explicitly enabled and the session grants both capabilities. Android's `REMOTE_DESKTOP_VALIDATED` gate is also false. The tunnel and FreeRDP adapter are connected in source, but no live tablet-to-PC session is certified until physical tests pass.


## 18. PACK34 administrator capability policy

Remote capabilities are server-side default-deny and are read when the Companion starts:
- `INFINITY_ENABLE_REMOTE_DISPLAY=true` and `INFINITY_ENABLE_REMOTE_INPUT=true` must both be set to permit the RDP capability pair.
- `INFINITY_ENABLE_FILE_TRANSFER=true` independently permits the file-transfer capability.
- Any missing, invalid or non-`true` value is treated as disabled.
- A session request must explicitly include `identity.read`; RDP display/input must be requested together.
- The RDP tunnel still checks both capabilities on every request and every two seconds during the tunnel.
- Changing the environment requires restarting the Windows Companion service.

Do not enable these variables until the matching Android/Windows integration tests and physical-device checks pass. The Android UI contains separate explicit consent flows for remote desktop and file transfer, but the hard-coded client feature gates remain false, so those screens stay unavailable during this development phase.


## 19. PACK37–38 Android remote client and safety gates

The Android client now contains:
- FreeRDP 3.33.1 adapter over the mTLS WebSocket-to-loopback tunnel.
- NLA authentication, SHA-256 RDP certificate pinning, no `/cert:ignore`, and clipboard redirection disabled.
- Frame rendering, pointer click/drag translation, FreeRDP keyboard layout mapping and Unicode text input.
- A SAF file-transfer panel using the mTLS file API, per-file SHA-256 verification and per-device file listing.

These client features are **not enabled yet**. `InfinityConnectFeatureGates.REMOTE_DESKTOP_VALIDATED` and `FILE_TRANSFER_VALIDATED` are both hard-coded false until a real Samsung tablet ↔ Windows PC test is completed. Windows also defaults all remote capability environment switches to false. Do not change either layer to true before the interoperability and revocation checklist passes.

The RDP certificate pin field expects the SHA-256 digest of the exact certificate Windows RDP presents. On Windows, inspect the local Remote Desktop certificate store with PowerShell:

```powershell
Get-ChildItem 'Cert:\\LocalMachine\\Remote Desktop' |
  Where-Object { $_.HasPrivateKey } |
  ForEach-Object {
    [pscustomobject]@{
      Subject = $_.Subject
      SHA256 = [Convert]::ToHexString(
        [System.Security.Cryptography.SHA256]::HashData($_.RawData)
      )
    }
  }
```

Use the digest only after confirming that the certificate is the one bound to the RDP listener. The Companion tunnel remains loopback-only and does not expose TCP/3389 to the network.
