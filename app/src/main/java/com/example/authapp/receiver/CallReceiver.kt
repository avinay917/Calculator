package com.example.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.CallRecordingPreferences
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.recorder.CallRecorder
import com.example.authapp.service.ChildForegroundService
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var callRecorder: CallRecorder? = null
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var incomingNumber: String = ""
        private var isIncoming: Boolean = false
        private var callStartTime: Long = 0L
        private var lastLoggedSystemCallId: String = ""

        fun getRecorder(context: Context): CallRecorder {
            if (callRecorder == null) {
                callRecorder = CallRecorder(context.applicationContext)
            }
            return callRecorder ?: error("CallRecorder initialization failed")
        }

        @JvmStatic
        fun resetStateForTesting() {
            callRecorder = null
            lastState = TelephonyManager.EXTRA_STATE_IDLE
            incomingNumber = ""
            isIncoming = false
            callStartTime = 0L
            lastLoggedSystemCallId = ""
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val outNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: ""
            if (outNumber.isNotEmpty()) {
                incomingNumber = outNumber
                isIncoming = false
                AppHealthTelemetry.logDiagnostic(
                    context,
                    "CALL_MONITORING",
                    "OUTGOING_DIAL",
                    "Outgoing phone call dialed to: $outNumber"
                )
            }
            return
        }

        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        // Do NOT record calls or touch mic if no user is logged in
        val currentUid = AppPreferences.getUserId(context)
        if (FirebaseRepository.currentUser == null && currentUid.isEmpty()) {
            return
        }

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        var number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        if (number.isEmpty()) {
            number = resolveLastCallNumber(context)
        }
        if (number.isNotEmpty()) {
            incomingNumber = number
        }

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                isIncoming = true
                lastState = TelephonyManager.EXTRA_STATE_RINGING
                AppHealthTelemetry.logDiagnostic(
                    context,
                    "CALL_MONITORING",
                    "RINGING",
                    "Phone ringing: $incomingNumber"
                )
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered or outgoing dialed
                if (lastState != TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    lastState = TelephonyManager.EXTRA_STATE_OFFHOOK
                    callStartTime = System.currentTimeMillis()
                    AppHealthTelemetry.logDiagnostic(
                        context,
                        "CALL_MONITORING",
                        "OFFHOOK",
                        "Call active (connected) for: $incomingNumber"
                    )

                    if (CallRecordingPreferences.isEnabled(context)) {
                        val serviceIntent = Intent(context, ChildForegroundService::class.java).apply {
                            action = ChildForegroundService.ACTION_START_CALL_RECORDING
                            putExtra(ChildForegroundService.EXTRA_PHONE_NUMBER, incomingNumber)
                        }
                        try {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } catch (e: Exception) {
                            AppHealthTelemetry.logDiagnostic(
                                context,
                                "CALL_MONITORING",
                                "FAILED",
                                "Foreground service start error, fallback to direct recorder: ${e.localizedMessage}",
                                e.localizedMessage
                            )
                            getRecorder(context).startCallRecording(incomingNumber)
                        }
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call terminated. The system CallLog is authoritative and also
                // contains missed calls that never reached OFFHOOK.
                val wasAnswered = lastState == TelephonyManager.EXTRA_STATE_OFFHOOK
                val wasRinging = lastState == TelephonyManager.EXTRA_STATE_RINGING
                if (wasAnswered || wasRinging) {
                    val durationSeconds = if (callStartTime > 0L) {
                        (System.currentTimeMillis() - callStartTime) / 1000
                    } else 0L
                    val currentUid = AppHealthTelemetry.getEffectiveUserId(context)
                    if (currentUid.isNotEmpty()) {
                        syncLatestSystemCallLog(
                            context = context,
                            childId = currentUid,
                            fallbackNumber = incomingNumber,
                            fallbackType = if (wasRinging) "MISSED" else if (isIncoming) "INCOMING" else "OUTGOING",
                            fallbackTimestamp = if (callStartTime > 0L) callStartTime else System.currentTimeMillis(),
                            fallbackDurationSeconds = durationSeconds
                        )
                    }

                    if (wasAnswered) {
                    lastState = TelephonyManager.EXTRA_STATE_IDLE
                    AppHealthTelemetry.logDiagnostic(
                        context,
                        "CALL_MONITORING",
                        "ENDED",
                        "Call finished with duration: ${durationSeconds}s"
                    )

                    val serviceIntent = Intent(context, ChildForegroundService::class.java).apply {
                        action = ChildForegroundService.ACTION_STOP_CALL_RECORDING
                    }
                    try {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } catch (e: Exception) {
                        val recordedFile = getRecorder(context).stopCallRecording()
                        if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                            if (currentUid.isNotEmpty()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val fileUri = Uri.fromFile(recordedFile)
                                        FirebaseRepository.uploadRecordingFile(
                                            childId = currentUid,
                                            fileUri = fileUri,
                                            streamType = "call",
                                            durationSeconds = durationSeconds,
                                            localFilePath = recordedFile.absolutePath,
                                            onSuccess = { session ->
                                                if (session.storageUrl.isNotEmpty()) {
                                                    FirebaseRepository.attachRecordingUrlToLatestCallLog(currentUid, session.storageUrl)
                                                }
                                            }
                                        )
                                    } catch (err: Exception) {
                                        AppHealthTelemetry.logDiagnostic(
                                            context,
                                            "CALL_MONITORING",
                                            "FAILED",
                                            "Direct call upload failed: ${err.localizedMessage}",
                                            err.localizedMessage
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                if (lastState != TelephonyManager.EXTRA_STATE_IDLE) {
                    lastState = TelephonyManager.EXTRA_STATE_IDLE
                }
                incomingNumber = ""
                isIncoming = false
                callStartTime = 0L
            }
        }
    }

    private fun syncLatestSystemCallLog(
        context: Context,
        childId: String,
        fallbackNumber: String,
        fallbackType: String,
        fallbackTimestamp: Long,
        fallbackDurationSeconds: Long
    ) {
        var synced = false
        try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                context.contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        android.provider.CallLog.Calls._ID,
                        android.provider.CallLog.Calls.NUMBER,
                        android.provider.CallLog.Calls.CACHED_NAME,
                        android.provider.CallLog.Calls.TYPE,
                        android.provider.CallLog.Calls.DATE,
                        android.provider.CallLog.Calls.DURATION
                    ),
                    null,
                    null,
                    "${android.provider.CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.CallLog.Calls._ID))
                        if (id != lastLoggedSystemCallId) {
                            val number = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.CallLog.Calls.NUMBER)).orEmpty()
                            val name = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.CallLog.Calls.CACHED_NAME)).orEmpty()
                            val typeValue = cursor.getInt(cursor.getColumnIndexOrThrow(android.provider.CallLog.Calls.TYPE))
                            val type = when (typeValue) {
                                android.provider.CallLog.Calls.MISSED_TYPE -> "MISSED"
                                android.provider.CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                                android.provider.CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                                else -> "INCOMING"
                            }
                            val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.CallLog.Calls.DATE))
                            val duration = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.CallLog.Calls.DURATION))
                            FirebaseRepository.logSingleCallLog(
                                childId,
                                com.example.authapp.data.CallLogItem(
                                    id = id,
                                    number = number.ifEmpty { fallbackNumber },
                                    name = name.ifEmpty { resolveContactName(context, number.ifEmpty { fallbackNumber }) },
                                    type = type,
                                    timestamp = timestamp,
                                    durationSeconds = duration
                                )
                            )
                            lastLoggedSystemCallId = id
                            synced = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        if (!synced && fallbackNumber.isNotEmpty()) {
            FirebaseRepository.logSingleCallLog(
                childId,
                com.example.authapp.data.CallLogItem(
                    number = fallbackNumber,
                    name = resolveContactName(context, fallbackNumber),
                    type = fallbackType,
                    timestamp = fallbackTimestamp,
                    durationSeconds = fallbackDurationSeconds
                )
            )
        }
    }

    private fun resolveContactName(context: Context, number: String): String {
        if (number.isEmpty()) return ""
        try {
            val uri = Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx) ?: ""
                }
            }
        } catch (e: Exception) {}
        return ""
    }

    private fun resolveLastCallNumber(context: Context): String {
        try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val cursor = context.contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    arrayOf(android.provider.CallLog.Calls.NUMBER),
                    null, null,
                    "${android.provider.CallLog.Calls.DATE} DESC"
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val numIdx = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                        if (numIdx >= 0) return it.getString(numIdx) ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }
}
