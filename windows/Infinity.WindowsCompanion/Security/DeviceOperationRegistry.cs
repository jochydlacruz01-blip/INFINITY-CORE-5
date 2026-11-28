using System.Collections.Concurrent;

namespace Infinity.WindowsCompanion.Security;

/// <summary>
/// Owns cancellation lifetimes for operations associated with an authorized device/session.
/// Revocation removes the state from admission before cancellation is signalled, so no new
/// operation can join a revoked generation. Existing leases retain the cancelled generation
/// until their finally/dispose paths release it.
/// </summary>
public sealed class DeviceOperationRegistry
{
    private readonly object gate = new();
    private readonly Dictionary<string, DeviceState> devices = new(StringComparer.Ordinal);
    private readonly Dictionary<string, DeviceState> drainingDevices = new(StringComparer.Ordinal);
    private readonly HashSet<string> revokedDevices = new(StringComparer.Ordinal);
    private int activeOperations;

    public void ActivateDevice(string certificateSha256)
    {
        var fingerprint = CertificatePinning.NormalizeSha256Pin(certificateSha256)
            ?? throw new ArgumentException("A valid device fingerprint is required.", nameof(certificateSha256));

        lock (gate)
        {
            if (revokedDevices.Contains(fingerprint))
                throw new InvalidOperationException("A revoked device certificate cannot be reactivated.");
            if (devices.ContainsKey(fingerprint)) return;
            devices.Add(fingerprint, new DeviceState());
        }
    }

    /// <summary>Admits a device only while its authoritative authorization remains current.</summary>
    public bool ActivateDeviceIfAuthorized(string certificateSha256, Func<bool> isCurrentlyAuthorized)
    {
        ArgumentNullException.ThrowIfNull(isCurrentlyAuthorized);
        var fingerprint = CertificatePinning.NormalizeSha256Pin(certificateSha256)
            ?? throw new ArgumentException("A valid device fingerprint is required.", nameof(certificateSha256));

        lock (gate)
        {
            if (revokedDevices.Contains(fingerprint) || !isCurrentlyAuthorized()) return false;
            if (devices.ContainsKey(fingerprint)) return true;
            devices.Add(fingerprint, new DeviceState());
            return true;
        }
    }

    public void ActivateSession(string certificateSha256, string sessionId)
    {
        var fingerprint = CertificatePinning.NormalizeSha256Pin(certificateSha256)
            ?? throw new ArgumentException("A valid device fingerprint is required.", nameof(certificateSha256));
        if (!Guid.TryParseExact(sessionId, "D", out _))
            throw new ArgumentException("A valid session ID is required.", nameof(sessionId));

        lock (gate)
        {
            if (!devices.TryGetValue(fingerprint, out var device) || revokedDevices.Contains(fingerprint))
                throw new InvalidOperationException("The device has been revoked or is not active.");
            if (!device.Sessions.ContainsKey(sessionId))
                device.Sessions.Add(sessionId, new SessionState());
        }
    }

    public OperationLease BeginOperation(
        string certificateSha256,
        string sessionId,
        CancellationToken requestAborted = default)
    {
        var fingerprint = CertificatePinning.NormalizeSha256Pin(certificateSha256)
            ?? throw new ArgumentException("A valid device fingerprint is required.", nameof(certificateSha256));

        lock (gate)
        {
            if (!devices.TryGetValue(fingerprint, out var device) ||
                revokedDevices.Contains(fingerprint) ||
                !device.Sessions.TryGetValue(sessionId, out var session) ||
                session.Revoked)
                throw new OperationCanceledException("The device or session has been revoked.");

            session.ActiveOperations++;
            device.ActiveOperations++;
            activeOperations++;
            var linked = CancellationTokenSource.CreateLinkedTokenSource(
                new[] { device.Cancellation.Token, session.Cancellation.Token, requestAborted });
            return new OperationLease(linked, () => Release(device, session));
        }
    }

    public bool RevokeSession(string certificateSha256, string sessionId)
    {
        var fingerprint = CertificatePinning.NormalizeSha256Pin(certificateSha256);
        if (fingerprint is null) return false;
        CancellationTokenSource? cancellation = null;

        lock (gate)
        {
            if (!devices.TryGetValue(fingerprint, out var device) ||
                !device.Sessions.TryGetValue(sessionId, out var session) ||
                session.Revoked)
                return false;

            session.Revoked = true;
            cancellation = session.Cancellation;
            if (session.ActiveOperations == 0)
            {
                device.Sessions.Remove(sessionId);
                cancellation.Dispose();
            }
        }

        try { cancellation.Cancel(); }
        catch (ObjectDisposedException) { }
        return true;
    }

