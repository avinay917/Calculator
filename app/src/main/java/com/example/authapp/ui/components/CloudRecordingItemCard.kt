package com.example.authapp.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.data.RecordingSession
import com.example.authapp.utils.formatTime
import java.io.File
import java.util.Locale

@Composable
fun CloudRecordingItemCard(
    recording: RecordingSession,
    childName: String,
    isPlaying: Boolean,
    onPlayRecording: (RecordingSession) -> Unit,
    onStopPlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCall = recording.streamType.equals("call", ignoreCase = true)
    val isScheduled = recording.streamType.equals("scheduled", ignoreCase = true) || recording.localFilePath.contains("scheduled", ignoreCase = true)
    val isVideo = recording.streamType.equals("video", ignoreCase = true)

    val formattedDate = remember(recording.startTime) {
        if (recording.startTime > 0L) recording.startTime.formatTime() else "Unknown Date"
    }

    val durationStr = remember(recording.durationSeconds) {
        val m = recording.durationSeconds / 60
        val s = recording.durationSeconds % 60
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    val hasLocal = recording.localFilePath.isNotEmpty() && File(recording.localFilePath).exists()
    val hasCloud = recording.storageUrl.isNotEmpty()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 3.dp else 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Media Type Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCall -> Color(0xFFE0F2FE)
                                isScheduled -> Color(0xFFF3E8FF)
                                isVideo -> MaterialTheme.colorScheme.secondaryContainer
                                else -> Color(0xFFFFE4E6)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isCall -> Icons.Default.Call
                            isScheduled -> Icons.Default.Schedule
                            isVideo -> Icons.Default.Videocam
                            else -> Icons.Default.FiberManualRecord
                        },
                        contentDescription = null,
                        tint = when {
                            isCall -> Color(0xFF0284C7)
                            isScheduled -> Color(0xFF7E22CE)
                            isVideo -> MaterialTheme.colorScheme.secondary
                            else -> Color(0xFFE11D48)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Details
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = childName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = durationStr,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isCall -> Color(0xFFE0F2FE)
                                isScheduled -> Color(0xFFF3E8FF)
                                else -> Color(0xFFFFE4E6)
                            }
                        ) {
                            Text(
                                text = when {
                                    isCall -> "Call"
                                    isScheduled -> "Scheduled"
                                    else -> "Live"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    isCall -> Color(0xFF0284C7)
                                    isScheduled -> Color(0xFF7E22CE)
                                    else -> Color(0xFFE11D48)
                                },
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                // Play / Stop Button
                FilledIconButton(
                    onClick = {
                        if (isPlaying) onStopPlayback() else onPlayRecording(recording)
                    },
                    colors = if (isPlaying) IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                    else IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }

            // Storage Badges & Playback Status
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasCloud) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE1F5FE)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF0288D1),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Cloud Backed",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF0277BD),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    if (hasLocal) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = "💾 Local File",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF2E7D32),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (isPlaying) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "▶ Playing Now...",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (isPlaying) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            }
        }
    }
}
