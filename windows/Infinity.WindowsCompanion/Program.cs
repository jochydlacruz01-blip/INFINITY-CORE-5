using System.Net;
using System.Security.Authentication;
using System.Security.Cryptography.X509Certificates;
using Infinity.WindowsCompanion.Security;
using Microsoft.AspNetCore.Server.Kestrel.Https;
using System.Runtime.Versioning;

[assembly: SupportedOSPlatform("windows")]

if (!OperatingSystem.IsWindows())
    throw new PlatformNotSupportedException("INFINITY Windows Companion runs only on Windows.");

if (args.Length == 2 && args[0] == "--approve-client-cert")
{
    if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException("Windows is required.");
    var approvedPin = AuthorizedDeviceStore.ApproveFromLocalConsole(args[1]);
    Console.WriteLine($"Approved Android client certificate SHA-256: {approvedPin}");
    return;
}
if (args.Length == 2 && args[0] == "--revoke-client-cert")
{
    if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException("Windows is required.");
    var revoked = AuthorizedDeviceStore.RevokeFromLocalConsole(args[1]);
    Console.WriteLine(revoked ? "Device certificate revoked." : "No matching authorized device was found.");
    return;
}

var settings = CompanionSettings.LoadFromEnvironment();
var authorizedDevices = new AuthorizedDeviceStore();
var serverCertificate = new X509Certificate2(
    settings.ServerCertificatePath,
    settings.ServerCertificatePassword,
    X509KeyStorageFlags.MachineKeySet | X509KeyStorageFlags.EphemeralKeySet);

if (!CertificatePinning.IsUsableServerCertificate(serverCertificate))
{
    throw new InvalidOperationException("The configured server certificate is unusable or outside its validity period.");
}

var builder = WebApplication.CreateBuilder(args);
builder.Host.UseWindowsService(options => options.ServiceName = "INFINITY Windows Companion");
builder.WebHost.ConfigureKestrel(options =>
{
    options.Listen(settings.BindAddress, settings.Port, listen =>
    {
        listen.UseHttps(https =>
        {
            https.ServerCertificate = serverCertificate;
            https.SslProtocols = SslProtocols.Tls13;
            https.ClientCertificateMode = ClientCertificateMode.RequireCertificate;
            https.ClientCertificateValidation = (certificate, _, _) =>
                certificate is not null &&
                authorizedDevices.IsAuthorized(certificate);
        });
    });
});

var app = builder.Build();
app.MapGet("/v1/identity", () => Results.Ok(new CompanionIdentity(
    settings.DeviceId,
    "INFINITY Windows Companion",
    "1.0.0",
    "TLS 1.3 mutual certificate authentication",
    "identity-only; remote desktop is not enabled")));
app.Run();

internal sealed record CompanionIdentity(
    string DeviceId,
    string Product,
    string Version,
    string TransportSecurity,
    string Capability);
