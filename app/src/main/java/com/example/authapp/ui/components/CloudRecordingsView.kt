package com.example.authapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.utils.formatTime
import java.io.File
import java.util.Locale

@Composable
fun CloudRecordingsView(
    recordings: List<RecordingSession>,
    childUsers: List<User>,
    isLoading: Boolean,
    currentFilter: String,
    onFilterChange: (String) -> Unit,
    currentlyPlayingRecId: String?,
    onPlayRecording: (RecordingSession) -> Unit,
    onStopPlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val childNameMap = remember(childUsers) {
        childUsers.associate { it.uid to it.name.ifEmpty { "Child" } }
    }

    val filteredList = remember(recordings, currentFilter) {
        when (currentFilter) {
            "CALL" -> recordings.filter { it.streamType.equals("call", ignoreCase = true) }
            "SCHEDULED" -> recordings.filter {
                it.streamType.equals("scheduled", ignoreCase = true) ||
                    it.localFilePath.contains("scheduled", ignoreCase = true)
            }
            "LIVE" -> recordings.filter {
                !it.streamType.equals("call", ignoreCase = true) &&
                    !it.streamType.equals("scheduled", ignoreCase = true) &&
                    !it.localFilePath.contains("scheduled", ignoreCase = true) &&
                    !it.streamType.contains("photo", ignoreCase = true)
            }
            else -> recordings
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Cloud Recordings History",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${recordings.size} total recordings synced",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = currentFilter == "ALL",
                onClick = { onFilterChange("ALL") },
                label = { Text("All (${recordings.size})", style = MaterialTheme.typography.labelSmall) }
            )
            val callCount = recordings.count { it.streamType.equals("call", ignoreCase = true) }
            FilterChip(
                selected = currentFilter == "CALL",
                onClick = { onFilterChange("CALL") },
                label = { Text("Calls ($callCount)", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            )
            val schedCount = recordings.count {
                it.streamType.equals("scheduled", ignoreCase = true) || it.localFilePath.contains("scheduled", ignoreCase = true)
            }
            FilterChip(
                selected = currentFilter == "SCHEDULED",
                onClick = { onFilterChange("SCHEDULED") },
                label = { Text("Scheduled ($schedCount)", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            )
            val liveCount = recordings.count {
                !it.streamType.equals("call", ignoreCase = true) &&
                    !it.streamType.equals("scheduled", ignoreCase = true) &&
                    !it.localFilePath.contains("scheduled", ignoreCase = true) &&
                    !it.streamType.contains("photo", ignoreCase = true)
            }
            FilterChip(
                selected = currentFilter == "LIVE",
                onClick = { onFilterChange("LIVE") },
                label = { Text("Live ($liveCount)", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Red)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content List / Loading / Empty State
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading recordings from Cloud...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (currentFilter == "ALL") "No recordings saved yet" else "No $currentFilter recordings found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Record live audio or video streams from child devices to save and play them here anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredList, key = { it.id }) { rec ->
                    val isPlaying = currentlyPlayingRecId == rec.id
                    val isCall = rec.streamType.equals("call", ignoreCase = true)
                    val isScheduled = rec.streamType.equals("scheduled", ignoreCase = true) || rec.localFilePath.contains("scheduled", ignoreCase = true)
                    val isVideo = rec.streamType.equals("video", ignoreCase = true)
                    val isLive = !isCall && !isScheduled && !rec.streamType.contains("photo", ignoreCase = true)
                    val childName = childNameMap[rec.childId] ?: "Child (${rec.childId.take(5)})"

                    val formattedDate = remember(rec.startTime) {
                        if (rec.startTime > 0L) rec.startTime.formatTime() else "Unknown Date"
                    }

                    val durationStr = remember(rec.durationSeconds) {
                        val m = rec.durationSeconds / 60
                        val s = rec.durationSeconds % 60
                        String.format(Locale.getDefault(), "%02d:%02d", m, s)
                    }

                    val hasLocal = rec.localFilePath.isNotEmpty() && File(rec.localFilePath).exists()
                    val hasCloud = rec.storageUrl.isNotEmpty()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 3.dp else 0.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                        if (isPlaying) {
                                            onStopPlayback()
                                        } else {
                                            onPlayRecording(rec)
                                        }
                                    },
                                    colors = if (isPlaying) IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error) else IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
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
            }
        }
    }
}
