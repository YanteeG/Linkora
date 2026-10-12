package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val certificateSha256: String,
    val pairedAt: Long
)

class PairedDeviceStore(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("linkora_secure_store", Context.MODE_PRIVATE)
    private val keyAlias = "LinkoraDeviceKey"

    init {
        ensureKeyStoreKey()
        ensureLocalDeviceId()
    }

    private fun ensureKeyStoreKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String? {
        return runCatching {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < 12) return null
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            val cipherText = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        }.getOrNull()
    }

    private fun ensureLocalDeviceId(): String {
        var id = prefs.getString("local_device_id", null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("local_device_id", id).apply()
        }
        return id
    }

    fun getLocalDeviceId(): String = ensureLocalDeviceId()

    fun getLocalDeviceName(): String {
        val model = Build.MODEL ?: "Android"
        return "Linkora ($model)"
    }

    fun savePairedDevice(device: PairedDevice) {
        val raw = "${device.deviceId}|${device.deviceName}|${device.host}|${device.port}|${device.certificateSha256}|${device.pairedAt}"
        val encrypted = encrypt(raw)
        prefs.edit().putString("paired_device_data", encrypted).apply()
    }

    fun getPairedDevice(): PairedDevice? {
        val encrypted = prefs.getString("paired_device_data", null) ?: return null
        val raw = decrypt(encrypted) ?: return null
        val parts = raw.split("|")
        if (parts.size < 6) return null
        return runCatching {
            PairedDevice(
                deviceId = parts[0],
                deviceName = parts[1],
                host = parts[2],
                port = parts[3].toInt(),
                certificateSha256 = parts[4],
                pairedAt = parts[5].toLong()
            )
        }.getOrNull()
    }

    fun revokePairedDevice() {
        prefs.edit().remove("paired_device_data").apply()
    }

    fun hasPairedDevice(): Boolean = getPairedDevice() != null
}
