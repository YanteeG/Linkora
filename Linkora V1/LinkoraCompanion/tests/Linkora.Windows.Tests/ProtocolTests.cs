using Xunit;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using Linkora.Protocol;
namespace Linkora.Windows.Tests;
public class ProtocolTests
{
    [Fact] public void BinaryFrameRoundTripsBigEndianGuidAndMaxIndex() { var id = Guid.NewGuid(); var bytes = BinaryFrameCodec.Encode(id, uint.MaxValue, [1,2,3]); var x = BinaryFrameCodec.Decode(bytes); Assert.Equal(id, x.TransferId); Assert.Equal(uint.MaxValue, x.ChunkIndex); Assert.Equal(new byte[]{1,2,3}, x.Payload); }
    [Fact] public void BinaryFrameRejectsOversizedPayloadAndWrongType() { Assert.Throws<ArgumentOutOfRangeException>(() => BinaryFrameCodec.Encode(Guid.NewGuid(), 0, new byte[ProtocolConstants.MaxChunkSize+1])); var b = BinaryFrameCodec.Encode(Guid.NewGuid(), 0, []); b[0]=2; Assert.Throws<ArgumentException>(()=>BinaryFrameCodec.Decode(b)); }
    [Fact] public void PairingTokenExpiresAndCannotBeReused() { var s = new PairingTokenService(TimeSpan.FromMinutes(5)); var t=s.Create(DateTimeOffset.Parse("2026-01-01T00:00:00Z")); Assert.True(s.TryConsume(t.Token, t.ExpiresAt.AddMinutes(-1), out _)); Assert.False(s.TryConsume(t.Token, t.ExpiresAt.AddMinutes(-2), out _)); }
    [Fact] public void PairingTokenRejectsWrongAndExpired() { var s=new PairingTokenService(TimeSpan.FromMinutes(5)); var t=s.Create(DateTimeOffset.Parse("2026-01-01T00:00:00Z")); Assert.False(s.TryConsume("wrong", t.ExpiresAt.AddMinutes(-1), out _)); s.Invalidate(); Assert.False(s.TryConsume(t.Token, t.ExpiresAt.AddMinutes(-1), out _)); }
    [Fact] public void CertificateFingerprintUsesDerBytes() { using var rsa=RSA.Create(2048); var req=new CertificateRequest("CN=test",rsa,HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1); using var c=req.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-1),DateTimeOffset.UtcNow.AddDays(1)); var expected=Convert.ToHexString(SHA256.HashData(c.RawData)).ToLowerInvariant(); Assert.Equal(expected, CertificateFingerprint.Sha256Hex(c)); Assert.Equal(64, expected.Length); }
    [Fact] public void PairPayloadContainsAndroidExpectedFieldNames() { var p=new PairingPayload(1,"192.168.1.2",43127,new string('a',64),"token1234","2026-01-01T00:00:00Z"); var json=JsonCodec.Serialize(p); Assert.Contains("\"protocolVersion\"",json); Assert.Contains("\"certificateSha256\"",json); Assert.Contains("\"pairingToken\"",json); Assert.NotNull(JsonCodec.Deserialize<PairingPayload>(json)); }
}
