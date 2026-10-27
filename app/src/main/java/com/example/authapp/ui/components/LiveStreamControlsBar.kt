package com.example.authapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.authapp.audio.AudioOutputRoute
import com.example.authapp.webrtc.WebRtcManager

@Composable
fun LiveStreamControlsBar(
    isVideo: Boolean,
    onFlipCamera: () -> Unit,
    currentAudioRoute: AudioOutputRoute,
    isBluetoothConnected: Boolean,
    onAudioRouteSelect: (AudioOutputRoute) -> Unit,
    audioSensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    isRecording: Boolean,
    onToggleRecording: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAudioBoostSlider by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.90f)),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Far-Voice Whisper Boost Slider
            AnimatedVisibility(visible = showAudioBoostSlider) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Far-Voice Whisper Gain: ${WebRtcManager.getGainMultiplierText(audioSensitivity)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = if (audioSensitivity >= 95f) "5.0x Ultra Whisper" else if (audioSensitivity >= 50f) "3.0x Far Speech" else "1.0x Normal",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray
                        )
                    }
                    Slider(
                        value = audioSensitivity,
                        onValueChange = onSensitivityChange,
                        valueRange = 10f..100f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF10B981),
                            activeTrackColor = Color(0xFF10B981)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Camera Switch Lens Icon
                IconButton(
                    onClick = onFlipCamera,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 2. Audio Route Toggle Icon
                IconButton(
                    onClick = {
                        val nextRoute = when (currentAudioRoute) {
                            AudioOutputRoute.SPEAKER -> AudioOutputRoute.EARPIECE
                            AudioOutputRoute.EARPIECE -> if (isBluetoothConnected) AudioOutputRoute.BLUETOOTH else AudioOutputRoute.SPEAKER
                            AudioOutputRoute.BLUETOOTH -> AudioOutputRoute.SPEAKER
                            else -> AudioOutputRoute.SPEAKER
                        }
                        onAudioRouteSelect(nextRoute)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = when (currentAudioRoute) {
                            AudioOutputRoute.EARPIECE -> Icons.Default.Hearing
                            AudioOutputRoute.BLUETOOTH, AudioOutputRoute.HEADSET -> Icons.Default.Headphones
                            else -> Icons.Default.VolumeUp
                        },
                        contentDescription = "Audio Output Route",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 3. Far-Voice Whisper Boost Icon
                IconButton(
                    onClick = { showAudioBoostSlider = !showAudioBoostSlider },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (showAudioBoostSlider || audioSensitivity >= 90f)
                                Color(0xFF10B981).copy(alpha = 0.35f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Far-Voice Whisper Boost",
                        tint = if (showAudioBoostSlider || audioSensitivity >= 90f) Color(0xFF10B981) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 4. Record Button
                IconButton(
                    onClick = { onToggleRecording(if (isVideo) "video" else "audio") },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFDC2626) else Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = "Record Stream",
                        tint = if (isRecording) Color.White else Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 5. Disconnect / End Stream Icon
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626))
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Disconnect Stream",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
