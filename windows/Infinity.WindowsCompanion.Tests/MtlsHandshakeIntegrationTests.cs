using System.Net;
using System.Net.Sockets;
using System.Net.Security;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

/// <summary>
/// Real loopback HTTPS exchanges over SslStream. The HTTP request/response is carried inside
/// TLS; the server independently validates client PKIX + device allowlist, while the client
/// validates server PKIX + hostname + exact leaf pin.
/// </summary>
public sealed class MtlsHandshakeIntegrationTests
{
    [Fact]
    public async Task HttpsMtlsRejectsExpiredUnauthorizedWrongPinWrongHostAndInvalidChains()
    {
        using var root = CreateRoot("INFINITY integration root");
        using var unrelatedRoot = CreateRoot("INFINITY unrelated root");
        using var serverCertificate = CreateLeaf(
            root, "localhost", CertificatePinning.ServerAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddHours(2), "localhost");
        using var validClient = CreateLeaf(
            root, "android-device-01", CertificatePinning.ClientAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddHours(2));
        using var expiredClient = CreateLeaf(
            root, "android-expired", CertificatePinning.ClientAuthenticationOid,
            DateTimeOffset.UtcNow.AddHours(-2), DateTimeOffset.UtcNow.AddHours(-1));
        using var unauthorizedClient = CreateLeaf(
            root, "android-not-approved", CertificatePinning.ClientAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddHours(2));
        using var untrustedClient = CreateLeaf(
            unrelatedRoot, "android-untrusted", CertificatePinning.ClientAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddHours(2));
        using var untrustedServer = CreateLeaf(
            unrelatedRoot, "localhost", CertificatePinning.ServerAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddHours(2), "localhost");
        using var expiredServer = CreateLeaf(
            root, "localhost", CertificatePinning.ServerAuthenticationOid,
            DateTimeOffset.UtcNow.AddHours(-2), DateTimeOffset.UtcNow.AddHours(-1), "localhost");

        Assert.True(CertificatePinning.ValidatePkixChain(
            validClient, root, CertificatePinning.ClientAuthenticationOid));
        Assert.True(CertificatePinning.ValidatePkixChain(
            serverCertificate, root, CertificatePinning.ServerAuthenticationOid));

        var approvedPins = new HashSet<string>(StringComparer.Ordinal)
        {
            CertificatePinning.ComputeSha256Pin(validClient)
        };
        var serverPin = CertificatePinning.ComputeSha256Pin(serverCertificate);

        var validDiagnostics = new System.Collections.Concurrent.ConcurrentQueue<string>();
        Assert.True(await ExchangeAsync(
            serverCertificate, validClient, root, root, approvedPins, "localhost", serverPin, validDiagnostics),
            string.Join(" | ", validDiagnostics));
        Assert.False(await ExchangeAsync(
            serverCertificate, validClient, root, root, approvedPins, "localhost", new string('0', 64)));
        Assert.False(await ExchangeAsync(
            serverCertificate, validClient, root, root, approvedPins, "127.0.0.1", serverPin));
        Assert.False(await ExchangeAsync(
            serverCertificate, expiredClient, root, root, approvedPins, "localhost", serverPin));
        Assert.False(await ExchangeAsync(
            serverCertificate, unauthorizedClient, root, root, approvedPins, "localhost", serverPin));
        Assert.False(await ExchangeAsync(
            serverCertificate, untrustedClient, root, root, approvedPins, "localhost", serverPin));
        Assert.False(await ExchangeAsync(
            untrustedServer, validClient, root, root, approvedPins, "localhost",
            CertificatePinning.ComputeSha256Pin(untrustedServer)));
        Assert.False(await ExchangeAsync(
            expiredServer, validClient, root, root, approvedPins, "localhost",
            CertificatePinning.ComputeSha256Pin(expiredServer)));
    }

