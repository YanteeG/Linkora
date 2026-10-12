using System;
using System.Buffers.Binary;

namespace Linkora.Protocol;

public static class BinaryFrameCodec
{
    public const byte FileChunkFrameType = 0x01;
    public const int HeaderSize = 21;

    public static byte[] Encode(Guid transferId, uint chunkIndex, ReadOnlySpan<byte> payload)
    {
        if (payload.Length > ProtocolConstants.MaxChunkSize)
            throw new ArgumentOutOfRangeException(nameof(payload), "Chunk payload exceeds 262144 bytes.");

        var result = new byte[HeaderSize + payload.Length];
        result[0] = FileChunkFrameType;
        transferId.TryWriteBytes(result.AsSpan(1, 16), bigEndian: true, out _);
        BinaryPrimitives.WriteUInt32BigEndian(result.AsSpan(17, 4), chunkIndex);
        payload.CopyTo(result.AsSpan(HeaderSize));
        return result;
    }

    public static BinaryFileChunk Decode(ReadOnlySpan<byte> frame)
    {
        if (frame.Length < HeaderSize)
            throw new ArgumentException($"Binary frame size ({frame.Length}) smaller than header ({HeaderSize}).", nameof(frame));
        if (frame[0] != FileChunkFrameType)
            throw new ArgumentException($"Unknown binary frame type: 0x{frame[0]:X2} (expected 0x01).", nameof(frame));

        var transferId = new Guid(frame.Slice(1, 16), bigEndian: true);
        var chunkIndex = BinaryPrimitives.ReadUInt32BigEndian(frame.Slice(17, 4));
        var payload = frame[HeaderSize..].ToArray();
        if (payload.Length > ProtocolConstants.MaxChunkSize)
            throw new ArgumentException("Binary frame payload exceeds 262144 bytes.", nameof(frame));
        return new BinaryFileChunk(frame[0], transferId, chunkIndex, payload);
    }
}
