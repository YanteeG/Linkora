using System.IO;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
namespace Linkora.Windows.Security;
public sealed record TrustedDevice(string DeviceId, string DeviceName, DateTimeOffset PairedAt);
public sealed class LocalIdentity : IDisposable
{
    private readonly string root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Linkora");
    public X509Certificate2 Certificate { get; }
    public string Id => Linkora.Protocol.CertificateFingerprint.Sha256Hex(Certificate);
    public LocalIdentity()
    {
        Directory.CreateDirectory(root);
        var path = Path.Combine(root, "identity.dpapi");
        if (File.Exists(path)) {
            var bytes = ProtectedData.Unprotect(File.ReadAllBytes(path), null, DataProtectionScope.CurrentUser);
            try { Certificate = new X509Certificate2(bytes, (string?)null, X509KeyStorageFlags.EphemeralKeySet); }
            finally { CryptographicOperations.ZeroMemory(bytes); }
        } else {
            using var rsa = RSA.Create(3072);
            var req = new CertificateRequest("CN=Linkora Local Companion", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
            req.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, true));
            req.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, true));
            var usages = new OidCollection { new Oid("1.3.6.1.5.5.7.3.1") };
            req.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(usages, false));
            using var cert = req.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-5), DateTimeOffset.UtcNow.AddYears(2));
            var bytes = cert.Export(X509ContentType.Pfx);
            try {
                File.WriteAllBytes(path, ProtectedData.Protect(bytes, null, DataProtectionScope.CurrentUser));
                Certificate = new X509Certificate2(bytes, (string?)null, X509KeyStorageFlags.EphemeralKeySet);
            } finally { CryptographicOperations.ZeroMemory(bytes); }
        }
    }
    public TrustedDevice? LoadTrust() {
        var path = Path.Combine(root, "trust.dpapi");
        if (!File.Exists(path)) return null;
        return JsonSerializer.Deserialize<TrustedDevice>(ProtectedData.Unprotect(File.ReadAllBytes(path), null, DataProtectionScope.CurrentUser));
    }
    public void SaveTrust(TrustedDevice value) {
        var path = Path.Combine(root, "trust.dpapi");
        File.WriteAllBytes(path + ".tmp", ProtectedData.Protect(JsonSerializer.SerializeToUtf8Bytes(value), null, DataProtectionScope.CurrentUser));
        File.Move(path + ".tmp", path, true);
    }
    public void Revoke() => File.Delete(Path.Combine(root, "trust.dpapi"));
    public void Dispose() => Certificate.Dispose();
}
