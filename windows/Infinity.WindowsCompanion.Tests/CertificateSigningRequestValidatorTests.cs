using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class CertificateSigningRequestValidatorTests
{
    [Fact]
    public void AcceptsValidP256Pkcs10AndVerifiesProofOfPossession()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var request = new CertificateRequest(
            "CN=infinity-device-01",
            key,
            HashAlgorithmName.SHA256);
        var encoded = request.CreateSigningRequest();

        using var validated = CertificateSigningRequestValidator.Validate(encoded);

        Assert.Equal("CN=infinity-device-01", validated.Subject.Name);
        Assert.Equal(256, validated.PublicKey.KeySize);
    }

    [Fact]
    public void RejectsModifiedRequestWithInvalidProofOfPossession()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var request = new CertificateRequest("CN=infinity-device-02", key, HashAlgorithmName.SHA256);
        var encoded = request.CreateSigningRequest();
        encoded[^1] ^= 0x01;

        Assert.Throws<CryptographicException>(() =>
            CertificateSigningRequestValidator.Validate(encoded));
    }

    [Fact]
    public void RejectsRsaRequest()
    {
        using var key = RSA.Create(2048);
        var request = new CertificateRequest(
            "CN=infinity-device-03",
            key,
            HashAlgorithmName.SHA256,
            RSASignaturePadding.Pkcs1);

        Assert.Throws<CryptographicException>(() =>
            CertificateSigningRequestValidator.Validate(request.CreateSigningRequest()));
    }

    [Fact]
    public void RejectsEmptyAndOversizedRequests()
    {
        Assert.Throws<CryptographicException>(() =>
            CertificateSigningRequestValidator.Validate(ReadOnlyMemory<byte>.Empty));
        Assert.Throws<CryptographicException>(() =>
            CertificateSigningRequestValidator.Validate(new byte[16 * 1024 + 1]));
    }
}
