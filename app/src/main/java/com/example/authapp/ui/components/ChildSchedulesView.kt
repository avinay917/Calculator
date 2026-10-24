package com.example.authapp.ui.components

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
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Scheduled Auto-Recordings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Offline-Ready • Auto Ambient Capture",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF10B981)
                )
            }

            Button(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(12.dp),
                enabled = selectedChild != null,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Schedule", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Child Device Selector Chips (if multiple children)
        if (childUsers.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(childUsers) { child ->
                    FilterChip(
                        selected = child.uid == selectedChild?.uid,
                        onClick = { selectedChildId = child.uid },
                        label = { Text(child.name.ifEmpty { "Child" }) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (child.isOnline) Color(0xFF10B981) else Color.Gray)
                            )
                        }
                    )
                }
            }
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No child device connected yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (activeSchedules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Koi active schedule nahi hai",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "'+ Schedule' par click karke time aur duration set karein",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (schedule.isEnabled) Color(0xFF10B981).copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (schedule.isEnabled) Color(0xFF10B981) else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", schedule.hour, schedule.minute)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = timeFormatted,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${schedule.durationMinutes} min",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF10B981),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = if (schedule.label.isNotBlank()) schedule.label else "Ambient Audio Capture",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = if (schedule.repeatDaily) "Repeat Daily • Offline Active" else "One-Time Alarm",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (schedule.isEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
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
