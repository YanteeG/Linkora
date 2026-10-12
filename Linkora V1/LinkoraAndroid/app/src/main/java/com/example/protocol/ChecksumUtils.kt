package com.example.protocol

import java.io.InputStream
import java.security.MessageDigest

object ChecksumUtils {

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return bytesToHex(hash)
    }

    fun sha256Hex(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read = inputStream.read(buffer)
        while (read != -1) {
            digest.update(buffer, 0, read)
            read = inputStream.read(buffer)
        }
        return bytesToHex(digest.digest())
    }

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(":", "").replace(" ", "").lowercase()
        require(clean.length % 2 == 0) { "Invalid hex string length" }
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = clean.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }
}
