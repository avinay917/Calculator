package com.example.authapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.data.RecordingSchedule
import com.example.authapp.data.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildScheduleDialog(
    childUser: User,
    schedules: List<RecordingSchedule>,
    onSaveSchedule: (RecordingSchedule) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var hourInput by remember { mutableIntStateOf(14) }
    var minuteInput by remember { mutableIntStateOf(0) }
    var durationMinutes by remember { mutableIntStateOf(5) }
    var captureType by remember { mutableStateOf("audio") }
    var labelInput by remember { mutableStateOf("Study Time") }

    AppFullScreenDialog(
        title = "${childUser.name.ifEmpty { "Child" }} • Auto-Record Schedules",
        subtitle = "Silent Scheduled Audio & Video Recordings (No Sound)",
        onDismiss = onDismiss
    ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                ) {
                    if (!showAddForm) {
                        Button(
                            onClick = { showAddForm = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add New Auto-Recording Schedule")
                        }
                    } else {
                        // Add Schedule Form
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Create Silent Recording Schedule",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Koi sound ya ghanti nahi bajegi — child phone par silently capture hoga.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = labelInput,
                                    onValueChange = { labelInput = it },
                                    label = { Text("Label (e.g. Night Sleep, Tuition, Study)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Capture Type Selector (Audio vs Video)
                                Text(
                                    text = "Recording Type:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = captureType == "audio",
                                        onClick = { captureType = "audio" },
                                        leadingIcon = {
                                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = { Text("🎙️ Silent Audio", fontWeight = FontWeight.Bold) }
                                    )
                                    FilterChip(
                                        selected = captureType == "video",
                                        onClick = { captureType = "video" },
                                        leadingIcon = {
                                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = { Text("🎥 Silent Video", fontWeight = FontWeight.Bold) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = hourInput.toString(),
                                        onValueChange = { hourInput = it.toIntOrNull()?.coerceIn(0, 23) ?: 0 },
                                        label = { Text("Hour (0-23)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = minuteInput.toString(),
                                        onValueChange = { minuteInput = it.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                                        label = { Text("Minute (0-59)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Duration:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    listOf(3, 5, 10, 15, 30).forEach { dur ->
                                        FilterChip(
                                            selected = durationMinutes == dur,
                                            onClick = { durationMinutes = dur },
                                            label = { Text("${dur}m", fontWeight = FontWeight.Bold) },
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { showAddForm = false }) {
                                        Text("Cancel")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        shape = RoundedCornerShape(12.dp),
                                        onClick = {
                                            val newSched = RecordingSchedule(
                                                hour = hourInput,
                                                minute = minuteInput,
                                                durationMinutes = durationMinutes,
                                                isEnabled = true,
                                                repeatDaily = true,
                                                captureType = captureType,
                                                label = labelInput.ifEmpty { if (captureType == "video") "Auto-Video" else "Auto-Audio" }
                                            )
                                            onSaveSchedule(newSched)
                                            showAddForm = false
                                        }
                                    ) {
                                        Text("Save Schedule")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Configured Schedules (${schedules.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (schedules.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateBox(
                                icon = Icons.Default.Alarm,
                                message = "No scheduled recordings configured.\nAdd a schedule to automatically record child ambient audio daily."
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(schedules) { sched ->
                                ScheduleListItem(
                                    schedule = sched,
                                    onToggle = { toggled ->
                                        onSaveSchedule(sched.copy(isEnabled = toggled))
                                    },
                                    onDelete = {
                                        onDeleteSchedule(sched.id)
                                    }
                                )
                            }
                        }
                    }
                }
    }
}

@Composable
fun ScheduleListItem(
    schedule: RecordingSchedule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val timeFormatted = remember(schedule.hour, schedule.minute) {
        val h = if (schedule.hour == 0) 12 else if (schedule.hour > 12) schedule.hour - 12 else schedule.hour
        val amPm = if (schedule.hour < 12) "AM" else "PM"
        val m = String.format(java.util.Locale.US, "%02d", schedule.minute)
        "$h:$m $amPm"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            val isVideo = schedule.captureType.equals("video", ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isVideo) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isVideo) Icons.Default.Videocam else Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isVideo) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = schedule.label.ifEmpty { if (isVideo) "Auto-Video" else "Auto-Audio" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = (if (isVideo) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isVideo) "🎥 Video" else "🎙️ Audio",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (isVideo) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                val dateLabel = if (schedule.targetDate.isNotBlank()) "📅 Date: ${schedule.targetDate}" else if (schedule.repeatDaily) "🔄 Daily Repeat" else "One-Time"
                Text(
                    text = "$timeFormatted • ${schedule.durationMinutes} mins • $dateLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = schedule.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.scale(0.85f)
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
