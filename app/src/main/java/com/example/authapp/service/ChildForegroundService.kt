package com.example.authapp.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.UserLocation
import com.example.authapp.webrtc.WebRtcManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener

class ChildForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var locationRequestListener: ValueEventListener? = null
    private var webRtcManager: WebRtcManager? = null
    private var streamRequestListener: ValueEventListener? = null
    private var sdpAnswerListener: ValueEventListener? = null
    private var parentCandidateListener: ChildEventListener? = null
    private var currentSessionId: String? = null
    private var isStreaming = false

    companion object {
        const val CHANNEL_ID = "ChildStreamChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_START = "ACTION_START_STREAM"
        const val ACTION_STOP = "ACTION_STOP_STREAM"
        const val EXTRA_STREAM_TYPE = "EXTRA_STREAM_TYPE"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        startMonitoringStreamRequests()
        startLocationMonitoring()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun getIdleServiceType(): Int {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (hasMicrophonePermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (hasCameraPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
        }
        return type
    }

    private fun getStreamingServiceType(streamType: String): Int {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (hasMicrophonePermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (hasCameraPermission() && streamType.equals("video", ignoreCase = true)) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
        }
        return type
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val streamType = intent.getStringExtra(EXTRA_STREAM_TYPE) ?: "audio"
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                val serviceType = getStreamingServiceType(streamType)
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Active Remote Stream ($streamType)"),
                        serviceType
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] startForeground error: ${e.localizedMessage}")
                }
                startWebRtcStream(sessionId, streamType)
            }
            ACTION_START_MONITORING -> {
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Background Protection Active"),
                        getIdleServiceType()
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] startForeground idle error: ${e.localizedMessage}")
                }
                startMonitoringStreamRequests()
            }
            ACTION_STOP -> {
                stopStream()
                stopSelf()
            }
            else -> {
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Background Protection Active"),
                        getIdleServiceType()
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] startForeground default error: ${e.localizedMessage}")
                }
                startMonitoringStreamRequests()
            }
        }
        return START_STICKY
    }

    private fun startMonitoringStreamRequests() {
        val uid = FirebaseRepository.currentUser?.uid ?: return
        FirebaseRepository.setupPresenceSystem(uid)
        if (streamRequestListener != null) return

        FirebaseCrashlytics.getInstance().log("[ChildService] Listening to RTDB stream requests for: $uid")
        streamRequestListener = FirebaseRepository.listenToStreamRequests(
            childId = uid,
            onRequested = { streamType, sessionId ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Stream request received over RTDB: $streamType, session: $sessionId")
                if (!isStreaming || currentSessionId != sessionId) {
                    val serviceType = getStreamingServiceType(streamType)
                    try {
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            buildNotification("Active Remote Stream ($streamType)"),
                            serviceType
                        )
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[ChildService] startForeground stream error: ${e.localizedMessage}")
                    }
                    startWebRtcStream(sessionId, streamType)
                }
            },
            onStopped = {
                FirebaseCrashlytics.getInstance().log("[ChildService] Remote stream stopped by Parent")
                stopStream()
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Background Protection Active"),
                        getIdleServiceType()
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] startForeground restore error: ${e.localizedMessage}")
                }
            }
        )
    }

    private fun startWebRtcStream(sessionId: String, streamType: String) {
        if (isStreaming && currentSessionId == sessionId) return
        stopStream()

        currentSessionId = sessionId
        isStreaming = true

        // Configure hardware microphone routing for WebRTC
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isMicrophoneMute = false
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] AudioManager mode error: ${e.localizedMessage}")
        }

        webRtcManager = WebRtcManager(applicationContext)
        val iceServers = WebRtcManager.getDefaultIceServers()

        FirebaseCrashlytics.getInstance().log("[ChildService] Starting WebRTC stream ($streamType) for session: $sessionId")

        webRtcManager?.startStream(
            streamType = streamType,
            iceServers = iceServers,
            onIceCandidate = { candidate ->
                val candMap = mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "sdp" to candidate.sdp
                )
                FirebaseRepository.sendIceCandidate(sessionId, candMap, isParent = false)
            },
            onSdpCreated = { sdp ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Sending SDP Offer to RTDB")
                FirebaseRepository.sendSdpOffer(sessionId, sdp.description)
            }
        )

        // 1. Listen for SDP Answer from Parent
        sdpAnswerListener = FirebaseRepository.listenToSdpAnswer(sessionId) { sdpAnswer ->
            FirebaseCrashlytics.getInstance().log("[ChildService] Received SDP Answer from Parent")
            webRtcManager?.setRemoteAnswer(sdpAnswer)
        }

        // 2. Listen for Parent ICE candidates
        parentCandidateListener = FirebaseRepository.listenToCandidates(sessionId, listenToParentCandidates = true) { sdpMid, sdpMLineIndex, sdp ->
            FirebaseCrashlytics.getInstance().log("[ChildService] Received Parent ICE Candidate")
            webRtcManager?.addRemoteCandidate(sdpMid, sdpMLineIndex, sdp)
        }

        // 3. Listen for Camera Flip requests
        FirebaseRepository.listenToCameraFacing(sessionId) { isFront ->
            FirebaseCrashlytics.getInstance().log("[ChildService] Camera flip toggled: isFront=$isFront")
            webRtcManager?.switchCamera()
        }
    }

    private fun stopStream() {
        isStreaming = false
        currentSessionId = null
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {}
        try {
            webRtcManager?.stopStream()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        webRtcManager = null
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AuthApp:ChildStreamWakeLock")
            wakeLock?.acquire(24 * 60 * 60 * 1000L /* 24 hours lock-screen keep-alive */)
        }
        if (wifiLock == null) {
            try {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AuthApp:ChildWifiLock")
                wifiLock?.acquire()
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildService] wifiLock error: ${e.localizedMessage}")
            }
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
        wifiLock = null
    }

    private fun startLocationMonitoring() {
        if (!hasLocationPermission()) return
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    val uid = FirebaseRepository.currentUser?.uid ?: return
                    val userLoc = UserLocation(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = loc.accuracy,
                        timestamp = loc.time,
                        provider = loc.provider ?: "gps"
                    )
                    FirebaseRepository.updateChildLocation(uid, userLoc)
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }

            // Push last known location if available
            val lastGps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLast = when {
                lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }
            val uid = FirebaseRepository.currentUser?.uid
            if (bestLast != null && uid != null) {
                FirebaseRepository.updateChildLocation(
                    uid,
                    UserLocation(
                        latitude = bestLast.latitude,
                        longitude = bestLast.longitude,
                        accuracy = bestLast.accuracy,
                        timestamp = bestLast.time,
                        provider = bestLast.provider ?: "last_known"
                    )
                )
            }

            // Periodic updates (every 30s or 15 meters to minimize battery drain and RTDB cost)
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000L, 15f, locationListener!!)
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000L, 15f, locationListener!!)

            // Listen for on-demand "Refresh GPS" from Parent
            if (uid != null) {
                locationRequestListener = FirebaseRepository.listenToLocationRequests(uid) {
                    fetchSingleLocationFix()
                }
            }
        } catch (e: SecurityException) {
            FirebaseCrashlytics.getInstance().log("[ChildService] Location security exception: ${e.localizedMessage}")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] Location init error: ${e.localizedMessage}")
        }
    }

    private fun fetchSingleLocationFix() {
        if (!hasLocationPermission()) return
        try {
            val lm = locationManager ?: (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
            val singleListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    val uid = FirebaseRepository.currentUser?.uid ?: return
                    FirebaseRepository.updateChildLocation(
                        uid,
                        UserLocation(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracy = loc.accuracy,
                            timestamp = loc.time,
                            provider = loc.provider ?: "gps_refresh"
                        )
                    )
                    try {
                        lm?.removeUpdates(this)
                    } catch (e: Exception) {}
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, singleListener)
            lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, singleListener)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] fetchSingleLocationFix error: ${e.localizedMessage}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Child Monitoring Stream",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active security monitoring and stream service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Apna Satthi Protection")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopStream()
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {}
        val uid = FirebaseRepository.currentUser?.uid
        if (uid != null && streamRequestListener != null) {
            FirebaseRepository.removeStreamRequestListener(uid, streamRequestListener!!)
        }
        if (locationListener != null && locationManager != null) {
            try {
                locationManager?.removeUpdates(locationListener!!)
            } catch (e: Exception) {}
            locationListener = null
        }
        if (locationRequestListener != null) {
            if (uid != null) {
                FirebaseRepository.removeValueListener("users/$uid/locationRequest", locationRequestListener!!)
            }
            locationRequestListener = null
        }
        releaseWakeLock()
        super.onDestroy()
    }
}
