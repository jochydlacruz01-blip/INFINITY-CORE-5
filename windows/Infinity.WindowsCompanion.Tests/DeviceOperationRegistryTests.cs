using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class DeviceOperationRegistryTests
{
    private const string DevicePin = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private const string OtherDevicePin = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private const string SessionId = "11111111-1111-4111-8111-111111111111";
    private const string OtherSessionId = "22222222-2222-4222-8222-222222222222";

    [Fact]
    public async Task DeviceRevocationCancelsEveryActiveOperationAndIsIdempotent()
    {
        var registry = CreateRegistry();
        using var first = registry.BeginOperation(DevicePin, SessionId);
        using var second = registry.BeginOperation(DevicePin, OtherSessionId);

        Assert.True(registry.RevokeDevice(DevicePin));
        Assert.False(registry.RevokeDevice(DevicePin));
        Assert.True(first.Token.IsCancellationRequested);
        Assert.True(second.Token.IsCancellationRequested);
        Assert.Throws<OperationCanceledException>(() => registry.BeginOperation(DevicePin, SessionId));

        first.Dispose();
        second.Dispose();
        Assert.Equal(0, registry.Snapshot.ActiveOperations);
    }

    [Fact]
    public async Task ConcurrentRepeatedRevocationSignalsAllLeasesWithoutAffectingOtherDevice()
    {
        var registry = CreateRegistry();
        using var deviceOperation = registry.BeginOperation(DevicePin, SessionId);
        using var otherOperation = registry.BeginOperation(OtherDevicePin, SessionId);

        await Task.WhenAll(Enumerable.Range(0, 16).Select(_ => Task.Run(() => registry.RevokeDevice(DevicePin))));

        Assert.True(deviceOperation.Token.IsCancellationRequested);
        Assert.False(otherOperation.Token.IsCancellationRequested);
        Assert.Throws<OperationCanceledException>(() => registry.BeginOperation(DevicePin, SessionId));
        using var unaffected = registry.BeginOperation(OtherDevicePin, SessionId);
        Assert.False(unaffected.Token.IsCancellationRequested);
    }

    [Fact]
    public void SessionRevocationCancelsOnlyThatSessionsOperations()
    {
        var registry = CreateRegistry();
        using var revoked = registry.BeginOperation(DevicePin, SessionId);
        using var retained = registry.BeginOperation(DevicePin, OtherSessionId);

        Assert.True(registry.RevokeSession(DevicePin, SessionId));
        Assert.False(registry.RevokeSession(DevicePin, SessionId));
        Assert.True(revoked.Token.IsCancellationRequested);
        Assert.False(retained.Token.IsCancellationRequested);
        Assert.Throws<OperationCanceledException>(() => registry.BeginOperation(DevicePin, SessionId));
    }

    [Fact]
    public async Task RequestDisconnectCancelsLeaseAndReleasesOperationCount()
    {
        var registry = CreateRegistry();
        using var request = new CancellationTokenSource();
        var lease = registry.BeginOperation(DevicePin, SessionId, request.Token);

        request.Cancel();
        Assert.True(lease.Token.IsCancellationRequested);
        lease.Dispose();
        Assert.Equal(0, registry.Snapshot.ActiveOperations);
        await Task.CompletedTask;
    }

    [Fact]
    public async Task RevocationDuringPendingTunnelEstablishmentCancelsSetupBeforeSocketAcceptance()
    {
        var registry = CreateRegistry();
        using var operation = registry.BeginOperation(DevicePin, SessionId);
        var setupStarted = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);

        var pendingSetup = Task.Run(async () =>
        {
            setupStarted.TrySetResult();
            await Task.Delay(Timeout.InfiniteTimeSpan, operation.Token);
        });

        await setupStarted.Task.WaitAsync(TimeSpan.FromSeconds(2));
        Assert.True(registry.RevokeDevice(DevicePin));
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => pendingSetup);
        operation.Dispose();
        Assert.Equal(0, registry.Snapshot.ActiveOperations);
    }

    [Fact]
    public void SessionExpiryCancelsAnAlreadyActiveOperationLease()
    {
        var now = new DateTimeOffset(2026, 1, 1, 0, 0, 0, TimeSpan.Zero);
        var sessions = new AuthorizedSessionStore(() => now);
        var grant = sessions.Create(DevicePin, true, new[] { "identity.read" });
        var registry = new DeviceOperationRegistry();
        registry.ActivateDevice(DevicePin);
        registry.ActivateSession(DevicePin, grant.SessionId);
        using var operation = registry.BeginOperation(DevicePin, grant.SessionId);

        now = now.AddMinutes(16);
        var expired = Assert.Single(sessions.DrainExpiredSessions());
        Assert.Equal(grant.SessionId, expired.SessionId);
        Assert.True(registry.RevokeSession(expired.ClientFingerprint, expired.SessionId));

        Assert.True(operation.Token.IsCancellationRequested);
        Assert.Equal(0, registry.Snapshot.ActiveOperations);
    }

    [Fact]
    public void RevocationOfDisconnectedDeviceInvalidatesItsGenerationWithoutClientRequest()
    {
        var registry = new DeviceOperationRegistry();
        registry.ActivateDevice(DevicePin);
        registry.ActivateSession(DevicePin, SessionId);

        Assert.True(registry.RevokeDevice(DevicePin));
        Assert.False(registry.RevokeDevice(DevicePin));
        Assert.Equal(0, registry.Snapshot.ActiveOperations);
        Assert.Throws<OperationCanceledException>(() =>
            registry.BeginOperation(DevicePin, SessionId));
    }

    [Fact]
    public void RevokedDeviceRemainsVisibleUntilItsLastOperationLeaseIsReleased()
    {
        var registry = CreateRegistry();
        var operation = registry.BeginOperation(DevicePin, SessionId);

        Assert.True(registry.RevokeDevice(DevicePin));
        Assert.Contains(DevicePin, registry.ActiveDeviceFingerprints);
        Assert.Equal(1, registry.Snapshot.ActiveOperations);

        operation.Dispose();
        Assert.DoesNotContain(DevicePin, registry.ActiveDeviceFingerprints);
        Assert.Equal(0, registry.Snapshot.ActiveOperations);
    }


    [Fact]
    public async Task ConcurrentOperationAdmissionAndRevocationLeavesNoOrphanedLeases()
    {
        var registry = CreateRegistry();
        var start = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var workers = Enumerable.Range(0, 8).Select(_ => Task.Run(async () =>
        {
            await start.Task;
            try
            {
                using var lease = registry.BeginOperation(DevicePin, SessionId);
                await Task.Delay(Timeout.InfiniteTimeSpan, lease.Token);
            }
            catch (OperationCanceledException) { }
        })).ToArray();

        start.TrySetResult();
        Assert.True(registry.RevokeDevice(DevicePin));
        await Task.WhenAll(workers);

        Assert.Equal(0, registry.Snapshot.ActiveOperations);
        Assert.DoesNotContain(DevicePin, registry.ActiveDeviceFingerprints);
        Assert.Throws<OperationCanceledException>(() =>
            registry.BeginOperation(DevicePin, SessionId));
    }

    [Fact]
    public async Task ConcurrentSessionAdmissionAndRevocationLeavesNoOrphanedLeases()
    {
        var registry = CreateRegistry();
        using var alreadyActive = registry.BeginOperation(DevicePin, SessionId);
        var start = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var workers = Enumerable.Range(0, 16).Select(_ => Task.Run(async () =>
        {
            await start.Task;
            try
            {
                using var lease = registry.BeginOperation(DevicePin, SessionId);
                await Task.Delay(Timeout.InfiniteTimeSpan, lease.Token);
            }
            catch (OperationCanceledException) { }
        })).ToArray();

        start.TrySetResult();
        Assert.True(registry.RevokeSession(DevicePin, SessionId));
        await Task.WhenAll(workers);

        Assert.True(alreadyActive.Token.IsCancellationRequested);
        using (var unaffected = registry.BeginOperation(DevicePin, OtherSessionId))
            Assert.False(unaffected.Token.IsCancellationRequested);
        alreadyActive.Dispose();
        Assert.Equal(0, registry.Snapshot.ActiveOperations);
        Assert.Throws<OperationCanceledException>(() =>
            registry.BeginOperation(DevicePin, SessionId));
    }

    private static DeviceOperationRegistry CreateRegistry()
    {
        var registry = new DeviceOperationRegistry();
        registry.ActivateDevice(DevicePin);
        registry.ActivateDevice(OtherDevicePin);
        registry.ActivateSession(DevicePin, SessionId);
        registry.ActivateSession(DevicePin, OtherSessionId);
        registry.ActivateSession(OtherDevicePin, SessionId);
        return registry;
    }
}
