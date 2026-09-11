package com.example.authapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.webrtc.WebRtcManager
import org.webrtc.PeerConnection

class ChildForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var webRtcManager: WebRtcManager? = null

    companion object {
        const val CHANNEL_ID = "ChildStreamChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_STREAM"
        const val ACTION_STOP = "ACTION_STOP_STREAM"
        const val EXTRA_STREAM_TYPE = "EXTRA_STREAM_TYPE"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val streamType = intent.getStringExtra(EXTRA_STREAM_TYPE) ?: "audio"
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""

                startForeground(NOTIFICATION_ID, buildNotification("Active Security Stream ($streamType)"))
                startWebRtcStream(sessionId, streamType)
            }
            ACTION_STOP -> {
                stopStream()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startWebRtcStream(sessionId: String, streamType: String) {
        webRtcManager = WebRtcManager(applicationContext)
        val stunServer = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()

        webRtcManager?.startAudioStream(
            iceServers = listOf(stunServer),
            onIceCandidate = { candidate ->
                val candMap = mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "sdp" to candidate.sdp
                )
                FirebaseRepository.sendIceCandidate(sessionId, candMap, isParent = false)
            },
            onSdpCreated = { sdp ->
                FirebaseRepository.sendSdpOffer(sessionId, sdp.description)
            }
        )
    }

    private fun stopStream() {
        webRtcManager?.stopStream()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AuthApp:ChildStreamWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes timeout */)
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Child Monitoring Stream",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active remote stream notification"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Parent Monitoring Active")
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
        super.onDestroy()
    }
}
