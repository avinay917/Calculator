package com.example.authapp.ui.components

import androidx.compose.foundation.BorderStroke
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

    val nonCallRecordings = remember(recordings) {
        recordings.filter { !it.streamType.equals("call", ignoreCase = true) }
    }

    val filteredList = remember(nonCallRecordings, currentFilter) {
        when (currentFilter) {
            "SCHEDULED" -> nonCallRecordings.filter {
                it.streamType.equals("scheduled", ignoreCase = true) ||
                    it.localFilePath.contains("scheduled", ignoreCase = true)
            }
            "LIVE" -> nonCallRecordings.filter {
                !it.streamType.equals("scheduled", ignoreCase = true) &&
                    !it.localFilePath.contains("scheduled", ignoreCase = true) &&
                    !it.streamType.contains("photo", ignoreCase = true)
            }
            else -> nonCallRecordings
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Floating Pill Header
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.96f),
            shadowElevation = 6.dp,
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloud Recordings",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${nonCallRecordings.size} captures • Call audio in Call History",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filters Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val schedCount = remember(nonCallRecordings) { nonCallRecordings.count { it.streamType.equals("scheduled", ignoreCase = true) || it.localFilePath.contains("scheduled", ignoreCase = true) } }
            val liveCount = remember(nonCallRecordings) { nonCallRecordings.count { !it.streamType.equals("scheduled", ignoreCase = true) && !it.localFilePath.contains("scheduled", ignoreCase = true) && !it.streamType.contains("photo", ignoreCase = true) } }
            listOf(
                Triple("ALL", "All (${nonCallRecordings.size})", null),
                Triple("LIVE", "Live Streams ($liveCount)", Icons.Default.FiberManualRecord),
                Triple("SCHEDULED", "Scheduled ($schedCount)", Icons.Default.Schedule)
            ).forEach { (key, label, icon) ->
                FilterChip(
                    selected = currentFilter == key,
                    onClick = { onFilterChange(key) },
                    shape = CircleShape,
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
            EmptyStateCard(
                icon = Icons.Default.FolderZip,
                title = if (currentFilter == "ALL") "No recordings saved yet" else "No $currentFilter recordings found",
                subtitle = "Record live audio or video streams from child devices to save and play them here anytime.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
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
