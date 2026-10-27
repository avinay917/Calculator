package com.example.authapp.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.authapp.audio.AudioRouteManager
import com.example.authapp.data.FirebaseControlRepository
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.theme.AuthAppTheme
import com.example.authapp.ui.components.LiveStreamDialog
import com.example.authapp.webrtc.WebRtcManager

class LiveStreamActivity : ComponentActivity() {

    private var sessionId: String = ""
    private var childId: String = ""
    private var childName: String = "Child Device"
    private var streamType: String = "video"

    private lateinit var audioRouteManager: AudioRouteManager
    private lateinit var coordinator: LiveStreamReceiverCoordinator
    private lateinit var recorder: LiveStreamLocalRecorder

    private var isFrontCameraState = mutableStateOf(false)
    private var cameraModeState = mutableStateOf("rear")

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!audioRouteManager.hasBluetoothPermission()) {
                bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        coordinator = LiveStreamReceiverCoordinator(
            context = applicationContext,
            sessionId = sessionId,
            childId = childId,
            streamType = streamType,
            audioRouteManager = audioRouteManager
        )
        recorder = LiveStreamLocalRecorder(
            context = this,
            childId = childId,
            childName = childName
        )

        coordinator.initReceiver()

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
                        streamStatusText = coordinator.streamStatusTextState.value,
                        isFrontCamera = isFrontCameraState.value,
                        cameraMode = cameraModeState.value,
                        isRecording = recorder.isRecordingState.value,
                        recordingDurationSeconds = recorder.recordingDurationSecondsState.longValue,
                        streamElapsedSeconds = coordinator.streamElapsedSecondsState.longValue,
                        audioSensitivity = coordinator.audioSensitivityState.floatValue,
                        remoteVideoTrack = coordinator.remoteVideoTrackState.value,
                        webRtcManager = coordinator.webRtcManager,
                        audioLevel = coordinator.liveAudioLevelState.floatValue,
                        onDismiss = { stopAndFinishStream() },
                        onFlipCamera = { cycleCameraMode() },
                        onToggleRecording = { type -> recorder.toggleRecording(type) },
                        onSensitivityChange = { sensitivity ->
                            coordinator.audioSensitivityState.floatValue = sensitivity.coerceIn(10f, 100f)
                            coordinator.remoteAudioTrackState.value?.setVolume(
                                WebRtcManager.calculateSuperBoostGain(coordinator.audioSensitivityState.floatValue)
                            )
                        },
                        currentAudioRoute = currentAudioRoute,
                        isBluetoothConnected = isBluetoothConnected,
                        isHeadsetConnected = isHeadsetConnected,
                        onAudioRouteSelect = { route -> audioRouteManager.setRoute(route) }
                    )
                }
            }
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

    private fun stopAndFinishStream() {
        if (recorder.isRecordingState.value) {
            recorder.toggleRecording(streamType)
        }
        try {
            FirebaseControlRepository.stopStream(childId, sessionId)
        } catch (_: Exception) {}
        coordinator.cleanup()
        recorder.cleanup()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder.cleanup()
        coordinator.cleanup()
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
