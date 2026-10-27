package com.example.authapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.authapp.data.*

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
    whatsAppLogs: List<WhatsAppLogItem> = emptyList(),
    snapshots: List<SnapshotInfo> = emptyList(),
    mediaItems: List<MediaItemInfo> = emptyList(),
    youtubeLogs: List<YouTubeLogItem> = emptyList(),
    mediaGallery: List<MediaGalleryItem> = emptyList(),
    fileExplorer: List<FileExplorerItem> = emptyList(),
    clipboardLogs: List<ClipboardLogItem> = emptyList(),
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    val audioPlayer = rememberActivityAudioPlayer()

    val filteredCallLogs = remember(callLogs, searchQuery) {
        if (searchQuery.isBlank()) callLogs
        else callLogs.filter { it.name.contains(searchQuery, ignoreCase = true) || it.number.contains(searchQuery) }
    }

    val filteredNotifications = remember(notifications, searchQuery) {
        if (searchQuery.isBlank()) notifications
        else notifications.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.text.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredSmsLogs = remember(smsLogs, searchQuery) {
        if (searchQuery.isBlank()) smsLogs
        else smsLogs.filter { it.address.contains(searchQuery, ignoreCase = true) || it.body.contains(searchQuery, ignoreCase = true) }
    }

    var whatsAppFilterType by remember { mutableStateOf("ALL") }
    var selectedWhatsAppContact by remember { mutableStateOf<String?>(null) }
    val whatsAppContacts = remember(whatsAppLogs) {
        whatsAppLogs.map { it.senderName.ifEmpty { "WhatsApp Contact" } }.distinct().sorted()
    }

    val filteredWhatsAppLogs = remember(whatsAppLogs, searchQuery, whatsAppFilterType, selectedWhatsAppContact) {
        whatsAppLogs.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.senderName.contains(searchQuery, ignoreCase = true) ||
                    item.messageText.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (whatsAppFilterType) {
                "CHATS" -> item.type.uppercase() == "CHAT"
                "STATUS" -> item.type.uppercase() == "STATUS"
                "MEDIA" -> listOf("AUDIO", "PHOTO", "VIDEO").contains(item.type.uppercase())
                else -> true
            }
            val matchesContact = selectedWhatsAppContact == null || (item.senderName.ifEmpty { "WhatsApp User" }).equals(selectedWhatsAppContact, ignoreCase = true)
            matchesSearch && matchesFilter && matchesContact
        }
    }

    val filteredWebHistory = remember(webHistory, searchQuery) {
        if (searchQuery.isBlank()) webHistory
        else webHistory.filter { it.title.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true) }
    }

    val filteredPackageEvents = remember(packageEvents, searchQuery) {
        if (searchQuery.isBlank()) packageEvents
        else packageEvents.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    val filteredSnapshots = remember(snapshots, searchQuery) {
        if (searchQuery.isBlank()) snapshots
        else snapshots.filter { it.cameraFacing.contains(searchQuery, ignoreCase = true) }
    }

    var selectedGalleryItemForDetails by remember { mutableStateOf<MediaGalleryItem?>(null) }
    var selectedFileItemForDetails by remember { mutableStateOf<FileExplorerItem?>(null) }

    val effectiveGallery = remember(mediaGallery, mediaItems) {
        if (mediaGallery.isNotEmpty()) mediaGallery
        else mediaItems.map {
            MediaGalleryItem(
                id = it.id,
                fileName = it.displayName,
                filePath = it.folderName,
                fileType = if (it.mimeType.contains("video", ignoreCase = true)) "VIDEO" else "IMAGE",
                sizeBytes = it.sizeBytes,
                dateAdded = it.timestamp
            )
        }
    }

    val filteredGallery = remember(effectiveGallery, searchQuery) {
        if (searchQuery.isBlank()) effectiveGallery
        else effectiveGallery.filter {
            it.fileName.contains(searchQuery, ignoreCase = true) || it.filePath.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredFileExplorer = remember(fileExplorer, searchQuery) {
        if (searchQuery.isBlank()) fileExplorer
        else fileExplorer.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredClipboardLogs = remember(clipboardLogs, searchQuery) {
        if (searchQuery.isBlank()) clipboardLogs
        else clipboardLogs.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
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
                                    text = "${childUser.name.ifEmpty { "Child" }} • Activity Center",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Real-time Activity Logs & Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search activities, logs, apps...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ActivityTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        callLogsCount = filteredCallLogs.size,
                        smsLogsCount = filteredSmsLogs.size,
                        whatsAppLogsCount = filteredWhatsAppLogs.size,
                        webHistoryCount = filteredWebHistory.size,
                        packageEventsCount = filteredPackageEvents.size,
                        notificationsCount = filteredNotifications.size,
                        snapshotsCount = filteredSnapshots.size,
                        galleryCount = filteredGallery.size,
                        fileExplorerCount = filteredFileExplorer.size,
                        clipboardLogsCount = filteredClipboardLogs.size
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ActivityTabContent(
                        selectedTab = selectedTab,
                        searchQuery = searchQuery,
                        filteredCallLogs = filteredCallLogs,
                        filteredSmsLogs = filteredSmsLogs,
                        filteredWhatsAppLogs = filteredWhatsAppLogs,
                        whatsAppLogs = whatsAppLogs,
                        whatsAppContacts = whatsAppContacts,
                        whatsAppFilterType = whatsAppFilterType,
                        onWhatsAppFilterChange = { whatsAppFilterType = it },
                        selectedWhatsAppContact = selectedWhatsAppContact,
                        onWhatsAppContactChange = { selectedWhatsAppContact = it },
                        filteredWebHistory = filteredWebHistory,
                        filteredPackageEvents = filteredPackageEvents,
                        filteredNotifications = filteredNotifications,
                        filteredSnapshots = filteredSnapshots,
                        filteredGallery = filteredGallery,
                        filteredFileExplorer = filteredFileExplorer,
                        filteredClipboardLogs = filteredClipboardLogs,
                        simInfo = simInfo,
                        networkHistory = networkHistory,
                        currentlyPlayingAudioUrl = audioPlayer.currentlyPlayingAudioUrl,
                        isAudioBuffering = audioPlayer.isAudioBuffering,
                        audioPositionMs = audioPlayer.audioPositionMs,
                        audioDurationMs = audioPlayer.audioDurationMs,
                        audioPlaybackSpeed = audioPlayer.audioPlaybackSpeed,
                        onSpeedToggle = { audioPlayer.togglePlaybackSpeed() },
                        onPlayAudio = { audioPlayer.playAudio(it) },
                        onSelectGalleryItem = { selectedGalleryItemForDetails = it },
                        onSelectFileItem = { selectedFileItemForDetails = it }
                    )
                }
            }

            selectedGalleryItemForDetails?.let { media ->
                GalleryItemDetailDialog(
                    media = media,
                    onDismiss = { selectedGalleryItemForDetails = null }
                )
            }

            selectedFileItemForDetails?.let { file ->
                FileItemDetailDialog(
                    file = file,
                    onDismiss = { selectedFileItemForDetails = null }
                )
            }
        }
    }
}
