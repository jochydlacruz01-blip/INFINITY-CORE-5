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
        var extension = certificate.Extensions.OfType<X509EnhancedKeyUsageExtension>().SingleOrDefault();
        return extension is not null &&
               extension.EnhancedKeyUsages.Cast<Oid>().Any(oid => oid.Value == requiredOid);
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
            !HasEnhancedKeyUsage(certificate2, ClientAuthenticationOid))
        {
            return false;
        }

        var actual = ComputeSha256Pin(certificate2);
        return CryptographicOperations.FixedTimeEquals(
            Encoding.ASCII.GetBytes(normalizedExpected),
            Encoding.ASCII.GetBytes(actual));
    }
}
