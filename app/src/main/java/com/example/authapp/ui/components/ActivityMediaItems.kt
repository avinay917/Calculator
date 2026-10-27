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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.authapp.data.*
import com.example.authapp.utils.DateTimeUtils
import com.example.authapp.utils.formatAsFileSize
import com.example.authapp.utils.formatTime

@Composable
fun WebHistoryListItem(item: WebHistoryItem) {
    ActivityLogCard(
        icon = if (item.isBlocked) Icons.Default.Warning else Icons.Default.Language,
        iconTint = if (item.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        title = item.title.ifEmpty { item.url },
        subtitle = item.url,
        timestampStr = remember(item.timestamp) { item.timestamp.formatTime() },
        badgeText = if (item.isBlocked) "Blocked" else "",
        badgeColor = MaterialTheme.colorScheme.error
    )
}

@Composable
fun NetworkHistoryListItem(item: NetworkHistoryItem) {
    val connectTimeStr = remember(item.connectedAt) { item.connectedAt.formatTime() }
    val disconnectTimeStr = remember(item.disconnectedAt) {
        if (item.disconnectedAt > 0L) DateTimeUtils.formatTimeOnly(item.disconnectedAt) else "Active now"
    }
    ActivityLogCard(
        icon = if (item.networkType == "WIFI") Icons.Default.Wifi else Icons.Default.SignalCellularAlt,
        iconTint = MaterialTheme.colorScheme.primary,
        title = item.ssid.ifEmpty { item.networkType },
        subtitle = "Connected: $connectTimeStr • Disconnected: $disconnectTimeStr",
        timestampStr = connectTimeStr
    )
}

@Composable
fun AppInstallEventItem(event: AppInstallEvent) {
    val isInstalled = event.eventType.uppercase() == "INSTALLED"
    val tint = if (isInstalled) Color(0xFF388E3C) else Color(0xFFD32F2F)
    ActivityLogCard(
        icon = if (isInstalled) Icons.Default.AddCircle else Icons.Default.Delete,
        iconTint = tint,
        title = event.appName.ifEmpty { event.packageName },
        subtitle = event.packageName,
        timestampStr = remember(event.timestamp) { event.timestamp.formatTime() },
        badgeText = event.eventType,
        badgeColor = tint
    )
}

@Composable
fun SnapshotGridItem(snapshot: SnapshotInfo) {
    val uriHandler = LocalUriHandler.current
    ActivityLogCard(
        icon = Icons.Default.CameraAlt,
        iconTint = MaterialTheme.colorScheme.primary,
        title = "Snapshot (${snapshot.cameraFacing.uppercase()} Camera)",
        timestampStr = remember(snapshot.timestamp) { snapshot.timestamp.formatTime() },
        actionIcon = if (snapshot.downloadUrl.isNotEmpty()) Icons.Default.OpenInNew else null,
        onActionClick = if (snapshot.downloadUrl.isNotEmpty()) {
            { try { uriHandler.openUri(snapshot.downloadUrl) } catch (_: Exception) {} }
        } else null
    )
}

@Composable
fun MediaGalleryCardItem(
    media: MediaGalleryItem,
    onClick: () -> Unit
) {
    val isVideo = media.fileType.equals("VIDEO", ignoreCase = true) || media.fileName.endsWith(".mp4", ignoreCase = true)
    val icon = if (isVideo) Icons.Default.Videocam else Icons.Default.Image
    val iconTint = if (isVideo) Color(0xFFE91E63) else Color(0xFF9C27B0)
    val sizeStr = remember(media.sizeBytes) { media.sizeBytes.formatAsFileSize() }

    ActivityLogCard(
        icon = icon,
        iconTint = iconTint,
        title = media.fileName.ifEmpty { "Gallery Item" },
        subtitle = "${media.filePath.ifEmpty { "Gallery" }} ${if (sizeStr.isNotEmpty()) "• $sizeStr" else ""}",
        actionIcon = Icons.Default.ChevronRight,
        onClick = onClick
    )
}

@Composable
fun FileExplorerCardItem(
    file: FileExplorerItem,
    onClick: () -> Unit
) {
    val isDir = file.isDirectory
    val icon = if (isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile
    val iconTint = if (isDir) Color(0xFFFFA000) else Color(0xFF1976D2)
    val sizeStr = remember(file.sizeBytes, isDir) {
        if (isDir) "Folder" else file.sizeBytes.formatAsFileSize()
    }

    ActivityLogCard(
        icon = icon,
        iconTint = iconTint,
        title = file.name.ifEmpty { file.path.substringAfterLast("/") },
        subtitle = "${file.path} ${if (sizeStr.isNotEmpty()) "• $sizeStr" else ""}",
        actionIcon = Icons.Default.ChevronRight,
        onClick = onClick
    )
}

@Composable
fun UnifiedSimAndNetworkView(
    simInfo: SimCardInfo?,
    networkHistory: List<NetworkHistoryItem>
) {
    val activeNetwork = networkHistory.firstOrNull { it.disconnectedAt == 0L } ?: networkHistory.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Section 1: Active SIM Card Details
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SIM Card Details",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Carrier & Cellular Subscription",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (simInfo != null) {
                        val isSimAbsent = simInfo.simState == "ABSENT" || simInfo.operatorName == "No SIM Card"
                        if (isSimAbsent) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SIM Card Removed or Missing",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        val lastCheckedStr = if (simInfo.lastUpdated > 0L) simInfo.lastUpdated.formatTime() else "Recent"

                        InfoRow("Operator / Carrier", simInfo.operatorName.ifEmpty { "Detected" })
                        InfoRow("Country ISO", simInfo.countryIso.ifEmpty { "IN" }.uppercase())
                        InfoRow("SIM Status", if (isSimAbsent) "ABSENT / REMOVED" else simInfo.simState.ifEmpty { "Ready (Active)" })
                        InfoRow("Last Updated", lastCheckedStr)
                    } else {
                        Text(
                            text = "No SIM card information synced yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section 2: Active Network / Wi-Fi Details
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (activeNetwork?.networkType == "WIFI") Icons.Default.Wifi else Icons.Default.SignalCellularAlt,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Network Connection",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (activeNetwork?.networkType == "WIFI") "Wi-Fi Connected 🟢" else "Cellular Data 🟢",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (activeNetwork != null) {
                        InfoRow("Network Type", activeNetwork.networkType)
                        InfoRow("Wi-Fi Name (SSID)", activeNetwork.ssid.ifEmpty { "Connected Network" })
                        val connectStr = activeNetwork.connectedAt.formatTime()
                        InfoRow("Connected Since", connectStr)
                    } else {
                        Text(
                            text = "No active network information reported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section 3: Connection History Header & List
        item {
            Text(
                text = "Past Wi-Fi & Network History (${networkHistory.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (networkHistory.isEmpty()) {
            item {
                EmptyStateBox(Icons.Default.Wifi, "No past network history recorded yet")
            }
        } else {
            items(networkHistory) { net ->
                NetworkHistoryListItem(net)
            }
        }
    }
}
