using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;
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
app.UseWebSockets();
var rdpTunnelGate = new SemaphoreSlim(1, 1);
var authorizedSessions = new AuthorizedSessionStore();
var auditLog = new InfinityAuditLog();
var fileTransferStore = new SecureFileTransferStore();

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
        try
        {
            auditLog.Write("session.created", fingerprint, grant.SessionId, "allowed");
        }
        catch
        {
            authorizedSessions.Revoke(grant.SessionId, fingerprint);
            throw;
        }
        return Results.Ok(grant);
    }
    catch (UnauthorizedAccessException)
    {
        auditLog.Write("session.capability-denied", fingerprint, null, "denied");
        return Results.StatusCode(StatusCodes.Status403Forbidden);
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

app.MapPost("/v1/files", async (HttpContext context) =>
{
    var clientCertificate = await context.Connection.GetClientCertificateAsync();
    if (clientCertificate is null) return Results.Unauthorized();
    var fingerprint = CertificatePinning.ComputeSha256Pin(clientCertificate);
    var sessionId = context.Request.Headers["X-INFINITY-Session-ID"].ToString();
    var token = ReadBearerToken(context);
    if (token is null || !authorizedSessions.IsAuthorized(sessionId, token, fingerprint, "file.transfer"))
    {
        auditLog.Write("file.upload-denied", fingerprint, null, "denied");
        return Results.StatusCode(StatusCodes.Status403Forbidden);
    }

    if (context.Request.ContentLength is not long length ||
        length < 0 || length > SecureFileTransferStore.MaximumFileBytes)
        return Results.StatusCode(StatusCodes.Status413PayloadTooLarge);

    var fileName = ReadEncodedFileName(context);
    var expectedHash = context.Request.Headers["X-INFINITY-File-SHA256"].ToString();
    if (fileName is null || CertificatePinning.NormalizeSha256Pin(expectedHash) is null)
        return Results.BadRequest("A valid filename and SHA-256 digest are required.");

    try
    {
        var receipt = await fileTransferStore.SaveAsync(
            context.Request.Body,
            fingerprint,
            fileName,
            length,
            expectedHash,
            context.RequestAborted);
        try
        {
            auditLog.Write("file.uploaded", fingerprint, sessionId, "allowed");
        }
        catch
        {
            fileTransferStore.Delete(receipt.FileId, fingerprint);
            throw;
        }
        return Results.Ok(receipt);
    }
    catch (InvalidDataException)
    {
        auditLog.Write("file.upload-failed", fingerprint, sessionId, "failed");
        return Results.BadRequest("The file failed filename, size or content validation.");
    }
    catch (CryptographicException)
    {
        auditLog.Write("file.upload-failed", fingerprint, sessionId, "failed");
        return Results.BadRequest("The uploaded file digest is invalid.");
    }
});

app.MapGet("/v1/files/{fileId}", async (HttpContext context, string fileId) =>
{
    var clientCertificate = await context.Connection.GetClientCertificateAsync();
    if (clientCertificate is null) return Results.Unauthorized();
    var fingerprint = CertificatePinning.ComputeSha256Pin(clientCertificate);
    var sessionId = context.Request.Headers["X-INFINITY-Session-ID"].ToString();
    var token = ReadBearerToken(context);
    if (token is null || !authorizedSessions.IsAuthorized(sessionId, token, fingerprint, "file.transfer"))
    {
        auditLog.Write("file.download-denied", fingerprint, null, "denied");
        return Results.StatusCode(StatusCodes.Status403Forbidden);
    }

    var metadata = fileTransferStore.Find(fileId, fingerprint);
    if (metadata is null) return Results.NotFound();
    await using var file = await fileTransferStore.OpenReadVerifiedAsync(metadata, context.RequestAborted);
    if (file is null) return Results.NotFound();
    context.Response.StatusCode = StatusCodes.Status200OK;
    context.Response.ContentType = "application/octet-stream";
    context.Response.ContentLength = metadata.Length;
    context.Response.Headers["Content-Disposition"] =
        $"attachment; filename*=UTF-8''{Uri.EscapeDataString(metadata.FileName)}";
    await file.CopyToAsync(context.Response.Body, 64 * 1024, context.RequestAborted);
    auditLog.Write("file.downloaded", fingerprint, sessionId, "allowed");
    return Results.StatusCode(StatusCodes.Status200OK);
});

app.MapDelete("/v1/files/{fileId}", async (HttpContext context, string fileId) =>
{
    var clientCertificate = await context.Connection.GetClientCertificateAsync();
    if (clientCertificate is null) return Results.Unauthorized();
    var fingerprint = CertificatePinning.ComputeSha256Pin(clientCertificate);
    var sessionId = context.Request.Headers["X-INFINITY-Session-ID"].ToString();
    var token = ReadBearerToken(context);
    if (token is null || !authorizedSessions.IsAuthorized(sessionId, token, fingerprint, "file.transfer"))
    {
        auditLog.Write("file.delete-denied", fingerprint, null, "denied");
        return Results.StatusCode(StatusCodes.Status403Forbidden);
    }

    var deleted = fileTransferStore.Delete(fileId, fingerprint);
    auditLog.Write("file.deleted", fingerprint, sessionId, deleted ? "revoked" : "failed");
    return deleted ? Results.NoContent() : Results.NotFound();
});

app.MapGet("/v1/tunnel/rdp", async (HttpContext context) =>
{
    if (!context.WebSockets.IsWebSocketRequest)
    {
        context.Response.StatusCode = StatusCodes.Status400BadRequest;
        return;
    }

    var clientCertificate = await context.Connection.GetClientCertificateAsync();
    if (clientCertificate is null)
    {
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        return;
    }

    var fingerprint = CertificatePinning.ComputeSha256Pin(clientCertificate);
    var sessionId = context.Request.Query["sessionId"].ToString();
    var token = ReadBearerToken(context);
    if (token is null ||
        !authorizedSessions.IsAuthorized(sessionId, token, fingerprint, "remote.display") ||
        !authorizedSessions.IsAuthorized(sessionId, token, fingerprint, "remote.input"))
    {
        auditLog.Write("tunnel.capability-denied", fingerprint, null, "denied");
        context.Response.StatusCode = StatusCodes.Status403Forbidden;
        return;
    }

    if (!await rdpTunnelGate.WaitAsync(0, context.RequestAborted))
    {
        context.Response.StatusCode = StatusCodes.Status429TooManyRequests;
        return;
    }

    try
    {
        using var rdpClient = new TcpClient(AddressFamily.InterNetwork) { NoDelay = true };
        try
        {
            await rdpClient.ConnectAsync(IPAddress.Loopback, 3389, context.RequestAborted);
        }
        catch (SocketException)
        {
            auditLog.Write("tunnel.rdp-unavailable", fingerprint, sessionId, "failed");
            context.Response.StatusCode = StatusCodes.Status503ServiceUnavailable;
            return;
        }

        using var webSocket = await context.WebSockets.AcceptWebSocketAsync();
        using var tunnelCancellation = CancellationTokenSource.CreateLinkedTokenSource(context.RequestAborted);
        var rdpStream = rdpClient.GetStream();
        auditLog.Write("tunnel.started", fingerprint, sessionId, "allowed");

        var androidToRdp = PumpWebSocketToRdpAsync(webSocket, rdpStream, tunnelCancellation.Token);
        var rdpToAndroid = PumpRdpToWebSocketAsync(rdpStream, webSocket, tunnelCancellation.Token);
        var authorizationWatch = MonitorRdpAuthorizationAsync(
            webSocket,
            clientCertificate,
            fingerprint,
            sessionId,
            token,
            authorizedDevices,
            authorizedSessions,
            auditLog,
            tunnelCancellation);

        await Task.WhenAny(androidToRdp, rdpToAndroid, authorizationWatch);
        tunnelCancellation.Cancel();
        webSocket.Abort();

        try
        {
            await Task.WhenAll(androidToRdp, rdpToAndroid, authorizationWatch);
        }
        catch (OperationCanceledException) { }
        catch (IOException) { }
        catch (WebSocketException) { }

        auditLog.Write("tunnel.ended", fingerprint, sessionId, "allowed");
    }
    finally
    {
        rdpTunnelGate.Release();
    }
});

app.Run();

static string? ReadBearerToken(HttpContext context)
{
    var header = context.Request.Headers["Authorization"].ToString();
    return header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)
        ? header["Bearer ".Length..].Trim()
        : null;
}

