using System;
using System.Linq;
using System.Text.Json.Serialization;

namespace Linkora.Protocol;

public static class ProtocolConstants
{
    public const int Version = 1;
    public const int DefaultPort = 43127;
    public const string WebSocketPath = "/linkora/v1";
    public const int MaxChunkSize = 262_144;
}

public sealed record PairingPayload(
    [property: JsonPropertyName("protocolVersion")] int ProtocolVersion,
    [property: JsonPropertyName("host")] string Host,
    [property: JsonPropertyName("port")] int Port,
    [property: JsonPropertyName("certificateSha256")] string CertificateSha256,
    [property: JsonPropertyName("pairingToken")] string PairingToken,
    [property: JsonPropertyName("expiresAt")] string ExpiresAt)
{
    public bool IsValid()
    {
        var certificate = CertificateSha256.Trim();
        return ProtocolVersion == ProtocolConstants.Version
            && !string.IsNullOrWhiteSpace(Host)
            && Port is >= 1024 and <= 65535
            && certificate.Length == 64
            && certificate.All(Uri.IsHexDigit)
            && !string.IsNullOrWhiteSpace(PairingToken)
            && PairingToken.Length >= 8
            && !string.IsNullOrWhiteSpace(ExpiresAt);
    }
}

public sealed record BaseMessage(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("protocolVersion")] int ProtocolVersion = ProtocolConstants.Version);

public sealed record PairRequestMessage(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("protocolVersion")] int ProtocolVersion,
    [property: JsonPropertyName("deviceId")] string DeviceId,
    [property: JsonPropertyName("deviceName")] string DeviceName,
    [property: JsonPropertyName("pairingToken")] string PairingToken)
{
    public PairRequestMessage(string deviceId, string deviceName, string pairingToken)
        : this("pair_request", ProtocolConstants.Version, deviceId, deviceName, pairingToken) { }
}

public sealed record PairChallengeMessage(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("protocolVersion")] int ProtocolVersion,
    [property: JsonPropertyName("challenge")] string Challenge,
    [property: JsonPropertyName("verificationCode")] string VerificationCode)
{
    public PairChallengeMessage(string challenge, string verificationCode)
        : this("pair_challenge", ProtocolConstants.Version, challenge, verificationCode) { }
}

public sealed record PairConfirmMessage(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("protocolVersion")] int ProtocolVersion,
    [property: JsonPropertyName("deviceId")] string DeviceId,
    [property: JsonPropertyName("challengeResponse")] string ChallengeResponse)
{
    public PairConfirmMessage(string deviceId, string challengeResponse)
        : this("pair_confirm", ProtocolConstants.Version, deviceId, challengeResponse) { }
}

public sealed record PairCompleteMessage(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("protocolVersion")] int ProtocolVersion,
    [property: JsonPropertyName("trusted")] bool Trusted,
    [property: JsonPropertyName("serverDeviceId")] string? ServerDeviceId,
    [property: JsonPropertyName("serverDeviceName")] string? ServerDeviceName)
{
    public PairCompleteMessage(bool trusted, string? serverDeviceId, string? serverDeviceName)
        : this("pair_complete", ProtocolConstants.Version, trusted, serverDeviceId, serverDeviceName) { }
}

public sealed record DeviceInfoMessage(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("protocolVersion")] int ProtocolVersion,
    [property: JsonPropertyName("deviceId")] string DeviceId,
    [property: JsonPropertyName("deviceName")] string DeviceName,
    [property: JsonPropertyName("platform")] string Platform,
    [property: JsonPropertyName("osVersion")] string OsVersion)
{
    public DeviceInfoMessage(string deviceId, string deviceName, string osVersion)
        : this("device_info", ProtocolConstants.Version, deviceId, deviceName, "Windows", osVersion) { }
}

public sealed record PingMessage(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("protocolVersion")] int ProtocolVersion,
    [property: JsonPropertyName("timestamp")] long Timestamp)
{
    public PingMessage(long timestamp) : this("ping", ProtocolConstants.Version, timestamp) { }
}

public sealed record PongMessage(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("protocolVersion")] int ProtocolVersion,
    [property: JsonPropertyName("timestamp")] long Timestamp)
{
    public PongMessage(long timestamp) : this("pong", ProtocolConstants.Version, timestamp) { }
}

public sealed record ErrorProtocolMessage(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("protocolVersion")] int ProtocolVersion,
    [property: JsonPropertyName("code")] string Code,
    [property: JsonPropertyName("message")] string Message)
{
    public ErrorProtocolMessage(string code, string message)
        : this("error", ProtocolConstants.Version, code, message) { }
}

public sealed record BinaryFileChunk(byte FrameType, Guid TransferId, uint ChunkIndex, byte[] Payload);
