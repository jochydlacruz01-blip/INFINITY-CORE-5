using System.Net;
using System.Security.Authentication;
using System.Security.Cryptography.X509Certificates;
using Infinity.WindowsCompanion.Security;
using Microsoft.AspNetCore.Server.Kestrel.Https;
using System.Runtime.Versioning;

[assembly: SupportedOSPlatform("windows")]

if (!OperatingSystem.IsWindows())
    throw new PlatformNotSupportedException("INFINITY Windows Companion runs only on Windows.");

if (args.Length == 1 && args[0] == "--server-cert-sha256")
{
    var serverSettings = CompanionSettings.LoadFromEnvironment();
    using var configuredCertificate = new X509Certificate2(
        serverSettings.ServerCertificatePath,
        serverSettings.ServerCertificatePassword,
        X509KeyStorageFlags.MachineKeySet | X509KeyStorageFlags.EphemeralKeySet);
    if (!CertificatePinning.IsUsableServerCertificate(configuredCertificate))
        throw new InvalidOperationException("The configured Windows TLS server certificate is unusable.");
    Console.WriteLine($"Windows Companion server certificate SHA-256: {CertificatePinning.ComputeSha256Pin(configuredCertificate)}");
    Console.WriteLine($"Windows Companion device ID: {serverSettings.DeviceId}");
    return;
}
if (args.Length == 1 && args[0] == "--initialize-enrollment-ca")
{
    var thumbprint = LocalEnrollmentCertificateAuthority.Initialize();
    Console.WriteLine($"INFINITY enrollment CA initialized. Windows certificate thumbprint: {thumbprint}");
    using var authority = new X509Certificate2(LocalEnrollmentCertificateAuthority.ExportAuthorityCertificate());
    Console.WriteLine($"Android CA SHA-256 pin: {CertificatePinning.ComputeSha256Pin(authority)}");
    return;
}
if (args.Length == 2 && args[0] == "--export-enrollment-ca")
{
    var certificateBytes = LocalEnrollmentCertificateAuthority.ExportAuthorityCertificate();
    File.WriteAllBytes(Path.GetFullPath(args[1]), certificateBytes);
    using var authority = new X509Certificate2(certificateBytes);
    Console.WriteLine($"Enrollment CA exported. SHA-256: {CertificatePinning.ComputeSha256Pin(authority)}");
    return;
}
if (args.Length == 4 && args[0] == "--issue-client-csr")
{
    var csrPath = Path.GetFullPath(args[1]);
    var chainPath = Path.GetFullPath(args[2]);
    var deviceId = args[3];
    var csrInfo = new FileInfo(csrPath);
    if (!csrInfo.Exists || csrInfo.Length is <= 0 or > 16384)
        throw new InvalidOperationException("CSR file must exist and be no larger than 16 KiB.");
    var issued = LocalEnrollmentCertificateAuthority.IssueClientCertificate(
        File.ReadAllBytes(csrPath), deviceId);
    File.WriteAllBytes(chainPath, issued.CertificateChainDer);
    var clientCertificatePath = chainPath + ".client.cer";
    File.WriteAllBytes(clientCertificatePath, issued.ClientCertificateDer);
    Console.WriteLine($"Client certificate issued for device: {deviceId}");
    Console.WriteLine($"Client SHA-256: {issued.ClientCertificateSha256}");
    Console.WriteLine($"CA SHA-256 pin for Android: {issued.AuthoritySha256}");
    Console.WriteLine($"Certificate chain: {chainPath}");
    Console.WriteLine($"Client certificate for local approval: {clientCertificatePath}");
    Console.WriteLine("Issuance does not itself authorize the device; approve it separately after reviewing the identity.");
    return;
}
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
var authorizedSessions = new AuthorizedSessionStore();
var auditLog = new InfinityAuditLog();

