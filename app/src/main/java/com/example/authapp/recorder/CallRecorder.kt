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

            val sources = mutableListOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    add(MediaRecorder.AudioSource.UNPROCESSED)
                }
            }

            var started = false
            for (source in sources) {
                var testRecorder: MediaRecorder? = null
                try {
                    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                    testRecorder = recorder
                    recorder.setAudioSource(source)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioEncodingBitRate(128000)
                    recorder.setAudioSamplingRate(44100)
                    recorder.setOutputFile(file.absolutePath)
                    recorder.prepare()
                    recorder.start()
                    mediaRecorder = recorder
                    started = true
                    FirebaseCrashlytics.getInstance().log("[CallRecorder] Call recording started with source=$source for $phoneNumber")
                    break
                } catch (err: Exception) {
                    FirebaseCrashlytics.getInstance().log("[CallRecorder] Source $source failed: ${err.localizedMessage}")
                    try {
                        testRecorder?.release()
                    } catch (_: Exception) {}
                }
            }

            if (!started) {
                com.example.authapp.analytics.AppHealthTelemetry.logDiagnostic(
                    context,
                    "CALL_RECORDER",
                    "HARDWARE_BUSY",
                    "All audio sources (VOICE_COMMUNICATION, MIC, VOICE_RECOGNITION) failed for call recording. OEM dialer locked microphone.",
                    "IllegalStateException: All audio sources failed"
                )
                throw IllegalStateException("All audio sources failed for call recording")
            }

            currentOutputFile = file
            isRecording = true
            activeCallNumber = phoneNumber
            recordingStartTimeMillis = System.currentTimeMillis()
            com.example.authapp.analytics.AppHealthTelemetry.logDiagnostic(
                context,
                "CALL_RECORDER",
                "SUCCESS",
                "Call recorder running with file: ${file.name}"
            )
            file
        } catch (e: Exception) {
            com.example.authapp.analytics.AppHealthTelemetry.logDiagnostic(
                context,
                "CALL_RECORDER",
                "FAILED",
                "Failed to start call recording: ${e.localizedMessage}",
                e.localizedMessage
            )
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
                    val durationMs = if (recordingStartTimeMillis > 0L) System.currentTimeMillis() - recordingStartTimeMillis else 0L
                    if (durationMs > 500L) { // Only stop if recorded for more than 0.5s to prevent stop failed IllegalStateException
                        stop()
                    }
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
