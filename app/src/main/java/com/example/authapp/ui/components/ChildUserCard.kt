package com.example.authapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.authapp.data.DeviceHealth
import com.example.authapp.data.User
import com.example.authapp.utils.formatTime

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
                        .background(if (user.isOnline) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant),
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
                    border = BorderStroke(
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
                val lastSeenText = remember(user.lastSeen) { user.lastSeen.formatTime() }
                Text(
                    text = "Last seen: $lastSeenText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 54.dp, top = 2.dp)
                )
            }

            // Device Health Bar (Battery, Service, Permissions)
            if (deviceHealth != null) {
                Spacer(modifier = Modifier.height(10.dp))
                ChildUserCardHealthBar(
                    deviceHealth = deviceHealth,
                    onShowHealthDialog = { showHealthDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Grid
            ChildUserCardActionsGrid(
                user = user,
                alertsCount = alertsCount,
                onAudioClick = onAudioClick,
                onVideoClick = onVideoClick,
                onLocationClick = onLocationClick,
                onSnapshotClick = onSnapshotClick,
                onScreenClick = onScreenClick,
                onActivityClick = onActivityClick,
                onWhatsAppClick = onWhatsAppClick,
                onAlertsClick = onAlertsClick,
                onScheduleClick = onScheduleClick
            )
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
