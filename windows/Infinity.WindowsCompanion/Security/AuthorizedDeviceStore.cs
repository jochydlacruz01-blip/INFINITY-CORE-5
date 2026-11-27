using System.Security.Cryptography.X509Certificates;
using System.Security.AccessControl;
using System.Runtime.Versioning;
using System.Text.Json;
using Microsoft.Win32;
using System.Security.Principal;

namespace Infinity.WindowsCompanion.Security;

/// <summary>Machine-wide authorization registry. Only SYSTEM and Administrators can mutate it.
/// Revoked certificates remain as tombstones and can never be re-enabled.</summary>
[SupportedOSPlatform("windows")]
public sealed class AuthorizedDeviceStore
{
    private const string RegistryPath = @"SOFTWARE\INFINITY\WindowsCompanion\AuthorizedDevices";
    private const string MetadataPath = @"SOFTWARE\INFINITY\WindowsCompanion\AuthorizedDeviceMetadata";
    private static readonly TimeSpan LastSeenWriteInterval = TimeSpan.FromMinutes(1);

    public bool IsAuthorized(X509Certificate certificate)
    {
        if (!OperatingSystem.IsWindows()) return false;
        using var owned = new X509Certificate2(certificate);
        if (!CertificatePinning.HasEnhancedKeyUsage(owned, CertificatePinning.ClientAuthenticationOid) ||
            !CertificatePinning.AllowsDigitalSignature(owned) ||
            DateTime.UtcNow < owned.NotBefore.ToUniversalTime() ||
            DateTime.UtcNow > owned.NotAfter.ToUniversalTime() ||
            !LocalEnrollmentCertificateAuthority.ValidateClientCertificateChain(owned))
            return false;
        return IsFingerprintAuthorized(CertificatePinning.ComputeSha256Pin(owned));
    }

    public bool IsFingerprintAuthorized(string fingerprint)
    {
        if (!OperatingSystem.IsWindows()) return false;
        var pin = CertificatePinning.NormalizeSha256Pin(fingerprint);
        if (pin is null) return false;
        using var key = Registry.LocalMachine.OpenSubKey(RegistryPath, writable: false);
        return string.Equals(key?.GetValue(pin) as string, "approved", StringComparison.Ordinal);
    }

    public IReadOnlyList<AuthorizedDeviceInfo> ListAuthorizedDevices()
    {
        if (!OperatingSystem.IsWindows()) return Array.Empty<AuthorizedDeviceInfo>();
        using var key = Registry.LocalMachine.OpenSubKey(RegistryPath, writable: false);
        if (key is null) return Array.Empty<AuthorizedDeviceInfo>();
        var devices = new List<AuthorizedDeviceInfo>();
        foreach (var fingerprint in key.GetValueNames().OrderBy(value => value, StringComparer.Ordinal))
        {
            var status = key.GetValue(fingerprint) as string;
            if (status is not ("approved" or "blocked" or "revoked")) continue;
            var metadata = ReadMetadata(fingerprint);
            devices.Add(metadata is null
                ? new AuthorizedDeviceInfo(fingerprint, "unknown", "unknown", "", "", "", "", null, status)
                : metadata with { Status = status });
        }
        return devices;
    }

    public AuthorizedDeviceInfo? GetDevice(string fingerprint)
    {
        var pin = CertificatePinning.NormalizeSha256Pin(fingerprint);
        return pin is null ? null : ListAuthorizedDevices()
            .FirstOrDefault(device => string.Equals(device.Fingerprint, pin, StringComparison.Ordinal));
    }

    public void TouchLastSeen(string fingerprint)
    {
        if (!OperatingSystem.IsWindows()) return;
        var pin = CertificatePinning.NormalizeSha256Pin(fingerprint);
        if (pin is null || !IsFingerprintAuthorized(pin)) return;
        var metadata = ReadMetadata(pin);
        if (metadata is null) return;
        var now = DateTimeOffset.UtcNow;
        if (DateTimeOffset.TryParse(metadata.LastSeenUtc, out var previous) &&
            now - previous < LastSeenWriteInterval) return;
        WriteMetadata(pin, metadata with { LastSeenUtc = now.ToString("O") });
    }

    public static string ApproveFromLocalConsole(string certificatePath)
    {
        EnsureElevatedWindowsAdministrator();
        using var certificate = new X509Certificate2(
            Path.GetFullPath(certificatePath), (string?)null, X509KeyStorageFlags.EphemeralKeySet);
        ValidateClientCertificate(certificate);
        var pin = CertificatePinning.ComputeSha256Pin(certificate);
        using var key = OpenWritableAuthorizationKey();
        var existing = key.GetValue(pin) as string;
        if (string.Equals(existing, "revoked", StringComparison.Ordinal))
            throw new InvalidOperationException("A revoked certificate cannot be re-enabled; issue a new certificate.");
        key.SetValue(pin, "approved", RegistryValueKind.String);
        key.Flush();
        var now = DateTimeOffset.UtcNow.ToString("O");
        WriteMetadata(pin, new AuthorizedDeviceInfo(
            pin,
            certificate.GetNameInfo(X509NameType.SimpleName, false),
            certificate.Subject,
            certificate.SerialNumber,
            certificate.NotBefore.ToUniversalTime().ToString("O"),
            certificate.NotAfter.ToUniversalTime().ToString("O"),
            now,
            null,
            "approved"));
        DeviceRevocationSignal.NotifyService();
        return pin;
    }

    public static bool BlockFromLocalConsole(string fingerprint)
    {
        EnsureElevatedWindowsAdministrator();
        return SetStatusFromLocalConsole(fingerprint, new[] { "approved" }, "blocked", "device.blocked");
    }

