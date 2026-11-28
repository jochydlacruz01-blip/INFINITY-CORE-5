using System.Formats.Asn1;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace Infinity.WindowsCompanion.Security;

/// <summary>Strict PKCS#10 validator for ECDSA P-256 / SHA-256 device enrollment requests.</summary>
public static class CertificateSigningRequestValidator
{
    private const int MaximumRequestBytes = 16 * 1024;
    private const string EcdsaWithSha256Oid = "1.2.840.10045.4.3.2";

    public sealed class ValidatedRequest : IDisposable
    {
        internal ValidatedRequest(X500DistinguishedName subject, ECDsa publicKey)
        {
            Subject = subject;
            PublicKey = publicKey;
        }

        public X500DistinguishedName Subject { get; }
        public ECDsa PublicKey { get; }
        public void Dispose() => PublicKey.Dispose();
    }

    public static ValidatedRequest Validate(ReadOnlyMemory<byte> encodedRequest)
    {
        if (encodedRequest.IsEmpty || encodedRequest.Length > MaximumRequestBytes)
            throw new CryptographicException("The PKCS#10 request is empty or exceeds 16 KiB.");

        try
        {
            var root = new AsnReader(encodedRequest, AsnEncodingRules.DER);
            var request = root.ReadSequence();
            root.ThrowIfNotEmpty();

            var requestInfo = request.ReadEncodedValue();
            var signatureAlgorithm = request.ReadSequence();
            var signatureOid = signatureAlgorithm.ReadObjectIdentifier();
            signatureAlgorithm.ThrowIfNotEmpty();
            var signature = request.ReadBitString(out var unusedBits);
            request.ThrowIfNotEmpty();

            if (signatureOid != EcdsaWithSha256Oid || unusedBits != 0)
                throw new CryptographicException("Only ECDSA-with-SHA256 PKCS#10 signatures are accepted.");

            var infoRoot = new AsnReader(requestInfo, AsnEncodingRules.DER);
            var info = infoRoot.ReadSequence();
            infoRoot.ThrowIfNotEmpty();
            if (!info.ReadInteger().IsZero)
                throw new CryptographicException("Unsupported PKCS#10 request version.");

            var subjectDer = info.ReadEncodedValue();
            var publicKeyDer = info.ReadEncodedValue();
            var attributesDer = info.ReadEncodedValue();
            info.ThrowIfNotEmpty();

            // This enrollment profile intentionally permits no CSR attributes.
            if (attributesDer.Length != 2 ||
                attributesDer.Span[0] != 0xA0 ||
                attributesDer.Span[1] != 0x00)
                throw new CryptographicException("PKCS#10 attributes are not permitted by this enrollment profile.");

            var subject = new X500DistinguishedName(subjectDer.Span);
            var publicKey = ECDsa.Create();
            try
            {
                publicKey.ImportSubjectPublicKeyInfo(publicKeyDer.Span, out var bytesRead);
                if (bytesRead != publicKeyDer.Length || publicKey.KeySize != 256)
                    throw new CryptographicException("Only a complete NIST P-256 public key is accepted.");

                if (!publicKey.VerifyData(
                    requestInfo.Span,
                    signature,
                    HashAlgorithmName.SHA256,
                    DSASignatureFormat.Rfc3279DerSequence))
                    throw new CryptographicException("The PKCS#10 proof-of-possession signature is invalid.");

                return new ValidatedRequest(subject, publicKey);
            }
            catch
            {
                publicKey.Dispose();
                throw;
            }
        }
        catch (AsnContentException error)
        {
            throw new CryptographicException("The PKCS#10 request is not valid DER.", error);
        }
    }
}
