package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.battery.BatteryInfo
import com.example.battery.BatteryMonitor
import com.example.data.PairedDevice
import com.example.data.PairedDeviceStore
import com.example.data.history.LinkoraDatabase
import com.example.data.history.TransferHistoryEntity
import com.example.filetransfer.ActiveTransfer
import com.example.filetransfer.FileTransferManager
import com.example.network.ConnectionState
import com.example.network.LinkoraClient
import com.example.notification.InstalledAppItem
import com.example.notification.LinkoraNotificationListenerService
import com.example.notification.NotificationSettingsManager
import com.example.protocol.ClipboardResultMessage
import com.example.protocol.ClipboardSendMessage
import com.example.protocol.FileOfferMessage
import com.example.protocol.JsonCodec
import com.example.protocol.PairingPayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val deviceStore = PairedDeviceStore(context)
    val client = LinkoraClient(deviceStore)
    private val database = LinkoraDatabase.getDatabase(context)
    val historyDao = database.transferHistoryDao()
    val fileTransferManager = FileTransferManager(context, client, historyDao)
    val batteryMonitor = BatteryMonitor(context, client)
    val notificationSettingsManager = NotificationSettingsManager(context)

    val connectionState: StateFlow<ConnectionState> = client.connectionState
    val activeTransfers: StateFlow<Map<String, ActiveTransfer>> = fileTransferManager.activeTransfers
    val pendingIncomingOffer: StateFlow<FileOfferMessage?> = fileTransferManager.pendingIncomingOffer
    val batteryInfo: StateFlow<BatteryInfo> = batteryMonitor.batteryInfo
    val diagnosticsLogs: StateFlow<List<String>> = client.diagnostics

    val recentTransfers: StateFlow<List<TransferHistoryEntity>> = historyDao.getRecentTransfers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransfers: StateFlow<List<TransferHistoryEntity>> = historyDao.getAllTransfers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Clipboard staging
    private val _lastReceivedClipboard = MutableStateFlow<String?>(null)
    val lastReceivedClipboard: StateFlow<String?> = _lastReceivedClipboard.asStateFlow()

    private val _uiToastEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val uiToastEvents: SharedFlow<String> = _uiToastEvents.asSharedFlow()

    // Notification settings UI state
    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    init {
        LinkoraNotificationListenerService.activeClient = client
        batteryMonitor.start()

        // Incoming clipboard listener
        viewModelScope.launch {
            client.incomingClipboard.collect { text ->
                _lastReceivedClipboard.value = text
                _uiToastEvents.emit("Received clipboard text from PC")
            }
        }

        // On connection changes, trigger battery update
        viewModelScope.launch {
            client.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    batteryMonitor.sendCurrentBatteryToPeer()
                }
            }
        }

        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = notificationSettingsManager.getInstalledApps()
        }
    }

    // Pairing actions
    fun pairWithPayload(payload: PairingPayload) {
        if (!payload.isValid()) {
            emitToast("Invalid pairing payload format or expired parameters")
            return
        }
        client.connectForPairing(
            host = payload.host,
            port = payload.port,
            certificateSha256 = payload.certificateSha256,
            pairingToken = payload.pairingToken
        )
    }

    fun confirmPairingCode() {
        client.confirmPairing()
    }

    fun reconnectToPairedDevice() {
        client.connectToPairedDevice()
    }

    fun disconnect() {
        client.disconnect("User disconnected")
    }

    fun revokePairedDevice() {
        client.disconnect("Device pairing revoked")
        deviceStore.revokePairedDevice()
        emitToast("Paired PC revoked")
    }

    fun getPairedDevice(): PairedDevice? = deviceStore.getPairedDevice()

    // File transfer actions
    fun sendFile(uri: Uri) {
        if (!client.isConnected()) {
            emitToast("Cannot send file: not connected to Windows companion")
            return
        }
        fileTransferManager.sendFile(uri)
    }

    fun acceptIncomingOffer(offer: FileOfferMessage) {
        fileTransferManager.acceptIncomingOffer(offer)
    }

    fun rejectIncomingOffer(offer: FileOfferMessage) {
        fileTransferManager.rejectIncomingOffer(offer)
    }

    fun cancelTransfer(transferId: String) {
        fileTransferManager.cancelTransfer(transferId, "Cancelled by user")
    }

    // Clipboard actions (manual explicit actions only)
    fun sendPhoneClipboardToPc(text: String) {
        if (!client.isConnected()) {
            emitToast("Not connected to Windows companion")
            return
        }
        if (text.isBlank()) {
            emitToast("Clipboard is empty")
            return
        }
        val msg = ClipboardSendMessage(text = text)
        val json = JsonCodec.clipboardSendAdapter.toJson(msg)
        val sent = client.sendTextMessage(json)
        if (sent) {
            emitToast("Sent clipboard text to PC")
            client.addDiagnosticLog("Sent clipboard text (${text.length} chars) to PC")
        } else {
            emitToast("Failed to send clipboard text")
        }
    }

    fun copyReceivedClipboardToSystem() {
        val text = _lastReceivedClipboard.value ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Linkora shared text", text)
        clipboard.setPrimaryClip(clip)
        emitToast("Copied text from PC to phone clipboard")
        val ack = ClipboardResultMessage(success = true)
        client.sendTextMessage(JsonCodec.clipboardResultAdapter.toJson(ack))
    }

    fun clearReceivedClipboard() {
        _lastReceivedClipboard.value = null
    }

    // History actions
    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            historyDao.deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
            emitToast("Transfer history cleared")
        }
    }

    // Notification settings actions
    fun toggleNotificationForwarding(enabled: Boolean) {
        notificationSettingsManager.setForwardingEnabled(enabled)
    }

    fun toggleOmitPreviews(omit: Boolean) {
        notificationSettingsManager.setOmitPreviews(omit)
    }

    fun toggleAppNotification(packageName: String, allowed: Boolean) {
        notificationSettingsManager.togglePackage(packageName, allowed)
        loadInstalledApps()
    }

    fun emitToast(message: String) {
        viewModelScope.launch {
            _uiToastEvents.emit(message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        batteryMonitor.stop()
        LinkoraNotificationListenerService.activeClient = null
    }
}