    private static async Task<bool> ExchangeAsync(
        X509Certificate2 serverCertificate,
        X509Certificate2 clientCertificate,
        X509Certificate2 trustedServerRoot,
        X509Certificate2 trustedClientRoot,
        IReadOnlySet<string> approvedClientPins,
        string targetHost,
        string expectedServerPin,
        System.Collections.Concurrent.ConcurrentQueue<string>? diagnostics = null)
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(8));

        var serverTask = Task.Run(async () =>
        {
            try
            {
                using var tcp = await listener.AcceptTcpClientAsync(timeout.Token);
                using var tls = new SslStream(tcp.GetStream(), leaveInnerStreamOpen: false,
                    (_, certificate, _, _) =>
                    {
                        if (certificate is null)
                        {
                            diagnostics?.Enqueue("server-client-cert:null");
                            return false;
                        }
                        using var presentedClient = new X509Certificate2(certificate);
                        var fingerprint = CertificatePinning.ComputeSha256Pin(presentedClient);
                        var pkix = CertificatePinning.ValidatePkixChain(
                            presentedClient, trustedClientRoot, CertificatePinning.ClientAuthenticationOid);
                        var approved = approvedClientPins.Contains(fingerprint);
                        diagnostics?.Enqueue($"server-client:pkix={pkix};approved={approved};subject={presentedClient.Subject}");
                        return pkix && approved;
                    });

                await tls.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
                {
                    ServerCertificate = serverCertificate,
                    ClientCertificateRequired = true,
                    EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13,
                    CertificateRevocationCheckMode = X509RevocationMode.NoCheck
                }, timeout.Token);

                var requestBuffer = new byte[2048];
                var requestBytes = await tls.ReadAsync(requestBuffer, timeout.Token);
                if (requestBytes <= 0) return false;
                var response = Encoding.ASCII.GetBytes(
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 6\r\nConnection: close\r\n\r\nmTLS-ok");
                await tls.WriteAsync(response, timeout.Token);
                await tls.FlushAsync(timeout.Token);
                return true;
            }
            catch (Exception error) when (error is AuthenticationException or IOException or
                                           SocketException or OperationCanceledException or CryptographicException)
            {
                diagnostics?.Enqueue($"server-exception:{error.GetType().Name}:{error.Message}");
                return false;
            }
        });

        try
        {
            using var tcp = new TcpClient();
            await tcp.ConnectAsync(IPAddress.Loopback, port, timeout.Token);
            using var tls = new SslStream(tcp.GetStream(), leaveInnerStreamOpen: false,
                (_, certificate, _, errors) =>
                {
                    if (certificate is null ||
                        (errors & (SslPolicyErrors.RemoteCertificateNameMismatch |
                                   SslPolicyErrors.RemoteCertificateNotAvailable)) != 0)
                    {
                        diagnostics?.Enqueue($"client-server-cert:missing-or-hostname:{errors}");
                        return false;
                    }

                    using var presentedServer = new X509Certificate2(certificate);
                    var actualPin = CertificatePinning.ComputeSha256Pin(presentedServer);
                    var expectedPin = CertificatePinning.NormalizeSha256Pin(expectedServerPin);
                    var pkix = CertificatePinning.ValidatePkixChain(
                        presentedServer, trustedServerRoot, CertificatePinning.ServerAuthenticationOid);
                    var pin = expectedPin is not null && CryptographicOperations.FixedTimeEquals(
                        Convert.FromHexString(actualPin), Convert.FromHexString(expectedPin));
                    diagnostics?.Enqueue($"client-server:errors={errors};pkix={pkix};pin={pin}");
                    return pkix && pin;
                });

            await tls.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
            {
                TargetHost = targetHost,
                ClientCertificates = new X509CertificateCollection { clientCertificate },
                EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13,
                CertificateRevocationCheckMode = X509RevocationMode.NoCheck
            }, timeout.Token);

            var request = Encoding.ASCII.GetBytes(
                $"GET / HTTP/1.1\r\nHost: {targetHost}:{port}\r\nConnection: close\r\n\r\n");
            await tls.WriteAsync(request, timeout.Token);
            using var response = new MemoryStream();
            var buffer = new byte[1024];
            while (true)
            {
                var count = await tls.ReadAsync(buffer, timeout.Token);
                if (count == 0) break;
                response.Write(buffer, 0, count);
            }

            var serverAccepted = await serverTask.WaitAsync(TimeSpan.FromSeconds(8));
            return serverAccepted && response.ToArray().AsSpan().StartsWith(
                "HTTP/1.1 200 OK"u8);
        }
        catch (Exception error) when (error is AuthenticationException or IOException or
                                       SocketException or OperationCanceledException or HttpRequestException)
        {
            diagnostics?.Enqueue($"client-exception:{error.GetType().Name}:{error.Message}");
            try { await serverTask.WaitAsync(TimeSpan.FromSeconds(8)); }
            catch (TimeoutException) { }
            return false;
        }
        finally
        {
            timeout.Cancel();
            listener.Stop();
        }
    }

    private static X509Certificate2 CreateRoot(string commonName)
    {
        using var key = RSA.Create(2048);
        var request = new CertificateRequest(
            $"CN={commonName}", key, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(true, false, 0, true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(
            X509KeyUsageFlags.KeyCertSign | X509KeyUsageFlags.CrlSign, true));
        request.CertificateExtensions.Add(new X509SubjectKeyIdentifierExtension(request.PublicKey, false));
        return request.CreateSelfSigned(
            DateTimeOffset.UtcNow.AddDays(-10),
            DateTimeOffset.UtcNow.AddDays(2));
    }

    private static X509Certificate2 CreateLeaf(
        X509Certificate2 issuer,
        string commonName,
        string ekuOid,
        DateTimeOffset notBefore,
        DateTimeOffset notAfter,
        string? dnsName = null)
    {
        using var key = RSA.Create(2048);
        var request = new CertificateRequest(
            $"CN={commonName}", key, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(
            X509KeyUsageFlags.DigitalSignature, true));
        request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(
            new OidCollection { new Oid(ekuOid) }, true));
        request.CertificateExtensions.Add(new X509SubjectKeyIdentifierExtension(request.PublicKey, false));
        if (dnsName is not null)
        {
            var san = new SubjectAlternativeNameBuilder();
            san.AddDnsName(dnsName);
            request.CertificateExtensions.Add(san.Build());
        }

        var serial = RandomNumberGenerator.GetBytes(16);
        serial[0] &= 0x7F;
        if (serial.All(value => value == 0)) serial[^1] = 1;
        using var issued = request.Create(issuer, notBefore, notAfter, serial);
        using var withPrivateKey = issued.CopyWithPrivateKey(key);
        var password = Convert.ToBase64String(RandomNumberGenerator.GetBytes(24));
        var pfx = withPrivateKey.Export(X509ContentType.Pfx, password);
        try
        {
            // SChannel on Windows requires a persisted CNG/CSP key handle for SslStream mTLS.
            return new X509Certificate2(
                pfx,
                password,
                X509KeyStorageFlags.UserKeySet | X509KeyStorageFlags.Exportable);
        }
        finally
        {
            CryptographicOperations.ZeroMemory(pfx);
        }
    }
}
