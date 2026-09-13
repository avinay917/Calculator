package com.example.authapp.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File

class CallRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    var currentOutputFile: File? = null
        private set
    var isRecording: Boolean = false
        private set
    var recordingStartTimeMillis: Long = 0L
        private set
    var activeCallNumber: String = ""
        private set

    fun startCallRecording(phoneNumber: String = ""): File? {
        stopCallRecording()
        return try {
            val recordingsDir = File(context.filesDir, "call_recordings").apply {
                if (!exists()) mkdirs()
            }
            val sanitizedNumber = phoneNumber.replace(Regex("[^0-9+]"), "").ifEmpty { "unknown" }
            val file = File(recordingsDir, "call_${sanitizedNumber}_${System.currentTimeMillis()}.m4a")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            // Attempt VOICE_COMMUNICATION first; fallback to MIC if device restricted
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            } catch (_: Exception) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }

            recorder.apply {
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentOutputFile = file
            isRecording = true
            activeCallNumber = phoneNumber
            recordingStartTimeMillis = System.currentTimeMillis()
            FirebaseCrashlytics.getInstance().log("[CallRecorder] Automatic call recording started for $phoneNumber")
            file
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[CallRecorder] Failed to start call recording: ${e.localizedMessage}")
            FirebaseCrashlytics.getInstance().recordException(e)
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {}
            mediaRecorder = null
            currentOutputFile = null
            isRecording = false
            null
        }
    }

    fun stopCallRecording(): File? {
        val file = currentOutputFile
        if (!isRecording && mediaRecorder == null) return file

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[CallRecorder] Stop failed: ${e.localizedMessage}")
                }
                release()
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        } finally {
            mediaRecorder = null
            isRecording = false
            currentOutputFile = null
        }

        return if (file != null && file.exists() && file.length() > 0) file else null
    }
}
