package com.example.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.network.LinkoraClient
import com.example.protocol.BatteryStatusMessage
import com.example.protocol.JsonCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryInfo(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val powerSource: String = "BATTERY"
)

class BatteryMonitor(
    private val context: Context,
    private val client: LinkoraClient
) {
    private val _batteryInfo = MutableStateFlow(BatteryInfo())
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

                val pct = if (level >= 0 && scale > 0) ((level.toFloat() / scale) * 100).toInt() else 100
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val powerSource = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                    else -> "BATTERY"
                }

                val newInfo = BatteryInfo(level = pct, isCharging = isCharging, powerSource = powerSource)
                val previous = _batteryInfo.value
                _batteryInfo.value = newInfo

                // Only transmit if value changed or first time, and only when connected to PC
                if (client.isConnected() && (previous.level != newInfo.level || previous.isCharging != newInfo.isCharging)) {
                    sendBatteryStatusToPeer(newInfo)
                }
            }
        }
    }

    fun start() {
        if (!receiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(receiver, filter)
            receiverRegistered = true
        }
    }

    fun stop() {
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
    }

    fun sendCurrentBatteryToPeer() {
        if (client.isConnected()) {
            sendBatteryStatusToPeer(_batteryInfo.value)
        }
    }

    private fun sendBatteryStatusToPeer(info: BatteryInfo) {
        val msg = BatteryStatusMessage(
            level = info.level,
            isCharging = info.isCharging,
            powerSource = info.powerSource
        )
        val json = JsonCodec.batteryStatusAdapter.toJson(msg)
        client.sendTextMessage(json)
    }
}
