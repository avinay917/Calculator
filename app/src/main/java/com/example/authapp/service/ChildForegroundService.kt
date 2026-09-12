package com.example.authapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.webrtc.WebRtcManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener

class ChildForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }

        when (intent?.action) {
            ACTION_START -> {
                val streamType = intent.getStringExtra(EXTRA_STREAM_TYPE) ?: "audio"
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification("Active Remote Stream ($streamType)"),
                    serviceType
                )
                startWebRtcStream(sessionId, streamType)
            }
            ACTION_START_MONITORING -> {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification("Background Protection Active"),
                    serviceType
                )
                startMonitoringStreamRequests()
            }
            ACTION_STOP -> {
                stopStream()
                stopSelf()
            }
            else -> {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification("Background Protection Active"),
                    serviceType
                )
                startMonitoringStreamRequests()
            }
        }
        return START_STICKY
    }

    private fun startMonitoringStreamRequests() {
        val uid = FirebaseRepository.currentUser?.uid ?: return
        if (streamRequestListener != null) return

        FirebaseCrashlytics.getInstance().log("[ChildService] Listening to RTDB stream requests for: $uid")
        streamRequestListener = FirebaseRepository.listenToStreamRequests(
            childId = uid,
            onRequested = { streamType, sessionId ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Stream request received over RTDB: $streamType, session: $sessionId")
                if (!isStreaming || currentSessionId != sessionId) {
                    val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    } else {
                        0
                    }
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Active Remote Stream ($streamType)"),
                        serviceType
                    )
                    startWebRtcStream(sessionId, streamType)
                }
            },
            onStopped = {
                FirebaseCrashlytics.getInstance().log("[ChildService] Remote stream stopped by Parent")
                stopStream()
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                } else {
                    0
                }
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification("Background Protection Active"),
                    serviceType
                )
            }
        )
    }

    private fun startWebRtcStream(sessionId: String, streamType: String) {
        if (isStreaming && currentSessionId == sessionId) return
        stopStream()

        currentSessionId = sessionId
        isStreaming = true
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
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
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
        val uid = FirebaseRepository.currentUser?.uid
        if (uid != null && streamRequestListener != null) {
            FirebaseRepository.removeStreamRequestListener(uid, streamRequestListener!!)
        }
        releaseWakeLock()
        super.onDestroy()
    }
}
