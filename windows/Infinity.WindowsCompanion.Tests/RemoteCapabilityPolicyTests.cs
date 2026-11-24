using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class RemoteCapabilityPolicyTests
{
    [Fact]
    public void AllRemoteCapabilitiesAreDisabledByDefault()
    {
        var policy = RemoteCapabilityPolicy.FromValues(null, null, null);
        Assert.False(policy.RemoteDisplayEnabled);
        Assert.False(policy.RemoteInputEnabled);
        Assert.False(policy.FileTransferEnabled);
        Assert.Equal(new[] { "identity.read" }, policy.GetAllowedCapabilities());
    }

    [Fact]
    public void DisplayAndInputMustBeEnabledTogether()
    {
        var partial = RemoteCapabilityPolicy.FromValues("true", "false", null);
        Assert.False(partial.RemoteDisplayEnabled);
        Assert.False(partial.RemoteInputEnabled);

        var enabled = RemoteCapabilityPolicy.FromValues("true", "true", null);
        Assert.True(enabled.RemoteDisplayEnabled);
        Assert.True(enabled.RemoteInputEnabled);
        Assert.Contains("remote.display", enabled.GetAllowedCapabilities());
        Assert.Contains("remote.input", enabled.GetAllowedCapabilities());
    }

    [Fact]
    public void FileTransferIsIndependentAndRequiresExplicitTrue()
    {
        var policy = RemoteCapabilityPolicy.FromValues("TRUE", "true", "true");
        Assert.True(policy.FileTransferEnabled);
        Assert.Contains("file.transfer", policy.GetAllowedCapabilities());

        var invalid = RemoteCapabilityPolicy.FromValues("yes", "true", "1");
        Assert.False(invalid.FileTransferEnabled);
        Assert.False(invalid.RemoteDisplayEnabled);
        Assert.False(invalid.RemoteInputEnabled);
    }
}
