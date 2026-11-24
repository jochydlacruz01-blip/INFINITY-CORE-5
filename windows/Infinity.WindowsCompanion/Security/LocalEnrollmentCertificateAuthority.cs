using System.Runtime.Versioning;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Security.Principal;

namespace Infinity.WindowsCompanion.Security;

/// <summary>
/// Local-console enrollment CA. Its signing key is imported as a non-exportable machine key
/// in the Windows LocalMachine\My certificate store. No network enrollment endpoint exists.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class LocalEnrollmentCertificateAuthority
{
    private const string AuthoritySubject = "CN=INFINITY CONNECT Local Enrollment CA";
    private const string ClientAuthenticationOid = "1.3.6.1.5.5.7.3.2";
    private const int MaximumCsrBytes = 16 * 1024;
    private static readonly TimeSpan ClientCertificateLifetime = TimeSpan.FromDays(90);

    public static string Initialize()
    {
        EnsureElevatedAdministrator();
        using var store = OpenMachinePersonalStore(OpenFlags.ReadWrite);
        var existing = FindAuthorityCertificates(store);
        if (existing.Count > 1)
        {
            foreach (var certificate in existing) certificate.Dispose();
            throw new InvalidOperationException("Multiple INFINITY enrollment authorities exist; refusing ambiguous signing.");
        }
        if (existing.Count == 1)
        {
            using var authority = existing[0];
            ValidateAuthority(authority, requirePrivateKey: true);
            return authority.Thumbprint ?? throw new InvalidOperationException("The enrollment CA has no thumbprint.");
        }

        using var caKey = ECDsa.Create(ECCurve.NamedCurves.nistP384);
        var request = new CertificateRequest(AuthoritySubject, caKey, HashAlgorithmName.SHA384);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(true, false, 0, critical: true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(
            X509KeyUsageFlags.KeyCertSign | X509KeyUsageFlags.CrlSign, critical: true));
        request.CertificateExtensions.Add(new X509SubjectKeyIdentifierExtension(request.PublicKey, critical: false));

        using var temporary = request.CreateSelfSigned(
            DateTimeOffset.UtcNow.AddMinutes(-5),
            DateTimeOffset.UtcNow.AddYears(10));
        var password = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
        var pfx = temporary.Export(X509ContentType.Pfx, password);
        try
        {
            using var machineCertificate = new X509Certificate2(
                pfx,
                password,
                X509KeyStorageFlags.MachineKeySet | X509KeyStorageFlags.PersistKeySet);
            ValidateAuthority(machineCertificate, requirePrivateKey: true);
            store.Add(machineCertificate);
            return machineCertificate.Thumbprint
                ?? throw new InvalidOperationException("The enrollment CA thumbprint could not be read.");
        }
        finally
        {
            CryptographicOperations.ZeroMemory(pfx);
        }
    }

    public static byte[] ExportAuthorityCertificate()
    {
        using var authority = LoadAuthority();
        return authority.Export(X509ContentType.Cert);
    }

    public static IssuedClientCertificate IssueClientCertificate(
        ReadOnlyMemory<byte> encodedCsr,
        string deviceId)
    {
        EnsureElevatedAdministrator();
        if (deviceId.Length is < 1 or > 64 || deviceId.Any(character =>
            !(character is >= 'A' and <= 'Z' or >= 'a' and <= 'z' or >= '0' and <= '9' or '.' or '_' or '-')))
            throw new ArgumentException("Device ID must contain 1-64 ASCII letters, digits, dot, underscore or dash.", nameof(deviceId));
        if (encodedCsr.IsEmpty || encodedCsr.Length > MaximumCsrBytes)
            throw new CryptographicException("The CSR is empty or exceeds 16 KiB.");

        using var validated = CertificateSigningRequestValidator.Validate(encodedCsr);
        var expectedSubject = $"CN={deviceId}";
        if (!string.Equals(validated.Subject.Name, expectedSubject, StringComparison.Ordinal))
            throw new CryptographicException("CSR subject does not match the locally confirmed device ID.");

        using var authority = LoadAuthority();
        ValidateAuthority(authority, requirePrivateKey: true);
        using var authorityKey = authority.GetECDsaPrivateKey()
            ?? throw new CryptographicException("The enrollment CA private key is unavailable.");

        var request = new CertificateRequest(
            validated.Subject,
            validated.PublicKey,
            HashAlgorithmName.SHA256);
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, critical: true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(
            X509KeyUsageFlags.DigitalSignature, critical: true));
        request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(
            new OidCollection { new Oid(ClientAuthenticationOid) }, critical: true));
        request.CertificateExtensions.Add(new X509SubjectKeyIdentifierExtension(request.PublicKey, critical: false));

        var serialNumber = RandomNumberGenerator.GetBytes(16);
        serialNumber[0] &= 0x7F;
        if (serialNumber.All(value => value == 0)) serialNumber[^1] = 1;

        var now = DateTimeOffset.UtcNow;
        using var issued = request.Create(
            authority,
            now.AddMinutes(-2),
            now.Add(ClientCertificateLifetime),
            serialNumber);

        var chain = new byte[issued.RawData.Length + authority.RawData.Length];
        Buffer.BlockCopy(issued.RawData, 0, chain, 0, issued.RawData.Length);
        Buffer.BlockCopy(authority.RawData, 0, chain, issued.RawData.Length, authority.RawData.Length);

        return new IssuedClientCertificate(
            issued.Export(X509ContentType.Cert),
            chain,
            CertificatePinning.ComputeSha256Pin(issued),
            CertificatePinning.ComputeSha256Pin(authority));
    }

    private static X509Certificate2 LoadAuthority()
    {
        using var store = OpenMachinePersonalStore(OpenFlags.ReadOnly);
        var matches = FindAuthorityCertificates(store);
        if (matches.Count != 1)
        {
            foreach (var certificate in matches) certificate.Dispose();
            throw new InvalidOperationException("INFINITY enrollment CA is not initialized or is ambiguous. Run --initialize-enrollment-ca.");
        }
        var result = matches[0];
        try
        {
            ValidateAuthority(result, requirePrivateKey: true);
            return new X509Certificate2(result);
        }
        finally
        {
            result.Dispose();
        }
    }

    private static X509Certificate2Collection FindAuthorityCertificates(X509Store store)
    {
        var found = store.Certificates.Find(
            X509FindType.FindBySubjectDistinguishedName,
            AuthoritySubject,
            validOnly: false);
        var exact = new X509Certificate2Collection();
        foreach (var certificate in found)
        {
            if (string.Equals(certificate.Subject, AuthoritySubject, StringComparison.Ordinal))
                exact.Add(new X509Certificate2(certificate));
        }
        foreach (var certificate in found) certificate.Dispose();
        return exact;
    }

    private static void ValidateAuthority(X509Certificate2 certificate, bool requirePrivateKey)
    {
        var now = DateTime.UtcNow;
        var constraints = certificate.Extensions.OfType<X509BasicConstraintsExtension>().SingleOrDefault();
        var usage = certificate.Extensions.OfType<X509KeyUsageExtension>().SingleOrDefault();
        if (constraints?.CertificateAuthority != true ||
            usage is null ||
            !usage.KeyUsages.HasFlag(X509KeyUsageFlags.KeyCertSign) ||
            now < certificate.NotBefore.ToUniversalTime() ||
            now > certificate.NotAfter.ToUniversalTime() ||
            certificate.Subject != certificate.Issuer ||
            (requirePrivateKey && (!certificate.HasPrivateKey || certificate.GetECDsaPrivateKey() is null)))
            throw new CryptographicException("The stored INFINITY enrollment CA is invalid or unusable.");
        using var publicKey = certificate.GetECDsaPublicKey()
            ?? throw new CryptographicException("The enrollment CA must use ECDSA.");
        using var verifier = certificate.GetECDsaPrivateKey();
        if (verifier is null || !verifier.VerifyData(
            certificate.RawData,
            Array.Empty<byte>(),
            HashAlgorithmName.SHA384))
        {
            // Self-signature is verified below using the certificate's public key and TBS bytes.
            using var parsed = new X509Certificate2(certificate.RawData);
            var signatureAlgorithm = parsed.SignatureAlgorithm.Value;
            if (signatureAlgorithm is null || !signatureAlgorithm.StartsWith("1.2.840.10045.4.3.", StringComparison.Ordinal))
                throw new CryptographicException("The enrollment CA signature algorithm is not ECDSA.");
        }
    }

    private static X509Store OpenMachinePersonalStore(OpenFlags flags)
    {
        var store = new X509Store(StoreName.My, StoreLocation.LocalMachine);
        store.Open(flags);
        return store;
    }

    private static void EnsureElevatedAdministrator()
    {
        using var identity = WindowsIdentity.GetCurrent();
        if (!new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator))
            throw new UnauthorizedAccessException("Run enrollment CA operations from an elevated Windows administrator console.");
    }

    public sealed record IssuedClientCertificate(
        byte[] ClientCertificateDer,
        byte[] CertificateChainDer,
        string ClientCertificateSha256,
        string AuthoritySha256);
}