static string? ReadEncodedFileName(HttpContext context)
{
    var encoded = context.Request.Headers["X-INFINITY-File-Name"].ToString();
    if (encoded.Length is < 1 or > 684 ||
        encoded.Any(character => !(character is >= 'A' and <= 'Z' or >= 'a' and <= 'z' or >= '0' and <= '9' or '-' or '_')))
        return null;
    try
    {
        var normalized = encoded.Replace('-', '+').Replace('_', '/');
        normalized += new string('=', (4 - normalized.Length % 4) % 4);
        var bytes = Convert.FromBase64String(normalized);
        try { return new UTF8Encoding(false, true).GetString(bytes); }
        finally { CryptographicOperations.ZeroMemory(bytes); }
    }
    catch (FormatException)
    {
        return null;
    }
    catch (DecoderFallbackException)
    {
        return null;
    }
}

static async Task PumpWebSocketToRdpAsync(
    WebSocket webSocket,
    Stream rdpStream,
    CancellationToken cancellationToken)
{
    var buffer = new byte[32 * 1024];
    while (!cancellationToken.IsCancellationRequested)
    {
        var result = await webSocket.ReceiveAsync(buffer.AsMemory(), cancellationToken);
        if (result.MessageType == WebSocketMessageType.Close) return;
        if (result.MessageType != WebSocketMessageType.Binary)
            throw new WebSocketException(WebSocketError.InvalidMessageType);
        await rdpStream.WriteAsync(buffer.AsMemory(0, result.Count), cancellationToken);
    }
}

