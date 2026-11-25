using System.Net;
using System.Net.Http;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using Infinity.WindowsCompanion.Security;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Server.Kestrel.Https;
using Microsoft.Extensions.Logging;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

/// <summary>
/// Real loopback TLS handshakes. The server validates client PKIX + its explicit device allowlist;
/// the client validates server PKIX + hostname policy + the exact leaf pin.
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

        Assert.True(validClient.HasPrivateKey);
        Assert.True(serverCertificate.HasPrivateKey);
        Assert.True(CertificatePinning.ValidatePkixChain(
            validClient, root, CertificatePinning.ClientAuthenticationOid),
            "The generated client certificate must pass Windows PKIX validation.");
        Assert.True(CertificatePinning.ValidatePkixChain(
            serverCertificate, root, CertificatePinning.ServerAuthenticationOid),
            "The generated server certificate must pass PKIX validation.");

        var approvedPins = new HashSet<string>(StringComparer.Ordinal)
        {
            CertificatePinning.ComputeSha256Pin(validClient)
        };
        var (app, port) = CreateMtlsServer(serverCertificate, root, approvedPins);
        await app.StartAsync();
        try
        {
            var endpoint = $"https://localhost:{port}/";
            using (var valid = CreateClient(validClient, root, serverCertificate))
            {
                using var response = await valid.GetAsync(endpoint);
                Assert.Equal(HttpStatusCode.OK, response.StatusCode);
                Assert.Equal("mTLS-ok", await response.Content.ReadAsStringAsync());
            }

            using (var wrongPin = CreateClient(
                validClient, root, serverCertificate, expectedServerPin: new string('0', 64)))
                await Assert.ThrowsAnyAsync<HttpRequestException>(() => wrongPin.GetAsync(endpoint));

            using (var wrongHostname = CreateClient(validClient, root, serverCertificate))
                await Assert.ThrowsAnyAsync<HttpRequestException>(() =>
                    wrongHostname.GetAsync($"https://127.0.0.1:{port}/"));

            using (var expired = CreateClient(expiredClient, root, serverCertificate))
                await Assert.ThrowsAnyAsync<HttpRequestException>(() => expired.GetAsync(endpoint));

            using (var unauthorized = CreateClient(unauthorizedClient, root, serverCertificate))
                await Assert.ThrowsAnyAsync<HttpRequestException>(() => unauthorized.GetAsync(endpoint));

            using (var invalidChain = CreateClient(untrustedClient, root, serverCertificate))
                await Assert.ThrowsAnyAsync<HttpRequestException>(() => invalidChain.GetAsync(endpoint));
        }
        finally
        {
            await app.StopAsync();
            await app.DisposeAsync();
        }

        using var expiredServer = CreateLeaf(
            root, "localhost", CertificatePinning.ServerAuthenticationOid,
            DateTimeOffset.UtcNow.AddHours(-2), DateTimeOffset.UtcNow.AddHours(-1), "localhost");
        var (expiredServerApp, expiredPort) = CreateMtlsServer(expiredServer, root, approvedPins);
        await expiredServerApp.StartAsync();
        try
        {
            using var client = CreateClient(validClient, root, expiredServer);
            await Assert.ThrowsAnyAsync<HttpRequestException>(() =>
                client.GetAsync($"https://localhost:{expiredPort}/"));
        }
        finally
        {
            await expiredServerApp.StopAsync();
            await expiredServerApp.DisposeAsync();
        }
    }

    private static (WebApplication App, int Port) CreateMtlsServer(
        X509Certificate2 serverCertificate,
        X509Certificate2 trustedClientRoot,
        IReadOnlySet<string> approvedClientPins)
    {
        var port = ReservePort();
        var builder = WebApplication.CreateBuilder();
        builder.Logging.ClearProviders();
        builder.WebHost.ConfigureKestrel(options =>
        {
            options.Listen(IPAddress.Loopback, port, listen => listen.UseHttps(https =>
            {
                https.ServerCertificate = serverCertificate;
                https.SslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13;
                https.ClientCertificateMode = ClientCertificateMode.RequireCertificate;
                https.ClientCertificateValidation = (certificate, _, _) =>
                {
                    if (certificate is null) return false;
                    using var clientCertificate = new X509Certificate2(certificate);
                    var pin = CertificatePinning.ComputeSha256Pin(clientCertificate);
                    return CertificatePinning.ValidatePkixChain(
                               clientCertificate,
                               trustedClientRoot,
                               CertificatePinning.ClientAuthenticationOid) &&
                           approvedClientPins.Contains(pin);
                };
            }));
        });
        var app = builder.Build();
        app.MapGet("/", () => Results.Text("mTLS-ok"));
        return (app, port);
    }

    private static HttpClient CreateClient(
        X509Certificate2 clientCertificate,
        X509Certificate2 trustedServerRoot,
        X509Certificate2 expectedServerCertificate,
        string? expectedServerPin = null)
    {
        var pin = expectedServerPin ?? CertificatePinning.ComputeSha256Pin(expectedServerCertificate);
        var handler = new HttpClientHandler();
        handler.ClientCertificates.Add(clientCertificate);
        handler.ServerCertificateCustomValidationCallback = (_, certificate, _, errors) =>
        {
            if (certificate is null ||
                (errors & (SslPolicyErrors.RemoteCertificateNameMismatch |
                           SslPolicyErrors.RemoteCertificateNotAvailable)) != 0)
                return false;

            using var serverLeaf = new X509Certificate2(certificate);
            var actualPin = CertificatePinning.ComputeSha256Pin(serverLeaf);
            var expected = CertificatePinning.NormalizeSha256Pin(pin);
            return expected is not null &&
                   CertificatePinning.ValidatePkixChain(
                       serverLeaf, trustedServerRoot, CertificatePinning.ServerAuthenticationOid) &&
                   CryptographicOperations.FixedTimeEquals(
                       Convert.FromHexString(actualPin), Convert.FromHexString(expected));
        };
        return new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(8) };
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
        return issued.CopyWithPrivateKey(key);
    }

    private static int ReservePort()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        listener.Stop();
        return port;
    }
}
