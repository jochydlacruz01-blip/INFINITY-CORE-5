# INFINITY CONNECT — Device authorization and revocation

## Security boundary

Windows Companion is the authority for device approval. Android may inspect and revoke only the client certificate presented by its current TLS 1.3 mutual-authenticated connection. It cannot enumerate, block, or re-enable other devices remotely. Those administrative operations require an elevated local Windows console; this avoids introducing an unreviewed remote-administrator credential or changing the Enrollment Proof/certificate protocol.

## Device states

- `approved`: current certificate, chains to the local enrollment CA, and may authenticate.
- `blocked`: temporarily disabled; TLS authorization is denied. An administrator may re-enable it.
- `revoked`: terminal tombstone; future connections are denied and this certificate cannot be re-enabled. Issue and approve a new certificate.

Allowed transitions:

```text
approved <-> blocked
approved -> revoked
blocked  -> revoked
revoked  -> (terminal)
```

The fingerprint remains SHA-256 of the exact DER client certificate. The CSR, Enrollment Proof, certificate format, key algorithm, and transcript are unchanged.

## Windows administration

Run the Companion executable from an elevated PowerShell/Command Prompt:

```powershell
.\Infinity.WindowsCompanion.exe --list-authorized-devices
.\Infinity.WindowsCompanion.exe --block-client-cert <SHA256_FINGERPRINT>
.\Infinity.WindowsCompanion.exe --enable-client-cert <SHA256_FINGERPRINT>
.\Infinity.WindowsCompanion.exe --revoke-client-cert <SHA256_FINGERPRINT>
```

The listing reports status, certificate subject/device ID, fingerprint, and last successful mTLS connection. The authorization and metadata registry keys are machine-wide and ACL-protected: SYSTEM and Administrators can write; built-in Users can read. Last-seen metadata is throttled to one registry write per device per minute.

A block/revoke operation signals the Companion service. The service denies new TLS handshakes, rechecks authorization on every HTTP request, closes admission to new operations, and cancels active sessions and operation leases. Re-enabling a blocked device permits new handshakes only; it does not restore old bearer sessions. A revoked certificate remains in the registry as a tombstone.

## Android self-management

After verifying the saved Windows device identity and TLS server fingerprint, Android can:

- Query `GET /v1/device/self` to display its own certificate status and last-seen time.
- Invoke `DELETE /v1/device/self` over mTLS to revoke only the fingerprint bound to that connection.
- After HTTP 204 only, delete the local Android Keystore client key and encrypted identity/remote/file session tokens.

The server persists the revoked state, cancels sessions and operation leases, and records `device.self-revoked` before acknowledging. If the request fails or times out, Android retains its key and tokens so the user can retry; the UI must not claim success.

## Audit

Windows audit events include `device.blocked`, `device.enabled`, `device.revoked`, and `device.self-revoked`. Records contain the certificate fingerprint and event time, never private keys, bearer tokens, file contents, or screen data.

## Validation boundary

Windows automated tests cover listing/metadata, approval, block, re-enable, terminal revocation, self-revocation input handling, registry ACLs, and active-operation cancellation. Android CI covers compilation, unit tests, and lint for the mTLS status/self-revocation client. The actual Samsung tablet ↔ Windows PC handshake and live revocation test must still run on the physical devices and be tied to the exact tested commit before enabling remote desktop or file transfer.

Remote desktop and file-transfer feature gates remain disabled.
