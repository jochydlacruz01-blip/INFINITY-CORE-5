using System.Security.Cryptography;
using System.Text;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class CompanionSettingsTests
{
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
