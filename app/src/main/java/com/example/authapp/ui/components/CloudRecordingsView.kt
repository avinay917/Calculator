package com.example.authapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User

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

        // Filters Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val callCount = remember(recordings) { recordings.count { it.streamType.equals("call", ignoreCase = true) } }
            val schedCount = remember(recordings) { recordings.count { it.streamType.equals("scheduled", ignoreCase = true) || it.localFilePath.contains("scheduled", ignoreCase = true) } }
            val liveCount = remember(recordings) { recordings.count { !it.streamType.equals("call", ignoreCase = true) && !it.streamType.equals("scheduled", ignoreCase = true) && !it.localFilePath.contains("scheduled", ignoreCase = true) && !it.streamType.contains("photo", ignoreCase = true) } }
            listOf(
                Triple("ALL", "All (${recordings.size})", null),
                Triple("CALL", "Calls ($callCount)", Icons.Default.Call),
                Triple("SCHEDULED", "Scheduled ($schedCount)", Icons.Default.Schedule),
                Triple("LIVE", "Live ($liveCount)", Icons.Default.FiberManualRecord)
            ).forEach { (key, label, icon) ->
                FilterChip(
                    selected = currentFilter == key,
                    onClick = { onFilterChange(key) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (key == "LIVE") Color.Red else MaterialTheme.colorScheme.primary) } }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                    CloudRecordingItemCard(
                        recording = rec,
                        childName = childNameMap[rec.childId] ?: "Child (${rec.childId.take(5)})",
                        isPlaying = currentlyPlayingRecId == rec.id,
                        onPlayRecording = onPlayRecording,
                        onStopPlayback = onStopPlayback
                    )
                }
            }
        }
    }
}
