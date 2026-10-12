package com.example.protocol

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object JsonCodec {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val pairingPayloadAdapter = moshi.adapter(PairingPayload::class.java)
    val baseMessageAdapter = moshi.adapter(BaseMessage::class.java)
    val pairRequestAdapter = moshi.adapter(PairRequestMessage::class.java)
    val pairChallengeAdapter = moshi.adapter(PairChallengeMessage::class.java)
    val pairConfirmAdapter = moshi.adapter(PairConfirmMessage::class.java)
    val pairCompleteAdapter = moshi.adapter(PairCompleteMessage::class.java)
    val deviceInfoAdapter = moshi.adapter(DeviceInfoMessage::class.java)
    val pingAdapter = moshi.adapter(PingMessage::class.java)
    val pongAdapter = moshi.adapter(PongMessage::class.java)
    val fileOfferAdapter = moshi.adapter(FileOfferMessage::class.java)
    val fileAcceptAdapter = moshi.adapter(FileAcceptMessage::class.java)
    val fileRejectAdapter = moshi.adapter(FileRejectMessage::class.java)
    val fileProgressAdapter = moshi.adapter(FileProgressMessage::class.java)
    val fileCompleteAdapter = moshi.adapter(FileCompleteMessage::class.java)
    val fileCancelAdapter = moshi.adapter(FileCancelMessage::class.java)
    val clipboardSendAdapter = moshi.adapter(ClipboardSendMessage::class.java)
    val clipboardResultAdapter = moshi.adapter(ClipboardResultMessage::class.java)
    val notificationEventAdapter = moshi.adapter(NotificationEventMessage::class.java)
    val batteryStatusAdapter = moshi.adapter(BatteryStatusMessage::class.java)
    val errorAdapter = moshi.adapter(ErrorProtocolMessage::class.java)

    fun parsePairingPayload(json: String): PairingPayload? {
        return runCatching { pairingPayloadAdapter.fromJson(json.trim()) }.getOrNull()
    }

    fun parseBase(json: String): BaseMessage? {
        return runCatching { baseMessageAdapter.fromJson(json.trim()) }.getOrNull()
    }
}
