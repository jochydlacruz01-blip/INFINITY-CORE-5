# INFINITY CONNECT — Windows Companion

## Current implementation scope

The Windows Companion source currently contains:
- TLS 1.3 with mandatory client-certificate authentication.
- A local-console enrollment CA, CSR issuance, explicit device approval and certificate revocation.
- A machine authorization registry with protected ACLs.
- Short-lived, in-memory session tokens bound to a client certificate and explicit capabilities.
- Administrator-controlled, default-deny remote capability policy.
- An RDP WebSocket tunnel whose upstream TCP connection is hard-coded to 127.0.0.1:3389.
- A managed file-transfer store with per-device ownership, size/hash validation and authenticated list/download/delete endpoints.
- Active RDP authorization polling; certificate or session revocation terminates the tunnel.
- Audit events for enrollment, authorization, transfer and tunnel operations.

These are source-level capabilities, **not proof of production readiness or physical interoperability**. The current implementation has not yet passed the complete Samsung tablet ↔ Windows PC test matrix. Do not advertise the remote desktop or file-transfer path as validated.

## Fail-closed configuration

The service refuses to start unless all required environment variables are set:
- `INFINITY_BIND_ADDRESS`: explicit private LAN/VPN interface address. Public addresses are rejected.
- `INFINITY_PORT`: optional TCP port, default 7443; allowed range 1024–65535.
- `INFINITY_DEVICE_ID`: stable device GUID.
- `INFINITY_SERVER_CERTIFICATE_PFX`: full path to the server certificate PFX.
- `INFINITY_SERVER_CERTIFICATE_PASSWORD`: PFX password supplied through a protected service secret mechanism, never committed to source control.

The server requires a valid TLS certificate and an approved client certificate. TLS 1.3 is the only enabled protocol. The Android client independently checks the expected server certificate fingerprint and Windows device identity.

## Enrollment and authorization

Enrollment is deliberately local-console driven:
1. Initialize the Windows enrollment CA from an elevated administrator console.
2. Export the public CA certificate and verify its SHA-256 fingerprint out of band.
3. Generate the Android identity key and CSR.
4. Review the CSR/device identity on Windows and issue the client certificate.
5. Install the certificate chain on Android; the private key remains in Android Keystore.
6. Approve the issued client certificate separately from the local Windows administrator console.

Issuance does not imply authorization. Revoking a certificate removes its authorization entry and active sessions are rechecked by the RDP tunnel authorization monitor. Sessions are short-lived and held in memory; they are not restored after service restart.

## Network safety

- Do not expose Windows RDP (TCP/3389) to the LAN or Internet.
- The Companion's RDP upstream target is fixed to 127.0.0.1:3389.
- Bind the Companion only to a reviewed private LAN or VPN interface and create a narrowly scoped Windows Firewall rule.
- Never configure public router port forwarding for RDP or the Companion.
- For cross-network access, use a separately reviewed private VPN.
- File transfer is restricted to the Companion-managed storage area; it is not arbitrary Windows filesystem access.

## Build and automated tests

```powershell
dotnet test .\windows\Infinity.WindowsCompanion.Tests\Infinity.WindowsCompanion.Tests.csproj --configuration Release
dotnet publish .\windows\Infinity.WindowsCompanion\Infinity.WindowsCompanion.csproj --configuration Release --runtime win-x64 --self-contained false
```

The CI workflow builds/tests the Windows Companion and publishes a CI artifact. That artifact is not a signed production installer and does not provision the CA, PFX, service environment, firewall rules or service recovery configuration.

## Acceptance gate

Follow [INFINITY CONNECT physical validation](../docs/INFINITY-CONNECT-PHYSICAL-VALIDATION.md). Keep Android remote desktop and file-transfer feature gates false until every applicable physical test passes on the actual tablet and PC, with sanitized logs and screenshots tied to exact SHAs.
