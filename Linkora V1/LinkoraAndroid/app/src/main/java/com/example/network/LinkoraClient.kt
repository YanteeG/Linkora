package com.example.network

import android.util.Log
import com.example.data.PairedDevice
import com.example.data.PairedDeviceStore
import com.example.protocol.BinaryFileChunk
import com.example.protocol.BinaryFrameCodec
import com.example.protocol.ClipboardSendMessage
import com.example.protocol.DeviceInfoMessage
import com.example.protocol.FileAcceptMessage
import com.example.protocol.FileCancelMessage
import com.example.protocol.FileCompleteMessage
import com.example.protocol.FileOfferMessage
import com.example.protocol.FileProgressMessage
import com.example.protocol.FileRejectMessage
import com.example.protocol.JsonCodec
import com.example.protocol.PairConfirmMessage
import com.example.protocol.PairRequestMessage
import com.example.protocol.PingMessage
import com.example.protocol.PongMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class LinkoraClient(
    private val deviceStore: PairedDeviceStore
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Events emitted for UI and managers
    private val _incomingOffers = MutableSharedFlow<FileOfferMessage>(extraBufferCapacity = 10)
    val incomingOffers: SharedFlow<FileOfferMessage> = _incomingOffers.asSharedFlow()

    private val _incomingChunks = MutableSharedFlow<BinaryFileChunk>(extraBufferCapacity = 64)
    val incomingChunks: SharedFlow<BinaryFileChunk> = _incomingChunks.asSharedFlow()

    private val _incomingClipboard = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val incomingClipboard: SharedFlow<String> = _incomingClipboard.asSharedFlow()

    private val _fileControlEvents = MutableSharedFlow<Pair<String, Any>>(extraBufferCapacity = 20)
    val fileControlEvents: SharedFlow<Pair<String, Any>> = _fileControlEvents.asSharedFlow()

    // Sanitized diagnostics logs (no secrets, no payload content)
    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())
    val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private var pingJob: Job? = null

    // Handshake state
    private var pendingChallenge: String? = null
    private var currentHost: String = ""
    private var currentPort: Int = 43127
    private var currentCertSha256: String = ""
    private var currentPairingToken: String? = null

    fun addDiagnosticLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = "[$time] $message"
        val current = _diagnostics.value.toMutableList()
        if (current.size > 50) current.removeAt(0)
        current.add(entry)
        _diagnostics.value = current
    }

    fun connectForPairing(
        host: String,
        port: Int,
        certificateSha256: String,
        pairingToken: String
    ) {
        disconnect("Initiating new pairing")
        currentHost = host
        currentPort = port
        currentCertSha256 = certificateSha256
        currentPairingToken = pairingToken
        addDiagnosticLog("Connecting for pairing to $host:$port (TLS pinned)")
        connectInternal(host, port, certificateSha256, isPairing = true)
    }

    fun connectToPairedDevice() {
        val device = deviceStore.getPairedDevice()
        if (device == null) {
            _connectionState.value = ConnectionState.Error("No paired device configured")
            return
        }
        disconnect("Reconnecting")
        currentHost = device.host
        currentPort = device.port
        currentCertSha256 = device.certificateSha256
        currentPairingToken = null
        addDiagnosticLog("Reconnecting to ${device.deviceName} at ${device.host}:${device.port}")
        connectInternal(device.host, device.port, device.certificateSha256, isPairing = false)
    }

    private fun connectInternal(
        host: String,
        port: Int,
        certSha256: String,
        isPairing: Boolean
    ) {
        _connectionState.value = ConnectionState.Connecting(host, port)

        try {
            val (sslSocketFactory, trustManager) = TlsFingerprintTrustManager.createSslSocketFactory(certSha256)

            val okHttpClient = OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustManager)
                .hostnameVerifier(TlsFingerprintTrustManager.PinnedIdentityHostnameVerifier)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket requires no read timeout
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .build()

            val wssUrl = "wss://$host:$port/linkora/v1"
            val request = Request.Builder().url(wssUrl).build()

            activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    addDiagnosticLog("TLS connection established & certificate verified")
                    startHeartbeat(webSocket)

                    if (isPairing) {
                        val token = currentPairingToken ?: ""
                        val pairReq = PairRequestMessage(
                            deviceId = deviceStore.getLocalDeviceId(),
                            deviceName = deviceStore.getLocalDeviceName(),
                            pairingToken = token
                        )
                        val json = JsonCodec.pairRequestAdapter.toJson(pairReq)
                        webSocket.send(json)
                        addDiagnosticLog("Sent pair_request to host")
                        _connectionState.value = ConnectionState.Handshaking(host, port)
                    } else {
                        // Reconnection handshake: send device_info
                        val info = DeviceInfoMessage(
                            deviceId = deviceStore.getLocalDeviceId(),
                            deviceName = deviceStore.getLocalDeviceName()
                        )
                        val json = JsonCodec.deviceInfoAdapter.toJson(info)
                        webSocket.send(json)
                        val device = deviceStore.getPairedDevice()
                        val pcName = device?.deviceName ?: "Windows PC"
                        _connectionState.value = ConnectionState.Connected(pcName, host, port)
                        addDiagnosticLog("Reconnected to $pcName")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleTextMessage(webSocket, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleBinaryMessage(bytes.toByteArray())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    addDiagnosticLog("Peer closing connection (code $code): $reason")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    addDiagnosticLog("Connection closed: $reason")
                    stopHeartbeat()
                    _connectionState.value = ConnectionState.Disconnected(reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    stopHeartbeat()
                    val isTls = t is SSLHandshakeException || t.message?.contains("fingerprint") == true
                    val msg = if (isTls) {
                        "TLS certificate verification failed: untrusted or changed server identity"
                    } else {
                        t.localizedMessage ?: "Connection error"
                    }
                    addDiagnosticLog("Connection failed: $msg")
                    _connectionState.value = ConnectionState.Error(msg, isTlsFailure = isTls)
                }
            })
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Failed to initialize TLS socket"
            addDiagnosticLog("TLS setup error: $msg")
            _connectionState.value = ConnectionState.Error(msg)
        }
    }

    private fun handleTextMessage(webSocket: WebSocket, text: String) {
        val base = JsonCodec.parseBase(text) ?: run {
            addDiagnosticLog("Rejected malformed JSON text frame")
            return
        }

        when (base.type) {
            "pair_challenge" -> {
                val challenge = JsonCodec.pairChallengeAdapter.fromJson(text)
                if (challenge != null) {
                    pendingChallenge = challenge.challenge
                    addDiagnosticLog("Received pairing challenge, verification code displayed")
                    _connectionState.value = ConnectionState.Handshaking(
                        host = currentHost,
                        port = currentPort,
                        verificationCode = challenge.verificationCode,
                        challenge = challenge.challenge
                    )
                }
            }

            "pair_complete" -> {
                val complete = JsonCodec.pairCompleteAdapter.fromJson(text)
                if (complete != null && complete.trusted) {
                    val serverName = complete.serverDeviceName ?: "Windows 11 PC"
                    val paired = PairedDevice(
                        deviceId = complete.serverDeviceId ?: "win_${System.currentTimeMillis()}",
                        deviceName = serverName,
                        host = currentHost,
                        port = currentPort,
                        certificateSha256 = currentCertSha256,
                        pairedAt = System.currentTimeMillis()
                    )
                    deviceStore.savePairedDevice(paired)
                    currentPairingToken = null // Token invalidated immediately
                    addDiagnosticLog("Pairing successful! Saved trusted device: $serverName")
                    _connectionState.value = ConnectionState.Connected(serverName, currentHost, currentPort)
                } else {
                    addDiagnosticLog("Pairing rejected or declined by host")
                    _connectionState.value = ConnectionState.Error("Pairing declined by Windows companion")
                }
            }

            "device_info" -> {
                val info = JsonCodec.deviceInfoAdapter.fromJson(text)
                if (info != null) {
                    val state = _connectionState.value
                    if (state is ConnectionState.Connected) {
                        _connectionState.value = state.copy(pcName = info.deviceName)
                    }
                    addDiagnosticLog("Peer device info: ${info.deviceName} (${info.platform})")
                }
            }

            "ping" -> {
                val pong = JsonCodec.pongAdapter.toJson(PongMessage())
                webSocket.send(pong)
            }

            "pong" -> {
                // Heartbeat alive
            }

            "file_offer" -> {
                val offer = JsonCodec.fileOfferAdapter.fromJson(text)
                if (offer != null) {
                    addDiagnosticLog("Incoming file offer: ${offer.fileName} (${offer.fileSize} bytes)")
                    scope.launch { _incomingOffers.emit(offer) }
                }
            }

            "file_accept" -> {
                val msg = JsonCodec.fileAcceptAdapter.fromJson(text)
                if (msg != null) {
                    scope.launch { _fileControlEvents.emit("file_accept" to msg) }
                }
            }

            "file_reject" -> {
                val msg = JsonCodec.fileRejectAdapter.fromJson(text)
                if (msg != null) {
                    scope.launch { _fileControlEvents.emit("file_reject" to msg) }
                }
            }

            "file_progress" -> {
                val msg = JsonCodec.fileProgressAdapter.fromJson(text)
                if (msg != null) {
                    scope.launch { _fileControlEvents.emit("file_progress" to msg) }
                }
            }

            "file_complete" -> {
                val msg = JsonCodec.fileCompleteAdapter.fromJson(text)
                if (msg != null) {
                    scope.launch { _fileControlEvents.emit("file_complete" to msg) }
                }
            }

            "file_cancel" -> {
                val msg = JsonCodec.fileCancelAdapter.fromJson(text)
                if (msg != null) {
                    scope.launch { _fileControlEvents.emit("file_cancel" to msg) }
                }
            }

            "clipboard_send" -> {
                val clip = JsonCodec.clipboardSendAdapter.fromJson(text)
                if (clip != null) {
                    addDiagnosticLog("Received clipboard text (${clip.text.length} chars)")
                    scope.launch { _incomingClipboard.emit(clip.text) }
                }
            }

            "error" -> {
                val err = JsonCodec.errorAdapter.fromJson(text)
                addDiagnosticLog("Protocol error received: ${err?.code} - ${err?.message}")
            }

            else -> {
                addDiagnosticLog("Ignored unrecognized control message: ${base.type}")
            }
        }
    }

    private fun handleBinaryMessage(bytes: ByteArray) {
        try {
            val chunk = BinaryFrameCodec.decode(bytes)
            scope.launch { _incomingChunks.emit(chunk) }
        } catch (e: Exception) {
            addDiagnosticLog("Invalid binary frame dropped: ${e.message}")
        }
    }

    fun confirmPairing() {
        val ws = activeWebSocket ?: return
        val challenge = pendingChallenge ?: ""
        val confirm = PairConfirmMessage(
            deviceId = deviceStore.getLocalDeviceId(),
            challengeResponse = challenge // Echo / acknowledge challenge
        )
        val json = JsonCodec.pairConfirmAdapter.toJson(confirm)
        ws.send(json)
        addDiagnosticLog("Sent user confirmation for pairing code")
    }

    fun sendTextMessage(json: String): Boolean {
        val ws = activeWebSocket ?: return false
        return ws.send(json)
    }

    fun sendBinaryFrame(bytes: ByteArray): Boolean {
        val ws = activeWebSocket ?: return false
        return ws.send(ByteString.of(*bytes))
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        stopHeartbeat()
        pingJob = scope.launch {
            while (isActive) {
                delay(20000)
                try {
                    val ping = JsonCodec.pingAdapter.toJson(PingMessage())
                    webSocket.send(ping)
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun stopHeartbeat() {
        pingJob?.cancel()
        pingJob = null
    }

    fun disconnect(reason: String = "User requested disconnect") {
        stopHeartbeat()
        try {
            activeWebSocket?.close(1000, reason)
        } catch (ignored: Exception) {}
        activeWebSocket = null
        _connectionState.value = ConnectionState.Disconnected(reason)
    }

    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected
}
