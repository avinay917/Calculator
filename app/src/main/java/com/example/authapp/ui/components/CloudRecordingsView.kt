package com.example.authapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
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
            "AUDIO" -> recordings.filter { it.streamType.equals("audio", ignoreCase = true) }
            "VIDEO" -> recordings.filter { it.streamType.equals("video", ignoreCase = true) }
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentFilter == "ALL",
                onClick = { onFilterChange("ALL") },
                label = { Text("All (${recordings.size})", style = MaterialTheme.typography.labelSmall) }
            )
            val audioCount = recordings.count { it.streamType.equals("audio", ignoreCase = true) }
            FilterChip(
                selected = currentFilter == "AUDIO",
                onClick = { onFilterChange("AUDIO") },
                label = { Text("Audio ($audioCount)", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            )
            val videoCount = recordings.count { it.streamType.equals("video", ignoreCase = true) }
            FilterChip(
                selected = currentFilter == "VIDEO",
                onClick = { onFilterChange("VIDEO") },
                label = { Text("Video ($videoCount)", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(14.dp))
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
                    val isVideo = rec.streamType.equals("video", ignoreCase = true)
                    val childName = childNameMap[rec.childId] ?: "Child (${rec.childId.take(5)})"

                    val formattedDate = remember(rec.startTime) {
                        if (rec.startTime > 0L) {
                            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(rec.startTime))
                        } else {
                            "Unknown Date"
                        }
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
                            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            // Media Type Icon
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
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

                            Spacer(modifier = Modifier.width(12.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = childName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = formattedDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Cloud & Local storage tags
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (hasCloud) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFE1F5FE)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CloudDone,
                                                    contentDescription = null,
                                                    tint = Color(0xFF0288D1),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "Cloud Backed",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = Color(0xFF0277BD),
                                                    fontSize = 10.sp
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
                                                text = "💾 Local",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = Color(0xFF2E7D32),
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Play / Stop Button
                            FilledTonalIconButton(
                                onClick = {
                                    if (isPlaying) {
                                        onStopPlayback()
                                    } else {
                                        onPlayRecording(rec)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
