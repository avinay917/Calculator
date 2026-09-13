package com.example.authapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.authapp.data.DeviceHealth
import com.example.authapp.data.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChildHealthDialog(
    user: User,
    health: DeviceHealth?,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Device Health & Status",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = user.name.ifEmpty { "Child Device" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                if (health == null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "No telemetry received yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Device health will update automatically when the child app connects to the internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // 1. Device Hardware Info
                    health.deviceInfo?.let { dev ->
                        SectionHeader(title = "Device Hardware", icon = Icons.Default.Smartphone)
                        InfoRow(label = "Model", value = "${dev.manufacturer.replaceFirstChar { it.uppercase() }} ${dev.model}")
                        InfoRow(label = "Android Version", value = "Android ${dev.androidVersion} (API ${dev.sdkInt})")
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 2. Battery & Service State
                    health.serviceHealth?.let { s ->
                        SectionHeader(title = "Battery & Service", icon = Icons.Default.BatteryChargingFull)
                        val batteryText = if (s.batteryPercent >= 0) {
                            if (s.isCharging) "${s.batteryPercent}% ⚡ (Charging)" else "${s.batteryPercent}%"
                        } else "Unknown"
                        InfoRow(label = "Battery Level", value = batteryText)
                        InfoRow(
                            label = "Protection Service",
                            value = if (s.isForegroundServiceRunning) "Running 🟢" else "Stopped 🔴",
                            isSuccess = s.isForegroundServiceRunning
                        )
                        InfoRow(label = "Current State", value = s.currentServiceState)
                        if (s.lastHeartbeat > 0L) {
                            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
                            InfoRow(label = "Last Heartbeat", value = sdf.format(Date(s.lastHeartbeat)))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 3. App Version
                    health.appVersion?.let { ver ->
                        SectionHeader(title = "App Version", icon = Icons.Default.Apps)
                        InfoRow(label = "Installed Version", value = "${ver.versionName} (Build #${ver.versionCode})")
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 4. Permissions Matrix
                    SectionHeader(title = "Permission Health", icon = Icons.Default.VerifiedUser)
                    val permissions = health.permissions
                    PermissionRow(name = "Microphone (Audio)", granted = permissions["microphone"] == true)
                    PermissionRow(name = "Camera (Video)", granted = permissions["camera"] == true)
                    PermissionRow(name = "GPS Location", granted = permissions["location"] == true)
                    PermissionRow(name = "Phone State", granted = permissions["readPhoneState"] == true)
                    PermissionRow(name = "Call Log", granted = permissions["readCallLog"] == true)
                    PermissionRow(name = "Post Notifications", granted = permissions["notifications"] == true)
                    PermissionRow(name = "Battery Optimization Disabled", granted = permissions["batteryOptimizationIgnored"] == true)
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, isSuccess: Boolean? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = when (isSuccess) {
                true -> Color(0xFF2E7D32)
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun PermissionRow(name: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall
        )
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (granted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        ) {
            Text(
                text = if (granted) "✅ Granted" else "❌ Denied",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
