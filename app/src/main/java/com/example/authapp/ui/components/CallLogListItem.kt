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
    onPlayClick: () -> Unit = {}
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

    ActivityLogCard(
        icon = typeIcon,
        iconTint = iconTint,
        title = log.name.ifEmpty { log.number.ifEmpty { "Unknown Caller" } },
        subtitle = listOf(log.number.takeIf { it.isNotEmpty() && it != log.name }, durationStr).filterNotNull().joinToString(" • "),
        timestampStr = timeStr,
        badgeText = displayType,
        badgeColor = iconTint,
        extraContent = if (log.audioRecordingUrl.isNotEmpty()) {
            {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPlayClick, modifier = Modifier.size(36.dp)) {
                            if (isBuffering) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isPlaying) (if (isBuffering) "Buffering..." else "Playing Recording...") else "Listen Recording 🎙️",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isPlaying && audioDurationMs > 0) {
                                    Text(
                                        text = "${(audioPositionMs / 1000).toLong().formatSecs()} / ${(audioDurationMs / 1000).toLong().formatSecs()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (isPlaying && audioDurationMs > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (audioPositionMs.toFloat() / audioDurationMs.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        AssistChip(
                            onClick = onSpeedToggle,
                            label = {
                                Text(
                                    text = "${if (playbackSpeed % 1.0f == 0f) playbackSpeed.toInt() else playbackSpeed}x",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        } else null
    )
}
