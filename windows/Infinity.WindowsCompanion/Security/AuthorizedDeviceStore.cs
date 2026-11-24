using System.Security.Cryptography.X509Certificates;
using Microsoft.Win32;
using System.Security.Principal;

namespace Infinity.WindowsCompanion.Security;

/// <summary>
/// Machine-wide authorization registry. HKLM inherits administrator/System write
/// permissions; the service reads the registry on every new TLS client handshake,
/// so revocation affects the next connection without restarting the service.
/// </summary>
public sealed class AuthorizedDeviceStore
{
    private const string RegistryPath = @"SOFTWARE\\INFINITY\\WindowsCompanion\\AuthorizedDevices";

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

    public static string ApproveFromLocalConsole(string certificatePath)
    {
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
        using var key = Registry.LocalMachine.CreateSubKey(RegistryPath, writable: true)
            ?? throw new InvalidOperationException("Could not open the machine authorization registry.");
        key.SetValue(pin, "approved", RegistryValueKind.String);
        key.Flush();
        return pin;
    }

    public static bool RevokeFromLocalConsole(string fingerprint)
    {
        EnsureElevatedWindowsAdministrator();
        var pin = CertificatePinning.NormalizeSha256Pin(fingerprint)
            ?? throw new ArgumentException("A SHA-256 certificate fingerprint is required.", nameof(fingerprint));
        using var key = Registry.LocalMachine.OpenSubKey(RegistryPath, writable: true);
        if (key is null || !key.GetValueNames().Contains(pin, StringComparer.Ordinal))
            return false;
        key.DeleteValue(pin, throwOnMissingValue: false);
        key.Flush();
        return true;
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
