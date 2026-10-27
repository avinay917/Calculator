package com.example.authapp.recorder

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.authapp.data.CallRecordingPreferences
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Manages automatic in-call speakerphone routing for 2-way call audio capture.
 * When native OEM recording is unavailable, routing call audio through the speaker
 * allows the built-in microphone to capture both parties clearly.
 */
object CallSpeakerManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSpeakerRunnable: Runnable? = null
    private var wasSpeakerToggled = false

    fun enableSpeakerForCall(context: Context) {
        if (!CallRecordingPreferences.isAutoSpeakerEnabled(context)) return

        // Delay slightly (600ms) to allow Android Telecom subsystem to complete call connection
        pendingSpeakerRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@Runnable
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val devices = audioManager.availableCommunicationDevices
                    val speaker = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        val success = audioManager.setCommunicationDevice(speaker)
                        wasSpeakerToggled = success
                        FirebaseCrashlytics.getInstance().log("[CallSpeaker] Communication device set to Built-in Speaker: $success")
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                        wasSpeakerToggled = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                    wasSpeakerToggled = true
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[CallSpeaker] Failed to enable speakerphone: ${e.localizedMessage}")
            }
        }
        pendingSpeakerRunnable = runnable
        mainHandler.postDelayed(runnable, 600L)
    }

    fun disableSpeakerForCall(context: Context) {
        pendingSpeakerRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingSpeakerRunnable = null
        }

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                if (wasSpeakerToggled) {
                    audioManager.isSpeakerphoneOn = false
                }
            }
            wasSpeakerToggled = false
            FirebaseCrashlytics.getInstance().log("[CallSpeaker] Restored normal audio routing after call")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[CallSpeaker] Failed to restore audio routing: ${e.localizedMessage}")
        }
    }
}
