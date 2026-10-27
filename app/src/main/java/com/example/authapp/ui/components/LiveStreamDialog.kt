package com.example.authapp.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.authapp.audio.AudioOutputRoute
import com.example.authapp.webrtc.WebRtcManager
import org.webrtc.VideoTrack

@Composable
fun LiveStreamDialog(
    activeStreamType: String?,
    activeChildName: String?,
    streamStatusText: String,
    isFrontCamera: Boolean,
    cameraMode: String = if (isFrontCamera) "front" else "rear",
    isRecording: Boolean,
    recordingDurationSeconds: Long,
    streamElapsedSeconds: Long = 0L,
    audioSensitivity: Float,
    remoteVideoTrack: VideoTrack?,
    webRtcManager: WebRtcManager?,
    audioLevel: Float = 0f,
    onDismiss: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleRecording: (String) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    currentAudioRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
    isBluetoothConnected: Boolean = false,
    isHeadsetConnected: Boolean = false,
    onAudioRouteSelect: (AudioOutputRoute) -> Unit = {}
) {
    val isVideo = activeStreamType.equals("video", ignoreCase = true) ||
            activeStreamType.equals("camera_video", ignoreCase = true) ||
            activeStreamType.equals("screen_mirror", ignoreCase = true) ||
            activeStreamType.equals("Screen Share", ignoreCase = true)

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isVideo) Color.Black else MaterialTheme.colorScheme.background
    ) {
        if (isVideo) {
            // Video Mode: Edge-to-edge SafeSurfaceViewRenderer with floating HUD
            Box(modifier = Modifier.fillMaxSize()) {
                if (webRtcManager != null) {
                    SafeSurfaceViewRenderer(
                        videoTrack = remoteVideoTrack,
                        eglContext = webRtcManager.eglBase.eglBaseContext,
                        modifier = Modifier.fillMaxSize(),
                        onRendererReleased = {
                            webRtcManager.releaseEglBase()
                        }
                    )
                }

                if (remoteVideoTrack == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = streamStatusText.ifEmpty { "Connecting to child camera..." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                val isActuallyLive = remoteVideoTrack != null && streamElapsedSeconds > 0

                // Top Floating Header
                LiveStreamTopBar(
                    activeChildName = activeChildName,
                    streamStatusText = streamStatusText,
                    streamElapsedSeconds = streamElapsedSeconds,
                    isActuallyLive = isActuallyLive,
                    onDismiss = onDismiss,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Bottom Floating Controls
                LiveStreamControlsBar(
                    isVideo = true,
                    onFlipCamera = onFlipCamera,
                    currentAudioRoute = currentAudioRoute,
                    isBluetoothConnected = isBluetoothConnected,
                    onAudioRouteSelect = onAudioRouteSelect,
                    audioSensitivity = audioSensitivity,
                    onSensitivityChange = onSensitivityChange,
                    isRecording = isRecording,
                    onToggleRecording = onToggleRecording,
                    onDismiss = onDismiss,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else {
            // Audio Mode: Fullscreen Ambient Audio Monitor
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Audio Cast",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = activeChildName ?: "Child Device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LiveAudioMonitorView(
                        childName = activeChildName ?: "Child",
                        statusText = streamStatusText,
                        audioSensitivity = audioSensitivity,
                        onSensitivityChange = onSensitivityChange,
                        isRecording = isRecording,
                        recordingDurationSeconds = recordingDurationSeconds,
                        onToggleRecording = { onToggleRecording("audio") },
                        audioLevel = audioLevel,
                        currentAudioRoute = currentAudioRoute,
                        isBluetoothConnected = isBluetoothConnected,
                        isHeadsetConnected = isHeadsetConnected,
                        onAudioRouteSelect = onAudioRouteSelect
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect Audio Cast", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}
