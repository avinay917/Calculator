package com.example.authapp.webrtc

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.PeerConnection
import org.webrtc.audio.JavaAudioDeviceModule

object WebRtcAudioHelper {

    fun createAudioRecordErrorCallback(appContext: Context): JavaAudioDeviceModule.AudioRecordErrorCallback {
        return object : JavaAudioDeviceModule.AudioRecordErrorCallback {
            override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Audio Record Init Error] $errorMessage")
                com.example.authapp.analytics.AppHealthTelemetry.logDiagnostic(
                    appContext, "LIVE_AUDIO", "FAILED", "Audio Record Init Error: $errorMessage"
                )
            }
            override fun onWebRtcAudioRecordStartError(
                errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                errorMessage: String?
            ) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Audio Record Start Error] Code: $errorCode, Msg: $errorMessage")
                com.example.authapp.analytics.AppHealthTelemetry.logDiagnostic(
                    appContext, "LIVE_AUDIO", "FAILED", "Mic start error ($errorCode): $errorMessage"
                )
            }
            override fun onWebRtcAudioRecordError(errorMessage: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Audio Record Runtime Error] $errorMessage")
            }
        }
    }

    fun enableHardwareAudioEffects(
        appContext: Context,
        audioDeviceModule: JavaAudioDeviceModule?
    ): Pair<AutomaticGainControl?, NoiseSuppressor?> {
        var hardwareAgc: AutomaticGainControl? = null
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val sessionId = getAudioSessionIdFromAdm(audioDeviceModule) ?: audioManager?.generateAudioSessionId()
            if (sessionId != null && sessionId != 0) {
                if (AutomaticGainControl.isAvailable()) {
                    try {
                        hardwareAgc = AutomaticGainControl.create(sessionId)?.apply {
                            enabled = true
                        }
                        FirebaseCrashlytics.getInstance().log("[WebRTC] Hardware AutomaticGainControl enabled (sessionId=$sessionId)")
                    } catch (e: Throwable) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC] Hardware AGC error: ${e.localizedMessage}")
                    }
                }
            }
            try {
                audioDeviceModule?.setNoiseSuppressorEnabled(false)
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] enableHardwareAudioEffects notice: ${e.localizedMessage}")
        }
        return Pair(hardwareAgc, null)
    }

    private fun getAudioSessionIdFromAdm(audioDeviceModule: JavaAudioDeviceModule?): Int? {
        return try {
            val audioInputField = audioDeviceModule?.javaClass?.getDeclaredField("audioInput")
            audioInputField?.isAccessible = true
            val audioInput = audioInputField?.get(audioDeviceModule)
            val audioRecordField = audioInput?.javaClass?.getDeclaredField("audioRecord")
            audioRecordField?.isAccessible = true
            val audioRecord = audioRecordField?.get(audioInput) as? android.media.AudioRecord
            audioRecord?.audioSessionId
        } catch (_: Throwable) {
            null
        }
    }

    fun parseAudioLevelFromStats(report: org.webrtc.RTCStatsReport): Float? {
        for (stats in report.statsMap.values) {
            if (stats.type == "inbound-rtp") {
                val kind = stats.members["kind"] ?: stats.members["mediaType"]
                if (kind == "audio" || stats.id.contains("audio", ignoreCase = true)) {
                    val levelObj = stats.members["audioLevel"]
                    if (levelObj != null) {
                        val rawLevel = when (levelObj) {
                            is Number -> levelObj.toFloat()
                            else -> levelObj.toString().toFloatOrNull() ?: 0f
                        }
                        return rawLevel.coerceIn(0f, 1f)
                    }
                }
            }
        }
        return null
    }

    fun createLoudnessEnhancer(audioSessionId: Int, gainMilliBel: Int = 1200): LoudnessEnhancer? {
        return try {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(gainMilliBel.coerceIn(0, 3000))
                enabled = true
            }
        } catch (_: Throwable) {
            null
        }
    }
}
