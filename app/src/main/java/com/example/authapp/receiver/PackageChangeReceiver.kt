package com.example.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppInstallEvent
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.SecurityAlert

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val dataUri = intent.data ?: return
        val packageName = dataUri.schemeSpecificPart ?: return

        val childId = AppHealthTelemetry.getEffectiveUserId(context)
        if (childId.isEmpty()) return

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    val event = AppInstallEvent(
                        packageName = packageName,
                        appName = appName,
                        eventType = "INSTALLED",
                        timestamp = System.currentTimeMillis()
                    )
                    FirebaseRepository.logAppInstallEvent(childId, event)

                    val alert = SecurityAlert(
                        type = "APP_INSTALLED",
                        title = "New App Installed",
                        message = "App '$appName' ($packageName) was installed on child device.",
                        timestamp = System.currentTimeMillis(),
                        severity = "INFO"
                    )
                    FirebaseRepository.pushSecurityAlert(childId, alert)
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    val event = AppInstallEvent(
                        packageName = packageName,
                        appName = appName,
                        eventType = "UNINSTALLED",
                        timestamp = System.currentTimeMillis()
                    )
                    FirebaseRepository.logAppInstallEvent(childId, event)

                    val alert = SecurityAlert(
                        type = "APP_UNINSTALLED",
                        title = "App Uninstalled",
                        message = "App '$appName' ($packageName) was uninstalled from child device.",
                        timestamp = System.currentTimeMillis(),
                        severity = "WARNING"
                    )
                    FirebaseRepository.pushSecurityAlert(childId, alert)
                }
            }
        }
    }
}
