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
var capabilityPolicy = RemoteCapabilityPolicy.LoadFromEnvironment();
var auditLog = new InfinityAuditLog();
var fileTransferStore = new SecureFileTransferStore();
var operationRegistry = new DeviceOperationRegistry();
using var deviceRevocationSignal = DeviceRevocationSignal.CreateListener();
using var stopRevocationMonitor = app.Lifetime.ApplicationStopping.Register(() => deviceRevocationSignal.Set());
var revocationMonitor = Task.Run(() =>
{
    while (!app.Lifetime.ApplicationStopping.IsCancellationRequested)
    {
        // The named event is the fast path. A short timed audit is the fail-safe if the
        // local-console process cannot open the global event because of Windows object ACLs.
        deviceRevocationSignal.WaitOne(TimeSpan.FromSeconds(2));
        if (app.Lifetime.ApplicationStopping.IsCancellationRequested) break;

        foreach (var deviceFingerprint in authorizedSessions.ActiveDeviceFingerprints
                     .Concat(operationRegistry.ActiveDeviceFingerprints)
                     .Distinct(StringComparer.Ordinal))
        {
            if (authorizedDevices.IsFingerprintAuthorized(deviceFingerprint)) continue;
            var revokedSessions = authorizedSessions.RevokeDevice(deviceFingerprint);
            var cancelled = operationRegistry.RevokeDevice(deviceFingerprint);
            auditLog.Write(
                "device.revocation-signalled",
                deviceFingerprint,
                null,
                $"sessions={revokedSessions};operations-cancelled={cancelled}");
        }
    }
});

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
        operationRegistry.RevokeDevice(fingerprint);
        auditLog.Write("request.device-denied", fingerprint, null, "denied");
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        return;
    }

    operationRegistry.ActivateDevice(fingerprint);
    auditLog.Write("request.authenticated", fingerprint, null, "allowed");
    await next();
});

app.MapGet("/v1/identity", () => Results.Ok(new CompanionIdentity(
    settings.DeviceId,
    "INFINITY Windows Companion",
    "1.0.0",
    "TLS 1.3 mutual certificate authentication",
    "Remote capabilities are administrator-policy gated and default-deny",
    capabilityPolicy.GetAllowedCapabilities().OrderBy(value => value, StringComparer.Ordinal).ToArray())));

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
            request.RequestedCapabilities ?? Array.Empty<string>(),
            capabilityPolicy.GetAllowedCapabilities());
        try
        {
            operationRegistry.ActivateSession(fingerprint, grant.SessionId);
            auditLog.Write("session.created", fingerprint, grant.SessionId, "allowed");
        }
        catch
        {
            authorizedSessions.Revoke(grant.SessionId, fingerprint);
            operationRegistry.RevokeSession(fingerprint, grant.SessionId);
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

    // Close admission and cancel active operations before removing the bearer grant.
    // A request that raced past IsAuthorized must still fail BeginOperation after this point.
    var operationRevoked = operationRegistry.RevokeSession(fingerprint, sessionId);
    var authorizationRevoked = authorizedSessions.Revoke(sessionId, fingerprint);
    var revoked = operationRevoked || authorizationRevoked;
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

    DeviceOperationRegistry.OperationLease uploadOperation;
    try { uploadOperation = operationRegistry.BeginOperation(fingerprint, sessionId, context.RequestAborted); }
    catch (OperationCanceledException) { return Results.Unauthorized(); }

    using (uploadOperation)
    try
    {
        var receipt = await fileTransferStore.SaveAsync(
            context.Request.Body,
            fingerprint,
            fileName,
            length,
            expectedHash,
            uploadOperation.Token);
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
    catch (OperationCanceledException) when (!context.RequestAborted.IsCancellationRequested)
    {
        auditLog.Write("file.upload-cancelled", fingerprint, sessionId, "revoked");
        return Results.Unauthorized();
    }
});

