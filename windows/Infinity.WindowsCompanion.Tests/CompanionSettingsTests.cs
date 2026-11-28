using System.Net;
using System.Security.Cryptography;
using System.Text;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class CompanionSettingsTests
{
    [Fact]
    public void BindAddressPolicyAllowsLoopbackLanAndOverlayRanges()
    {
        var allowed = new[]
        {
            IPAddress.Loopback,
            IPAddress.IPv6Loopback,
            IPAddress.Parse("10.0.0.8"),
            IPAddress.Parse("172.16.4.2"),
            IPAddress.Parse("172.31.255.254"),
            IPAddress.Parse("192.168.1.20"),
            IPAddress.Parse("100.64.0.10"),
            IPAddress.Parse("100.127.255.254"),
            IPAddress.Parse("fd12:3456:789a::10")
        };

        Assert.All(allowed, address => Assert.True(
            NetworkAddressPolicy.IsAllowedBindAddress(address), $"Expected allowed bind address: {address}"));
    }

    [Fact]
    public void BindAddressPolicyRejectsPublicLinkLocalAndMappedAddresses()
    {
        var rejected = new[]
        {
            IPAddress.Parse("8.8.8.8"),
            IPAddress.Parse("203.0.113.10"),
            IPAddress.Parse("169.254.10.20"),
            IPAddress.Parse("fe80::1"),
            IPAddress.Parse("2001:4860:4860::8888"),
            IPAddress.Parse("ff02::1"),
            IPAddress.Parse("::ffff:192.168.1.20")
        };

        Assert.All(rejected, address => Assert.False(
            NetworkAddressPolicy.IsAllowedBindAddress(address), $"Expected rejected bind address: {address}"));
    }

    [Fact]
    public void DpapiPasswordFileCanBeReadByTheSameMachine()
    {
        if (!OperatingSystem.IsWindows()) return;
        var password = Encoding.UTF8.GetBytes("test-only-PFX-password");
        var protectedBytes = ProtectedData.Protect(password, null, DataProtectionScope.LocalMachine);
        var path = Path.Combine(Path.GetTempPath(), $"infinity-dpapi-{Guid.NewGuid():N}.bin");
        try
        {
            File.WriteAllBytes(path, protectedBytes);
            Assert.Equal("test-only-PFX-password", CompanionSettings.ReadProtectedPasswordFile(path));
        }
        finally
        {
            CryptographicOperations.ZeroMemory(password);
            CryptographicOperations.ZeroMemory(protectedBytes);
            if (File.Exists(path)) File.Delete(path);
        }
    }

    [Fact]
    public void DpapiPasswordFileRejectsTampering()
    {
        if (!OperatingSystem.IsWindows()) return;
        var path = Path.Combine(Path.GetTempPath(), $"infinity-dpapi-tampered-{Guid.NewGuid():N}.bin");
        try
        {
            File.WriteAllBytes(path, new byte[] { 1, 2, 3, 4, 5 });
            Assert.ThrowsAny<CryptographicException>(() => CompanionSettings.ReadProtectedPasswordFile(path));
        }
        finally
        {
            if (File.Exists(path)) File.Delete(path);
        }
    }

    [Fact]
    public void DpapiPasswordFileRejectsMissingAndOversizedFiles()
    {
        if (!OperatingSystem.IsWindows()) return;
        var missing = Path.Combine(Path.GetTempPath(), $"infinity-missing-{Guid.NewGuid():N}.bin");
        Assert.Throws<InvalidOperationException>(() => CompanionSettings.ReadProtectedPasswordFile(missing));

        var oversized = Path.Combine(Path.GetTempPath(), $"infinity-large-{Guid.NewGuid():N}.bin");
        try
        {
            File.WriteAllBytes(oversized, new byte[16 * 1024 + 1]);
            Assert.Throws<InvalidOperationException>(() => CompanionSettings.ReadProtectedPasswordFile(oversized));
        }
        finally
        {
            if (File.Exists(oversized)) File.Delete(oversized);
        }
    }
}
