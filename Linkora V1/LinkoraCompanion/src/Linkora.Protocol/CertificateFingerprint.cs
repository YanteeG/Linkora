using System;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace Linkora.Protocol;

public static class CertificateFingerprint
{
    public static string Sha256Hex(X509Certificate2 certificate) => Sha256Hex(certificate.RawData);

    public static string Sha256Hex(ReadOnlySpan<byte> derEncodedCertificate)
    {
        return Convert.ToHexString(SHA256.HashData(derEncodedCertificate)).ToLowerInvariant();
    }
}
