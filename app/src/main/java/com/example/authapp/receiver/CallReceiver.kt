package com.example.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.recorder.CallRecorder
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
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
        if (number.isNotEmpty()) {
            incomingNumber = number
        }

        val recorder = getRecorder(context)

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                isIncoming = true
                lastState = TelephonyManager.EXTRA_STATE_RINGING
                FirebaseCrashlytics.getInstance().log("[CallReceiver] Phone RINGING from: $incomingNumber")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered or outgoing dialed
                if (lastState != TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    lastState = TelephonyManager.EXTRA_STATE_OFFHOOK
                    callStartTime = System.currentTimeMillis()
                    FirebaseCrashlytics.getInstance().log("[CallReceiver] Call OFFHOOK (Active). Starting recording...")
                    recorder.startCallRecording(incomingNumber)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call terminated
                if (lastState == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    lastState = TelephonyManager.EXTRA_STATE_IDLE
                    val durationSeconds = if (callStartTime > 0L) {
                        (System.currentTimeMillis() - callStartTime) / 1000
                    } else 0L

                    val recordedFile = recorder.stopCallRecording()
                    FirebaseCrashlytics.getInstance().log("[CallReceiver] Call IDLE (Ended). Recorded: ${recordedFile?.name}, duration: ${durationSeconds}s")

                    if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                        val currentUid = FirebaseRepository.currentUser?.uid
                        if (currentUid != null) {
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
                                    FirebaseCrashlytics.getInstance().log("[CallReceiver] Auto-uploaded call recording to Firebase Storage")
                                } catch (e: Exception) {
                                    FirebaseCrashlytics.getInstance().recordException(e)
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
