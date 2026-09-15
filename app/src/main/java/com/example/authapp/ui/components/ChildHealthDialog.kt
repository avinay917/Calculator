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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildHealthDialog(
    user: User,
    health: DeviceHealth?,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "${user.name.ifEmpty { "Child" }} • Device Health",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "System Diagnostics, Hardware & Permissions",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                ) {
                    if (health == null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "No telemetry received yet.",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Device health and sensor telemetry will update automatically as soon as the child device is connected to the network.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // 1. Device Hardware Info Card
                        health.deviceInfo?.let { dev ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    SectionHeader(title = "Device Hardware", icon = Icons.Default.Smartphone)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    InfoRow(label = "Model", value = "${dev.manufacturer.replaceFirstChar { it.uppercase() }} ${dev.model}")
                                    InfoRow(label = "Android Version", value = "Android ${dev.androidVersion} (API ${dev.sdkInt})")
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 2. Battery & Service State Card
                        health.serviceHealth?.let { s ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    SectionHeader(title = "Battery & Service State", icon = Icons.Default.BatteryChargingFull)
                                    Spacer(modifier = Modifier.height(8.dp))
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
                                        val sdf = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault())
                                        InfoRow(label = "Last Heartbeat", value = sdf.format(Date(s.lastHeartbeat)))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 3. App Version Card
                        health.appVersion?.let { ver ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    SectionHeader(title = "Installed Version", icon = Icons.Default.Apps)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    InfoRow(label = "Release", value = "${ver.versionName} (Build #${ver.versionCode})")
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 4. Permissions Matrix Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                SectionHeader(title = "Permission Matrix", icon = Icons.Default.VerifiedUser)
                                Spacer(modifier = Modifier.height(8.dp))
                                val permissions = health.permissions
                                PermissionRow(name = "Microphone (Ambient Audio)", granted = permissions["microphone"] == true)
                                PermissionRow(name = "Camera (Video & Snapshots)", granted = permissions["camera"] == true)
                                PermissionRow(name = "GPS Fine Location", granted = permissions["location"] == true)
                                PermissionRow(name = "Phone State & Calls", granted = permissions["readPhoneState"] == true)
                                PermissionRow(name = "Post Notifications", granted = permissions["postNotifications"] == true || permissions["notifications"] == true)
                                PermissionRow(name = "Display Over Apps (Overlay)", granted = permissions["systemAlertWindow"] == true)
                                PermissionRow(name = "Battery Optimization Disabled", granted = permissions["batteryOptimizationIgnored"] == true)
                            }
                        }
                    }
                }
            }
        }
    }
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
