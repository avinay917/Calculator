package com.example.authapp.service

import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.FirebaseRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AppPreferences.saveFcmToken(applicationContext, token)
        FirebaseRepository.updateFcmToken()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val action = data["action"]
        val streamType = data["streamType"] ?: "audio"
        val sessionId = data["sessionId"] ?: ""

        AppHealthTelemetry.logDiagnostic(
            applicationContext,
            "FCM_PUSH",
            "RECEIVED",
            "FCM wake-up payload received: action=$action, streamType=$streamType, session=$sessionId"
        )

        if (action == "START_STREAM") {
            val intent = Intent(this, ChildForegroundService::class.java).apply {
                this.action = ChildForegroundService.ACTION_START
                putExtra(ChildForegroundService.EXTRA_STREAM_TYPE, streamType)
                putExtra(ChildForegroundService.EXTRA_SESSION_ID, sessionId)
            }
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (e: Exception) {
                AppHealthTelemetry.logDiagnostic(
                    applicationContext,
                    "FCM_PUSH",
                    "FAILED",
                    "Failed to start foreground service from FCM: ${e.localizedMessage}",
                    e.localizedMessage
                )
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        } else if (action == "STOP_STREAM") {
            val intent = Intent(this, ChildForegroundService::class.java).apply {
                this.action = ChildForegroundService.ACTION_STOP_STREAM
            }
            try {
                startService(intent)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[FCM] Failed to stop stream: ${e.localizedMessage}")
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        } else if (action == "REFRESH_LOCATION" || action == "WAKEUP") {
            val intent = Intent(this, ChildForegroundService::class.java).apply {
                this.action = ChildForegroundService.ACTION_START_MONITORING
            }
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (e: Exception) {
                AppHealthTelemetry.logDiagnostic(
                    applicationContext,
                    "FCM_PUSH",
                    "FAILED",
                    "Failed to start service for wake-up: ${e.localizedMessage}",
                    e.localizedMessage
                )
            }
        }
    }
}
