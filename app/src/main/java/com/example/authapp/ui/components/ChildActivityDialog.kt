package com.example.authapp.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.authapp.data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildActivityDialog(
    childUser: User,
    callLogs: List<CallLogItem>,
    notifications: List<NotificationItem>,
    smsLogs: List<SmsItem> = emptyList(),
    webHistory: List<WebHistoryItem> = emptyList(),
    networkHistory: List<NetworkHistoryItem> = emptyList(),
    simInfo: SimCardInfo? = null,
    packageEvents: List<AppInstallEvent> = emptyList(),
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Calls, 1=SMS, 2=Web, 3=Wi-Fi, 4=Apps, 5=SIM, 6=Alerts

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = when (selectedTab) {
                            0 -> Icons.Default.Call
                            1 -> Icons.Default.Email
                            2 -> Icons.Default.Language
                            3 -> Icons.Default.Wifi
                            4 -> Icons.Default.Apps
                            5 -> Icons.Default.SimCard
                            else -> Icons.Default.Notifications
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Activity: ${childUser.name.ifEmpty { "Child" }}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Tab Switcher
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Calls (${callLogs.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("SMS (${smsLogs.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Web (${webHistory.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Wi-Fi (${networkHistory.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { Text("Apps (${packageEvents.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                    Tab(
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5 },
                        text = { Text("SIM", style = MaterialTheme.typography.labelSmall) }
                    )
                    Tab(
                        selected = selectedTab == 6,
                        onClick = { selectedTab = 6 },
                        text = { Text("Alerts (${notifications.size})", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        // Call Logs List
                        if (callLogs.isEmpty()) {
                            EmptyStateBox(Icons.Default.Call, "No call logs recorded yet")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(callLogs) { log ->
                                    CallLogListItem(log)
                                }
                            }
                        }
                    }
                    1 -> {
                        // SMS Logs List
                        if (smsLogs.isEmpty()) {
                            EmptyStateBox(Icons.Default.Email, "No SMS messages recorded yet")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(smsLogs) { sms ->
                                    SmsListItem(sms)
                                }
                            }
                        }
                    }
                    2 -> {
                        // Web History
                        if (webHistory.isEmpty()) {
                            EmptyStateBox(Icons.Default.Language, "No web browsing history yet")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(webHistory) { item ->
                                    WebHistoryListItem(item)
                                }
                            }
                        }
                    }
                    3 -> {
                        // Wi-Fi / Network History
                        if (networkHistory.isEmpty()) {
                            EmptyStateBox(Icons.Default.Wifi, "No network history recorded yet")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(networkHistory) { net ->
                                    NetworkHistoryListItem(net)
                                }
                            }
                        }
                    }
                    4 -> {
                        // App Install / Uninstall Events
                        if (packageEvents.isEmpty()) {
                            EmptyStateBox(Icons.Default.Apps, "No app install/uninstall events yet")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(packageEvents) { event ->
                                    AppInstallEventItem(event)
                                }
                            }
                        }
                    }
                    5 -> {
                        // SIM Card Info
                        if (simInfo == null) {
                            EmptyStateBox(Icons.Default.SimCard, "No SIM card information reported yet")
                        } else {
                            SimInfoCard(simInfo)
                        }
                    }
                    else -> {
                        // Notification & Security Alerts
                        if (notifications.isEmpty()) {
                            EmptyStateBox(Icons.Default.Notifications, "No notifications captured yet")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(notifications) { notif ->
                                    NotificationListItem(notif)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateBox(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CallLogListItem(log: CallLogItem) {
    val typeIcon = when (log.type.uppercase()) {
        "OUTGOING" -> Icons.Default.CallMade
        "INCOMING" -> Icons.Default.CallReceived
        "MISSED" -> Icons.Default.CallMissed
        "REJECTED" -> Icons.Default.PhoneDisabled
        else -> Icons.Default.Phone
    }
    val iconTint = when (log.type.uppercase()) {
        "OUTGOING" -> Color(0xFF1976D2)
        "INCOMING" -> Color(0xFF388E3C)
        "MISSED" -> Color(0xFFD32F2F)
        "REJECTED" -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val timeStr = remember(log.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }
    val durationStr = remember(log.durationSeconds) {
        if (log.durationSeconds == 0L) "0s"
        else {
            val mins = log.durationSeconds / 60
            val secs = log.durationSeconds % 60
            if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        }
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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(typeIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.name.ifEmpty { log.number.ifEmpty { "Unknown Caller" } },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (log.name.isNotEmpty() && log.number.isNotEmpty()) {
                    Text(
                        text = log.number,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$timeStr • Duration: $durationStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun SmsListItem(sms: SmsItem) {
    val isIncoming = sms.type.uppercase() == "INCOMING"
    val icon = if (isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade
    val tint = if (isIncoming) Color(0xFF388E3C) else Color(0xFF1976D2)

    val timeStr = remember(sms.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(sms.timestamp))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sms.address.ifEmpty { "Unknown" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isIncoming) "Received" else "Sent",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sms.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun WebHistoryListItem(item: WebHistoryItem) {
    val timeStr = remember(item.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (item.isBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = if (item.isBlocked) Icons.Default.Warning else Icons.Default.Language,
                contentDescription = null,
                tint = if (item.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifEmpty { item.url },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun NetworkHistoryListItem(item: NetworkHistoryItem) {
    val connectTimeStr = remember(item.connectedAt) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(item.connectedAt))
    }
    val disconnectTimeStr = remember(item.disconnectedAt) {
        if (item.disconnectedAt > 0L) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(Date(item.disconnectedAt))
        } else "Active now"
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
            Icon(
                imageVector = if (item.networkType == "WIFI") Icons.Default.Wifi else Icons.Default.SignalCellularAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.ssid.ifEmpty { item.networkType },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Connected: $connectTimeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Disconnected: $disconnectTimeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun AppInstallEventItem(event: AppInstallEvent) {
    val isInstalled = event.eventType.uppercase() == "INSTALLED"
    val icon = if (isInstalled) Icons.Default.AddCircle else Icons.Default.Delete
    val tint = if (isInstalled) Color(0xFF388E3C) else Color(0xFFD32F2F)

    val timeStr = remember(event.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(event.timestamp))
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
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.appName.ifEmpty { event.packageName },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${event.eventType} • $timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }
        }
    }
}

@Composable
fun SimInfoCard(simInfo: SimCardInfo) {
    val timeStr = remember(simInfo.lastUpdated) {
        if (simInfo.lastUpdated > 0L) {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            sdf.format(Date(simInfo.lastUpdated))
        } else "Never"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SIM Card Details",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Carrier: ${simInfo.operatorName.ifEmpty { "Unknown" }}", style = MaterialTheme.typography.bodyMedium)
            Text("Country: ${simInfo.countryIso.ifEmpty { "N/A" }}", style = MaterialTheme.typography.bodyMedium)
            Text("State: ${simInfo.simState}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Last Checked: $timeStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun NotificationListItem(notif: NotificationItem) {
    val timeStr = remember(notif.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(notif.timestamp))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = notif.appName.ifEmpty { notif.packageName },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (notif.title.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notif.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            if (notif.text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notif.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