    public static bool EnableFromLocalConsole(string fingerprint)
    {
        EnsureElevatedWindowsAdministrator();
        return SetStatusFromLocalConsole(fingerprint, new[] { "blocked" }, "approved", "device.enabled");
    }

    public static bool RevokeFromLocalConsole(string fingerprint)
    {
        EnsureElevatedWindowsAdministrator();
        return SetStatusFromLocalConsole(
            fingerprint, new[] { "approved", "blocked" }, "revoked", "device.revoked");
    }

    /// <summary>Revokes only the fingerprint bound to the caller's authenticated mTLS certificate.</summary>
    public bool RevokeAuthenticatedDevice(string fingerprint)
    {
        var pin = CertificatePinning.NormalizeSha256Pin(fingerprint);
        if (pin is null) return false;
        using var key = OpenWritableAuthorizationKey();
        var status = key.GetValue(pin) as string;
        if (status is not ("approved" or "blocked")) return false;
        key.SetValue(pin, "revoked", RegistryValueKind.String);
        key.Flush();
        UpdateMetadataStatus(pin, "revoked");
        DeviceRevocationSignal.NotifyService();
        return true;
    }

    private static bool SetStatusFromLocalConsole(
        string fingerprint, IReadOnlyCollection<string> expectedStatuses, string newStatus, string auditEvent)
    {
        var pin = CertificatePinning.NormalizeSha256Pin(fingerprint)
            ?? throw new ArgumentException("A SHA-256 certificate fingerprint is required.", nameof(fingerprint));
        using var key = OpenWritableAuthorizationKey();
        var current = key.GetValue(pin) as string;
        if (current is null || !expectedStatuses.Contains(current, StringComparer.Ordinal)) return false;
        key.SetValue(pin, newStatus, RegistryValueKind.String);
        key.Flush();
        UpdateMetadataStatus(pin, newStatus);
        DeviceRevocationSignal.NotifyService();
        new InfinityAuditLog().Write(auditEvent, pin, null, newStatus == "revoked" ? "revoked" : "allowed");
        return true;
    }

    private static void ValidateClientCertificate(X509Certificate2 certificate)
    {
        if (!CertificatePinning.HasEnhancedKeyUsage(certificate, CertificatePinning.ClientAuthenticationOid) ||
            !CertificatePinning.AllowsDigitalSignature(certificate) ||
            DateTime.UtcNow < certificate.NotBefore.ToUniversalTime() ||
            DateTime.UtcNow > certificate.NotAfter.ToUniversalTime() ||
            !LocalEnrollmentCertificateAuthority.ValidateClientCertificateChain(certificate))
            throw new InvalidOperationException(
                "The certificate must be current, permit TLS client authentication and chain to the local INFINITY enrollment CA.");
    }

    private static AuthorizedDeviceInfo? ReadMetadata(string fingerprint)
    {
        using var key = Registry.LocalMachine.OpenSubKey(MetadataPath, writable: false);
        if (key?.GetValue(fingerprint) is not string json) return null;
        try { return JsonSerializer.Deserialize<AuthorizedDeviceInfo>(json); }
        catch (JsonException) { return null; }
    }

    private static void WriteMetadata(string fingerprint, AuthorizedDeviceInfo metadata)
    {
        using var key = OpenWritableMetadataKey();
        key.SetValue(fingerprint, JsonSerializer.Serialize(metadata), RegistryValueKind.String);
        key.Flush();
    }

    private static void UpdateMetadataStatus(string fingerprint, string status)
    {
        var metadata = ReadMetadata(fingerprint);
        if (metadata is not null) WriteMetadata(fingerprint, metadata with { Status = status });
    }

    private static RegistryKey OpenWritableAuthorizationKey() => OpenWritableKey(RegistryPath);
    private static RegistryKey OpenWritableMetadataKey() => OpenWritableKey(MetadataPath);

    private static RegistryKey OpenWritableKey(string path)
    {
        var security = new RegistrySecurity();
        security.SetAccessRuleProtection(isProtected: true, preserveInheritance: false);
        security.AddAccessRule(new RegistryAccessRule(
            new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null),
            RegistryRights.FullControl, AccessControlType.Allow));
        security.AddAccessRule(new RegistryAccessRule(
            new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null),
            RegistryRights.FullControl, AccessControlType.Allow));
        security.AddAccessRule(new RegistryAccessRule(
            new SecurityIdentifier(WellKnownSidType.BuiltinUsersSid, null),
            RegistryRights.ReadKey, AccessControlType.Allow));
        var key = Registry.LocalMachine.CreateSubKey(
            path, RegistryKeyPermissionCheck.ReadWriteSubTree, RegistryOptions.None, security)
            ?? throw new InvalidOperationException("Could not open the machine device registry.");
        key.SetAccessControl(security);
        return key;
    }

    private static void EnsureElevatedWindowsAdministrator()
    {
        if (!OperatingSystem.IsWindows())
            throw new PlatformNotSupportedException("Device management is available only on Windows.");
        using var identity = WindowsIdentity.GetCurrent();
        if (!new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator))
            throw new UnauthorizedAccessException("Run this command from an elevated Windows administrator console.");
    }

    public sealed record AuthorizedDeviceInfo(
        string Fingerprint,
        string DeviceId,
        string Subject,
        string SerialNumber,
        string NotBeforeUtc,
        string NotAfterUtc,
        string ApprovedAtUtc,
        string? LastSeenUtc,
        string Status);
}
