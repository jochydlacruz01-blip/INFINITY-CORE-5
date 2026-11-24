using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class AuthorizedDeviceStoreTests
{
    [Fact]
    public void LocalApprovalPersistsAndRevocationBlocksTheNextAuthorizationCheck()
    {
        if (!OperatingSystem.IsWindows()) return;

        using var certificate = CreateClientCertificate();
        var path = Path.Combine(Path.GetTempPath(), $"infinity-client-{Guid.NewGuid():N}.cer");
        var pin = CertificatePinning.ComputeSha256Pin(certificate);
        try
        {
            File.WriteAllBytes(path, certificate.Export(X509ContentType.Cert));
            var approvedPin = AuthorizedDeviceStore.ApproveFromLocalConsole(path);
            Assert.Equal(pin, approvedPin);

            var store = new AuthorizedDeviceStore();
            Assert.True(store.IsAuthorized(certificate));

            Assert.True(AuthorizedDeviceStore.RevokeFromLocalConsole(pin));
            Assert.False(store.IsAuthorized(certificate));
            Assert.False(AuthorizedDeviceStore.RevokeFromLocalConsole(pin));
        }
        finally
        {
            AuthorizedDeviceStore.RevokeFromLocalConsole(pin);
            File.Delete(path);
        }
    }

    [Fact]
    public void ApprovalRejectsCertificateWithServerOnlyEku()
    {
        if (!OperatingSystem.IsWindows()) return;

        using var certificate = CreateCertificate(CertificatePinning.ServerAuthenticationOid);
        var path = Path.Combine(Path.GetTempPath(), $"infinity-server-{Guid.NewGuid():N}.cer");
        try
        {
            File.WriteAllBytes(path, certificate.Export(X509ContentType.Cert));
            Assert.Throws<InvalidOperationException>(() =>
                AuthorizedDeviceStore.ApproveFromLocalConsole(path));
        }
        finally
        {
            File.Delete(path);
        }
    }

    private static X509Certificate2 CreateClientCertificate() =>
        CreateCertificate(CertificatePinning.ClientAuthenticationOid);

    private static X509Certificate2 CreateCertificate(string ekuOid)
    {
        using var key = RSA.Create(2048);
        var request = new CertificateRequest(
            "CN=INFINITY authorized-device test",
            key,
            HashAlgorithmName.SHA256,
            RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(
            new OidCollection { new Oid(ekuOid) }, critical: true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(
            X509KeyUsageFlags.DigitalSignature, critical: true));
        return request.CreateSelfSigned(
            DateTimeOffset.UtcNow.AddMinutes(-1),
            DateTimeOffset.UtcNow.AddHours(1));
    }
}
