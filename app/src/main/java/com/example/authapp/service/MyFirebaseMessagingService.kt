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
        val type = data["type"]
        val facing = data["facing"] ?: "back"
        val command = data["command"] ?: ""
        val commandValue = data["value"] ?: "true"
        val streamType = data["streamType"] ?: "audio"
        val sessionId = data["sessionId"] ?: ""

        AppHealthTelemetry.logDiagnostic(
            applicationContext,
            "FCM_PUSH",
            "RECEIVED",
            "FCM wake-up payload received: action=$action, type=$type, streamType=$streamType, session=$sessionId"
        )

        when {
            action == "START_STREAM" -> dispatchChildService(ChildForegroundService.ACTION_START) {
                putExtra(ChildForegroundService.EXTRA_STREAM_TYPE, streamType)
                putExtra(ChildForegroundService.EXTRA_SESSION_ID, sessionId)
            }
            action == "STOP_STREAM" -> dispatchChildService(ChildForegroundService.ACTION_STOP_STREAM)
            action == "WAKEUP" && type == "SNAPSHOT" -> dispatchChildService(ChildForegroundService.ACTION_TAKE_SNAPSHOT) {
                putExtra(ChildForegroundService.EXTRA_CAMERA_FACING, facing)
            }
            action == "WAKEUP" && type == "COMMAND" -> dispatchChildService(ChildForegroundService.ACTION_EXECUTE_COMMAND) {
                putExtra(ChildForegroundService.EXTRA_COMMAND, command)
                putExtra(ChildForegroundService.EXTRA_COMMAND_VALUE, commandValue)
            }
            action == "REFRESH_LOCATION" || action == "WAKEUP" -> dispatchChildService(ChildForegroundService.ACTION_START_MONITORING)
            action == "SECURITY_ALERT" -> {
                val alertType = data["alertType"] ?: ""
                if (alertType != "CHARGER_CONNECTED" && alertType != "CHARGER_DISCONNECTED") {
                    val alertTitle = data["title"] ?: "🚨 Security Alert"
                    val alertMessage = data["message"] ?: "Security event detected on child device"
                    showSecurityAlertNotification(alertTitle, alertMessage)
                }
            }
        }
    }

    private fun dispatchChildService(action: String, configure: (Intent.() -> Unit)? = null) {
        val intent = Intent(this, ChildForegroundService::class.java).apply {
            this.action = action
            configure?.invoke(this)
        }
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "FCM_PUSH",
                "FAILED",
                "Failed to dispatch service action $action: ${e.localizedMessage}",
                e.localizedMessage
            )
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun showSecurityAlertNotification(title: String, message: String) {
        try {
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            val channelId = "security_alerts_channel"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Security Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for SIM swap, low battery, and emergency events"
                    enableLights(true)
                    lightColor = android.graphics.Color.RED
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                (System.currentTimeMillis() % 10000).toInt(),
                launchIntent,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(com.example.authapp.R.mipmap.ic_launcher)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
