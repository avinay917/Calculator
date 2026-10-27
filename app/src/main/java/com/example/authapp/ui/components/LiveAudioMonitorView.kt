package com.example.authapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.authapp.audio.AudioOutputRoute
import java.util.Locale

@Composable
fun LiveAudioMonitorView(
    childName: String,
    statusText: String,
    audioSensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    isRecording: Boolean,
    recordingDurationSeconds: Long,
    onToggleRecording: () -> Unit,
    audioLevel: Float = 0f,
    currentAudioRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
    isBluetoothConnected: Boolean = false,
    isHeadsetConnected: Boolean = false,
    onAudioRouteSelect: (AudioOutputRoute) -> Unit = {}
) {
    val animatedScale by animateFloatAsState(
        targetValue = 1.0f + (audioLevel.coerceIn(0f, 1f) * 0.45f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "animatedScale"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = (0.25f + audioLevel.coerceIn(0f, 1f) * 0.7f).coerceIn(0.15f, 0.95f),
        animationSpec = tween(120),
        label = "animatedAlpha"
    )

    val micColor by animateColorAsState(
        targetValue = if (audioLevel > 0.08f) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
        animationSpec = tween(200),
        label = "micColor"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Reactive Microphone Radar Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(105.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp * animatedScale)
                        .clip(CircleShape)
                        .background(micColor.copy(alpha = animatedAlpha * 0.35f))
                )
                Box(
                    modifier = Modifier
                        .size(80.dp * animatedScale)
                        .clip(CircleShape)
                        .background(micColor.copy(alpha = animatedAlpha * 0.6f))
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(micColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Live Remote Mic",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Listening to $childName",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (statusText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (statusText.contains("🟢") || statusText.contains("Streaming")) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // VU Meter & Audio Output Route Switcher
            LiveAudioMonitorMeterAndRoute(
                audioLevel = audioLevel,
                micColor = micColor,
                currentAudioRoute = currentAudioRoute,
                isBluetoothConnected = isBluetoothConnected,
                isHeadsetConnected = isHeadsetConnected,
                onAudioRouteSelect = onAudioRouteSelect
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Far-Voice Boost Slider Card
            LiveAudioMonitorBoostCard(
                audioSensitivity = audioSensitivity,
                onSensitivityChange = onSensitivityChange
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Start / Stop Recording Button
            Button(
                onClick = onToggleRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (isRecording) Color.White else MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val recTimeStr = remember(recordingDurationSeconds) {
                    val m = recordingDurationSeconds / 60
                    val s = recordingDurationSeconds % 60
                    String.format(Locale.getDefault(), "%02d:%02d", m, s)
                }
                Text(
                    text = if (isRecording) "Stop Recording ($recTimeStr)" else "Start Audio Recording",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
