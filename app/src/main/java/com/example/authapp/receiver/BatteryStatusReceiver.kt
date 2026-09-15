package com.example.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.SecurityAlert

class BatteryStatusReceiver : BroadcastReceiver() {

    companion object {
        private var lastAlertTime = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val childId = AppHealthTelemetry.getEffectiveUserId(context)
        if (childId.isEmpty()) return

        when (action) {
            Intent.ACTION_BATTERY_LOW -> {
                val now = System.currentTimeMillis()
                // Throttle low battery alerts to once every 15 minutes
                if (now - lastAlertTime > 15 * 60 * 1000L) {
                    lastAlertTime = now
                    val alert = SecurityAlert(
                        type = "BATTERY_LOW",
                        title = "Low Battery Warning",
                        message = "Child device battery is critically low (≤15%). Please remind them to charge.",
                        timestamp = now,
                        severity = "CRITICAL"
                    )
                    FirebaseRepository.pushSecurityAlert(childId, alert)
                }
            }
            Intent.ACTION_POWER_CONNECTED -> {
                val alert = SecurityAlert(
                    type = "CHARGER_CONNECTED",
                    title = "Charger Connected",
                    message = "Child device has been plugged into a charger.",
                    timestamp = System.currentTimeMillis(),
                    severity = "INFO"
                )
                FirebaseRepository.pushSecurityAlert(childId, alert)
                AppHealthTelemetry.syncDeviceHealth(context)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                val alert = SecurityAlert(
                    type = "CHARGER_DISCONNECTED",
                    title = "Charger Disconnected",
                    message = "Child device was disconnected from the charger.",
                    timestamp = System.currentTimeMillis(),
                    severity = "INFO"
                )
                FirebaseRepository.pushSecurityAlert(childId, alert)
                AppHealthTelemetry.syncDeviceHealth(context)
            }
        }
    }
}
