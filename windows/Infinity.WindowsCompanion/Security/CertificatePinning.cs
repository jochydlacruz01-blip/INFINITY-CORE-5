using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;

namespace Infinity.WindowsCompanion.Security;

public static class CertificatePinning
{
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

    public static bool MatchesSha256Pin(X509Certificate certificate, string expectedPin)
    {
        var normalizedExpected = NormalizeSha256Pin(expectedPin);
        if (normalizedExpected is null) return false;

        using var certificate2 = certificate as X509Certificate2 ?? new X509Certificate2(certificate);
        var actual = ComputeSha256Pin(certificate2);
        return CryptographicOperations.FixedTimeEquals(
            Encoding.ASCII.GetBytes(normalizedExpected),
            Encoding.ASCII.GetBytes(actual));
    }
}
