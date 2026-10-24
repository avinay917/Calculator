package com.example.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppInstallEvent
import com.example.authapp.data.FirebaseRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val packageName = intent.data?.schemeSpecificPart ?: return

        // Ignore self updates
        if (packageName == context.packageName) return

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast(".")
        }

        val eventType = when (action) {
            Intent.ACTION_PACKAGE_ADDED -> "INSTALLED"
            Intent.ACTION_PACKAGE_REMOVED -> "UNINSTALLED"
            Intent.ACTION_PACKAGE_REPLACED -> "UPDATED"
            else -> "INSTALLED"
        }

        val childId = AppHealthTelemetry.getEffectiveUserId(context)
        if (childId.isNotEmpty()) {
            val event = AppInstallEvent(
                packageName = packageName,
                appName = appName,
                eventType = eventType,
                timestamp = System.currentTimeMillis()
            )
            FirebaseRepository.recordPackageEvent(childId, event)
            FirebaseCrashlytics.getInstance().log("[PackageChangeReceiver] Recorded $eventType for $appName ($packageName)")
        }
    }
}
