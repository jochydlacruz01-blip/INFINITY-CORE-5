using Xunit;
using Infinity.WindowsCompanion.Security;

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
}
