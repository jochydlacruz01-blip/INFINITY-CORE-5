using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class AuthorizedSessionStoreTests
{
    private const string DevicePin = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private const string OtherDevicePin = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    [Fact]
    public void SessionIsBoundToCertificateAndCapabilityAndCanBeRevoked()
    {
        var store = new AuthorizedSessionStore();
        var grant = store.Create(DevicePin, true, new[] { "identity.read" });

        Assert.True(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "identity.read"));
        Assert.False(store.IsAuthorized(grant.SessionId, grant.Token, OtherDevicePin, "identity.read"));
        Assert.False(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "remote.display"));
        Assert.True(store.Revoke(grant.SessionId, DevicePin));
        Assert.False(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "identity.read"));
        Assert.Equal(0, store.ActiveCount);
    }

    [Fact]
    public void RequiresExplicitConsentAndRejectsRemoteCapabilities()
    {
        var store = new AuthorizedSessionStore();
        Assert.Throws<UnauthorizedAccessException>(() =>
            store.Create(DevicePin, false, new[] { "identity.read" }));
        Assert.Throws<UnauthorizedAccessException>(() =>
            store.Create(DevicePin, true, new[] { "remote.display" }));
        Assert.Equal(0, store.ActiveCount);
    }

    [Fact]
    public void RevokingDeviceRevokesAllItsSessions()
    {
        var store = new AuthorizedSessionStore();
        var first = store.Create(DevicePin, true, new[] { "identity.read" });
        var second = store.Create(DevicePin, true, new[] { "identity.read" });
        var other = store.Create(OtherDevicePin, true, new[] { "identity.read" });

        Assert.Equal(2, store.RevokeDevice(DevicePin));
        Assert.False(store.IsAuthorized(first.SessionId, first.Token, DevicePin, "identity.read"));
        Assert.False(store.IsAuthorized(second.SessionId, second.Token, DevicePin, "identity.read"));
        Assert.True(store.IsAuthorized(other.SessionId, other.Token, OtherDevicePin, "identity.read"));
    }

    [Fact]
    public void GrantsRemoteCapabilitiesOnlyWhenWindowsPolicyAllowsTheCompletePair()
    {
        var store = new AuthorizedSessionStore();
        var allowed = new HashSet<string>(StringComparer.Ordinal)
        {
            "identity.read", "remote.display", "remote.input", "file.transfer"
        };
        var grant = store.Create(
            DevicePin,
            true,
            new[] { "identity.read", "remote.display", "remote.input" },
            allowed);

        Assert.True(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "remote.display"));
        Assert.True(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "remote.input"));
        Assert.False(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "file.transfer"));
        Assert.Throws<UnauthorizedAccessException>(() =>
            store.Create(DevicePin, true, new[] { "identity.read", "remote.display" }, allowed));
        Assert.Throws<UnauthorizedAccessException>(() =>
            store.Create(DevicePin, true, new[] { "identity.read", "file.transfer" }));
    }

    [Fact]
    public void RejectsMalformedTokensAndSessionIds()
    {
        var store = new AuthorizedSessionStore();
        Assert.False(store.IsAuthorized("not-a-guid", "not-a-token", DevicePin, "identity.read"));
        var grant = store.Create(DevicePin, true, new[] { "identity.read" });
        Assert.False(store.IsAuthorized(grant.SessionId, "!", DevicePin, "identity.read"));
    }
}
