# INFINITY CONNECT — Pairing Challenge Contract v1

Status: **design contract only; no network endpoint or production pairing flow is enabled**.

## Security objective

Prove that an Android enrollment request is bound to a fresh challenge created by the intended Windows Companion, to the Android identity key that signs the CSR, and to a local administrator approval. Certificate issuance and device authorization remain separate decisions.

## Trust bootstrap

1. On the Windows PC, an elevated administrator initializes the local enrollment CA.
2. The administrator exports the CA public certificate and verifies its SHA-256 fingerprint through a trusted out-of-band channel.
3. Android imports/pins that CA fingerprint only after the user independently verifies it.
4. Windows creates a signed challenge locally and displays its complete compact payload as a QR code. The QR is a local/out-of-band handoff; **this contract does not define or authorize a network enrollment endpoint**.
5. Android scans the QR, validates the CA signature and expiry, then signs the enrollment proof using the same non-exportable Android Keystore key whose public key is in the CSR.
6. The proof/CSR is returned to Windows through an administrator-controlled local handoff. Windows verifies all bindings before presenting the request for explicit local approval.

A short numeric code alone is not an authentication mechanism. If a human-readable code is added to the UX later, it must be a high-entropy, one-time confirmation derived from the signed challenge and protected against online guessing; it must not replace signature validation.

## Windows-signed challenge

The QR payload is UTF-8 JSON with these required fields:

| Field | Type | Rule |
|---|---|---|
| `protocolVersion` | integer | Exactly `1`. |
| `requestId` | UUID string | Canonical lowercase `D` format; unique for each challenge. |
| `companionDeviceId` | UUID string | Stable Windows Companion identity. |
| `issuedAtUnixMs` | integer | UTC Unix milliseconds from the Windows host clock. |
| `expiresAtUnixMs` | integer | Greater than issue time and no more than 5 minutes later. |
| `nonce` | base64url string | Exactly 32 cryptographically random bytes; no padding. |
| `caSha256` | string | Uppercase 64-character SHA-256 fingerprint of the pinned CA certificate DER. |
| `signature` | base64url string | ECDSA P-384 / SHA-384 signature in RFC 3279 DER sequence format. |

The signature input is the following deterministic binary sequence (not serialized JSON):

- ASCII bytes `INFINITY-CONNECT-PAIRING-CHALLENGE-V1` followed by one zero byte.
- `protocolVersion` as unsigned 16-bit big-endian.
- `requestId` as 16 UUID bytes in RFC 4122/network byte order.
- `companionDeviceId` as 16 UUID bytes in RFC 4122/network byte order.
- `issuedAtUnixMs` as signed 64-bit big-endian.
- `expiresAtUnixMs` as signed 64-bit big-endian.
- The 32 raw nonce bytes.
- The 32 raw bytes represented by `caSha256`.

The CA signing key MUST remain in the Windows machine certificate store and MUST NOT be exported to Android or embedded in the app. Android verifies the signature using the exact CA certificate whose fingerprint was independently pinned. Reject unknown fields only if the parser is configured for strict v1 schema; always reject missing, duplicated, malformed, oversized, or out-of-range fields.

## Android enrollment proof

The Android response binds all of the following:

- protocol version, request ID, Companion ID, nonce, and challenge expiry;
- Android device ID;
- SHA-256 fingerprint of the DER-encoded CSR;
- CSR itself (PKCS#10), containing the Android Keystore public key and proof-of-possession;
- Android signature over a deterministic binary message.

Proof signature input:

- ASCII bytes `INFINITY-CONNECT-ENROLLMENT-PROOF-V1` followed by one zero byte.
- The exact challenge signature input bytes above.
- 16 UUID bytes for the Android device ID in RFC 4122/network byte order.
- 32 raw bytes of SHA-256(CSR DER).

Sign with the CSR public key's corresponding Android Keystore private key using ECDSA P-256 / SHA-256 and RFC 3279 DER sequence format. Windows MUST verify that the proof signature public key is byte-for-byte the CSR public key after canonical SPKI encoding, and independently validate the CSR PKCS#10 signature. A valid proof does not itself authorize the device.

## Windows verification and approval

Windows rejects the request unless every check passes:

1. Challenge CA signature, pinned CA fingerprint, protocol version and Companion identity match.
2. Challenge was issued by this Companion, is not expired, is within the allowed clock-skew policy, and has not been consumed.
3. Request ID and nonce are present in a bounded, persistent replay-protection store; consumption is atomic and one-time.
4. CSR DER is within the existing 16 KiB limit, uses ECDSA P-256 / SHA-256, has a valid proof-of-possession, and its subject matches the Android device ID confirmed by the administrator.
5. CSR digest and Android proof signature match the challenge and CSR exactly.
6. The Android device ID/public-key fingerprint is not already bound to a different active identity unless an administrator explicitly performs a replacement workflow.
7. An elevated local Windows administrator explicitly approves the exact device ID, CSR digest, and public-key fingerprint shown in the approval prompt.

Only after approval may Windows issue a 90-day client-auth certificate and separately add that certificate/device to the authorized-device store. Issuance MUST NOT automatically imply authorization. Rejection, expiry, revocation, or replay MUST fail closed.

## State machine

`Created -> Displayed -> ProofReceived -> Verified -> LocallyApproved -> CertificateIssued -> DeviceAuthorized`

Terminal alternatives: `Rejected`, `Expired`, `Revoked`. Every transition is monotonic. A challenge can be consumed at most once; duplicate submissions return a generic rejection and do not reveal whether a request ID exists.

## Limits and operational controls

- Challenge lifetime: at most 5 minutes.
- Nonce: 256 bits from a cryptographically secure random generator.
- CSR: existing 16 KiB maximum.
- QR payload and proof payload must have strict size limits before parsing.
- Rate-limit failed proof attempts per challenge and globally.
- Persist replay/consumption state safely across Companion restarts; an in-memory-only registry is insufficient.
- Never log nonce, private key material, full CSR, signatures, or enrollment secrets. Audit only request ID, device ID, CSR digest, actor, decision, and timestamps.
- Use constant-time comparisons for fixed-size digests/fingerprints.
- No public or LAN enrollment listener is permitted by this contract.
- Do not enable RDP, file transfer, remote input, or feature gates as a side effect of pairing.

## Required automated tests before implementation is accepted

- Deterministic challenge encoding and cross-language test vectors.
- Valid/invalid CA signature, altered field, altered nonce, altered CA pin, wrong Companion ID.
- Expiry, future-issued challenge, maximum lifetime, clock skew and overflow boundaries.
- Invalid UUID/base64url/DER, duplicate or missing JSON fields, oversized payloads.
- CSR mismatch, wrong key, invalid CSR proof-of-possession, wrong Android device ID.
- Proof replay, concurrent double-consumption, persistence/restart behavior and bounded storage.
- Approval required, rejection, revocation, replacement identity and authorization separation.
- Android and .NET implementations must pass the same shared test vectors before any UI or transport integration.

## Current implementation status

The existing Android challenge registry is a local primitive and is not connected to the enrollment UI. Windows currently issues and approves CSRs through local CLI operations. This document defines the next protocol boundary; it does **not** claim that pairing is implemented. No code path, network endpoint, or remote capability is enabled by this document.
