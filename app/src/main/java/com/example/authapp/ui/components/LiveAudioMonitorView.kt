package com.example.authapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    // Dynamic spring-based pulse responding to actual WebRTC audio volume
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
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
                // Outermost dynamic sound energy ring
                Box(
                    modifier = Modifier
                        .size(100.dp * animatedScale)
                        .clip(CircleShape)
                        .background(micColor.copy(alpha = animatedAlpha * 0.35f))
                )
                // Mid energy ring
                Box(
                    modifier = Modifier
                        .size(80.dp * animatedScale)
                        .clip(CircleShape)
                        .background(micColor.copy(alpha = animatedAlpha * 0.6f))
                )
                // Core Mic Button
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

            // Render live status text with connection badge
            if (statusText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = if (statusText.contains("🟢") || statusText.contains("Streaming")) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real-Time Live Audio Energy VU Meter Bar
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = micColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Mic Volume",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val percent = (audioLevel.coerceIn(0f, 1f) * 100).toInt()
                        Text(
                            text = if (percent > 0) "$percent%" else "Quiet",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (percent > 50) Color(0xFFEF4444) else if (percent > 10) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 12-segment real-time LED volume meter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val activeSegments = (audioLevel.coerceIn(0f, 1f) * 12).toInt()
                        for (i in 0 until 12) {
                            val isLit = i <= activeSegments && activeSegments > 0
                            val segmentColor = when {
                                i >= 10 -> Color(0xFFEF4444) // Red for loud
                                i >= 7 -> Color(0xFFF59E0B)  // Orange/Amber for medium
                                else -> Color(0xFF10B981)    // Green for normal
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isLit) segmentColor else MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Audio Output Switcher (Speaker / Earpiece / Bluetooth)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Audio Output",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Loudspeaker
                        FilterChip(
                            selected = currentAudioRoute == AudioOutputRoute.SPEAKER,
                            onClick = { onAudioRouteSelect(AudioOutputRoute.SPEAKER) },
                            label = { Text("Speaker", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // 2. Earpiece
                        FilterChip(
                            selected = currentAudioRoute == AudioOutputRoute.EARPIECE,
                            onClick = { onAudioRouteSelect(AudioOutputRoute.EARPIECE) },
                            label = { Text("Earpiece", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // 3. Bluetooth Earbuds
                        if (isBluetoothConnected || currentAudioRoute == AudioOutputRoute.BLUETOOTH) {
                            FilterChip(
                                selected = currentAudioRoute == AudioOutputRoute.BLUETOOTH,
                                onClick = { onAudioRouteSelect(AudioOutputRoute.BLUETOOTH) },
                                label = { Text("Earbuds", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 4. Wired Earphones
                        if (isHeadsetConnected || currentAudioRoute == AudioOutputRoute.HEADSET) {
                            FilterChip(
                                selected = currentAudioRoute == AudioOutputRoute.HEADSET,
                                onClick = { onAudioRouteSelect(AudioOutputRoute.HEADSET) },
                                label = { Text("Earphones", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Audio Sensitivity & Whisper Boost Slider Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sensitivity / Boost",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${audioSensitivity.toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = audioSensitivity,
                        onValueChange = onSensitivityChange,
                        valueRange = 10f..100f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Sensitivity preset chips evenly distributed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { onSensitivityChange(30f) },
                            label = { Text("30% Normal", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = { onSensitivityChange(60f) },
                            label = { Text("60% Boost", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = { onSensitivityChange(100f) },
                            label = { Text("100% Whisper", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

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
