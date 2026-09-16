package com.example.authapp.ui.activity

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.authapp.analytics.AppAnalytics
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.audio.AudioOutputRoute
import com.example.authapp.audio.AudioRouteManager
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.ui.components.LiveStreamDialog
import com.example.authapp.theme.AuthAppTheme
import com.example.authapp.webrtc.WebRtcManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack

class LiveStreamActivity : ComponentActivity() {

    private var sessionId: String = ""
    private var childId: String = ""
    private var childName: String = "Child Device"
    private var streamType: String = "video" // "video" or "audio"

    private lateinit var audioRouteManager: AudioRouteManager
    private var webRtcManager: WebRtcManager? = null
    private var remoteVideoTrackState = mutableStateOf<VideoTrack?>(null)
    private var remoteAudioTrackState = mutableStateOf<AudioTrack?>(null)
    private var streamStatusTextState = mutableStateOf("Connecting to child device...")
    private var isFrontCameraState = mutableStateOf(true)
    private var isRecordingState = mutableStateOf(false)
    private var recordingDurationSecondsState = mutableLongStateOf(0L)
    private var audioSensitivityState = mutableFloatStateOf(100f)
    private var liveAudioLevelState = mutableFloatStateOf(0f)

    private var sdpOfferListener: com.google.firebase.database.ValueEventListener? = null
    private var candidateListener: com.google.firebase.database.ChildEventListener? = null
    private val candidateBuffer = mutableListOf<Map<String, Any>>()
    private val candidateHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recordingHandler: Handler? = null
    private var recordingRunnable: Runnable? = null

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            audioRouteManager.checkAndAutoRoute()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
        childId = intent.getStringExtra(EXTRA_CHILD_ID) ?: ""
        childName = intent.getStringExtra(EXTRA_CHILD_NAME) ?: "Child Device"
        streamType = intent.getStringExtra(EXTRA_STREAM_TYPE) ?: "video"

        if (sessionId.isEmpty() || childId.isEmpty()) {
            Toast.makeText(this, "Invalid live stream session", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        audioRouteManager = AudioRouteManager(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!audioRouteManager.hasBluetoothPermission()) {
                bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        initWebRtcReceiver()

        setContent {
            AuthAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentAudioRoute by audioRouteManager.currentRoute.collectAsState()
                    val isBluetoothConnected by audioRouteManager.isBluetoothConnected.collectAsState()
                    val isHeadsetConnected by audioRouteManager.isHeadsetConnected.collectAsState()

                    LiveStreamDialog(
                        activeStreamType = if (streamType.equals("video", ignoreCase = true)) "Video" else "Audio",
                        activeChildName = childName,
                        streamStatusText = streamStatusTextState.value,
                        isFrontCamera = isFrontCameraState.value,
                        isRecording = isRecordingState.value,
                        recordingDurationSeconds = recordingDurationSecondsState.longValue,
                        audioSensitivity = audioSensitivityState.floatValue,
                        remoteVideoTrack = remoteVideoTrackState.value,
                        webRtcManager = webRtcManager,
                        audioLevel = liveAudioLevelState.floatValue,
                        onDismiss = {
                            stopAndFinishStream()
                        },
                        onFlipCamera = {
                            toggleCameraFacing()
                        },
                        onToggleRecording = { type ->
                            toggleRecording(type)
                        },
                        onSensitivityChange = { sensitivity ->
                            audioSensitivityState.floatValue = sensitivity.coerceIn(10f, 100f)
                            remoteAudioTrackState.value?.setVolume(
                                WebRtcManager.calculateSuperBoostGain(audioSensitivityState.floatValue)
                            )
                        },
                        currentAudioRoute = currentAudioRoute,
                        isBluetoothConnected = isBluetoothConnected,
                        isHeadsetConnected = isHeadsetConnected,
                        onAudioRouteSelect = { route ->
                            audioRouteManager.setRoute(route)
                        }
                    )
                }
            }
        }
    }

