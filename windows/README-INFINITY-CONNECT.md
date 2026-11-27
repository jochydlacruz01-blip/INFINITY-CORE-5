# INFINITY CONNECT — Windows Companion

## Implemented Windows components

- Windows Service Control Manager host with controlled startup and graceful shutdown.
- WPF administrative console: service status/start/stop, device inventory, certificate approval, block, re-enable and terminal revocation.
- TLS 1.3 with mandatory client-certificate authentication.
- Local-console enrollment CA, CSR issuance, explicit device approval and certificate revocation.
- Machine authorization registry with SYSTEM/Administrators write ACLs.
- Short-lived in-memory sessions bound to an approved client certificate and explicit capabilities.
- Session heartbeat with a sliding 15-minute expiry; invalid token/certificate and expired sessions fail closed.
- Administrator-controlled, default-deny remote capability policy.
- RDP WebSocket tunnel upstream fixed to 127.0.0.1:3389.
- Managed file-transfer store with per-device ownership, size/hash validation and authenticated endpoints.
- Active operation leases cancelled on session/device revocation and expiry.
- Persistent bounded audit trail without bearer tokens, private keys, screen content or file contents.

These are source-level capabilities, not proof of physical interoperability or production readiness. The actual Samsung tablet ↔ Windows PC test matrix remains a release gate. Keep remote desktop and file transfer disabled until the exact Android/Windows SHAs pass physical validation.

## Build and test

Run from the repository root on Windows with .NET 8 SDK:

```powershell
dotnet test .\windows\Infinity.WindowsCompanion.Tests\Infinity.WindowsCompanion.Tests.csproj --configuration Release
dotnet publish .\windows\Infinity.WindowsCompanion\Infinity.WindowsCompanion.csproj --configuration Release --runtime win-x64 --self-contained true --output .\artifacts\infinity-windows-companion
dotnet publish .\windows\Infinity.WindowsCompanion.Desktop\Infinity.WindowsCompanion.Desktop.csproj --configuration Release --runtime win-x64 --self-contained true --output .\artifacts\infinity-windows-companion-desktop
```

## Install the Windows service

Use an elevated PowerShell 7.4+ session. Supply the publish directory and a PFX with a valid TLS server certificate. The installer:

1. Copies binaries, PFX and configuration to protected Program Files/ProgramData directories.
2. Protects the PFX password using machine-scope Windows DPAPI; plaintext is not stored in the registry or a configuration file. The password ciphertext and PFX are ACL-restricted to SYSTEM and local Administrators.
3. Requires an explicit private/VPN bind address assigned to a local interface and a stable non-empty device GUID.
4. Installs the service as LocalSystem with delayed automatic startup.
5. Configures SCM recovery actions (restart after 5, 15 and 60 seconds).
6. Adds a Windows Firewall rule limited to the selected local address, TCP port, Private profile and requested remote subnet (default LocalSubnet).

```powershell
.\windows\scripts\Install-INFINITYCompanion.ps1 `
  -PublishDirectory .\artifacts\infinity-windows-companion `
  -ServerCertificatePfx C:\Secure\infinity-server.pfx `
  -BindAddress 192.168.1.20 `
  -CertificateHostName 192.168.1.20 `
  -DeviceId 11111111-1111-4111-8111-111111111111 `
  -Port 7443 `
  -RemoteAddress LocalSubnet
```

The PFX must include a current TLS server-auth certificate with its private key. Its SAN must match the host/IP used by Android; Android still verifies the trusted certificate fingerprint and hostname. The installer does not create router port forwarding or expose TCP/3389. For access across different networks, first establish a separately reviewed private VPN and bind to its private interface. Do not use a public bind address.

The desktop console is published separately as `INFINITY-WINDOWS-COMPANION-DESKTOP-win-x64`. It requests elevation because approval, blocking, enabling and revocation are local administrator operations.

Uninstall without deleting authorization/audit data:

```powershell
.\windows\scripts\Uninstall-INFINITYCompanion.ps1
```

To additionally delete the protected PFX/password copies, pass `-RemoveProtectedPassword`. The CA private key, authorization registry and audit history are intentionally preserved; removal of those trust records must be a separate reviewed operation.

## Security boundary and remaining acceptance

- LocalSystem is used by this initial service package because the current registry and enrollment CA ACLs are explicitly provisioned for SYSTEM/Administrators. A dedicated virtual service identity is a hardening follow-up before broad production deployment.
- Local administrators and SYSTEM remain trusted principals. Keep the Windows host patched and limit local administrator access.
- Sessions are in-memory and intentionally not restored after service restart. Android must re-authenticate the pinned Windows identity and create a fresh session after restart; an old bearer token is never replayed.
- SCM recovery restarts the Windows service after unexpected termination. It does not silently restore desktop-control or file-transfer operations.
- Android reconnect policy is bounded (1, 2, 4, 8, 16 seconds) and must not retry authentication, user-cancelled or invalid-configuration failures. The policy is not a substitute for the physical reconnect acceptance test.
- The native RDP engine and tablet/PC physical handshake still require the documented device acceptance gate. Remote display/input/file transfer remain default-deny.
