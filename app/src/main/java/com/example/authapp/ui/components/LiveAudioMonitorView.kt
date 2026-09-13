package com.example.authapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
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
    currentAudioRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
    isBluetoothConnected: Boolean = false,
    onAudioRouteSelect: (AudioOutputRoute) -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioRadar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp * pulseScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Live Remote Mic",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Listening to $childName",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                        // 1. Loudspeaker (Neeche waala)
                        FilterChip(
                            selected = currentAudioRoute == AudioOutputRoute.SPEAKER,
                            onClick = { onAudioRouteSelect(AudioOutputRoute.SPEAKER) },
                            label = { Text("Speaker", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // 2. Earpiece (Upar waala speaker)
                        FilterChip(
                            selected = currentAudioRoute == AudioOutputRoute.EARPIECE,
                            onClick = { onAudioRouteSelect(AudioOutputRoute.EARPIECE) },
                            label = { Text("Earpiece", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // 3. Bluetooth (Wireless - automatically available / selected if connected)
                        if (isBluetoothConnected || currentAudioRoute == AudioOutputRoute.BLUETOOTH) {
                            FilterChip(
                                selected = currentAudioRoute == AudioOutputRoute.BLUETOOTH,
                                onClick = { onAudioRouteSelect(AudioOutputRoute.BLUETOOTH) },
                                label = { Text("Bluetooth", style = MaterialTheme.typography.labelSmall) },
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
