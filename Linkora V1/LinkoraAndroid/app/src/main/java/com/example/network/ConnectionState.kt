package com.example.network

sealed interface ConnectionState {
    data class Disconnected(val reason: String? = null) : ConnectionState
    data class Connecting(val host: String, val port: Int) : ConnectionState
    data class Handshaking(
        val host: String,
        val port: Int,
        val verificationCode: String? = null,
        val challenge: String? = null
    ) : ConnectionState
    data class Connected(
        val pcName: String,
        val host: String,
        val port: Int,
        val connectedAt: Long = System.currentTimeMillis()
    ) : ConnectionState
    data class Error(val message: String, val isTlsFailure: Boolean = false) : ConnectionState
}