app.MapGet("/v1/files", async (HttpContext context) =>
{
    var clientCertificate = await context.Connection.GetClientCertificateAsync();
    if (clientCertificate is null) return Results.Unauthorized();
    var fingerprint = CertificatePinning.ComputeSha256Pin(clientCertificate);
    var sessionId = context.Request.Headers["X-INFINITY-Session-ID"].ToString();
    var token = ReadBearerToken(context);
    if (token is null || !authorizedSessions.IsAuthorized(sessionId, token, fingerprint, "file.transfer"))
    {
        auditLog.Write("file.list-denied", fingerprint, null, "denied");
        return Results.StatusCode(StatusCodes.Status403Forbidden);
    }

    DeviceOperationRegistry.OperationLease listOperation;
    try { listOperation = operationRegistry.BeginOperation(fingerprint, sessionId, context.RequestAborted); }
    catch (OperationCanceledException) { return Results.Unauthorized(); }

    try
    {
        using (listOperation)
        {
            listOperation.Token.ThrowIfCancellationRequested();
            var files = fileTransferStore.List(fingerprint);
            listOperation.Token.ThrowIfCancellationRequested();
            auditLog.Write("file.listed", fingerprint, sessionId, "allowed");
            return Results.Ok(files);
        }
    }
    catch (OperationCanceledException) when (!context.RequestAborted.IsCancellationRequested)
    {
        auditLog.Write("file.list-cancelled", fingerprint, sessionId, "revoked");
        return Results.Unauthorized();
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

    DeviceOperationRegistry.OperationLease downloadOperation;
    try { downloadOperation = operationRegistry.BeginOperation(fingerprint, sessionId, context.RequestAborted); }
    catch (OperationCanceledException) { return Results.Unauthorized(); }

    using (downloadOperation)
    {
        var metadata = fileTransferStore.Find(fileId, fingerprint);
        if (metadata is null) return Results.NotFound();
        await using var file = await fileTransferStore.OpenReadVerifiedAsync(metadata, downloadOperation.Token);
        if (file is null) return Results.NotFound();
        context.Response.StatusCode = StatusCodes.Status200OK;
        context.Response.ContentType = "application/octet-stream";
        context.Response.ContentLength = metadata.Length;
        context.Response.Headers["X-INFINITY-File-SHA256"] = metadata.Sha256;
        context.Response.Headers["X-INFINITY-File-Name"] =
            Convert.ToBase64String(Encoding.UTF8.GetBytes(metadata.FileName))
                .TrimEnd('=').Replace('+', '-').Replace('/', '_');
        context.Response.Headers["Content-Disposition"] =
            $"attachment; filename*=UTF-8''{Uri.EscapeDataString(metadata.FileName)}";
        await file.CopyToAsync(context.Response.Body, 64 * 1024, downloadOperation.Token);
        auditLog.Write("file.downloaded", fingerprint, sessionId, "allowed");
        return Results.StatusCode(StatusCodes.Status200OK);
    }
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

    DeviceOperationRegistry.OperationLease deleteOperation;
    try { deleteOperation = operationRegistry.BeginOperation(fingerprint, sessionId, context.RequestAborted); }
    catch (OperationCanceledException) { return Results.Unauthorized(); }

    try
    {
        using (deleteOperation)
        {
            deleteOperation.Token.ThrowIfCancellationRequested();
            var deleted = fileTransferStore.Delete(fileId, fingerprint);
            deleteOperation.Token.ThrowIfCancellationRequested();
            auditLog.Write("file.deleted", fingerprint, sessionId, deleted ? "revoked" : "failed");
            return deleted ? Results.NoContent() : Results.NotFound();
        }
    }
    catch (OperationCanceledException) when (!context.RequestAborted.IsCancellationRequested)
    {
        auditLog.Write("file.delete-cancelled", fingerprint, sessionId, "revoked");
        return Results.Unauthorized();
    }
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

    DeviceOperationRegistry.OperationLease tunnelOperation;
    try { tunnelOperation = operationRegistry.BeginOperation(fingerprint, sessionId, context.RequestAborted); }
    catch (OperationCanceledException)
    {
        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        return;
    }

    using (tunnelOperation)
    {
        try
        {
            if (!await rdpTunnelGate.WaitAsync(0, tunnelOperation.Token))
            {
                context.Response.StatusCode = StatusCodes.Status429TooManyRequests;
                return;
            }
        }
        catch (OperationCanceledException)
        {
            context.Response.StatusCode = StatusCodes.Status401Unauthorized;
            return;
        }

        try
        {
            using var rdpClient = new TcpClient(AddressFamily.InterNetwork) { NoDelay = true };
            try
            {
                await rdpClient.ConnectAsync(IPAddress.Loopback, 3389, tunnelOperation.Token);
            }
            catch (SocketException)
            {
                auditLog.Write("tunnel.rdp-unavailable", fingerprint, sessionId, "failed");
                context.Response.StatusCode = StatusCodes.Status503ServiceUnavailable;
                return;
            }

            tunnelOperation.Token.ThrowIfCancellationRequested();
            // Revocation can race between the RDP connect and WebSocket upgrade. Abort both the
            // pending HTTP upgrade and the loopback socket until the active WebSocket binding exists.
            using var pendingHandshakeRevocation = TunnelRevocationBinding.BindPendingHandshake(
                tunnelOperation.Token, context, rdpClient);
            using var webSocket = await context.WebSockets.AcceptWebSocketAsync();
            using var tunnelCancellation = CancellationTokenSource.CreateLinkedTokenSource(
                context.RequestAborted, tunnelOperation.Token);
            using var closeResources = TunnelRevocationBinding.Bind(tunnelCancellation.Token, webSocket, rdpClient);
            pendingHandshakeRevocation.Dispose();
            var rdpStream = rdpClient.GetStream();
            auditLog.Write("tunnel.started", fingerprint, sessionId, "allowed");

            var androidToRdp = PumpWebSocketToRdpAsync(webSocket, rdpStream, tunnelCancellation.Token);
            var rdpToAndroid = PumpRdpToWebSocketAsync(rdpStream, webSocket, tunnelCancellation.Token);
            await Task.WhenAny(androidToRdp, rdpToAndroid);
            tunnelCancellation.Cancel();
            webSocket.Abort();
            rdpClient.Close();

            try
            {
                await Task.WhenAll(androidToRdp, rdpToAndroid);
            }
            catch (OperationCanceledException) { }
            catch (IOException) { }
            catch (WebSocketException) { }
            catch (ObjectDisposedException) { }
            catch (SocketException) { }

            auditLog.Write("tunnel.ended", fingerprint, sessionId, "allowed");
        }
        catch (Exception) when (tunnelOperation.Token.IsCancellationRequested)
        {
            // Includes cancellation during the pending WebSocket upgrade, which may surface as
            // an IOException/WebSocketException after HttpContext.Abort rather than OCE.
            auditLog.Write("tunnel.revoked", fingerprint, sessionId, "revoked");
            if (!context.RequestAborted.IsCancellationRequested && !context.Response.HasStarted)
                context.Response.StatusCode = StatusCodes.Status401Unauthorized;
        }
        finally
        {
            rdpTunnelGate.Release();
        }
    }

});

await app.RunAsync();
await revocationMonitor;

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

internal sealed record SessionCreateRequest(bool Consent, string[]? RequestedCapabilities);

internal sealed record CompanionIdentity(
    string DeviceId,
    string Product,
    string Version,
    string TransportSecurity,
    string Capability,
    string[] AllowedCapabilities);
