# INFINITY CONNECT — Windows host and Companion setup

## Compatibility

The supported host is Windows 10/11 Pro, Enterprise or Education. Windows Home cannot host Microsoft's built-in RDP sessions. The INFINITY Companion is a separate .NET 8 Windows service; it does not replace the Windows RDP host.

## 1. Prepare the built-in RDP host

Run `tools/windows/Prepare-InfinityRdpHost.ps1` from an elevated Windows PowerShell 5.1 console on a trusted private LAN. The script:
- rejects Windows Home and unsupported Windows editions;
- requires an existing Private network profile and does not silently change it;
- enables RDP and Network Level Authentication (NLA);
- enables only built-in Remote Desktop firewall rules on the Private profile and restricts them to `LocalSubnet`;
- does not create broad firewall rules, configure router forwarding or expose TCP 3389 to the Internet.

Use `-WhatIf` first. To turn RDP back off, run `tools/windows/Disable-InfinityRdpHost.ps1` elevated.

## 2. Configure the Windows Companion

The Companion listens on a deliberately selected private LAN/VPN interface and defaults to TCP 7443. It requires:
- `INFINITY_BIND_ADDRESS`: the PC's private/VPN interface IP; public addresses are rejected;
- `INFINITY_PORT`: optional, defaults to 7443;
- `INFINITY_DEVICE_ID`: a stable non-empty GUID for this PC;
- `INFINITY_SERVER_CERTIFICATE_PFX`: path to a server-authentication PFX;
- `INFINITY_SERVER_CERTIFICATE_PASSWORD`: PFX password.

The PFX must be current, contain a private key and meet the Companion server-certificate policy. Do not put the PFX password in source control, scripts, command-line arguments, chat, or ordinary configuration files. Protect the PFX and the mechanism that supplies its password with Windows ACLs restricted to the service identity and local administrators. The repository does not yet provide a production installer or a hardened secret-provisioning UI; service installation and secret storage remain operator-controlled deployment steps.

Build/test/publish the Windows Companion from GitHub Actions artifact `INFINITY-WINDOWS-COMPANION-win-x64`. The current workflow runs .NET tests and publishes the Windows x64 Companion; it does not generate an Android APK. Install the published files as a Windows service using an administrator-approved deployment procedure. The service name is `INFINITY Windows Companion`.

## 3. Enroll the Android tablet

Run the Companion's local certificate-authority commands from an elevated Windows console:
1. `Infinity.WindowsCompanion.exe --initialize-enrollment-ca` — initialize the local enrollment CA and record the printed CA SHA-256 pin.
2. `Infinity.WindowsCompanion.exe --server-cert-sha256` — record the server certificate pin and Companion device ID.
3. On Android, open INFINITY CONNECT, generate a Keystore-backed CSR and export the CSR file.
4. Transfer only the CSR to Windows. It contains the public key and proof-of-possession signature, never the private key.
5. Run `Infinity.WindowsCompanion.exe --issue-client-csr <csr-file> <chain-output.der> <android-device-id>`.
6. Review the device identity and approve the generated leaf certificate locally with `--approve-client-cert <chain-output.der.client.cer>`.
7. Transfer the certificate chain to Android and install it using the CA pin.
8. Enter the PC's private/VPN address, Companion port, TLS server pin and expected Companion GUID; run the Android mTLS identity check.

To revoke a device, run `--revoke-client-cert <SHA256>` from the elevated local console. The Companion rechecks authorization and revokes the device's active sessions.

## 4. Remote desktop and file transfer policy

The RDP tunnel does not accept an arbitrary destination. Windows Companion forwards only to `127.0.0.1:3389`, and Android binds its local FreeRDP relay only to `127.0.0.1`. TCP 3389 must never be published to the public Internet.

Remote desktop requires both Windows environment variables:
- `INFINITY_ENABLE_REMOTE_DISPLAY=true`
- `INFINITY_ENABLE_REMOTE_INPUT=true`

File transfer is independently controlled by:
- `INFINITY_ENABLE_FILE_TRANSFER=true`

All are disabled unless explicitly set to the literal value `true`. Changing them requires restarting the Companion. Android also has hard-coded feature gates for remote desktop and file transfer; both remain false until the physical interoperability/security checklist passes. Do not enable either side before the tests are complete.

File transfer is limited to 50 MiB per file and a bounded per-device storage quota. Files are stored in the Companion-managed `ProgramData\INFINITY\WindowsCompanion\Transfers` area, not written to arbitrary Windows folders. Android uses SAF for user-selected source and destination documents.

## 5. Network model

- Same LAN: use the PC's private interface address.
- Different networks: use a separately configured private VPN/overlay and use the VPN address.
- Never configure router port forwarding or UPnP for TCP 3389 or the Companion endpoint.
- Scope Windows Firewall rules to the trusted Private profile and local/VPN subnet.
- Do not bind the Companion to a public interface.

## 6. Required physical validation

Before enabling the Android feature gates, test on the actual Samsung tablet and Windows PC:
1. CSR creation, certificate installation and Android Keystore private-key use.
2. TLS 1.3 mTLS handshake, server pin, hostname and expected device ID.
3. Wrong/expired/revoked client certificate rejection.
4. RDP NLA login and exact RDP certificate SHA-256 pin.
5. Live screen frames, orientation/resolution changes and memory limits.
6. Keyboard hardware events, Unicode text, mouse click/drag and release on disconnect.
7. Session expiry, explicit revocation and certificate revocation during an active tunnel.
8. Bidirectional file transfer, SHA-256, interrupted transfers, quotas and per-device isolation.
9. Same-LAN and VPN operation without public TCP 3389 exposure.

A green CI workflow or successful TCP probe does not prove a working remote desktop session. Until the physical checklist passes, the remote desktop and file-transfer UI remains blocked by design.
