package com.example.authapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
    onSensitivityChange: (Float) -> Unit
) {
    val isVideo = activeStreamType.equals("video", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(vertical = 12.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isVideo) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isVideo) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live ${activeStreamType?.replaceFirstChar { it.uppercase() } ?: "Media"} Cast",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Device: ${activeChildName ?: "Child"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = streamStatusText,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Video Stream Display via SafeSurfaceViewRenderer
                if (isVideo) {
                    if (remoteVideoTrack != null && webRtcManager != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            SafeSurfaceViewRenderer(
                                videoTrack = remoteVideoTrack,
                                eglContext = webRtcManager.eglBase.eglBaseContext,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Connecting to child camera...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Video Controls: Camera Switch & Record
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onFlipCamera,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isFrontCamera) "Flip (Back)" else "Flip (Front)", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = { onToggleRecording("video") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (isRecording) Color.White else MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val recTimeStr = remember(recordingDurationSeconds) {
                                val m = recordingDurationSeconds / 60
                                val s = recordingDurationSeconds % 60
                                String.format(Locale.getDefault(), "%02d:%02d", m, s)
                            }
                            Text(if (isRecording) "Stop ($recTimeStr)" else "Record", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    // Real-time Live Audio Monitor with Whisper Boost & Recording
                    LiveAudioMonitorView(
                        childName = activeChildName ?: "Child",
                        statusText = streamStatusText,
                        audioSensitivity = audioSensitivity,
                        onSensitivityChange = onSensitivityChange,
                        isRecording = isRecording,
                        recordingDurationSeconds = recordingDurationSeconds,
                        onToggleRecording = { onToggleRecording("audio") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Disconnect Stream")
            }
        }
    )
}
