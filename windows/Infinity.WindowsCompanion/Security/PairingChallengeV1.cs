using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Infinity.WindowsCompanion.Security;

/// <summary>
/// Signed, offline Windows pairing challenge contract v1. This type performs no I/O
/// and deliberately exposes no listener or enrollment endpoint.
/// </summary>
public static class PairingChallengeV1
{
    public const ushort ProtocolVersion = 1;
    public const long MaximumLifetimeMilliseconds = 5 * 60 * 1000;
    public const int MaximumPayloadBytes = 4096;
    private const string Domain = "INFINITY-CONNECT-PAIRING-CHALLENGE-V1";
    private static readonly byte[] DomainPrefix = Encoding.ASCII.GetBytes(Domain + "\0");
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow
    };

    public sealed record Challenge(
        [property: JsonPropertyName("protocolVersion")] int ProtocolVersion,
        [property: JsonPropertyName("requestId")] string RequestId,
        [property: JsonPropertyName("companionDeviceId")] string CompanionDeviceId,
        [property: JsonPropertyName("issuedAtUnixMs")] long IssuedAtUnixMs,
        [property: JsonPropertyName("expiresAtUnixMs")] long ExpiresAtUnixMs,
        [property: JsonPropertyName("nonce")] string Nonce,
        [property: JsonPropertyName("caSha256")] string CaSha256,
        [property: JsonPropertyName("signature")] string Signature);

    public static Challenge Create(
        ECDsa caSigningKey,
        Guid companionDeviceId,
        ReadOnlySpan<byte> caCertificateDer,
        long issuedAtUnixMs,
        TimeSpan? lifetime = null)
    {
        ArgumentNullException.ThrowIfNull(caSigningKey);
        if (caSigningKey.KeySize != 384)
            throw new CryptographicException("Pairing challenge signing requires ECDSA P-384.");
        if (companionDeviceId == Guid.Empty)
            throw new ArgumentException("Companion device ID must be a non-empty UUID.", nameof(companionDeviceId));
        if (caCertificateDer.IsEmpty)
            throw new ArgumentException("CA certificate DER is required.", nameof(caCertificateDer));
        if (issuedAtUnixMs < 0)
            throw new ArgumentOutOfRangeException(nameof(issuedAtUnixMs));

        var duration = lifetime ?? TimeSpan.FromMilliseconds(MaximumLifetimeMilliseconds);
        if (duration <= TimeSpan.Zero || duration.TotalMilliseconds > MaximumLifetimeMilliseconds ||
            duration.TotalMilliseconds != Math.Truncate(duration.TotalMilliseconds))
            throw new ArgumentOutOfRangeException(nameof(lifetime), "Challenge lifetime must be 1 ms to 5 minutes.");
        var durationMs = checked((long)duration.TotalMilliseconds);
        if (issuedAtUnixMs > long.MaxValue - durationMs)
            throw new ArgumentOutOfRangeException(nameof(issuedAtUnixMs), "Challenge expiry exceeds the supported timestamp range.");
        var expiresAt = issuedAtUnixMs + durationMs;
        var requestId = Guid.NewGuid();
        var nonce = RandomNumberGenerator.GetBytes(32);
        var caHash = SHA256.HashData(caCertificateDer);
        var caPin = Convert.ToHexString(caHash);
        var unsigned = new Challenge(
            ProtocolVersion,
            requestId.ToString("D"),
            companionDeviceId.ToString("D"),
            issuedAtUnixMs,
            expiresAt,
            EncodeBase64Url(nonce),
            caPin,
            string.Empty);
        var signature = caSigningKey.SignData(
            GetSignatureInput(unsigned, nonce, caHash),
            HashAlgorithmName.SHA384,
            DSASignatureFormat.Rfc3279DerSequence);
        return unsigned with { Signature = EncodeBase64Url(signature) };
    }

    public static bool Verify(
        Challenge challenge,
        ECDsa caPublicKey,
        ReadOnlySpan<byte> expectedCaCertificateDer,
        Guid expectedCompanionDeviceId,
        long nowUnixMs,
        long allowedFutureClockSkewMilliseconds = 30_000)
    {
        ArgumentNullException.ThrowIfNull(challenge);
        ArgumentNullException.ThrowIfNull(caPublicKey);
        if (caPublicKey.KeySize != 384 || expectedCaCertificateDer.IsEmpty ||
            expectedCompanionDeviceId == Guid.Empty || nowUnixMs < 0 ||
            allowedFutureClockSkewMilliseconds < 0 ||
            challenge.ProtocolVersion != ProtocolVersion ||
            !TryCanonicalGuid(challenge.RequestId, out var requestId) || requestId == Guid.Empty ||
            !TryCanonicalGuid(challenge.CompanionDeviceId, out var companionId) ||
            companionId != expectedCompanionDeviceId ||
            challenge.IssuedAtUnixMs < 0 || challenge.ExpiresAtUnixMs <= challenge.IssuedAtUnixMs ||
            challenge.ExpiresAtUnixMs - challenge.IssuedAtUnixMs > MaximumLifetimeMilliseconds ||
            (challenge.IssuedAtUnixMs > nowUnixMs &&
                challenge.IssuedAtUnixMs - nowUnixMs > allowedFutureClockSkewMilliseconds) ||
            nowUnixMs >= challenge.ExpiresAtUnixMs)
            return false;

        if (!TryDecodeBase64Url(challenge.Nonce, 32, 32, out var nonce) ||
            !TryDecodeBase64Url(challenge.Signature, 8, 104, out var signature) ||
            !TryDecodeFingerprint(challenge.CaSha256, out var suppliedCaHash))
            return false;

        var actualCaHash = SHA256.HashData(expectedCaCertificateDer);
        try
        {
            if (!CryptographicOperations.FixedTimeEquals(suppliedCaHash, actualCaHash))
                return false;
            return caPublicKey.VerifyData(
                GetSignatureInput(challenge, nonce, actualCaHash),
                signature,
                HashAlgorithmName.SHA384,
                DSASignatureFormat.Rfc3279DerSequence);
        }
        catch (CryptographicException)
        {
            return false;
        }
        finally
        {
            CryptographicOperations.ZeroMemory(nonce);
            CryptographicOperations.ZeroMemory(signature);
            CryptographicOperations.ZeroMemory(suppliedCaHash);
            CryptographicOperations.ZeroMemory(actualCaHash);
        }
    }

    public static byte[] Serialize(Challenge challenge)
    {
        ArgumentNullException.ThrowIfNull(challenge);
        var payload = JsonSerializer.SerializeToUtf8Bytes(challenge, JsonOptions);
        if (payload.Length > MaximumPayloadBytes)
            throw new CryptographicException("Pairing challenge payload exceeds 4 KiB.");
        return payload;
    }

    private static byte[] GetSignatureInput(Challenge challenge, ReadOnlySpan<byte> nonce, ReadOnlySpan<byte> caHash)
    {
        if (!TryCanonicalGuid(challenge.RequestId, out var requestId) ||
            !TryCanonicalGuid(challenge.CompanionDeviceId, out var companionId) ||
            nonce.Length != 32 || caHash.Length != 32)
            throw new CryptographicException("Malformed pairing challenge fields.");

        var result = new byte[DomainPrefix.Length + 2 + 16 + 16 + 8 + 8 + 32 + 32];
        var offset = 0;
        DomainPrefix.CopyTo(result, offset); offset += DomainPrefix.Length;
        BinaryPrimitives.WriteUInt16BigEndian(result.AsSpan(offset, 2), checked((ushort)challenge.ProtocolVersion)); offset += 2;
        if (!requestId.TryWriteBytes(result.AsSpan(offset, 16), bigEndian: true, out var requestWritten) || requestWritten != 16)
            throw new CryptographicException("Could not encode request UUID.");
        offset += 16;
        if (!companionId.TryWriteBytes(result.AsSpan(offset, 16), bigEndian: true, out var companionWritten) || companionWritten != 16)
            throw new CryptographicException("Could not encode Companion UUID.");
        offset += 16;
        BinaryPrimitives.WriteInt64BigEndian(result.AsSpan(offset, 8), challenge.IssuedAtUnixMs); offset += 8;
        BinaryPrimitives.WriteInt64BigEndian(result.AsSpan(offset, 8), challenge.ExpiresAtUnixMs); offset += 8;
        nonce.CopyTo(result.AsSpan(offset, 32)); offset += 32;
        caHash.CopyTo(result.AsSpan(offset, 32));
        return result;
    }

    private static bool TryCanonicalGuid(string value, out Guid result) =>
        Guid.TryParseExact(value, "D", out result) &&
        string.Equals(value, result.ToString("D"), StringComparison.Ordinal);

    private static bool TryDecodeFingerprint(string value, out byte[] bytes)
    {
        bytes = Array.Empty<byte>();
        if (value.Length != 64 || value.Any(character =>
            !(character is >= '0' and <= '9' or >= 'A' and <= 'F')))
            return false;
        try { bytes = Convert.FromHexString(value); return bytes.Length == 32; }
        catch (FormatException) { return false; }
    }

    private static string EncodeBase64Url(ReadOnlySpan<byte> bytes) =>
        Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');

    private static bool TryDecodeBase64Url(string value, int minimumLength, int maximumLength, out byte[] bytes)
    {
        bytes = Array.Empty<byte>();
        var maximumEncodedLength = (maximumLength * 4 + 2) / 3;
        if (string.IsNullOrEmpty(value) || value.Length > maximumEncodedLength || value.Contains('=') ||
            value.Any(character => !(character is >= 'A' and <= 'Z' or >= 'a' and <= 'z' or
                >= '0' and <= '9' or '-' or '_')))
            return false;
        try
        {
            var normalized = value.Replace('-', '+').Replace('_', '/');
            normalized += new string('=', (4 - normalized.Length % 4) % 4);
            bytes = Convert.FromBase64String(normalized);
            if (bytes.Length >= minimumLength && bytes.Length <= maximumLength && EncodeBase64Url(bytes) == value) return true;
            CryptographicOperations.ZeroMemory(bytes);
            bytes = Array.Empty<byte>();
            return false;
        }
        catch (FormatException) { return false; }
    }
}
