package com.example.authapp.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.authapp.analytics.AppAnalytics
import com.example.authapp.data.DeviceHealth
import com.example.authapp.data.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Chat

@Composable
fun ChildUserCard(
    user: User,
    deviceHealth: DeviceHealth? = null,
    alertsCount: Int = 0,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSnapshotClick: () -> Unit,
    onScreenClick: () -> Unit = {},
    onActivityClick: () -> Unit,
    onWhatsAppClick: (() -> Unit)? = null,
    onAlertsClick: () -> Unit,
    onScheduleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showHealthDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Child Info Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (user.isOnline) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = if (user.isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.name.ifEmpty { "Child Device" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (user.isOnline) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (user.isOnline) Color(0xFF86EFAC) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (user.isOnline) Color(0xFF16A34A) else Color(0xFF9CA3AF))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (user.isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (user.isOnline) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!user.isOnline && user.lastSeen > 0L) {
                val lastSeenText = remember(user.lastSeen) {
                    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    sdf.format(Date(user.lastSeen))
                }
                Text(
                    text = "Last seen: $lastSeenText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 54.dp, top = 2.dp)
                )
            }

            // Device Health Quick Bar (Battery, Service, Permissions)
            if (deviceHealth != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val serviceHealth = deviceHealth.serviceHealth
                val battery = serviceHealth?.batteryPercent ?: -1
                val isCharging = serviceHealth?.isCharging == true
                val isServiceRunning = serviceHealth?.isForegroundServiceRunning == true
                val perms = deviceHealth.permissions
                val allPermsGranted = perms.isNotEmpty() && perms.values.none { !it }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Battery Pill
                        if (battery >= 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                val batColor = if (battery > 30) Color(0xFF2E7D32) else if (battery > 15) Color(0xFFEF6C00) else Color(0xFFC62828)
                                Text(
                                    text = if (isCharging) "⚡ $battery%" else "🔋 $battery%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = batColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Protection Service Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isServiceRunning) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ) {
                            Text(
                                text = if (isServiceRunning) "🛡️ Active" else "⚠️ Off",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isServiceRunning) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Permission Warning (if any denied)
                        if (!allPermsGranted && perms.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFF3E0)
                            ) {
                                Text(
                                    text = "⚠️ Perms",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFE65100),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Clickable Details Info Button
                    TextButton(
                        onClick = { showHealthDialog = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "Status ❯",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Low Battery Critical Warning Alert (Phase 1)
                if (battery in 0..15 && !isCharging) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Battery Low ($battery%) - Child needs to charge",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Actions Grid — High Contrast & Vibrant Visibility
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Row 1: Live Audio & Live Video
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureActionButton(
                        icon = Icons.Default.Mic,
                        label = "Live Audio",
                        containerColor = Color(0xFF0F766E),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            AppAnalytics.logButtonClick("audio_cast", "ParentScreen", mapOf("childId" to user.uid))
                            if (!user.isOnline) {
                                Toast.makeText(context, "${user.name.ifEmpty { "Child device" }} is currently offline.", Toast.LENGTH_SHORT).show()
                                return@FeatureActionButton
                            }
                            onAudioClick()
                        }
                    )

                    FeatureActionButton(
                        icon = Icons.Default.Videocam,
                        label = "Live Video",
                        containerColor = Color(0xFF1D4ED8),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            AppAnalytics.logButtonClick("video_cast", "ParentScreen", mapOf("childId" to user.uid))
                            if (!user.isOnline) {
                                Toast.makeText(context, "${user.name.ifEmpty { "Child device" }} is currently offline.", Toast.LENGTH_SHORT).show()
                                return@FeatureActionButton
                            }
                            onVideoClick()
                        }
                    )
                }

                // Row 2: Screen Cast & Snapshot
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureActionButton(
                        icon = Icons.Default.Smartphone,
                        label = "Screen Cast",
                        containerColor = Color(0xFF4338CA),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            AppAnalytics.logButtonClick("screen_cast", "ParentScreen", mapOf("childId" to user.uid))
                            if (!user.isOnline) {
                                Toast.makeText(context, "${user.name.ifEmpty { "Child device" }} is currently offline.", Toast.LENGTH_SHORT).show()
                                return@FeatureActionButton
                            }
                            onScreenClick()
                        }
                    )

                    FeatureActionButton(
                        icon = Icons.Default.CameraAlt,
                        label = "Snapshot",
                        containerColor = Color(0xFFC2410C),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            AppAnalytics.logButtonClick("snapshot", "ParentScreen", mapOf("childId" to user.uid))
                            onSnapshotClick()
                        }
                    )
                }

                // Row 3: GPS Track & Activity Logs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureActionButton(
                        icon = Icons.Default.LocationOn,
                        label = "GPS Track",
                        containerColor = Color(0xFFB91C1C),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            AppAnalytics.logButtonClick("gps_location", "ParentScreen", mapOf("childId" to user.uid))
                            onLocationClick()
                        }
                    )

                    FeatureActionButton(
                        icon = Icons.Default.Call,
                        label = "Activity Logs",
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = onActivityClick
                    )
                }

                // Row 4: WhatsApp, Security Alerts & Schedules
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FeatureActionButton(
                        icon = Icons.Default.Chat,
                        label = "WhatsApp",
                        containerColor = Color(0xFF15803D),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = { onWhatsAppClick?.invoke() ?: onActivityClick() }
                    )

                    FeatureActionButton(
                        icon = Icons.Default.Security,
                        label = "Alerts",
                        containerColor = if (alertsCount > 0) Color(0xFF991B1B) else Color(0xFF475569),
                        contentColor = Color.White,
                        badgeCount = alertsCount,
                        modifier = Modifier.weight(1f),
                        onClick = onAlertsClick
                    )

                    FeatureActionButton(
                        icon = Icons.Default.Schedule,
                        label = "Schedules",
                        containerColor = Color(0xFF7E22CE),
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = onScheduleClick
                    )
                }
            }
        }
    }

    if (showHealthDialog) {
        ChildHealthDialog(
            user = user,
            health = deviceHealth,
            onDismiss = { showHealthDialog = false }
        )
    }
}
