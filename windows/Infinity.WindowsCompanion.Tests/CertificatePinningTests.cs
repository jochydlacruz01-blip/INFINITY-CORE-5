using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class CertificatePinningTests
{
    [Fact]
    public void NormalizesColonSeparatedSha256Fingerprint()
    {
        var expected = new string('A', 64);
        var colonSeparated = string.Join(":", Enumerable.Repeat("AA", 32));
        Assert.Equal(expected, CertificatePinning.NormalizeSha256Pin(colonSeparated));
    }

    [Theory]
    [InlineData("")]
    [InlineData("ABCD")]
    [InlineData("GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG")]
    public void RejectsMalformedFingerprints(string value)
    {
        Assert.False(CertificatePinning.IsValidSha256Pin(value));
    }

    [Fact]
    public void AcceptsOnlyCurrentClientAuthenticationCertificateWithMatchingPin()
    {
        using var certificate = CreateCertificate(CertificatePinning.ClientAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-1), DateTimeOffset.UtcNow.AddHours(1));
        var pin = CertificatePinning.ComputeSha256Pin(certificate);
        Assert.True(CertificatePinning.MatchesSha256Pin(certificate, pin));
        Assert.False(CertificatePinning.MatchesSha256Pin(certificate, new string('0', 64)));
    }

    [Fact]
    public void RejectsServerCertificateWhenPresentedAsClient()
    {
        using var certificate = CreateCertificate(CertificatePinning.ServerAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-1), DateTimeOffset.UtcNow.AddHours(1));
        Assert.False(CertificatePinning.MatchesSha256Pin(
            certificate, CertificatePinning.ComputeSha256Pin(certificate)));
    }

    [Fact]
    public void RejectsClientCertificateWhenKeyUsageForbidsDigitalSignature()
    {
        using var certificate = CreateCertificate(
            CertificatePinning.ClientAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-1),
            DateTimeOffset.UtcNow.AddHours(1),
            includeKeyUsage: true,
            keyUsage: X509KeyUsageFlags.KeyEncipherment);
        Assert.False(CertificatePinning.MatchesSha256Pin(
            certificate, CertificatePinning.ComputeSha256Pin(certificate)));
    }

    [Fact]
    public void AcceptsClientCertificateWhenKeyUsageAllowsDigitalSignature()
    {
        using var certificate = CreateCertificate(
            CertificatePinning.ClientAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-1),
            DateTimeOffset.UtcNow.AddHours(1),
            includeKeyUsage: true,
            keyUsage: X509KeyUsageFlags.DigitalSignature);
        Assert.True(CertificatePinning.MatchesSha256Pin(
            certificate, CertificatePinning.ComputeSha256Pin(certificate)));
    }

    [Fact]
    public void AcceptsCurrentServerCertificateWithDigitalSignatureUsage()
    {
        using var certificate = CreateCertificate(
            CertificatePinning.ServerAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-1),
            DateTimeOffset.UtcNow.AddHours(1),
            includeKeyUsage: true,
            keyUsage: X509KeyUsageFlags.DigitalSignature);
        Assert.True(CertificatePinning.IsUsableServerCertificate(certificate));
    }

    [Fact]
    public void RejectsServerCertificateWhenKeyUsageForbidsDigitalSignature()
    {
        using var certificate = CreateCertificate(
            CertificatePinning.ServerAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-1),
            DateTimeOffset.UtcNow.AddHours(1),
            includeKeyUsage: true,
            keyUsage: X509KeyUsageFlags.KeyEncipherment);
        Assert.False(CertificatePinning.IsUsableServerCertificate(certificate));
    }

    [Fact]
    public void RejectsClientCertificateAsWindowsServerCertificate()
    {
        using var certificate = CreateCertificate(
            CertificatePinning.ClientAuthenticationOid,
            DateTimeOffset.UtcNow.AddMinutes(-1),
            DateTimeOffset.UtcNow.AddHours(1));
        Assert.False(CertificatePinning.IsUsableServerCertificate(certificate));
    }

    [Fact]
    public void RejectsExpiredServerCertificate()
    {
        using var certificate = CreateCertificate(
            CertificatePinning.ServerAuthenticationOid,
            DateTimeOffset.UtcNow.AddHours(-2),
            DateTimeOffset.UtcNow.AddHours(-1));
        Assert.False(CertificatePinning.IsUsableServerCertificate(certificate));
    }

    [Fact]
    public void RejectsExpiredClientCertificate()
    {
        using var certificate = CreateCertificate(CertificatePinning.ClientAuthenticationOid,
            DateTimeOffset.UtcNow.AddHours(-2), DateTimeOffset.UtcNow.AddHours(-1));
        Assert.False(CertificatePinning.MatchesSha256Pin(
            certificate, CertificatePinning.ComputeSha256Pin(certificate)));
    }

    private static X509Certificate2 CreateCertificate(
        string usageOid,
        DateTimeOffset notBefore,
        DateTimeOffset notAfter,
        bool includeKeyUsage = false,
        X509KeyUsageFlags keyUsage = X509KeyUsageFlags.DigitalSignature)
    {
        using var key = RSA.Create(2048);
        var request = new CertificateRequest("CN=INFINITY test certificate", key,
            HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        var usages = new OidCollection { new Oid(usageOid) };
        request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(usages, critical: true));
        if (includeKeyUsage)
            request.CertificateExtensions.Add(new X509KeyUsageExtension(keyUsage, critical: true));
        return request.CreateSelfSigned(notBefore, notAfter);
    }
}
