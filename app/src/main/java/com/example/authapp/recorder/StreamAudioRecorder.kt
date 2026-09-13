package com.example.authapp.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File

/**
 * Handles physical audio and media recording on the device into real files
 * (.m4a) in the app's files directory with robust hardware source fallbacks.
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
        val recordingsDir = File(context.filesDir, "recordings").apply {
            if (!exists()) mkdirs()
        }
        val file = File(recordingsDir, "rec_${streamType.lowercase()}_${System.currentTimeMillis()}.m4a")

        val audioSources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )

        for (source in audioSources) {
            var recorder: MediaRecorder? = null
            try {
                recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                recorder.apply {
                    setAudioSource(source)
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
                FirebaseCrashlytics.getInstance().log("[StreamAudioRecorder] Recording started with source: $source")
                return file
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[StreamAudioRecorder] Source $source failed: ${e.localizedMessage}")
                try {
                    recorder?.release()
                } catch (_: Exception) {}
            }
        }

        FirebaseCrashlytics.getInstance().log("[StreamAudioRecorder] All audio sources failed to start recording")
        mediaRecorder = null
        currentOutputFile = null
        isRecording = false
        return null
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
