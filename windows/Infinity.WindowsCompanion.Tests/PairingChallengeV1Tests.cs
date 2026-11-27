using System.Security.Cryptography;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class PairingChallengeV1Tests
{
    private static readonly byte[] CaDer = "INFINITY test CA certificate"u8.ToArray();

    [Fact]
    public void CreatesAndVerifiesSignedP384Challenge()
    {
        using var ca = ECDsa.Create(ECCurve.NamedCurves.nistP384);
        var companionId = Guid.NewGuid();
        var challenge = PairingChallengeV1.Create(ca, companionId, CaDer, 1_000_000);

        Assert.Equal(1, challenge.ProtocolVersion);
        Assert.Equal(64, challenge.CaSha256.Length);
        Assert.DoesNotContain("=", challenge.Nonce);
        Assert.True(PairingChallengeV1.Verify(
            challenge, ca, CaDer, companionId, 1_001_000));
        Assert.InRange(PairingChallengeV1.Serialize(challenge).Length, 1, PairingChallengeV1.MaximumPayloadBytes);
    }

    [Fact]
    public void RejectsAlteredSignedFieldsAndWrongCompanion()
    {
        using var ca = ECDsa.Create(ECCurve.NamedCurves.nistP384);
        var companionId = Guid.NewGuid();
        var challenge = PairingChallengeV1.Create(ca, companionId, CaDer, 2_000_000);

        Assert.False(PairingChallengeV1.Verify(challenge with { ExpiresAtUnixMs = challenge.ExpiresAtUnixMs - 1 },
            ca, CaDer, companionId, 2_001_000));
        Assert.False(PairingChallengeV1.Verify(challenge with { Nonce = PairingChallengeV1.Create(
            ca, companionId, CaDer, 2_000_000).Nonce }, ca, CaDer, companionId, 2_001_000));
        Assert.False(PairingChallengeV1.Verify(challenge, ca, CaDer, Guid.NewGuid(), 2_001_000));
    }

    [Fact]
    public void RejectsWrongCaPinAndExpiredOrFutureChallenge()
    {
        using var ca = ECDsa.Create(ECCurve.NamedCurves.nistP384);
        var companionId = Guid.NewGuid();
        var challenge = PairingChallengeV1.Create(ca, companionId, CaDer, 3_000_000);

        Assert.False(PairingChallengeV1.Verify(challenge, ca, "different CA"u8, companionId, 3_001_000));
        Assert.False(PairingChallengeV1.Verify(challenge, ca, CaDer, companionId, challenge.ExpiresAtUnixMs));
        Assert.False(PairingChallengeV1.Verify(challenge, ca, CaDer, companionId, 2_000_000));
    }

    [Fact]
    public void RejectsMalformedNonceSignatureFingerprintAndVersion()
    {
        using var ca = ECDsa.Create(ECCurve.NamedCurves.nistP384);
        var companionId = Guid.NewGuid();
        var challenge = PairingChallengeV1.Create(ca, companionId, CaDer, 4_000_000);

        Assert.False(PairingChallengeV1.Verify(challenge with { Nonce = "bad" }, ca, CaDer, companionId, 4_001_000));
        Assert.False(PairingChallengeV1.Verify(challenge with { Signature = "bad" }, ca, CaDer, companionId, 4_001_000));
        Assert.False(PairingChallengeV1.Verify(challenge with { Signature = new string('A', 100_000) },
            ca, CaDer, companionId, 4_001_000));
        Assert.False(PairingChallengeV1.Verify(challenge with { CaSha256 = challenge.CaSha256.ToLowerInvariant() },
            ca, CaDer, companionId, 4_001_000));
        Assert.False(PairingChallengeV1.Verify(challenge with { ProtocolVersion = 2 },
            ca, CaDer, companionId, 4_001_000));
    }

    [Fact]
    public void EnforcesP384SignerAndMaximumLifetime()
    {
        using var p256 = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        using var p384 = ECDsa.Create(ECCurve.NamedCurves.nistP384);
        var companionId = Guid.NewGuid();

        Assert.Throws<CryptographicException>(() =>
            PairingChallengeV1.Create(p256, companionId, CaDer, 5_000_000));
        Assert.Throws<ArgumentOutOfRangeException>(() =>
            PairingChallengeV1.Create(p384, companionId, CaDer, 5_000_000,
                TimeSpan.FromMilliseconds(PairingChallengeV1.MaximumLifetimeMilliseconds + 1)));
        Assert.Throws<ArgumentOutOfRangeException>(() =>
            PairingChallengeV1.Create(p384, companionId, CaDer, long.MaxValue));
        Assert.Throws<ArgumentOutOfRangeException>(() =>
            PairingChallengeV1.Create(p384, companionId, CaDer, long.MaxValue - 100));
    }
    [Fact]
    public void SignatureInputMatchesSharedV1CrossPlatformVector()
    {
        var challenge = new PairingChallengeV1.Challenge(
            1,
            "abcdefab-cdef-abcd-efab-cdefabcdefab",
            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            1_700_000_000_123,
            1_700_000_300_123,
            Convert.ToBase64String(Enumerable.Range(0, 32).Select(value => (byte)value).ToArray())
                .TrimEnd('=').Replace('+', '-').Replace('/', '_'),
            Convert.ToHexString(Enumerable.Range(0, 32).Select(value => (byte)(0xA0 + value)).ToArray()),
            Convert.ToBase64String(Enumerable.Repeat((byte)1, 70).ToArray())
                .TrimEnd('=').Replace('+', '-').Replace('/', '_'));
        var nonce = Enumerable.Range(0, 32).Select(value => (byte)value).ToArray();
        var caHash = Enumerable.Range(0, 32).Select(value => (byte)(0xA0 + value)).ToArray();
        var actual = Convert.ToHexString(PairingChallengeV1.GetSignatureInput(challenge, nonce, caHash))
            .ToLowerInvariant();
        Assert.Equal("494e46494e4954592d434f4e4e4543542d50414952494e472d4348414c4c454e47452d5631000001abcdefabcdefabcdefabcdefabcdefabaaaaaaaabbbbccccddddeeeeeeeeeeee0000018bcfe5687b0000018bcfe9fc5b000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf", actual);
    }

}
