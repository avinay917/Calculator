package com.example.authapp.service

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics

object RemoteActionsManager {
    private var mediaPlayer: MediaPlayer? = null
    var isTorchOn = false
        private set
    var isSirenPlaying = false
        private set

    fun setTorch(context: Context, enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
                for (id in cameraManager.cameraIdList) {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraManager.setTorchMode(id, enable)
                        isTorchOn = enable
                        FirebaseCrashlytics.getInstance().log("[RemoteAction] Flashlight set to: $enable")
                        break
                    }
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[RemoteAction] Flashlight error: ${e.localizedMessage}")
            }
        }
    }

    fun playSiren(context: Context, durationSeconds: Int = 30) {
        stopSiren()
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )

            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            isSirenPlaying = true

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopSiren()
            }, (durationSeconds * 1000L).coerceIn(5000L, 120000L))

            FirebaseCrashlytics.getInstance().log("[RemoteAction] Emergency Siren triggered for $durationSeconds s")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[RemoteAction] Siren error: ${e.localizedMessage}")
        }
    }

    fun stopSiren() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isSirenPlaying = false
    }
}
