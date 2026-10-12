package com.example.protocol

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Shared Linkora Protocol - Version 1 specification data models.
 * Used for authenticated WebSocket control messages between Android and Windows companion.
 */

const val LINKORA_PROTOCOL_VERSION = 1
const val DEFAULT_LINKORA_PORT = 43127
const val DEFAULT_LINKORA_WS_PATH = "/linkora/v1"
const val MAX_CHUNK_SIZE = 262144 // 256 KB

@JsonClass(generateAdapter = true)
data class PairingPayload(
    @Json(name = "protocolVersion") val protocolVersion: Int,
    @Json(name = "host") val host: String,
    @Json(name = "port") val port: Int,
    @Json(name = "certificateSha256") val certificateSha256: String,
    @Json(name = "pairingToken") val pairingToken: String,
    @Json(name = "expiresAt") val expiresAt: String
) {
    fun isValid(): Boolean {
        if (protocolVersion != LINKORA_PROTOCOL_VERSION) return false
        if (host.isBlank()) return false
        if (port !in 1024..65535) return false
        val cleanCert = certificateSha256.lowercase().trim()
        if (cleanCert.length != 64 || !cleanCert.all { it in "0123456789abcdef" }) return false
        if (pairingToken.isBlank() || pairingToken.length < 8) return false
        return true
    }
}

/**
 * Base envelope for incoming message dispatch.
 */
@JsonClass(generateAdapter = true)
data class BaseMessage(
    @Json(name = "type") val type: String,
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION
)

@JsonClass(generateAdapter = true)
data class PairRequestMessage(
    @Json(name = "type") val type: String = "pair_request",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "deviceName") val deviceName: String,
    @Json(name = "pairingToken") val pairingToken: String
)

@JsonClass(generateAdapter = true)
data class PairChallengeMessage(
    @Json(name = "type") val type: String = "pair_challenge",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "challenge") val challenge: String,
    @Json(name = "verificationCode") val verificationCode: String
)

@JsonClass(generateAdapter = true)
data class PairConfirmMessage(
    @Json(name = "type") val type: String = "pair_confirm",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "challengeResponse") val challengeResponse: String
)

@JsonClass(generateAdapter = true)
data class PairCompleteMessage(
    @Json(name = "type") val type: String = "pair_complete",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "trusted") val trusted: Boolean,
    @Json(name = "serverDeviceId") val serverDeviceId: String? = null,
    @Json(name = "serverDeviceName") val serverDeviceName: String? = null
)

@JsonClass(generateAdapter = true)
data class DeviceInfoMessage(
    @Json(name = "type") val type: String = "device_info",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "deviceName") val deviceName: String,
    @Json(name = "platform") val platform: String = "Android",
    @Json(name = "osVersion") val osVersion: String = android.os.Build.VERSION.RELEASE ?: "Unknown"
)

@JsonClass(generateAdapter = true)
data class PingMessage(
    @Json(name = "type") val type: String = "ping",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class PongMessage(
    @Json(name = "type") val type: String = "pong",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class FileOfferMessage(
    @Json(name = "type") val type: String = "file_offer",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "transferId") val transferId: String,
    @Json(name = "fileName") val fileName: String,
    @Json(name = "fileSize") val fileSize: Long,
    @Json(name = "sha256") val sha256: String,
    @Json(name = "chunkSize") val chunkSize: Int = MAX_CHUNK_SIZE
)

@JsonClass(generateAdapter = true)
data class FileAcceptMessage(
    @Json(name = "type") val type: String = "file_accept",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "transferId") val transferId: String
)

@JsonClass(generateAdapter = true)
data class FileRejectMessage(
    @Json(name = "type") val type: String = "file_reject",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "transferId") val transferId: String,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class FileProgressMessage(
    @Json(name = "type") val type: String = "file_progress",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "transferId") val transferId: String,
    @Json(name = "bytesTransferred") val bytesTransferred: Long,
    @Json(name = "totalBytes") val totalBytes: Long
)

@JsonClass(generateAdapter = true)
data class FileCompleteMessage(
    @Json(name = "type") val type: String = "file_complete",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "transferId") val transferId: String,
    @Json(name = "sha256") val sha256: String
)

@JsonClass(generateAdapter = true)
data class FileCancelMessage(
    @Json(name = "type") val type: String = "file_cancel",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "transferId") val transferId: String,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class ClipboardSendMessage(
    @Json(name = "type") val type: String = "clipboard_send",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "text") val text: String,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class ClipboardResultMessage(
    @Json(name = "type") val type: String = "clipboard_result",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class NotificationEventMessage(
    @Json(name = "type") val type: String = "notification_event",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "id") val id: String,
    @Json(name = "packageName") val packageName: String,
    @Json(name = "appName") val appName: String,
    @Json(name = "title") val title: String,
    @Json(name = "text") val text: String? = null,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class BatteryStatusMessage(
    @Json(name = "type") val type: String = "battery_status",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "level") val level: Int,
    @Json(name = "isCharging") val isCharging: Boolean,
    @Json(name = "powerSource") val powerSource: String = "BATTERY"
)

@JsonClass(generateAdapter = true)
data class ErrorProtocolMessage(
    @Json(name = "type") val type: String = "error",
    @Json(name = "protocolVersion") val protocolVersion: Int = LINKORA_PROTOCOL_VERSION,
    @Json(name = "code") val code: String,
    @Json(name = "message") val message: String
)
