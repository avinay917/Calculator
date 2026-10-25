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
import com.example.authapp.data.FirebaseControlRepository
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
    private var isFrontCameraState = mutableStateOf(false)
    private var cameraModeState = mutableStateOf("rear")
    private var isRecordingState = mutableStateOf(false)
    private var recordingDurationSecondsState = mutableLongStateOf(0L)
    private var streamElapsedSecondsState = mutableLongStateOf(0L)
    private var audioSensitivityState = mutableFloatStateOf(100f)
    private var liveAudioLevelState = mutableFloatStateOf(0f)

    private var sdpOfferListener: com.google.firebase.database.ValueEventListener? = null
    private var candidateListener: com.google.firebase.database.ChildEventListener? = null
    private var candidateBatcher: com.example.authapp.webrtc.CandidateBatcher? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recordingHandler: Handler? = null
    private var recordingRunnable: Runnable? = null
    private var streamTimerHandler: Handler? = null
    private var streamTimerRunnable: Runnable? = null
    private var parentMediaRecorder: android.media.MediaRecorder? = null
    private var parentRecordingFile: java.io.File? = null

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

                    val displayStreamType = when (streamType.lowercase()) {
                        "camera_video", "video" -> "Video"
                        "screen_mirror", "screen" -> "Screen Share"
                        "audio_only", "audio" -> "Audio"
                        else -> if (streamType.contains("audio", ignoreCase = true)) "Audio" else "Video"
                    }

                    LiveStreamDialog(
                        activeStreamType = displayStreamType,
                        activeChildName = childName,
                        streamStatusText = streamStatusTextState.value,
                        isFrontCamera = isFrontCameraState.value,
                        cameraMode = cameraModeState.value,
                        isRecording = isRecordingState.value,
                        recordingDurationSeconds = recordingDurationSecondsState.longValue,
                        streamElapsedSeconds = streamElapsedSecondsState.longValue,
                        audioSensitivity = audioSensitivityState.floatValue,
                        remoteVideoTrack = remoteVideoTrackState.value,
                        webRtcManager = webRtcManager,
                        audioLevel = liveAudioLevelState.floatValue,
                        onDismiss = {
                            stopAndFinishStream()
                        },
                        onFlipCamera = {
                            cycleCameraMode()
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
                audioManager?.let { am ->
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    val maxMusicVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
                    val maxVoiceVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoiceVol, 0)
                    am.isSpeakerphoneOn = true
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[LiveStreamActivity] AudioManager init error: ${e.localizedMessage}")
            }

            FirebaseRepository.fetchIceServers { iceServers ->
                try {
                    audioRouteManager.checkAndAutoRoute()
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[LiveStreamActivity] audioRouteManager error: ${e.localizedMessage}")
                }

                candidateBatcher = com.example.authapp.webrtc.CandidateBatcher(sessionId, isParent = true)
                manager.startReceiver(
                    iceServers = iceServers,
                    onIceCandidate = { candidate ->
                        candidateBatcher?.addCandidate(candidate)
                    },
                    onRemoteVideoTrack = { track -> handleRemoteVideoTrack(track) },
                    onRemoteAudioTrack = { track -> handleRemoteAudioTrack(track) }
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

    private fun handleRemoteVideoTrack(track: VideoTrack) {
        mainHandler.post {
            remoteVideoTrackState.value = track
            val isScreen = streamType.contains("screen", ignoreCase = true)
            val isAudioActive = remoteAudioTrackState.value != null
            streamStatusTextState.value = when {
                isScreen -> "Live Screen Streaming 🟢"
                isAudioActive -> "Live Camera & Audio Streaming 🟢"
                else -> "Live Video Streaming 🟢"
            }
            startStreamDurationTimer()
            AppAnalytics.logFeatureUsage(if (isScreen) "screen_cast" else "video_cast", "connected")
        }
    }

    private fun handleRemoteAudioTrack(track: AudioTrack) {
        mainHandler.post {
            remoteAudioTrackState.value = track
            val isVideoActive = remoteVideoTrackState.value != null
            val isScreen = streamType.contains("screen", ignoreCase = true)
            streamStatusTextState.value = when {
                streamType.equals("audio", ignoreCase = true) -> "Live Audio Streaming 🟢"
                isVideoActive && !isScreen -> "Live Camera & Audio Streaming 🟢"
                else -> streamStatusTextState.value
            }
            startStreamDurationTimer()
            try {
                track.setEnabled(true)
                if (!audioRouteManager.isBluetoothConnected.value) {
                    audioRouteManager.setRoute(AudioOutputRoute.SPEAKER)
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            AppAnalytics.logFeatureUsage("audio_cast", "connected")
        }
    }

    private fun cycleCameraMode() {
        val nextMode = when (cameraModeState.value.lowercase()) {
            "rear", "back" -> "ultrawide"
            "ultrawide", "wide", "0.5x" -> "front"
            else -> "rear"
        }
        cameraModeState.value = nextMode
        isFrontCameraState.value = nextMode == "front"
        try {
            FirebaseRepository.setCameraFacingMode(sessionId, nextMode)
        } catch (_: Exception) {}
        val label = when (nextMode) {
            "ultrawide" -> "Ultra-Wide (0.5x) Lens"
            "front" -> "Front Camera"
            else -> "Rear (1x) Camera"
        }
        Toast.makeText(this, "Switching to $label...", Toast.LENGTH_SHORT).show()
    }

    private fun startParentLocalRecording() {
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val file = java.io.File(downloadsDir, "LiveRec_${childName.replace(" ", "_")}_$timeStamp.m4a")
            parentRecordingFile = file

            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }
            recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            parentMediaRecorder = recorder
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[LiveStreamActivity] Parent local recorder start notice: ${e.localizedMessage}")
        }
    }

    private fun stopParentLocalRecording() {
        try {
            parentMediaRecorder?.stop()
            parentMediaRecorder?.release()
            parentMediaRecorder = null
            parentRecordingFile?.let { f ->
                if (f.exists() && f.length() > 0) {
                    Toast.makeText(this, "Saved to Phone: ${f.name}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[LiveStreamActivity] Stop parent local recorder error: ${e.localizedMessage}")
            try {
                parentMediaRecorder?.release()
                parentMediaRecorder = null
            } catch (_: Exception) {}
        }
    }

    private fun startStreamDurationTimer() {
        if (streamTimerHandler != null) return
        streamElapsedSecondsState.longValue = 0L
        streamTimerHandler = Handler(Looper.getMainLooper())
        streamTimerRunnable = object : Runnable {
            override fun run() {
                streamElapsedSecondsState.longValue += 1
                streamTimerHandler?.postDelayed(this, 1000L)
            }
        }
        streamTimerRunnable?.let { streamTimerHandler?.post(it) }
    }

    private fun stopStreamDurationTimer() {
        streamTimerHandler?.removeCallbacksAndMessages(null)
        streamTimerHandler = null
        streamTimerRunnable = null
        streamElapsedSecondsState.longValue = 0L
    }

    private fun toggleRecording(streamTypeParam: String) {
        if (isRecordingState.value) {
            stopRecordingTimer()
            stopParentLocalRecording()
            FirebaseControlRepository.requestRemoteRecording(childId, false, streamTypeParam.lowercase())
            Toast.makeText(this, "Recording stopped & saved to Phone storage!", Toast.LENGTH_SHORT).show()
        } else {
            startRecordingTimer()
            startParentLocalRecording()
            FirebaseControlRepository.requestRemoteRecording(childId, true, streamTypeParam.lowercase())
            Toast.makeText(this, "Recording started (Saving directly to Phone)...", Toast.LENGTH_SHORT).show()
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
        recordingRunnable?.let { recordingHandler?.post(it) }
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
            FirebaseControlRepository.stopStream(childId, sessionId)
        } catch (_: Exception) {}
        cleanupWebRtc()
        finish()
    }

    private fun cleanupWebRtc() {
        stopStreamDurationTimer()
        stopParentLocalRecording()
        remoteVideoTrackState.value = null
        remoteAudioTrackState.value = null
        mainHandler.removeCallbacksAndMessages(null)
        candidateBatcher?.flush()
        candidateBatcher?.clear()
        candidateBatcher = null
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
        stopStreamDurationTimer()
        stopParentLocalRecording()
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
