package com.example.authapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.authapp.audio.AudioOutputRoute

@Composable
fun LiveAudioMonitorMeterAndRoute(
    audioLevel: Float,
    micColor: Color,
    currentAudioRoute: AudioOutputRoute,
    isBluetoothConnected: Boolean,
    isHeadsetConnected: Boolean,
    onAudioRouteSelect: (AudioOutputRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val activeSegments = (audioLevel.coerceIn(0f, 1f) * 12).toInt()
                    for (i in 0 until 12) {
                        val isLit = i <= activeSegments && activeSegments > 0
                        val segmentColor = when {
                            i >= 10 -> Color(0xFFEF4444)
                            i >= 7 -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
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
                            label = { Text("Bluetooth", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isHeadsetConnected || currentAudioRoute == AudioOutputRoute.HEADSET) {
                        FilterChip(
                            selected = currentAudioRoute == AudioOutputRoute.HEADSET,
                            onClick = { onAudioRouteSelect(AudioOutputRoute.HEADSET) },
                            label = { Text("Wired", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
