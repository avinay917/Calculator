package com.example.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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
            val currentUser = FirebaseRepository.currentUser
            if (currentUser != null) {
                FirebaseRepository.getUserRoleOnce(currentUser.uid) { role ->
                    if (role == "child") {
                        val serviceIntent = Intent(context, ChildForegroundService::class.java).apply {
                            this.action = ChildForegroundService.ACTION_START_MONITORING
                        }
                        try {
                            ContextCompat.startForegroundService(context, serviceIntent)
                            FirebaseCrashlytics.getInstance().log("[BootReceiver] Started ChildForegroundService after reboot.")
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().log("[BootReceiver] Failed to start service: ${e.localizedMessage}")
                            FirebaseCrashlytics.getInstance().recordException(e)
                        }
                    }
                }
            }
        }
    }
}
