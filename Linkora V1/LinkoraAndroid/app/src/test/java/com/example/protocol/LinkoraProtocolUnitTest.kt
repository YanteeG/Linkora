package com.example.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class LinkoraProtocolUnitTest {

    @Test
    fun testChecksumUtils_sha256Hex() {
        // Known SHA-256 test vector for "hello"
        val expectedHelloHash = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        val actualHash = ChecksumUtils.sha256Hex("hello".toByteArray(Charsets.UTF_8))
        assertEquals(expectedHelloHash, actualHash)

        // Empty string SHA-256
        val expectedEmptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val actualEmptyHash = ChecksumUtils.sha256Hex(ByteArray(0))
        assertEquals(expectedEmptyHash, actualEmptyHash)
    }

    @Test
    fun testChecksumUtils_hexRoundtrip() {
        val original = byteArrayOf(0x00, 0x01, 0x0F, 0x7F, -0x80, -0x01)
        val hex = ChecksumUtils.bytesToHex(original)
        val recovered = ChecksumUtils.hexToBytes(hex)
        assertArrayEquals(original, recovered)
    }

    @Test
    fun testFilenameSanitizer_pathTraversalStripping() {
        assertEquals("report.pdf", FilenameSanitizer.sanitize("../../report.pdf"))
        assertEquals("notes.txt", FilenameSanitizer.sanitize("..\\..\\Windows\\System32\\notes.txt"))
        assertEquals("secret.doc", FilenameSanitizer.sanitize("../../../secret.doc"))
    }

    @Test
    fun testFilenameSanitizer_illegalCharacterReplacement() {
        val sanitized = FilenameSanitizer.sanitize("file:name*with?bad<chars>|.png")
        assertFalse(sanitized.contains(":"))
        assertFalse(sanitized.contains("*"))
        assertFalse(sanitized.contains("?"))
        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
        assertFalse(sanitized.contains("|"))
        assertTrue(sanitized.endsWith(".png"))
    }

    @Test
    fun testFilenameSanitizer_fallback() {
        assertEquals("received_file.bin", FilenameSanitizer.sanitize(""))
        assertEquals("received_file.bin", FilenameSanitizer.sanitize("   "))
        assertEquals("received_file.bin", FilenameSanitizer.sanitize("..."))
    }

    @Test
    fun testBinaryFrameCodec_encodeAndDecode() {
        val transferId = UUID.randomUUID()
        val chunkIndex = 42L
        val samplePayload = "Linkora Secure Binary Transfer Payload Test".toByteArray(Charsets.UTF_8)

        val encoded = BinaryFrameCodec.encode(transferId, chunkIndex, samplePayload)

        assertEquals(BinaryFrameCodec.HEADER_SIZE + samplePayload.size, encoded.size)
        assertEquals(BinaryFrameCodec.FRAME_TYPE_FILE_CHUNK, encoded[0])

        val decoded = BinaryFrameCodec.decode(encoded)

        assertEquals(BinaryFrameCodec.FRAME_TYPE_FILE_CHUNK, decoded.frameType)
        assertEquals(transferId, decoded.transferId)
        assertEquals(chunkIndex, decoded.chunkIndex)
        assertArrayEquals(samplePayload, decoded.payload)
    }

    @Test
    fun testBinaryFrameCodec_rejectMalformedHeader() {
        val truncated = ByteArray(15) // Less than 21 bytes
        try {
            BinaryFrameCodec.decode(truncated)
            fail("Expected IllegalArgumentException for short frame")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("smaller than header"))
        }
    }

    @Test
    fun testBinaryFrameCodec_rejectWrongFrameType() {
        val transferId = UUID.randomUUID()
        val encoded = BinaryFrameCodec.encode(transferId, 0L, ByteArray(10))
        encoded[0] = 0x02 // Invalid frame type

        try {
            BinaryFrameCodec.decode(encoded)
            fail("Expected IllegalArgumentException for wrong frame type")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Unknown binary frame type"))
        }
    }

    @Test
    fun testPairingPayload_validation() {
        val validPayload = PairingPayload(
            protocolVersion = 1,
            host = "192.168.1.100",
            port = 43127,
            certificateSha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            pairingToken = "secr3t_tok3n_123",
            expiresAt = "2026-10-11T12:00:00Z"
        )
        assertTrue(validPayload.isValid())

        // Wrong protocol version
        assertFalse(validPayload.copy(protocolVersion = 2).isValid())

        // Blank host
        assertFalse(validPayload.copy(host = "").isValid())

        // Out of range port
        assertFalse(validPayload.copy(port = 80).isValid())
        assertFalse(validPayload.copy(port = 70000).isValid())

        // Invalid certificate fingerprint length or characters
        assertFalse(validPayload.copy(certificateSha256 = "tooshort").isValid())
        assertFalse(validPayload.copy(certificateSha256 = "zzzz" + validPayload.certificateSha256.drop(4)).isValid())

        // Too short pairing token
        assertFalse(validPayload.copy(pairingToken = "abc").isValid())
    }

    @Test
    fun testJsonCodec_serializationRoundtrip() {
        val offer = FileOfferMessage(
            transferId = "550e8400-e29b-41d4-a716-446655440000",
            fileName = "photo.jpg",
            fileSize = 1048576L,
            sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            chunkSize = 262144
        )

        val json = JsonCodec.fileOfferAdapter.toJson(offer)
        assertTrue(json.contains("\"type\":\"file_offer\""))
        assertTrue(json.contains("\"photo.jpg\""))

        val parsedBase = JsonCodec.parseBase(json)
        assertNotNull(parsedBase)
        assertEquals("file_offer", parsedBase!!.type)
        assertEquals(1, parsedBase.protocolVersion)

        val parsedOffer = JsonCodec.fileOfferAdapter.fromJson(json)
        assertNotNull(parsedOffer)
        assertEquals(offer.transferId, parsedOffer!!.transferId)
        assertEquals(offer.fileName, parsedOffer.fileName)
        assertEquals(offer.fileSize, parsedOffer.fileSize)
    }

    @Test
    fun testJsonCodec_pairMessages() {
        val request = PairRequestMessage(
            deviceId = "android_device_1",
            deviceName = "Pixel 8 Pro",
            pairingToken = "secure_token_abc"
        )
        val reqJson = JsonCodec.pairRequestAdapter.toJson(request)
        val parsedReq = JsonCodec.pairRequestAdapter.fromJson(reqJson)
        assertEquals("pair_request", parsedReq!!.type)
        assertEquals("Pixel 8 Pro", parsedReq.deviceName)

        val challenge = PairChallengeMessage(
            challenge = "nonce_12345",
            verificationCode = "482-915"
        )
        val chalJson = JsonCodec.pairChallengeAdapter.toJson(challenge)
        val parsedChal = JsonCodec.pairChallengeAdapter.fromJson(chalJson)
        assertEquals("482-915", parsedChal!!.verificationCode)
    }
}
