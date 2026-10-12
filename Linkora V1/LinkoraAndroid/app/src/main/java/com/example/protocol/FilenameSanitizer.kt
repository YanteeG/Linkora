package com.example.protocol

import java.io.File

object FilenameSanitizer {

    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")
    private const val MAX_FILENAME_LENGTH = 120

    /**
     * Sanitizes an untrusted incoming filename:
     * - Strips path traversal sequences (../, ..\, etc.)
     * - Replaces illegal filesystem characters with underscores
     * - Trims whitespace and trailing dots
     * - Bounds length to 120 characters while preserving extension
     * - Provides a safe fallback name if the result is empty
     */
    fun sanitize(rawName: String, fallback: String = "received_file.bin"): String {
        if (rawName.isBlank()) return fallback

        // Extract base name handling both Windows (\) and Unix (/) separators
        val normalized = rawName.replace('\\', '/')
        val simpleName = normalized.substringAfterLast('/')

        // Remove illegal characters
        var cleaned = simpleName.replace(ILLEGAL_CHARS, "_").trim()

        // Strip leading periods that hide files or traversal tokens
        while (cleaned.startsWith(".") || cleaned.startsWith("..")) {
            cleaned = cleaned.trimStart('.')
        }

        if (cleaned.isBlank()) return fallback

        // Bound length while preserving extension
        if (cleaned.length > MAX_FILENAME_LENGTH) {
            val dotIndex = cleaned.lastIndexOf('.')
            cleaned = if (dotIndex in 1 until cleaned.length - 1 && cleaned.length - dotIndex <= 10) {
                val ext = cleaned.substring(dotIndex)
                val base = cleaned.substring(0, dotIndex).take(MAX_FILENAME_LENGTH - ext.length)
                base + ext
            } else {
                cleaned.take(MAX_FILENAME_LENGTH)
            }
        }

        return cleaned.ifBlank { fallback }
    }
}
