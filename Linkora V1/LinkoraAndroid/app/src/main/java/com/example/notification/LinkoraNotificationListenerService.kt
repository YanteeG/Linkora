package com.example.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.network.LinkoraClient
import com.example.protocol.JsonCodec
import com.example.protocol.NotificationEventMessage
import java.util.UUID

class LinkoraNotificationListenerService : NotificationListenerService() {

    companion object {
        var activeClient: LinkoraClient? = null
    }

    private lateinit var settingsManager: NotificationSettingsManager

    override fun onCreate() {
        super.onCreate()
        settingsManager = NotificationSettingsManager(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val client = activeClient ?: return
        if (!client.isConnected()) return

        val packageName = sbn.packageName ?: return

        // Strict filter: only forward notifications from user-selected apps!
        if (!settingsManager.isPackageAllowed(packageName)) {
            return
        }

        // Filter out ongoing / persistent system notifications (like music player, downloads, foreground services)
        if (sbn.isOngoing) {
            return
        }

        val extras = sbn.notification?.extras ?: return
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        var rawText: String? = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (rawTitle.isBlank() && rawText.isNullOrBlank()) {
            return
        }

        // If user configured "omit previews", redact text content
        if (settingsManager.omitPreviews.value) {
            rawText = null // Content preview omitted for privacy
        }

        val pm = packageManager
        val appName = runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)

        val event = NotificationEventMessage(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            appName = appName,
            title = rawTitle.ifBlank { "Notification" },
            text = rawText,
            timestamp = sbn.postTime
        )

        val json = JsonCodec.notificationEventAdapter.toJson(event)
        client.sendTextMessage(json)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
    }
}
