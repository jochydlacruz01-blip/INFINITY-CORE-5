using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;

namespace Infinity.WindowsCompanion.Security;

public static class CertificatePinning
{
    public const string ServerAuthenticationOid = "1.3.6.1.5.5.7.3.1";
    public const string ClientAuthenticationOid = "1.3.6.1.5.5.7.3.2";

    public static string ComputeSha256Pin(X509Certificate2 certificate) =>
        Convert.ToHexString(SHA256.HashData(certificate.RawData));

    public static string? NormalizeSha256Pin(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;
        var normalized = value.Replace(":", "", StringComparison.Ordinal)
            .Replace(" ", "", StringComparison.Ordinal)
            .ToUpperInvariant();

        return normalized.Length == 64 &&
               normalized.All(character => character is >= '0' and <= '9' or >= 'A' and <= 'F')
            ? normalized
            : null;
    }

    public static bool IsValidSha256Pin(string? value) => NormalizeSha256Pin(value) is not null;

    public static bool HasEnhancedKeyUsage(X509Certificate2 certificate, string requiredOid)
    {
        try
        {
            var extensions = certificate.Extensions.OfType<X509EnhancedKeyUsageExtension>().ToArray();
            return extensions.Length == 1 &&
                   extensions[0].EnhancedKeyUsages.Cast<Oid>().Any(oid => oid.Value == requiredOid);
        }
        catch (CryptographicException)
        {
            return false;
        }
    }

    public static bool AllowsDigitalSignature(X509Certificate2 certificate)
    {
        try
        {
            var extensions = certificate.Extensions.OfType<X509KeyUsageExtension>().ToArray();
            // RFC 5280 permits the Key Usage extension to be absent. If present,
            // require digitalSignature for TLS client authentication.
            return extensions.Length <= 1 &&
                   (extensions.Length == 0 ||
                    extensions[0].KeyUsages.HasFlag(X509KeyUsageFlags.DigitalSignature));
        }
        catch (CryptographicException)
        {
            return false;
        }
    }

    /// <summary>
    /// Performs RFC 5280 path validation to an explicitly trusted root, including the requested
    /// application policy. Revocation is not checked here because this private enrollment CA
    /// does not publish CRLs/OCSP; device revocation is enforced by the live approval/session layer.
    /// </summary>
    public static bool ValidatePkixChain(
        X509Certificate2 leaf,
        X509Certificate2 trustedRoot,
        string requiredEkuOid,
        DateTime? verificationTime = null)
    {
        ArgumentNullException.ThrowIfNull(leaf);
        ArgumentNullException.ThrowIfNull(trustedRoot);
        if (!HasEnhancedKeyUsage(leaf, requiredEkuOid) || !AllowsDigitalSignature(leaf))
            return false;

        using var chain = new X509Chain();
        var policy = chain.ChainPolicy;
        policy.TrustMode = X509ChainTrustMode.CustomRootTrust;
        policy.CustomTrustStore.Add(trustedRoot);
        policy.ExtraStore.Add(trustedRoot);
        policy.RevocationMode = X509RevocationMode.NoCheck;
        policy.RevocationFlag = X509RevocationFlag.ExcludeRoot;
        policy.VerificationFlags = X509VerificationFlags.NoFlag;
        policy.VerificationTime = verificationTime ?? DateTime.UtcNow;
        policy.ApplicationPolicy.Add(new Oid(requiredEkuOid));
        policy.DisableCertificateDownloads = true;

        if (!chain.Build(leaf) || chain.ChainElements.Count < 2)
            return false;

        var actualRoot = chain.ChainElements[^1].Certificate;
        return CryptographicOperations.FixedTimeEquals(
            SHA256.HashData(actualRoot.RawData),
            SHA256.HashData(trustedRoot.RawData));
    }

    public static bool IsUsableServerCertificate(X509Certificate2 certificate)
    {
        var now = DateTime.UtcNow;
        return certificate.HasPrivateKey &&
               now >= certificate.NotBefore.ToUniversalTime() &&
               now <= certificate.NotAfter.ToUniversalTime() &&
               HasEnhancedKeyUsage(certificate, ServerAuthenticationOid) &&
               AllowsDigitalSignature(certificate);
    }

    public static bool MatchesSha256Pin(X509Certificate certificate, string expectedPin)
    {
        var normalizedExpected = NormalizeSha256Pin(expectedPin);
        if (normalizedExpected is null) return false;

        // Always work on an owned copy. TLS callbacks may provide a short-lived certificate
        // handle, and disposing a caller-owned X509Certificate2 would invalidate it.
        using var certificate2 = new X509Certificate2(certificate);
        var now = DateTime.UtcNow;
        if (now < certificate2.NotBefore.ToUniversalTime() ||
            now > certificate2.NotAfter.ToUniversalTime() ||
            !HasEnhancedKeyUsage(certificate2, ClientAuthenticationOid) ||
            !AllowsDigitalSignature(certificate2))
        {
            return false;
        }

        var actual = ComputeSha256Pin(certificate2);
        return CryptographicOperations.FixedTimeEquals(
            Encoding.ASCII.GetBytes(normalizedExpected),
            Encoding.ASCII.GetBytes(actual));
    }
}
