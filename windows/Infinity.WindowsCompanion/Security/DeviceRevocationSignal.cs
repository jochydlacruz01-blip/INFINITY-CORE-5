using System.Threading;

namespace Infinity.WindowsCompanion.Security;

/// <summary>
/// Cross-process wake-up for the Windows service after local-console certificate revocation.
/// The event carries no identity data; the service re-reads the protected authorization registry
/// and revokes only fingerprints whose approval has actually disappeared.
/// </summary>
public static class DeviceRevocationSignal
{
    private const string EventName = @"Global\\INFINITY.WindowsCompanion.DeviceRevocation";

    public static EventWaitHandle CreateListener()
    {
        if (!OperatingSystem.IsWindows())
            throw new PlatformNotSupportedException("The revocation signal is Windows-only.");
        return new EventWaitHandle(false, EventResetMode.AutoReset, EventName);
    }

    public static void NotifyService()
    {
        if (!OperatingSystem.IsWindows()) return;
        try
        {
            using var signal = EventWaitHandle.OpenExisting(EventName);
            signal.Set();
        }
        catch (WaitHandleCannotBeOpenedException)
        {
            // No service listener exists; no in-process tunnel or transfer can be active.
        }
    }
}
