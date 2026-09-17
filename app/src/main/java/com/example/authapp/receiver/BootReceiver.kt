package com.example.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.service.ChildForegroundService
import com.google.firebase.crashlytics.FirebaseCrashlytics

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            FirebaseCrashlytics.getInstance().log("[BootReceiver] Device boot event detected: $action")

            val cachedUid = AppPreferences.getUserId(context)
            val cachedRole = AppPreferences.getUserRole(context)
            val currentUid = FirebaseRepository.currentUser?.uid ?: cachedUid

            if (currentUid.isNotEmpty()) {
                val shouldStartChild = cachedRole == "child"
                if (shouldStartChild) {
                    val serviceIntent = Intent(context, ChildForegroundService::class.java).apply {
                        this.action = ChildForegroundService.ACTION_START_MONITORING
                    }
                    try {
                        ContextCompat.startForegroundService(context, serviceIntent)
                        AppHealthTelemetry.logDiagnostic(
                            context,
                            "DEVICE_BOOT",
                            "SUCCESS",
                            "ChildForegroundService auto-started after device reboot (action: $action)"
                        )
                    } catch (e: Exception) {
                        AppHealthTelemetry.logDiagnostic(
                            context,
                            "DEVICE_BOOT",
                            "FAILED",
                            "Failed to start service after reboot: ${e.localizedMessage}",
                            e.localizedMessage
                        )
                    }
                }
            }

            // OFFLINE-FIRST: Reboot ke baad pending upload queue sync karo (net aane par)
            try {
                if (!com.example.authapp.sync.PendingUploadQueue.isEmpty(context)) {
                    FirebaseCrashlytics.getInstance().log(
                        "[BootReceiver] Pending uploads found after reboot (${com.example.authapp.sync.PendingUploadQueue.size(context)} items) — scheduling sync"
                    )
                    com.example.authapp.sync.SyncScheduler.scheduleWorker(context)
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[BootReceiver] Sync schedule error: ${e.localizedMessage}")
            }
        }
    }
}