static async Task PumpRdpToWebSocketAsync(
    Stream rdpStream,
    WebSocket webSocket,
    CancellationToken cancellationToken)
{
    var buffer = new byte[32 * 1024];
    while (!cancellationToken.IsCancellationRequested)
    {
        var count = await rdpStream.ReadAsync(buffer.AsMemory(), cancellationToken);
        if (count == 0) return;
        await webSocket.SendAsync(
            buffer.AsMemory(0, count),
            WebSocketMessageType.Binary,
            endOfMessage: true,
            cancellationToken);
    }
}

static async Task MonitorRdpAuthorizationAsync(
    WebSocket webSocket,
    X509Certificate clientCertificate,
    string fingerprint,
    string sessionId,
    string token,
    AuthorizedDeviceStore authorizedDevices,
    AuthorizedSessionStore authorizedSessions,
    InfinityAuditLog auditLog,
    CancellationTokenSource cancellation)
{
    while (!cancellation.IsCancellationRequested)
    {
        await Task.Delay(TimeSpan.FromSeconds(2), cancellation.Token);
        if (authorizedDevices.IsAuthorized(clientCertificate) &&
            authorizedSessions.IsAuthorized(sessionId, token, fingerprint, "remote.display") &&
            authorizedSessions.IsAuthorized(sessionId, token, fingerprint, "remote.input"))
            continue;

        auditLog.Write("tunnel.revoked", fingerprint, sessionId, "revoked");
        cancellation.Cancel();
        webSocket.Abort();
        return;
    }
}

internal sealed record SessionCreateRequest(bool Consent, string[]? RequestedCapabilities);

internal sealed record CompanionIdentity(
    string DeviceId,
    string Product,
    string Version,
    string TransportSecurity,
    string Capability);
