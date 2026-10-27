package com.example.authapp.ui.activity

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.example.authapp.data.FirebaseControlRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LiveStreamLocalRecorder(
    private val context: Context,
    private val childId: String,
    private val childName: String
) {
    val isRecordingState = mutableStateOf(false)
    val recordingDurationSecondsState = mutableLongStateOf(0L)

    private var parentMediaRecorder: MediaRecorder? = null
    private var parentRecordingFile: File? = null
    private var recordingHandler: Handler? = null
    private var recordingRunnable: Runnable? = null

    fun toggleRecording(streamTypeParam: String) {
        if (isRecordingState.value) {
            stopRecordingTimer()
            stopParentLocalRecording()
            FirebaseControlRepository.requestRemoteRecording(childId, false, streamTypeParam.lowercase())
            Toast.makeText(context, "Recording stopped & saved to Phone storage!", Toast.LENGTH_SHORT).show()
        } else {
            startRecordingTimer()
            startParentLocalRecording()
            FirebaseControlRepository.requestRemoteRecording(childId, true, streamTypeParam.lowercase())
            Toast.makeText(context, "Recording started (Saving directly to Phone)...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startParentLocalRecording() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(downloadsDir, "LiveRec_${childName.replace(" ", "_")}_$timeStamp.m4a")
            parentRecordingFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            parentMediaRecorder = recorder
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[LiveStreamLocalRecorder] Parent local recorder start notice: ${e.localizedMessage}")
        }
    }

    fun stopParentLocalRecording() {
        try {
            parentMediaRecorder?.stop()
            parentMediaRecorder?.release()
            parentMediaRecorder = null
            parentRecordingFile?.let { f ->
                if (f.exists() && f.length() > 0) {
                    Toast.makeText(context, "Saved to Phone: ${f.name}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[LiveStreamLocalRecorder] Stop parent local recorder error: ${e.localizedMessage}")
            try {
                parentMediaRecorder?.release()
                parentMediaRecorder = null
            } catch (_: Exception) {}
        }
    }

    private fun startRecordingTimer() {
        isRecordingState.value = true
        recordingDurationSecondsState.longValue = 0L
        recordingHandler?.removeCallbacksAndMessages(null)
        recordingHandler = Handler(Looper.getMainLooper())
        recordingRunnable = object : Runnable {
            override fun run() {
                if (isRecordingState.value) {
                    recordingDurationSecondsState.longValue += 1
                    recordingHandler?.postDelayed(this, 1000L)
                }
            }
        }
        recordingRunnable?.let { recordingHandler?.post(it) }
    }

    fun stopRecordingTimer() {
        isRecordingState.value = false
        recordingDurationSecondsState.longValue = 0L
        recordingHandler?.removeCallbacksAndMessages(null)
        recordingHandler = null
        recordingRunnable = null
    }

    fun cleanup() {
        stopRecordingTimer()
        stopParentLocalRecording()
    }
}
