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
                    val lowerText = text.lowercase()
                    val lowerTitle = title.lowercase()
                    val type = when {
                        lowerText.contains("status") || lowerTitle.contains("status") -> "STATUS"
                        lowerText.contains("voice message") || lowerText.contains("audio") || lowerText.contains("audio message") -> "AUDIO"
                        lowerText.contains("photo") || lowerText.contains("image") -> "PHOTO"
                        lowerText.contains("video") -> "VIDEO"
                        else -> "CHAT"
                    }
                    val waItem = com.example.authapp.data.WhatsAppLogItem(
                        senderName = title,
                        messageText = text,
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
}
