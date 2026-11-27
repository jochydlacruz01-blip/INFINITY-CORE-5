using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;

namespace Infinity.WindowsCompanion.Security;

/// <summary>
/// Builds the deterministic Enrollment Proof v1 signature transcript.
/// The caller must pass the challenge transcript only after successful challenge verification.
/// </summary>
public static class EnrollmentProofV1
{
    private const int MaximumCsrBytes = 16 * 1024;
    private static readonly byte[] DomainPrefix =
        Encoding.ASCII.GetBytes("INFINITY-CONNECT-ENROLLMENT-PROOF-V1\0");

    public static byte[] BuildSignatureInput(
        ReadOnlySpan<byte> verifiedChallengeSignatureInput,
        Guid androidDeviceId,
        ReadOnlySpan<byte> csrDer)
    {
        if (verifiedChallengeSignatureInput.IsEmpty)
            throw new CryptographicException("Verified challenge transcript is required.");
        if (androidDeviceId == Guid.Empty)
            throw new CryptographicException("Android device ID must not be empty.");
        if (csrDer.IsEmpty || csrDer.Length > MaximumCsrBytes)
            throw new CryptographicException("CSR is empty or exceeds 16 KiB.");

        var csrDigest = SHA256.HashData(csrDer);
        var result = new byte[DomainPrefix.Length + verifiedChallengeSignatureInput.Length + 16 + csrDigest.Length];
        var offset = 0;
        DomainPrefix.CopyTo(result, offset);
        offset += DomainPrefix.Length;
        verifiedChallengeSignatureInput.CopyTo(result.AsSpan(offset));
        offset += verifiedChallengeSignatureInput.Length;
        if (!androidDeviceId.TryWriteBytes(result.AsSpan(offset, 16), bigEndian: true, out var written) || written != 16)
            throw new CryptographicException("Could not encode Android device UUID.");
        offset += 16;
        csrDigest.CopyTo(result.AsSpan(offset));
        CryptographicOperations.ZeroMemory(csrDigest);
        return result;
    }
}
