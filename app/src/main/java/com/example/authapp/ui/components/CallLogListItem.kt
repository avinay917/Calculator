package com.example.authapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.authapp.data.CallLogItem
import com.example.authapp.utils.formatSecs
import com.example.authapp.utils.formatTime

@Composable
fun CallLogListItem(
    log: CallLogItem,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    audioPositionMs: Int = 0,
    audioDurationMs: Int = 0,
    playbackSpeed: Float = 1.0f,
    onSpeedToggle: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onSeekTo: (Int) -> Unit = {},
    onForward: () -> Unit = {},
    onRewind: () -> Unit = {},
    onDownloadClick: () -> Unit = {}
) {
    val isWhatsApp = log.type.uppercase().contains("WHATSAPP")
    val typeIcon = when {
        isWhatsApp -> Icons.Default.Call
        log.type.uppercase() == "OUTGOING" -> Icons.Default.CallMade
        log.type.uppercase() == "INCOMING" -> Icons.Default.CallReceived
        log.type.uppercase() == "MISSED" -> Icons.Default.CallMissed
        log.type.uppercase() == "REJECTED" -> Icons.Default.PhoneDisabled
        else -> Icons.Default.Phone
    }
    val iconTint = when {
        isWhatsApp -> Color(0xFF25D366)
        log.type.uppercase() == "OUTGOING" -> Color(0xFF1976D2)
        log.type.uppercase() == "INCOMING" -> Color(0xFF388E3C)
        log.type.uppercase() == "MISSED" -> Color(0xFFD32F2F)
        log.type.uppercase() == "REJECTED" -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val timeStr = remember(log.timestamp) { log.timestamp.formatTime() }
    val durationStr = remember(log.durationSeconds) { log.durationSeconds.formatSecs() }

    val displayType = when (log.type.uppercase()) {
        "INCOMING_WHATSAPP" -> "WHATSAPP INCOMING"
        "OUTGOING_WHATSAPP" -> "WHATSAPP OUTGOING"
        else -> log.type.uppercase()
    }

    val callerTitle = if (log.name.isNotBlank()) log.name else if (log.number.isNotBlank()) log.number else "Unknown Caller"
    val callerSubtitle = if (log.name.isNotBlank() && log.number.isNotBlank() && log.name != log.number) {
        "📞 ${log.number} • ⏱️ $durationStr"
    } else {
        "⏱️ Duration: $durationStr"
    }

    ActivityLogCard(
        icon = typeIcon,
        iconTint = iconTint,
        title = callerTitle,
        subtitle = callerSubtitle,
        timestampStr = timeStr,
        badgeText = displayType,
        badgeColor = iconTint,
        extraContent = if (log.audioRecordingUrl.isNotEmpty()) {
            {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Title row: Playing Status & Download button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isPlaying) (if (isBuffering) "⏳ Buffering..." else "🎙️ Playing Call Audio") else "🎙️ Call Recording Available",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Download Icon Button
                                IconButton(
                                    onClick = onDownloadClick,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Recording",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Playback Speed
                                AssistChip(
                                    onClick = onSpeedToggle,
                                    label = {
                                        Text(
                                            text = "${if (playbackSpeed % 1.0f == 0f) playbackSpeed.toInt() else playbackSpeed}x",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    },
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }

                        // Seekbar (Slider)
                        if (isPlaying && audioDurationMs > 0) {
                            Slider(
                                value = audioPositionMs.toFloat().coerceIn(0f, audioDurationMs.toFloat()),
                                onValueChange = { onSeekTo(it.toInt()) },
                                valueRange = 0f..audioDurationMs.toFloat(),
                                modifier = Modifier.fillMaxWidth().height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Playback Control Buttons (Rewind -10s, Play/Pause, Forward +10s) and Time Label
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Play / Pause
                                IconButton(onClick = onPlayClick, modifier = Modifier.size(36.dp)) {
                                    if (isBuffering) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                if (isPlaying) {
                                    // Rewind -10s
                                    IconButton(onClick = onRewind, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Replay10,
                                            contentDescription = "Rewind 10s",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Forward +10s
                                    IconButton(onClick = onForward, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Forward10,
                                            contentDescription = "Forward 10s",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Time Duration Text
                            Text(
                                text = if (isPlaying && audioDurationMs > 0) {
                                    "${(audioPositionMs / 1000).toLong().formatSecs()} / ${(audioDurationMs / 1000).toLong().formatSecs()}"
                                } else {
                                    "Tap to Play"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else null
    )
}
