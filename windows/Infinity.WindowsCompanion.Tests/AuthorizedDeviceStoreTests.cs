using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Security.AccessControl;
using System.Security.Principal;
using Microsoft.Win32;
using Infinity.WindowsCompanion.Security;
using Xunit;

namespace Infinity.WindowsCompanion.Tests;

public sealed class AuthorizedDeviceStoreTests
{
    [Fact]
    public void LocalApprovalPersistsAndRevocationBlocksTheNextAuthorizationCheck()
    {
        if (!OperatingSystem.IsWindows()) return;

        LocalEnrollmentCertificateAuthority.Initialize();
        using var certificate = CreateIssuedClientCertificate();
        var path = Path.Combine(Path.GetTempPath(), $"infinity-client-{Guid.NewGuid():N}.cer");
        var pin = CertificatePinning.ComputeSha256Pin(certificate);
        try
        {
            File.WriteAllBytes(path, certificate.Export(X509ContentType.Cert));
            var approvedPin = AuthorizedDeviceStore.ApproveFromLocalConsole(path);
            Assert.Equal(pin, approvedPin);

            var store = new AuthorizedDeviceStore();
            Assert.True(store.IsAuthorized(certificate));

            using (var registryKey = Registry.LocalMachine.OpenSubKey(
                @"SOFTWARE\\INFINITY\\WindowsCompanion\\AuthorizedDevices", writable: false))
            {
                Assert.NotNull(registryKey);
                var security = registryKey!.GetAccessControl(AccessControlSections.Access);
                Assert.True(security.AreAccessRulesProtected);
                var rules = security.GetAccessRules(true, false, typeof(SecurityIdentifier))
                    .Cast<RegistryAccessRule>().ToArray();
                var users = new SecurityIdentifier(WellKnownSidType.BuiltinUsersSid, null);
                var admins = new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null);
                var system = new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null);
                Assert.Contains(rules, rule => rule.IdentityReference == users &&
                    (rule.RegistryRights & RegistryRights.ReadKey) == RegistryRights.ReadKey);
                Assert.DoesNotContain(rules, rule => rule.IdentityReference == users &&
                    (rule.RegistryRights & (RegistryRights.SetValue | RegistryRights.CreateSubKey | RegistryRights.Delete)) != 0);
                Assert.Contains(rules, rule => rule.IdentityReference == admins &&
                    (rule.RegistryRights & RegistryRights.FullControl) == RegistryRights.FullControl);
                Assert.Contains(rules, rule => rule.IdentityReference == system &&
                    (rule.RegistryRights & RegistryRights.FullControl) == RegistryRights.FullControl);
            }

            Assert.True(AuthorizedDeviceStore.RevokeFromLocalConsole(pin));
            Assert.False(store.IsAuthorized(certificate));
            Assert.False(AuthorizedDeviceStore.RevokeFromLocalConsole(pin));
        }
        finally
        {
            AuthorizedDeviceStore.RevokeFromLocalConsole(pin);
            File.Delete(path);
        }
    }

    [Fact]
    public void ApprovalRejectsCertificateWithServerOnlyEku()
    {
        if (!OperatingSystem.IsWindows()) return;

        using var certificate = CreateCertificate(CertificatePinning.ServerAuthenticationOid);
        var path = Path.Combine(Path.GetTempPath(), $"infinity-server-{Guid.NewGuid():N}.cer");
        try
        {
            File.WriteAllBytes(path, certificate.Export(X509ContentType.Cert));
            Assert.Throws<InvalidOperationException>(() =>
                AuthorizedDeviceStore.ApproveFromLocalConsole(path));
        }
        finally
        {
            File.Delete(path);
        }
    }

    [Fact]
    public void ApprovalRejectsSelfSignedClientCertificateOutsideEnrollmentAuthority()
    {
        if (!OperatingSystem.IsWindows()) return;

        LocalEnrollmentCertificateAuthority.Initialize();
        using var certificate = CreateCertificate(CertificatePinning.ClientAuthenticationOid);
        var path = Path.Combine(Path.GetTempPath(), $"infinity-untrusted-{Guid.NewGuid():N}.cer");
        try
        {
            File.WriteAllBytes(path, certificate.Export(X509ContentType.Cert));
            Assert.Throws<InvalidOperationException>(() =>
                AuthorizedDeviceStore.ApproveFromLocalConsole(path));
        }
        finally
        {
            File.Delete(path);
        }
    }

    private static X509Certificate2 CreateIssuedClientCertificate()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var request = new CertificateRequest(
            "CN=infinity-authorized-device-test",
            key,
            HashAlgorithmName.SHA256);
        var csr = request.CreateSigningRequest();
        var issued = LocalEnrollmentCertificateAuthority.IssueClientCertificate(
            csr, "infinity-authorized-device-test");
        return new X509Certificate2(issued.ClientCertificateDer);
    }

    private static X509Certificate2 CreateCertificate(string ekuOid)
    {
        using var key = RSA.Create(2048);
        var request = new CertificateRequest(
            "CN=INFINITY authorized-device test",
            key,
            HashAlgorithmName.SHA256,
            RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(
            new OidCollection { new Oid(ekuOid) }, critical: true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(
            X509KeyUsageFlags.DigitalSignature, critical: true));
        return request.CreateSelfSigned(
            DateTimeOffset.UtcNow.AddMinutes(-1),
            DateTimeOffset.UtcNow.AddHours(1));
    }
}