    private fun initWebRtcReceiver() {
        try {
            streamStatusTextState.value = "Connecting to child device..."
            FirebaseCrashlytics.getInstance().log("[LiveStreamActivity] Initializing WebRtcManager receiver for session: $sessionId")

            val manager = WebRtcManager(applicationContext, isReceiverOnly = true)
            webRtcManager = manager

            try {
                val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[LiveStreamActivity] AudioManager init error: ${e.localizedMessage}")
            }

            FirebaseRepository.fetchIceServers { iceServers ->
                try {
                    audioRouteManager.checkAndAutoRoute()
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[LiveStreamActivity] audioRouteManager error: ${e.localizedMessage}")
                }

                manager.startReceiver(
                    iceServers = iceServers,
                    onIceCandidate = { candidate ->
                        try {
                            val candMap = mapOf(
                                "sdpMid" to candidate.sdpMid,
                                "sdpMLineIndex" to candidate.sdpMLineIndex,
                                "sdp" to candidate.sdp
                            )
                            synchronized(candidateBuffer) {
                                candidateBuffer.add(candMap)
                            }
                            candidateHandler.removeCallbacksAndMessages(null)
                            candidateHandler.postDelayed({
                                val batch = synchronized(candidateBuffer) {
                                    val list = candidateBuffer.toList()
                                    candidateBuffer.clear()
                                    list
                                }
                                if (batch.isNotEmpty()) {
                                    FirebaseRepository.sendIceCandidatesBatch(sessionId, batch, isParent = true)
                                }
                            }, 500L)
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().recordException(e)
                        }
                    },
                    onRemoteVideoTrack = { track ->
                        mainHandler.post {
                            streamStatusTextState.value = "Live Video Streaming 🟢"
                            remoteVideoTrackState.value = track
                            AppAnalytics.logFeatureUsage("video_cast", "connected")
                        }
                    },
                    onRemoteAudioTrack = { track ->
                        mainHandler.post {
                            if (streamType.equals("audio", ignoreCase = true)) {
                                streamStatusTextState.value = "Live Audio Streaming 🟢"
                            }
                            remoteAudioTrackState.value = track
                            try {
                                track.setEnabled(true)
                                track.setVolume(WebRtcManager.calculateSuperBoostGain(audioSensitivityState.floatValue))
                                if (!audioRouteManager.isBluetoothConnected.value) {
                                    audioRouteManager.setRoute(AudioOutputRoute.SPEAKER)
                                }
                            } catch (e: Exception) {
                                FirebaseCrashlytics.getInstance().recordException(e)
                            }
                            AppAnalytics.logFeatureUsage("audio_cast", "connected")
                        }
                    }
                )

                manager.startAudioLevelMonitoring { level ->
                    mainHandler.post {
                        liveAudioLevelState.floatValue = level
                    }
                }

                // Listen for SDP Offer from Child
                sdpOfferListener = FirebaseRepository.listenToSdpOffer(sessionId) { sdpOffer ->
                    mainHandler.post {
                        streamStatusTextState.value = "Child Offer Received, establishing connection..."
                    }
                    try {
                        manager.setRemoteOfferAndCreateAnswer(sdpOffer) { sdpAnswer ->
                            FirebaseRepository.sendSdpAnswer(sessionId, sdpAnswer.description)
                        }
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }

                // Listen for ICE Candidates from Child
                candidateListener = FirebaseRepository.listenToCandidates(sessionId, listenToParentCandidates = false) { sdpMid, sdpMLineIndex, sdp ->
                    try {
                        manager.addRemoteCandidate(sdpMid, sdpMLineIndex, sdp)
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
            }
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(t)
            AppHealthTelemetry.logDiagnostic(applicationContext, "LIVE_STREAM_RECEIVER", "FAILED", "LiveStreamActivity init failed: ${t.localizedMessage}", t.message)
            streamStatusTextState.value = "Failed to start stream: ${t.localizedMessage}"
        }
    }

    private fun toggleCameraFacing() {
        val newFacing = !isFrontCameraState.value
        try {
            FirebaseRepository.toggleCameraFacing(sessionId, newFacing)
        } catch (_: Exception) {}
        isFrontCameraState.value = newFacing
        Toast.makeText(this, "Switching Camera...", Toast.LENGTH_SHORT).show()
    }

    private fun toggleRecording(streamTypeParam: String) {
        if (isRecordingState.value) {
            stopRecordingTimer()
            FirebaseRepository.requestRemoteRecording(childId, false, streamTypeParam.lowercase())
            Toast.makeText(this, "Recording stopped. Syncing to Cloud History...", Toast.LENGTH_SHORT).show()
        } else {
            startRecordingTimer()
            FirebaseRepository.requestRemoteRecording(childId, true, streamTypeParam.lowercase())
            Toast.makeText(this, "High-quality master recording started on child device...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecordingTimer() {
        isRecordingState.value = true
        recordingDurationSecondsState.longValue = 0L
        recordingHandler?.removeCallbacksAndMessages(null)
        recordingHandler = Handler(Looper.getMainLooper())
        recordingRunnable = object : Runnable {
            override fun run() {
                if (isRecordingState.value) {
                    recordingDurationSecondsState.longValue += 1
                    recordingHandler?.postDelayed(this, 1000L)
                }
            }
        }
        recordingHandler?.post(recordingRunnable!!)
    }

    private fun stopRecordingTimer() {
        isRecordingState.value = false
        recordingDurationSecondsState.longValue = 0L
        recordingHandler?.removeCallbacksAndMessages(null)
        recordingHandler = null
        recordingRunnable = null
    }

    private fun stopAndFinishStream() {
        if (isRecordingState.value) {
            toggleRecording(streamType)
        }
        try {
            FirebaseRepository.stopStream(childId, sessionId)
        } catch (_: Exception) {}
        cleanupWebRtc()
        finish()
    }

    private fun cleanupWebRtc() {
        candidateHandler.removeCallbacksAndMessages(null)
        val remaining = synchronized(candidateBuffer) {
            val list = candidateBuffer.toList()
            candidateBuffer.clear()
            list
        }
        if (remaining.isNotEmpty()) {
            FirebaseRepository.sendIceCandidatesBatch(sessionId, remaining, isParent = true)
        }
        FirebaseRepository.cleanupSignalingData(sessionId, childId)
        try {
            sdpOfferListener?.let {
                FirebaseRepository.removeSdpOfferListener(sessionId, it)
            }
        } catch (_: Exception) {}
        try {
            candidateListener?.let {
                FirebaseRepository.removeCandidateListener(sessionId, listenToParentCandidates = false, it)
            }
        } catch (_: Exception) {}
        try {
            val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
        } catch (_: Exception) {}
        try {
            webRtcManager?.stopStream()
        } catch (_: Throwable) {}
        webRtcManager = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecordingTimer()
        cleanupWebRtc()
        try { audioRouteManager.release() } catch (_: Exception) {}
    }

    companion object {
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_CHILD_ID = "extra_child_id"
        private const val EXTRA_CHILD_NAME = "extra_child_name"
        private const val EXTRA_STREAM_TYPE = "extra_stream_type"

        fun createIntent(
            context: Context,
            sessionId: String,
            childId: String,
            childName: String,
            streamType: String
        ): Intent {
            return Intent(context, LiveStreamActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_CHILD_ID, childId)
                putExtra(EXTRA_CHILD_NAME, childName)
                putExtra(EXTRA_STREAM_TYPE, streamType)
            }
        }
    }
}
