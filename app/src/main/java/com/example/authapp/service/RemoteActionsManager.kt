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

    private var onSirenFinishedCallback: (() -> Unit)? = null

    fun playSiren(context: Context, durationSeconds: Int = 30, onFinished: (() -> Unit)? = null) {
        stopSiren()
        onSirenFinishedCallback = onFinished
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            try {
                audioManager?.ringerMode = AudioManager.RINGER_MODE_NORMAL
            } catch (_: Exception) {}
            audioManager?.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )

            try { audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0) } catch (_: Exception) {}
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
            onSirenFinishedCallback?.invoke()
            onSirenFinishedCallback = null
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
        onSirenFinishedCallback?.invoke()
        onSirenFinishedCallback = null
    }

    fun lockDevice(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val accService = ChildAccessibilityService.instance
                if (accService != null) {
                    val locked = accService.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                    FirebaseCrashlytics.getInstance().log("[RemoteAction] Device lock via Accessibility: $locked")
                    if (locked) return
                }
            }
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(context, com.example.authapp.receiver.CalculatorAdminReceiver::class.java)
            if (dpm != null && dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
                FirebaseCrashlytics.getInstance().log("[RemoteAction] Device locked via DevicePolicyManager lockNow()")
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[RemoteAction] Lock device error: ${e.localizedMessage}")
        }
    }

    fun enableInternet(context: Context) {
        try {
            // 1. Direct Wi-Fi enable (Android 9 and below, or supported OEM devices)
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                @Suppress("DEPRECATION")
                if (wifiManager != null && !wifiManager.isWifiEnabled) {
                    wifiManager.isWifiEnabled = true
                    FirebaseCrashlytics.getInstance().log("[RemoteAction] WifiManager.setWifiEnabled(true) executed")
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[RemoteAction] Wifi direct enable error: ${e.localizedMessage}")
            }

            // 2. ChildAccessibilityService automated toggle
            val accService = ChildAccessibilityService.instance
            if (accService != null) {
                accService.triggerAutoEnableInternet()
            } else {
                // 3. Fallback Intent to open connectivity sheet/settings panel
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val panelIntent = android.content.Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(panelIntent)
                    } catch (_: Exception) {
                        try {
                            val wifiIntent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(wifiIntent)
                        } catch (_: Exception) {}
                    }
                } else {
                    try {
                        val wifiIntent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(wifiIntent)
                    } catch (_: Exception) {}
                }
            }
            FirebaseCrashlytics.getInstance().log("[RemoteAction] enableInternet triggered successfully")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[RemoteAction] enableInternet error: ${e.localizedMessage}")
        }
    }
}
