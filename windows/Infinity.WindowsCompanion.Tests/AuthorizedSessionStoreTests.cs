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

    [Fact]
    public void ExpiredSessionIsRemovedAndReturnedForOperationRevocation()
    {
        var now = new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero);
        var store = new AuthorizedSessionStore(() => now);
        var grant = store.Create(DevicePin, true, new[] { "identity.read" });

        Assert.True(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "identity.read"));
        now = now.AddMinutes(16);

        Assert.False(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "identity.read"));
        var expired = Assert.Single(store.DrainExpiredSessions());
        Assert.Equal(grant.SessionId, expired.SessionId);
        Assert.Equal(DevicePin, expired.ClientFingerprint);
        Assert.Empty(store.DrainExpiredSessions());
        Assert.Equal(0, store.ActiveCount);
    }

    [Fact]
    public void HeartbeatExtendsOnlyAnUnexpiredSessionBoundToTheSameCertificateAndToken()
    {
        var now = new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero);
        var store = new AuthorizedSessionStore(() => now);
        var grant = store.Create(DevicePin, true, new[] { "identity.read" });

        Assert.Null(store.Heartbeat(grant.SessionId, grant.Token, OtherDevicePin, 1, true));
        Assert.Null(store.Heartbeat(grant.SessionId, "invalid", DevicePin, 1, true));
        Assert.True(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "identity.read"));

        // Heartbeats require recent user activity; idle sessions cannot be kept alive.
        now = now.AddMinutes(1);
        var renewed = store.Heartbeat(grant.SessionId, grant.Token, DevicePin, 1, true);
        Assert.Equal(now.AddMinutes(15), renewed);

        now = now.AddMinutes(1);
        Assert.True(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "identity.read"));
        Assert.NotNull(store.Heartbeat(grant.SessionId, grant.Token, DevicePin, 2, true));

        now = now.AddMinutes(16);
        Assert.Null(store.Heartbeat(grant.SessionId, grant.Token, DevicePin, 3, true));
        Assert.False(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "identity.read"));
        Assert.Single(store.DrainExpiredSessions());
    }


    [Fact]
    public void HeartbeatRequiresRecentActivityAndCannotExtendBeyondAbsoluteLifetime()
    {
        var now = new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero);
        var store = new AuthorizedSessionStore(() => now);
        var grant = store.Create(DevicePin, true, new[] { "identity.read" });

        // A heartbeat without user activity is rejected and does not consume its sequence.
        Assert.Null(store.Heartbeat(grant.SessionId, grant.Token, DevicePin, 1, false));
        Assert.NotNull(store.Heartbeat(grant.SessionId, grant.Token, DevicePin, 1, true));
        now = now.AddMinutes(1);
        Assert.NotNull(store.Heartbeat(grant.SessionId, grant.Token, DevicePin, 2, true));

        now = now.AddHours(8);
        Assert.Null(store.Heartbeat(grant.SessionId, grant.Token, DevicePin, 2, true));
        Assert.False(store.IsAuthorized(grant.SessionId, grant.Token, DevicePin, "identity.read"));
    }

    [Fact]
    public async Task DeviceRevocationInvalidatesAllOldTokensAfterConcurrentAuthorizationChecks()
    {
        var store = new AuthorizedSessionStore();
        var grants = Enumerable.Range(0, 4)
            .Select(index => store.Create(DevicePin, true, new[] { "identity.read" }))
            .ToArray();
        using var revokeCompleted = new ManualResetEventSlim(false);

        var readers = Enumerable.Range(0, 4).Select(workerIndex => Task.Run(() =>
        {
            while (!revokeCompleted.IsSet)
            {
                foreach (var grant in grants)
                    _ = store.IsAuthorized(
                        grant.SessionId, grant.Token, DevicePin, "identity.read");
            }

            // Every check begun after RevokeDevice has returned must reject old tokens.
            foreach (var grant in grants)
                Assert.False(store.IsAuthorized(
                    grant.SessionId, grant.Token, DevicePin, "identity.read"));
        })).ToArray();

        Assert.Equal(grants.Length, store.RevokeDevice(DevicePin));
        revokeCompleted.Set();
        await Task.WhenAll(readers);

        Assert.Equal(0, store.ActiveCount);
        Assert.All(grants, grant => Assert.False(store.IsAuthorized(
            grant.SessionId, grant.Token, DevicePin, "identity.read")));
    }

}
