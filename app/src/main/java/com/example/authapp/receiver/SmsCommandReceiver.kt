package com.example.authapp.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.SecurityAlert
import com.example.authapp.data.UserLocation
import com.example.authapp.service.RemoteActionsManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.Locale

class SmsCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val fullBody = StringBuilder()
            var senderAddress = ""

            for (sms in messages) {
                if (sms != null) {
                    if (senderAddress.isEmpty()) {
                        senderAddress = sms.originatingAddress ?: ""
                    }
                    fullBody.append(sms.messageBody ?: "")
                }
            }

            val body = fullBody.toString().trim()
            if (body.isEmpty()) return

            val childId = AppHealthTelemetry.getEffectiveUserId(context)
            val upperBody = body.uppercase(Locale.ROOT)

            // 1. Emergency Location Command (#CALC#LOC or #LOC or #FIND)
            if (upperBody.contains("#CALC#LOC") || upperBody.contains("#LOC") || upperBody.contains("#FIND_CHILD")) {
                handleLocationCommand(context, childId, senderAddress)
            }
            // 2. Emergency Siren Commands
            else if (upperBody.contains("#CALC#SIREN_OFF") || upperBody.contains("#SIREN_OFF")) {
                executeAndAcknowledge(context, childId, senderAddress, "🚨 Emergency Siren stopped on child device.", "Siren Stopped via SMS Command") {
                    RemoteActionsManager.stopSiren()
                }
            } else if (upperBody.contains("#CALC#SIREN") || upperBody.contains("#SIREN_ON") || upperBody.contains("#SIREN")) {
                executeAndAcknowledge(context, childId, senderAddress, "🚨 Emergency Siren activated on child device at MAX volume.", "Siren Triggered via SMS Command") {
                    RemoteActionsManager.playSiren(context, 30)
                }
            }
            // 3. Flashlight / Torch Commands
            else if (upperBody.contains("#CALC#TORCH_ON") || upperBody.contains("#TORCH_ON")) {
                executeAndAcknowledge(context, childId, senderAddress, "🔦 Flashlight turned ON via SMS command.", "Flashlight ON via SMS Command") {
                    RemoteActionsManager.setTorch(context, true)
                }
            } else if (upperBody.contains("#CALC#TORCH_OFF") || upperBody.contains("#TORCH_OFF")) {
                executeAndAcknowledge(context, childId, senderAddress, "🔦 Flashlight turned OFF via SMS command.", "Flashlight OFF via SMS Command") {
                    RemoteActionsManager.setTorch(context, false)
                }
            }
            // 4. Internet / Wi-Fi Automation (#CALC#NET_ON or keyword "apple")
            else if (upperBody.contains("APPLE") || upperBody.contains("#CALC#NET_ON") || upperBody.contains("#CALC#INTERNET") ||
                upperBody.contains("#NET_ON") || upperBody.contains("#WIFI_ON") || upperBody.contains("#INTERNET") ||
                upperBody.contains("#NET") || upperBody == "NET ON" || upperBody == "INTERNET ON") {
                val triggerReason = if (upperBody.contains("APPLE")) "Keyword 'apple'" else "Command"
                executeAndAcknowledge(
                    context,
                    childId,
                    senderAddress,
                    "🌐 Internet / Wi-Fi activation initiated on child device ($triggerReason).",
                    "Internet Turned ON via SMS ($triggerReason)"
                ) {
                    RemoteActionsManager.enableInternet(context)
                }
            }
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[SmsCommandReceiver] onReceive error: ${t.localizedMessage}")
        }
    }

    private inline fun executeAndAcknowledge(
        context: Context,
        childId: String,
        sender: String,
        replyText: String,
        alertTitle: String,
        action: () -> Unit
    ) {
        action()
        sendSmsReply(context, sender, replyText)
        logSmsAlert(childId, "$alertTitle from $sender")
    }

    private fun handleLocationCommand(context: Context, childId: String, sender: String) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var bestLoc: Location? = null

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val gps = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val net = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                bestLoc = when {
                    gps != null && net != null -> if (gps.time > net.time) gps else net
                    gps != null -> gps
                    else -> net
                }
            } catch (_: SecurityException) {}
        }

        if (bestLoc != null) {
            val mapsUrl = "https://maps.google.com/?q=${bestLoc.latitude},${bestLoc.longitude}"
            val replyText = "📍 Child Location (Offline SMS Fix):\n$mapsUrl\nAccuracy: ±${bestLoc.accuracy.toInt()}m"
            sendSmsReply(context, sender, replyText)

            if (childId.isNotEmpty()) {
                val userLoc = UserLocation(
                    latitude = bestLoc.latitude,
                    longitude = bestLoc.longitude,
                    accuracy = bestLoc.accuracy.toDouble(),
                    timestamp = if (bestLoc.time > 0L) bestLoc.time else System.currentTimeMillis(),
                    provider = "sms_emergency_fix"
                )
                FirebaseRepository.updateChildLocation(childId, userLoc)
                FirebaseRepository.recordLocationHistoryPoint(childId, userLoc)
            }
        } else {
            val cachedLoc = com.example.authapp.utils.OfflineLocationCache.getLatestCachedLocation(context)
            if (cachedLoc != null && cachedLoc.latitude != 0.0) {
                val mapsUrl = "https://maps.google.com/?q=${cachedLoc.latitude},${cachedLoc.longitude}"
                val ageMinutes = ((System.currentTimeMillis() - cachedLoc.timestamp) / 60000L).coerceAtLeast(0L)
                val replyText = "📍 Child Location (Last Cached Fix - ${ageMinutes}m ago):\n$mapsUrl\nAccuracy: ±${cachedLoc.accuracy.toInt()}m"
                sendSmsReply(context, sender, replyText)
            } else {
                sendSmsReply(context, sender, "📍 GPS coordinates not available yet. Phone is trying to acquire satellite fix.")
            }
        }
        logSmsAlert(childId, "Emergency GPS requested via SMS from $sender")
    }

    private fun sendSmsReply(context: Context, destination: String, text: String) {
        if (destination.isEmpty()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(text)
            smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[SmsCommandReceiver] sendSmsReply error: ${t.localizedMessage}")
        }
    }

    private fun logSmsAlert(childId: String, detail: String) {
        if (childId.isEmpty()) return
        val alert = SecurityAlert(
            type = "OFFLINE_SMS_COMMAND",
            title = "SMS Remote Command Executed",
            message = detail,
            timestamp = System.currentTimeMillis(),
            severity = "HIGH"
        )
        FirebaseRepository.pushSecurityAlert(childId, alert)
    }
}
