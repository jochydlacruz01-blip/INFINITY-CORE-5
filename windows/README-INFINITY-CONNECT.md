# INFINITY CONNECT — Windows Companion (PACK 12)

## Scope of this package

This is the Windows-side companion foundation. It runs as a Windows Service and exposes only a small device-identity endpoint over HTTPS with TLS 1.3 and mandatory client-certificate authentication.

It does **not** provide screen capture, keyboard/mouse control, file transfer, RDP proxying, or a remote desktop session. No Windows password or RDP credential is accepted by this service.

## Fail-closed configuration

The service refuses to start unless all required environment variables are set:

- `INFINITY_BIND_ADDRESS`: explicit local interface IP. Loopback, RFC1918, Tailscale CGNAT (100.64.0.0/10), IPv6 link-local and IPv6 ULA are accepted. Public addresses are rejected.
- `INFINITY_PORT`: optional TCP port, default 7443; allowed range 1024–65535.
- `INFINITY_DEVICE_ID`: stable non-empty GUID generated and retained by the installer.
- `INFINITY_SERVER_CERTIFICATE_PFX`: full path to the server certificate PFX.
- `INFINITY_SERVER_CERTIFICATE_PASSWORD`: PFX password supplied through a protected service environment/secret mechanism, never committed to source control.
- `INFINITY_ANDROID_CLIENT_CERT_SHA256`: SHA-256 fingerprint of the Android client certificate, verified against the presented client certificate.

The service requires a valid server certificate with a private key and a client certificate matching the configured SHA-256 pin. TLS 1.3 is the only enabled protocol. It does not fall back to cleartext or unauthenticated TLS.

## Network setup

For a first local test, bind to the PC's loopback address. For a remote tablet, bind only to the PC's private LAN or VPN interface address and create a narrowly scoped firewall rule for that interface/subnet. Do not expose TCP/3389 or this companion port to the public Internet. A private VPN such as WireGuard or Tailscale is the preferred cross-network transport.

## Build and test

```powershell
dotnet test .\windows\Infinity.WindowsCompanion.Tests\Infinity.WindowsCompanion.Tests.csproj --configuration Release
dotnet publish .\windows\Infinity.WindowsCompanion\Infinity.WindowsCompanion.csproj --configuration Release --runtime win-x64 --self-contained false
```

Install and configure the service only after certificates are provisioned and the firewall scope is reviewed. Do not place certificate passwords or private keys in the repository.

## Integration boundary

The Android app must independently validate the server certificate fingerprint and present its client certificate. The current Android PACK 11 provides local challenge/fingerprint primitives, not certificate provisioning or a TLS client yet. The next integration pack must define a reviewed certificate enrollment flow and verify interoperability on the Android tablet and Windows PC before enabling remote-control capabilities.
