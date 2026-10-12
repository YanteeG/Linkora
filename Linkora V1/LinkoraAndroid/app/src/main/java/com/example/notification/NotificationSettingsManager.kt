package com.example.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val isAllowed: Boolean
)

class NotificationSettingsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("linkora_notifications", Context.MODE_PRIVATE)

    private val _isForwardingEnabled = MutableStateFlow(prefs.getBoolean("forwarding_enabled", false))
    val isForwardingEnabled: StateFlow<Boolean> = _isForwardingEnabled.asStateFlow()

    private val _omitPreviews = MutableStateFlow(prefs.getBoolean("omit_previews", false))
    val omitPreviews: StateFlow<Boolean> = _omitPreviews.asStateFlow()

    private val _allowedPackages = MutableStateFlow<Set<String>>(
        prefs.getStringSet("allowed_packages", emptySet()) ?: emptySet()
    )
    val allowedPackages: StateFlow<Set<String>> = _allowedPackages.asStateFlow()

    fun setForwardingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("forwarding_enabled", enabled).apply()
        _isForwardingEnabled.value = enabled
    }

    fun setOmitPreviews(omit: Boolean) {
        prefs.edit().putBoolean("omit_previews", omit).apply()
        _omitPreviews.value = omit
    }

    fun togglePackage(packageName: String, allow: Boolean) {
        val current = _allowedPackages.value.toMutableSet()
        if (allow) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet("allowed_packages", current).apply()
        _allowedPackages.value = current
    }

    fun isPackageAllowed(packageName: String): Boolean {
        if (!_isForwardingEnabled.value) return false
        return _allowedPackages.value.contains(packageName)
    }

    fun isNotificationAccessGranted(): Boolean {
        val cn = ComponentName(context, LinkoraNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }

    fun getNotificationAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun getInstalledApps(): List<InstalledAppItem> {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val allowed = _allowedPackages.value

        return installed
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName == "com.google.android.apps.messaging" }
            .map { appInfo ->
                InstalledAppItem(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    isAllowed = allowed.contains(appInfo.packageName)
                )
            }
            .sortedBy { it.appName.lowercase() }
    }
}