    public bool RevokeDevice(string certificateSha256) => ChangeDeviceState(certificateSha256, terminal: true);

    /// <summary>Closes a blocked device's active work without making the block terminal.</summary>
    public bool DeactivateDevice(string certificateSha256) => ChangeDeviceState(certificateSha256, terminal: false);

    private bool ChangeDeviceState(string certificateSha256, bool terminal)
    {
        var fingerprint = CertificatePinning.NormalizeSha256Pin(certificateSha256);
        if (fingerprint is null) return false;
        DeviceState? device;
        CancellationTokenSource[] sessionCancellations;

        lock (gate)
        {
            if (terminal && revokedDevices.Contains(fingerprint)) return false;
            if (terminal) revokedDevices.Add(fingerprint);
            devices.Remove(fingerprint, out device);
            if (device is null) return terminal;
            if (device.ActiveOperations > 0)
                drainingDevices[fingerprint] = device;
            sessionCancellations = device.Sessions.Values
                .Select(session => session.Cancellation).ToArray();
        }

        // Cancel outside the registry lock: callbacks may synchronously dispose leases.
        try { device.Cancellation.Cancel(); }
        catch (ObjectDisposedException) { }
        foreach (var cancellation in sessionCancellations)
        {
            try { cancellation.Cancel(); }
            catch (ObjectDisposedException) { }
        }

        lock (gate)
        {
            if (device.ActiveOperations == 0)
            {
                DisposeState(device);
                RemoveDrainingState(device);
            }
        }
        return true;
    }

    public OperationSnapshot Snapshot
    {
        get
        {
            lock (gate)
            {
                var states = devices.Values.Concat(drainingDevices.Values).Distinct().ToArray();
                return new OperationSnapshot(
                    devices.Keys.Concat(drainingDevices.Keys).Distinct(StringComparer.Ordinal).Count(),
                    states.Sum(device => device.Sessions.Count),
                    activeOperations,
                    revokedDevices.Count);
            }
        }
    }

    public IReadOnlyList<string> ActiveDeviceFingerprints
    {
        get
        {
            lock (gate)
                return devices.Keys.Concat(drainingDevices.Keys)
                    .Distinct(StringComparer.Ordinal).ToArray();
        }
    }

    private void Release(DeviceState device, SessionState session)
    {
        lock (gate)
        {
            session.ActiveOperations--;
            device.ActiveOperations--;
            activeOperations--;

            if (session.Revoked && session.ActiveOperations == 0)
            {
                session.Cancellation.Dispose();
                foreach (var pair in device.Sessions.Where(pair => ReferenceEquals(pair.Value, session)).ToArray())
                    device.Sessions.Remove(pair.Key);
            }

            if (device.ActiveOperations == 0 && device.Cancellation.IsCancellationRequested)
            {
                DisposeState(device);
                RemoveDrainingState(device);
            }
        }
    }

    private void RemoveDrainingState(DeviceState device)
    {
        foreach (var pair in drainingDevices.Where(pair => ReferenceEquals(pair.Value, device)).ToArray())
            drainingDevices.Remove(pair.Key);
    }

    private static void DisposeState(DeviceState device)
    {
        foreach (var session in device.Sessions.Values)
            session.Cancellation.Dispose();
        device.Sessions.Clear();
        device.Cancellation.Dispose();
    }

    private sealed class DeviceState
    {
        public CancellationTokenSource Cancellation { get; } = new();
        public Dictionary<string, SessionState> Sessions { get; } = new(StringComparer.Ordinal);
        public int ActiveOperations { get; set; }
    }

    private sealed class SessionState
    {
        public CancellationTokenSource Cancellation { get; } = new();
        public bool Revoked { get; set; }
        public int ActiveOperations { get; set; }
    }

    public sealed class OperationLease : IDisposable
    {
        private readonly CancellationTokenSource linked;
        private Action? release;

        internal OperationLease(CancellationTokenSource linked, Action release)
        {
            this.linked = linked;
            this.release = release;
        }

        public CancellationToken Token => linked.Token;

        public void Dispose()
        {
            var callback = Interlocked.Exchange(ref release, null);
            if (callback is null) return;
            linked.Dispose();
            callback();
        }
    }

    public sealed record OperationSnapshot(
        int ActiveDevices,
        int ActiveSessions,
        int ActiveOperations,
        int RevokedDeviceTombstones);
}
