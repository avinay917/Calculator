package com.example.authapp.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ActivityTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    callLogsCount: Int,
    smsLogsCount: Int,
    whatsAppLogsCount: Int,
    webHistoryCount: Int,
    packageEventsCount: Int,
    notificationsCount: Int,
    snapshotsCount: Int,
    galleryCount: Int,
    fileExplorerCount: Int,
    clipboardLogsCount: Int
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            text = { Text("Calls ($callLogsCount)", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            text = { Text("SMS ($smsLogsCount)", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            text = { Text("WhatsApp ($whatsAppLogsCount)", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            text = { Text("Web History ($webHistoryCount)", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = selectedTab == 4,
            onClick = { onTabSelected(4) },
            text = { Text("App Events ($packageEventsCount)", fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = selectedTab == 5,
            onClick = { onTabSelected(5) },
            text = { Text("SIM & Network", fontWeight = if (selectedTab == 5) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = selectedTab == 6,
            onClick = { onTabSelected(6) },
            text = { Text("Notifications ($notificationsCount)", fontWeight = if (selectedTab == 6) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = selectedTab == 7,
            onClick = { onTabSelected(7) },
            text = { Text("Snapshots ($snapshotsCount)", fontWeight = if (selectedTab == 7) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = selectedTab == 8,
            onClick = { onTabSelected(8) },
            text = { Text("Media Gallery ($galleryCount)", fontWeight = if (selectedTab == 8) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = selectedTab == 9,
            onClick = { onTabSelected(9) },
            text = { Text("File Explorer ($fileExplorerCount)", fontWeight = if (selectedTab == 9) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = selectedTab == 10,
            onClick = { onTabSelected(10) },
            text = { Text("Clipboard ($clipboardLogsCount)", fontWeight = if (selectedTab == 10) FontWeight.Bold else FontWeight.Normal) },
            icon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
}
