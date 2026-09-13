package com.example.authapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.authapp.audio.AudioOutputRoute
import com.example.authapp.webrtc.WebRtcManager
import org.webrtc.VideoTrack
import java.util.Locale

@Composable
fun LiveStreamDialog(
    activeStreamType: String?,
    activeChildName: String?,
    streamStatusText: String,
    isFrontCamera: Boolean,
    isRecording: Boolean,
    recordingDurationSeconds: Long,
    audioSensitivity: Float,
    remoteVideoTrack: VideoTrack?,
    webRtcManager: WebRtcManager?,
    onDismiss: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleRecording: (String) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    currentAudioRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
    isBluetoothConnected: Boolean = false,
    onAudioRouteSelect: (AudioOutputRoute) -> Unit = {}
) {
    val isVideo = activeStreamType.equals("video", ignoreCase = true)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isVideo) Color.Black else MaterialTheme.colorScheme.background
        ) {
            if (isVideo) {
                // Video Mode: Edge-to-edge SafeSurfaceViewRenderer with floating HUD
                Box(modifier = Modifier.fillMaxSize()) {
                    if (remoteVideoTrack != null && webRtcManager != null) {
                        SafeSurfaceViewRenderer(
                            videoTrack = remoteVideoTrack,
                            eglContext = webRtcManager.eglBase.eglBaseContext,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
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
                                    text = "Connecting to child camera...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // Top Floating Bar with Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.8f),
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Stream",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeChildName ?: "Child Device",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = streamStatusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFE53935)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Floating Glassmorphic Controls Bar
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Audio Route Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = currentAudioRoute == AudioOutputRoute.SPEAKER,
                                    onClick = { onAudioRouteSelect(AudioOutputRoute.SPEAKER) },
                                    label = { Text("Speaker", style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = currentAudioRoute == AudioOutputRoute.EARPIECE,
                                    onClick = { onAudioRouteSelect(AudioOutputRoute.EARPIECE) },
                                    label = { Text("Earpiece", style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (isBluetoothConnected || currentAudioRoute == AudioOutputRoute.BLUETOOTH) {
                                    FilterChip(
                                        selected = currentAudioRoute == AudioOutputRoute.BLUETOOTH,
                                        onClick = { onAudioRouteSelect(AudioOutputRoute.BLUETOOTH) },
                                        label = { Text("BT", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        modifier = Modifier.weight(0.8f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action Buttons Row: Flip, Record, End Call
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalIconButton(
                                    onClick = onFlipCamera,
                                    modifier = Modifier.size(50.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cameraswitch,
                                        contentDescription = if (isFrontCamera) "Switch to Back Camera" else "Switch to Front Camera",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Button(
                                    onClick = { onToggleRecording("video") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = if (isRecording) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val recTimeStr = remember(recordingDurationSeconds) {
                                        val m = recordingDurationSeconds / 60
                                        val s = recordingDurationSeconds % 60
                                        String.format(Locale.getDefault(), "%02d:%02d", m, s)
                                    }
                                    Text(
                                        text = if (isRecording) "Stop $recTimeStr" else "Record",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Button(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.size(50.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CallEnd,
                                        contentDescription = "Disconnect Stream",
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
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

                        // Audio Monitor Card
                        LiveAudioMonitorView(
                            childName = activeChildName ?: "Child",
                            statusText = streamStatusText,
                            audioSensitivity = audioSensitivity,
                            onSensitivityChange = onSensitivityChange,
                            isRecording = isRecording,
                            recordingDurationSeconds = recordingDurationSeconds,
                            onToggleRecording = { onToggleRecording("audio") },
                            currentAudioRoute = currentAudioRoute,
                            isBluetoothConnected = isBluetoothConnected,
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
}
