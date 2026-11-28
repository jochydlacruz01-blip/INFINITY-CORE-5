using System.Security.Cryptography;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class EnrollmentProofV1Tests
{
    private static readonly byte[] ChallengeInput = Convert.FromHexString(
        "494e46494e4954592d434f4e4e4543542d50414952494e472d4348414c4c454e47452d5631000001abcdefabcdefabcdefabcdefabcdefabaaaaaaaabbbbccccddddeeeeeeeeeeee0000018bcfe5687b0000018bcfe9fc5b000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf");

    [Fact]
    public void MatchesSharedEnrollmentProofV1Vector()
    {
        var actual = EnrollmentProofV1.BuildSignatureInput(
            ChallengeInput,
            Guid.Parse("11111111-2222-3333-4444-555555555555"),
            Enumerable.Range(0, 32).Select(value => (byte)value).ToArray());
        Assert.Equal(
            "494e46494e4954592d434f4e4e4543542d454e524f4c4c4d454e542d50524f4f462d563100494e46494e4954592d434f4e4e4543542d50414952494e472d4348414c4c454e47452d5631000001abcdefabcdefabcdefabcdefabcdefabaaaaaaaabbbbccccddddeeeeeeeeeeee0000018bcfe5687b0000018bcfe9fc5b000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf11111111222233334444555555555555630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd",
            Convert.ToHexString(actual).ToLowerInvariant());
    }

    [Fact]
    public void BindsAndroidIdentityAndCsrDigest()
    {
        var csr = Enumerable.Range(0, 32).Select(value => (byte)value).ToArray();
        var baseline = EnrollmentProofV1.BuildSignatureInput(ChallengeInput,
            Guid.Parse("11111111-2222-3333-4444-555555555555"), csr);
        var changedDevice = EnrollmentProofV1.BuildSignatureInput(ChallengeInput,
            Guid.Parse("21111111-2222-3333-4444-555555555555"), csr);
        var changedCsr = EnrollmentProofV1.BuildSignatureInput(ChallengeInput,
            Guid.Parse("11111111-2222-3333-4444-555555555555"), csr.Select(value => (byte)(value + 1)).ToArray());
        Assert.NotEqual(baseline, changedDevice);
        Assert.NotEqual(baseline, changedCsr);
    }

    [Fact]
    public void RejectsMalformedChallengeTranscriptAndInvalidEnrollmentInputs()
    {
        Assert.Throws<CryptographicException>(() =>
            EnrollmentProofV1.BuildSignatureInput([], Guid.NewGuid(), [1]));
        Assert.Throws<CryptographicException>(() =>
            EnrollmentProofV1.BuildSignatureInput(new byte[151], Guid.NewGuid(), [1]));
        Assert.Throws<CryptographicException>(() =>
            EnrollmentProofV1.BuildSignatureInput(new byte[153], Guid.NewGuid(), [1]));
        Assert.Throws<CryptographicException>(() =>
            EnrollmentProofV1.BuildSignatureInput(ChallengeInput, Guid.Empty, [1]));
        Assert.Throws<CryptographicException>(() =>
            EnrollmentProofV1.BuildSignatureInput(ChallengeInput, Guid.NewGuid(), new byte[16 * 1024 + 1]));
    }
}
