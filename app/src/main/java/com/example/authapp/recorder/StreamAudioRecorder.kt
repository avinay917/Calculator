package com.example.authapp.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File

/**
 * Handles physical audio and media recording on the device into real files
 * (.m4a / .mp4) in the app's files directory.
 */
class StreamAudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    var currentOutputFile: File? = null
        private set
    var isRecording: Boolean = false
        private set
    var recordingStartTimeMillis: Long = 0L
        private set

    fun startRecording(streamType: String): File? {
        stopRecording() // Clean up any active session first
        return try {
            val recordingsDir = File(context.filesDir, "recordings").apply {
                if (!exists()) mkdirs()
            }
            val extension = if (streamType.equals("video", ignoreCase = true)) "mp4" else "m4a"
            val file = File(recordingsDir, "rec_${streamType.lowercase()}_${System.currentTimeMillis()}.$extension")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(48000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentOutputFile = file
            isRecording = true
            recordingStartTimeMillis = System.currentTimeMillis()
            file
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[StreamAudioRecorder] Failed to start recording: ${e.localizedMessage}")
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

    fun stopRecording(): File? {
        val file = currentOutputFile
        if (!isRecording && mediaRecorder == null) return file

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[StreamAudioRecorder] Error on stop: ${e.localizedMessage}")
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