// Re-check authorization on every HTTP request, not only at the TLS handshake.
// This makes certificate revocation effective on the next request of an existing TLS connection.
app.Use(async (context, next) =>
{
    var clientCertificate = await context.Connection.GetClientCertificateAsync();
    if (clientCertificate is null)
    {
        auditLog.Write("request.missing-certificate", null, null, "denied");
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        return;
    }

    var fingerprint = CertificatePinning.ComputeSha256Pin(clientCertificate);
    if (!authorizedDevices.IsAuthorized(clientCertificate))
    {
        authorizedSessions.RevokeDevice(fingerprint);
        auditLog.Write("request.device-denied", fingerprint, null, "denied");
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        return;
    }

    auditLog.Write("request.authenticated", fingerprint, null, "allowed");
    await next();
});

app.MapGet("/v1/identity", () => Results.Ok(new CompanionIdentity(
    settings.DeviceId,
    "INFINITY Windows Companion",
    "1.0.0",
    "TLS 1.3 mutual certificate authentication",
    "identity-only; remote desktop is not enabled")));

app.MapPost("/v1/sessions", async (HttpContext context, SessionCreateRequest request) =>
{
    var clientCertificate = await context.Connection.GetClientCertificateAsync();
    if (clientCertificate is null) return Results.Unauthorized();
    var fingerprint = CertificatePinning.ComputeSha256Pin(clientCertificate);

    if (!request.Consent)
    {
        auditLog.Write("session.consent-denied", fingerprint, null, "denied");
        return Results.StatusCode(StatusCodes.Status403Forbidden);
    }

    try
    {
        var grant = authorizedSessions.Create(
            fingerprint,
            request.Consent,
            request.RequestedCapabilities ?? Array.Empty<string>());
        auditLog.Write("session.created", fingerprint, grant.SessionId, "allowed");
        return Results.Ok(grant);
    }
    catch (UnauthorizedAccessException)
    {
        auditLog.Write("session.capability-denied", fingerprint, null, "denied");
        return Results.Forbid();
    }
    catch (InvalidOperationException)
    {
        auditLog.Write("session.limit-reached", fingerprint, null, "failed");
        return Results.Problem("The session limit has been reached.", statusCode: StatusCodes.Status503ServiceUnavailable);
    }
});

app.MapGet("/v1/sessions/{sessionId}", async (HttpContext context, string sessionId) =>
{
    var clientCertificate = await context.Connection.GetClientCertificateAsync();
    if (clientCertificate is null) return Results.Unauthorized();
    var fingerprint = CertificatePinning.ComputeSha256Pin(clientCertificate);
    var token = ReadBearerToken(context);
    if (token is null || !authorizedSessions.IsAuthorized(sessionId, token, fingerprint, "identity.read"))
    {
        auditLog.Write("session.validation-denied", fingerprint, null, "denied");
        return Results.Unauthorized();
    }
    return Results.Ok(new { sessionId, active = true, capability = "identity.read" });
});

app.MapDelete("/v1/sessions/{sessionId}", async (HttpContext context, string sessionId) =>
{
    var clientCertificate = await context.Connection.GetClientCertificateAsync();
    if (clientCertificate is null) return Results.Unauthorized();
    var fingerprint = CertificatePinning.ComputeSha256Pin(clientCertificate);
    var token = ReadBearerToken(context);
    if (token is null || !authorizedSessions.IsAuthorized(sessionId, token, fingerprint, "identity.read"))
        return Results.Unauthorized();

    var revoked = authorizedSessions.Revoke(sessionId, fingerprint);
    auditLog.Write("session.revoked", fingerprint, sessionId, revoked ? "revoked" : "failed");
    return revoked ? Results.NoContent() : Results.NotFound();
});

app.Run();

static string? ReadBearerToken(HttpContext context)
{
    var header = context.Request.Headers["Authorization"].ToString();
    return header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)
        ? header["Bearer ".Length..].Trim()
        : null;
}

internal sealed record SessionCreateRequest(bool Consent, string[]? RequestedCapabilities);

internal sealed record CompanionIdentity(
    string DeviceId,
    string Product,
    string Version,
    string TransportSecurity,
    string Capability);
