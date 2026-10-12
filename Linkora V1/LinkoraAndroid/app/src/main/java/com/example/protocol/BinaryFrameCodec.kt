package com.example.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class BinaryFileChunk(
    val frameType: Byte,
    val transferId: UUID,
    val chunkIndex: Long, // represented as unsigned 32-bit int
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BinaryFileChunk

        if (frameType != other.frameType) return false
        if (transferId != other.transferId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = frameType.toInt()
        result = 31 * result + transferId.hashCode()
        result = 31 * result + chunkIndex.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

object BinaryFrameCodec {

    const val FRAME_TYPE_FILE_CHUNK: Byte = 0x01
    const val HEADER_SIZE = 21 // 1 byte type + 16 bytes UUID + 4 bytes chunk index
    const val MAX_PAYLOAD_SIZE = 262144 // 256 KB

    /**
     * Encodes a chunk into a binary frame:
     * - 1 byte: 0x01
     * - 16 bytes: transfer UUID in canonical RFC 4122 network byte order (big-endian)
     * - 4 bytes: unsigned chunk index (big-endian)
     * - payload bytes
     */
    fun encode(transferId: UUID, chunkIndex: Long, payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD_SIZE) { "Chunk size exceeds max allowed ($MAX_PAYLOAD_SIZE)" }
        require(chunkIndex in 0..0xFFFFFFFFL) { "Chunk index out of unsigned 32-bit range" }

        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size)
        buffer.order(ByteOrder.BIG_ENDIAN)

        buffer.put(FRAME_TYPE_FILE_CHUNK)
        buffer.putLong(transferId.mostSignificantBits)
        buffer.putLong(transferId.leastSignificantBits)
        buffer.putInt(chunkIndex.toInt())
        buffer.put(payload)

        return buffer.array()
    }

    /**
     * Decodes a binary frame.
     * Rejects frames that are too short, have wrong magic frame type, or exceed max payload size.
     */
    fun decode(bytes: ByteArray): BinaryFileChunk {
        if (bytes.size < HEADER_SIZE) {
            throw IllegalArgumentException("Binary frame size (${bytes.size}) smaller than header ($HEADER_SIZE)")
        }

        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.BIG_ENDIAN)

        val frameType = buffer.get()
        if (frameType != FRAME_TYPE_FILE_CHUNK) {
            throw IllegalArgumentException("Unknown binary frame type: $frameType (expected $FRAME_TYPE_FILE_CHUNK)")
        }

        val msb = buffer.long
        val lsb = buffer.long
        val transferId = UUID(msb, lsb)

        val chunkIndex = buffer.int.toLong() and 0xFFFFFFFFL

        val payloadSize = bytes.size - HEADER_SIZE
        if (payloadSize > MAX_PAYLOAD_SIZE) {
            throw IllegalArgumentException("Binary frame payload ($payloadSize) exceeds limit ($MAX_PAYLOAD_SIZE)")
        }

        val payload = ByteArray(payloadSize)
        buffer.get(payload)

        return BinaryFileChunk(
            frameType = frameType,
            transferId = transferId,
            chunkIndex = chunkIndex,
            payload = payload
        )
    }
}
