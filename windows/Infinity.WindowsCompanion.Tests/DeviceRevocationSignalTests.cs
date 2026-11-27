using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class DeviceRevocationSignalTests
{
    [Fact]
    public void LocalConsoleSignalWakesServiceListenerAcrossNamedEventHandle()
    {
        using var listener = DeviceRevocationSignal.CreateListener();

        DeviceRevocationSignal.NotifyService();

        Assert.True(listener.WaitOne(TimeSpan.FromSeconds(2)));
    }
}
