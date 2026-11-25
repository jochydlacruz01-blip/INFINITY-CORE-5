using System.Security.Cryptography.X509Certificates;
using System.Security.AccessControl;
using System.Runtime.Versioning;
using Microsoft.Win32;
using System.Security.Principal;

namespace Infinity.WindowsCompanion.Security;

/// <summary>
/// Machine-wide authorization registry. Its ACL is explicitly protected: SYSTEM and
/// Administrators can write; built-in Users can read only. The service reads on every handshake,
/// so revocation affects the next connection without restarting the service.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class AuthorizedDeviceStore
{
    private const string RegistryPath = @"SOFTWARE\INFINITY\WindowsCompanion\AuthorizedDevices";

    public bool IsAuthorized(X509Certificate certificate)
    {
        if (!OperatingSystem.IsWindows()) return false;
        using var owned = new X509Certificate2(certificate);
        if (!CertificatePinning.HasEnhancedKeyUsage(owned, CertificatePinning.ClientAuthenticationOid) ||
            !CertificatePinning.AllowsDigitalSignature(owned) ||
            DateTime.UtcNow < owned.NotBefore.ToUniversalTime() ||
            DateTime.UtcNow > owned.NotAfter.ToUniversalTime())
            return false;

        var pin = CertificatePinning.ComputeSha256Pin(owned);
        using var key = Registry.LocalMachine.OpenSubKey(RegistryPath, writable: false);
        return key?.GetValue(pin) is string status &&
               string.Equals(status, "approved", StringComparison.Ordinal);
    }

    public bool IsFingerprintAuthorized(string fingerprint)
    {
        if (!OperatingSystem.IsWindows()) return false;
        var pin = CertificatePinning.NormalizeSha256Pin(fingerprint);
        if (pin is null) return false;
        using var key = Registry.LocalMachine.OpenSubKey(RegistryPath, writable: false);
        return key?.GetValue(pin) is string status &&
               string.Equals(status, "approved", StringComparison.Ordinal);
    }

    public static string ApproveFromLocalConsole(string certificatePath)
    {
        if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException("Windows is required.");
        EnsureElevatedWindowsAdministrator();
        using var certificate = new X509Certificate2(
            Path.GetFullPath(certificatePath),
            (string?)null,
            X509KeyStorageFlags.EphemeralKeySet);
        if (!CertificatePinning.HasEnhancedKeyUsage(certificate, CertificatePinning.ClientAuthenticationOid) ||
            !CertificatePinning.AllowsDigitalSignature(certificate) ||
            DateTime.UtcNow < certificate.NotBefore.ToUniversalTime() ||
            DateTime.UtcNow > certificate.NotAfter.ToUniversalTime())
            throw new InvalidOperationException("The certificate is not a currently valid Android client-auth certificate.");

        var pin = CertificatePinning.ComputeSha256Pin(certificate);
        using var key = OpenWritableAuthorizationKey();
        key.SetValue(pin, "approved", RegistryValueKind.String);
        key.Flush();
        return pin;
    }

    public static bool RevokeFromLocalConsole(string fingerprint)
    {
        if (!OperatingSystem.IsWindows()) throw new PlatformNotSupportedException("Windows is required.");
        EnsureElevatedWindowsAdministrator();
        var pin = CertificatePinning.NormalizeSha256Pin(fingerprint)
            ?? throw new ArgumentException("A SHA-256 certificate fingerprint is required.", nameof(fingerprint));
        using var key = OpenWritableAuthorizationKey();
        if (key is null || !key.GetValueNames().Contains(pin, StringComparer.Ordinal))
            return false;
        key.DeleteValue(pin, throwOnMissingValue: false);
        key.Flush();
        DeviceRevocationSignal.NotifyService();
        return true;
    }

    private static RegistryKey OpenWritableAuthorizationKey()
    {
        var security = new RegistrySecurity();
        security.SetAccessRuleProtection(isProtected: true, preserveInheritance: false);
        security.AddAccessRule(new RegistryAccessRule(
            new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null),
            RegistryRights.FullControl,
            AccessControlType.Allow));
        security.AddAccessRule(new RegistryAccessRule(
            new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null),
            RegistryRights.FullControl,
            AccessControlType.Allow));
        security.AddAccessRule(new RegistryAccessRule(
            new SecurityIdentifier(WellKnownSidType.BuiltinUsersSid, null),
            RegistryRights.ReadKey,
            AccessControlType.Allow));

        var key = Registry.LocalMachine.CreateSubKey(
            RegistryPath,
            RegistryKeyPermissionCheck.ReadWriteSubTree,
            RegistryOptions.None,
            security) ?? throw new InvalidOperationException("Could not open the machine authorization registry.");
        key.SetAccessControl(security);
        return key;
    }

    private static void EnsureElevatedWindowsAdministrator()
    {
        if (!OperatingSystem.IsWindows())
            throw new PlatformNotSupportedException("Device approval is available only on Windows.");
        using var identity = WindowsIdentity.GetCurrent();
        var principal = new WindowsPrincipal(identity);
        if (!principal.IsInRole(WindowsBuiltInRole.Administrator))
            throw new UnauthorizedAccessException("Run this command from an elevated Windows administrator console.");
    }
}
