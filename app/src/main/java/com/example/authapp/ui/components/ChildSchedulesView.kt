package com.example.authapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.data.RecordingSchedule
import com.example.authapp.data.User
import java.util.Locale

@Composable
fun ChildSchedulesView(
    childUsers: List<User>,
    schedulesMap: Map<String, List<RecordingSchedule>>,
    onSaveSchedule: (String, RecordingSchedule) -> Unit,
    onDeleteSchedule: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedChildId by remember(childUsers) {
        mutableStateOf(childUsers.firstOrNull()?.uid ?: "")
    }

    val selectedChild = childUsers.find { it.uid == selectedChildId } ?: childUsers.firstOrNull()
    val activeSchedules = (selectedChild?.let { schedulesMap[it.uid] } ?: emptyList())
        .sortedBy { it.hour * 60 + it.minute }

    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Floating Pill Header
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.96f),
            shadowElevation = 6.dp,
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Scheduled Recordings",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Offline-Ready • Silent Audio & Video",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { showCreateDialog = true },
                    shape = CircleShape,
                    enabled = selectedChild != null,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Schedule",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Child Device Selector Chips (if multiple children)
        ChildSelectorRow(
            childUsers = childUsers,
            selectedChildId = selectedChild?.uid.orEmpty(),
            onSelectChild = { selectedChildId = it }
        )
        if (childUsers.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Offline Guarantee Banner Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚡ 100% Offline Guaranteed",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "अगर चाइल्ड के फोन का इंटरनेट बंद भी होगा, तो भी यह तय समय पर रिकॉर्ड होगा और इंटरनेट ऑन होते ही पैरेंट ऐप पर ऑटो-अपलोड हो जाएगा।",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Schedules List
        if (selectedChild == null) {
            EmptyStateCard(
                icon = Icons.Default.Smartphone,
                title = "No Child Device Connected",
                subtitle = "Pair a child device to schedule automatic background recordings.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            )
        } else if (activeSchedules.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Schedule,
                title = "Koi active schedule nahi hai",
                subtitle = "'+ Schedule' par click karke time aur duration set karein",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(activeSchedules, key = { it.id }) { schedule ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isVideo = schedule.captureType.equals("video", ignoreCase = true)
                            val accentColor = if (isVideo) Color(0xFFA855F7) else Color(0xFF10B981)

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (schedule.isEnabled) accentColor.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (schedule.isEnabled) accentColor else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                val h = if (schedule.hour == 0) 12 else if (schedule.hour > 12) schedule.hour - 12 else schedule.hour
                                val amPm = if (schedule.hour < 12) "AM" else "PM"
                                val m = String.format(Locale.getDefault(), "%02d", schedule.minute)
                                val timeFormatted = "$h:$m $amPm"

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = timeFormatted,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = accentColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${schedule.durationMinutes} min",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = accentColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (isVideo) "🎥 Video" else "🎙️ Audio",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = if (schedule.label.isNotBlank()) schedule.label else (if (isVideo) "Silent Video Capture" else "Silent Audio Capture"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                val dateText = if (schedule.targetDate.isNotBlank()) "📅 Date: ${schedule.targetDate} (One-Time)" else if (schedule.repeatDaily) "🔄 Daily Repeat (Everyday)" else "⏱️ One-Time Today"
                                Text(
                                    text = dateText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                    color = if (schedule.isEnabled) accentColor else MaterialTheme.colorScheme.outline
                                )
                            }

                            // Toggle Switch
                            Switch(
                                checked = schedule.isEnabled,
                                onCheckedChange = { checked ->
                                    val updated = schedule.copy(isEnabled = checked)
                                    selectedChild?.let { onSaveSchedule(it.uid, updated) }
                                }
                            )

                            // Delete Button
                            IconButton(
                                onClick = {
                                    selectedChild?.let { onDeleteSchedule(it.uid, schedule.id) }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal to create new schedule
    if (showCreateDialog && selectedChild != null) {
        ChildScheduleDialog(
            childUser = selectedChild,
            schedules = activeSchedules,
            onSaveSchedule = { newSchedule ->
                onSaveSchedule(selectedChild.uid, newSchedule)
                showCreateDialog = false
            },
            onDeleteSchedule = { scheduleId ->
                onDeleteSchedule(selectedChild.uid, scheduleId)
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}
