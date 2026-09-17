package com.example.authapp.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    whatsAppLogs: List<WhatsAppLogItem> = emptyList(),
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) } // 0=Calls, 1=SMS, 2=WhatsApp, 3=Web, 4=Apps, 5=SIM & Network, 6=Notifications
    var searchQuery by remember { mutableStateOf("") }
    var currentlyPlayingAudioUrl by remember { mutableStateOf<String?>(null) }
    var isAudioBuffering by remember { mutableStateOf(false) }
    var audioPositionMs by remember { mutableIntStateOf(0) }
    var audioDurationMs by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        currentlyPlayingAudioUrl = null
        isAudioBuffering = false
        audioPositionMs = 0
        audioDurationMs = 0
    }

    fun playAudio(url: String) {
        if (currentlyPlayingAudioUrl == url) {
            stopAudio()
            return
        }
        stopAudio()
        isAudioBuffering = true
        currentlyPlayingAudioUrl = url

        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnCompletionListener {
                stopAudio()
            }
            setOnErrorListener { _, _, _ ->
                stopAudio()
                true
            }
        }
        try {
            mp.setDataSource(url)
            mp.setOnPreparedListener { player ->
                isAudioBuffering = false
                audioDurationMs = player.duration
                player.start()
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (e: Exception) {
            stopAudio()
        }
    }

    LaunchedEffect(currentlyPlayingAudioUrl) {
        if (currentlyPlayingAudioUrl != null) {
            while (currentlyPlayingAudioUrl != null) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        audioPositionMs = mp.currentPosition
                    }
                }
                kotlinx.coroutines.delay(250L)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAudio()
        }
    }

    val filteredCallLogs = remember(callLogs, searchQuery) {
        if (searchQuery.isBlank()) callLogs
        else callLogs.filter { it.name.contains(searchQuery, ignoreCase = true) || it.number.contains(searchQuery, ignoreCase = true) }
    }

    val filteredSmsLogs = remember(smsLogs, searchQuery) {
        if (searchQuery.isBlank()) smsLogs
        else smsLogs.filter { it.address.contains(searchQuery, ignoreCase = true) || it.body.contains(searchQuery, ignoreCase = true) }
    }

    val filteredWebHistory = remember(webHistory, searchQuery) {
        if (searchQuery.isBlank()) webHistory
        else webHistory.filter { it.title.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true) }
    }

    val filteredPackageEvents = remember(packageEvents, searchQuery) {
        if (searchQuery.isBlank()) packageEvents
        else packageEvents.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    var whatsAppFilterType by rememberSaveable { mutableStateOf("ALL") }

    val filteredWhatsAppLogs = remember(whatsAppLogs, searchQuery, whatsAppFilterType) {
        val baseList = when (whatsAppFilterType) {
            "CHATS" -> whatsAppLogs.filter { it.type.equals("CHAT", ignoreCase = true) }
            "STATUS" -> whatsAppLogs.filter { it.type.equals("STATUS", ignoreCase = true) }
            "MEDIA" -> whatsAppLogs.filter { !it.type.equals("CHAT", ignoreCase = true) && !it.type.equals("STATUS", ignoreCase = true) }
            else -> whatsAppLogs
        }
        if (searchQuery.isBlank()) baseList
        else baseList.filter { it.senderName.contains(searchQuery, ignoreCase = true) || it.messageText.contains(searchQuery, ignoreCase = true) || it.type.contains(searchQuery, ignoreCase = true) }
    }

    val filteredNotifications = remember(notifications, searchQuery) {
        if (searchQuery.isBlank()) notifications
        else notifications.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.title.contains(searchQuery, ignoreCase = true) || it.text.contains(searchQuery, ignoreCase = true) }
    }

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
                                    text = "${childUser.name.ifEmpty { "Child" }} • Activity Center",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Call Logs, WhatsApp Chats & Status, Communications",
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Search Bar for Quick Log Filtering
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search calls, WhatsApp, apps, or text...", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Modern Scrollable Tab Switcher
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 8.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Calls (${filteredCallLogs.size})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("SMS (${filteredSmsLogs.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("WhatsApp (${filteredWhatsAppLogs.size})", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 2) Color(0xFF25D366) else Color.Unspecified) },
                            icon = { Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF25D366)) }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("Web (${filteredWebHistory.size})", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            text = { Text("Apps (${filteredPackageEvents.size})", fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 5,
                            onClick = { selectedTab = 5 },
                            text = { Text("SIM & Network", fontWeight = if (selectedTab == 5) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.SignalCellularAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 6,
                            onClick = { selectedTab = 6 },
                            text = { Text("Notifications (${filteredNotifications.size})", fontWeight = if (selectedTab == 6) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content Area (Full Height & Modern Cards)
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (selectedTab) {
                            0 -> {
                                if (filteredCallLogs.isEmpty()) {
                                    EmptyStateBox(Icons.Default.Call, if (searchQuery.isNotEmpty()) "No matching calls found" else "No call logs recorded yet")
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(filteredCallLogs) { log ->
                                            CallLogListItem(
                                                log = log,
                                                isPlaying = currentlyPlayingAudioUrl == log.audioRecordingUrl && log.audioRecordingUrl.isNotEmpty(),
                                                isBuffering = isAudioBuffering,
                                                audioPositionMs = audioPositionMs,
                                                audioDurationMs = audioDurationMs,
                                                onPlayClick = {
                                                    if (log.audioRecordingUrl.isNotEmpty()) {
                                                        playAudio(log.audioRecordingUrl)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (filteredSmsLogs.isEmpty()) {
                                    EmptyStateBox(Icons.Default.Email, if (searchQuery.isNotEmpty()) "No matching SMS messages found" else "No SMS messages recorded yet")
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(filteredSmsLogs) { sms ->
                                            SmsListItem(sms)
                                        }
                                    }
                                }
                            }
                            2 -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = whatsAppFilterType == "ALL",
                                            onClick = { whatsAppFilterType = "ALL" },
                                            label = { Text("All (${whatsAppLogs.size})", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        FilterChip(
                                            selected = whatsAppFilterType == "CHATS",
                                            onClick = { whatsAppFilterType = "CHATS" },
                                            label = { Text("💬 Chats", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        FilterChip(
                                            selected = whatsAppFilterType == "STATUS",
                                            onClick = { whatsAppFilterType = "STATUS" },
                                            label = { Text("📸 Statuses", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        FilterChip(
                                            selected = whatsAppFilterType == "MEDIA",
                                            onClick = { whatsAppFilterType = "MEDIA" },
                                            label = { Text("🎙️ Media", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }

                                    if (filteredWhatsAppLogs.isEmpty()) {
                                        EmptyStateBox(Icons.Default.Chat, if (searchQuery.isNotEmpty()) "No matching WhatsApp logs found" else "No WhatsApp chats or status captured yet")
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(bottom = 24.dp)
                                        ) {
                                            items(filteredWhatsAppLogs) { waItem ->
                                                WhatsAppListItem(waItem)
                                            }
                                        }
                                    }
                                }
                            }
                            3 -> {
                                if (filteredWebHistory.isEmpty()) {
                                    EmptyStateBox(Icons.Default.Language, if (searchQuery.isNotEmpty()) "No matching web history found" else "No web browsing history yet")
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(filteredWebHistory) { item ->
                                            WebHistoryListItem(item)
                                        }
                                    }
                                }
                            }
                            4 -> {
                                if (filteredPackageEvents.isEmpty()) {
                                    EmptyStateBox(Icons.Default.Apps, if (searchQuery.isNotEmpty()) "No matching app events found" else "No app install/uninstall events yet")
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(filteredPackageEvents) { event ->
                                            AppInstallEventItem(event)
                                        }
                                    }
                                }
                            }
                            5 -> {
                                UnifiedSimAndNetworkView(simInfo = simInfo, networkHistory = networkHistory)
                            }
                            else -> {
                                if (filteredNotifications.isEmpty()) {
                                    EmptyStateBox(Icons.Default.Notifications, if (searchQuery.isNotEmpty()) "No matching notifications found" else "No notifications captured yet")
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(filteredNotifications) { notif ->
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
fun CallLogListItem(
    log: CallLogItem,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    audioPositionMs: Int = 0,
    audioDurationMs: Int = 0,
    onPlayClick: () -> Unit = {}
) {
    val isWhatsApp = log.type.uppercase().contains("WHATSAPP")
    val typeIcon = when {
        isWhatsApp -> Icons.Default.Call
        log.type.uppercase() == "OUTGOING" -> Icons.Default.CallMade
        log.type.uppercase() == "INCOMING" -> Icons.Default.CallReceived
        log.type.uppercase() == "MISSED" -> Icons.Default.CallMissed
        log.type.uppercase() == "REJECTED" -> Icons.Default.PhoneDisabled
        else -> Icons.Default.Phone
    }
    val iconTint = when {
        isWhatsApp -> Color(0xFF25D366)
        log.type.uppercase() == "OUTGOING" -> Color(0xFF1976D2)
        log.type.uppercase() == "INCOMING" -> Color(0xFF388E3C)
        log.type.uppercase() == "MISSED" -> Color(0xFFD32F2F)
        log.type.uppercase() == "REJECTED" -> Color(0xFFE65100)
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

    val displayType = when (log.type.uppercase()) {
        "INCOMING_WHATSAPP" -> "WHATSAPP INCOMING"
        "OUTGOING_WHATSAPP" -> "WHATSAPP OUTGOING"
        else -> log.type.uppercase()
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.name.ifEmpty { log.number.ifEmpty { "Unknown Caller" } },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (log.name.isNotEmpty() && log.number.isNotEmpty()) {
                        Text(
                            text = log.number,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$displayType • $timeStr • $durationStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Enhanced Audio Call Recording Player Bar
            if (log.audioRecordingUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        FilledIconButton(
                            onClick = onPlayClick,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else Color(0xFF388E3C)
                            )
                        ) {
                            if (isPlaying && isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause Recording" else "Play Call Recording",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isPlaying) (if (isBuffering) "Buffering..." else "Playing Call Recording...") else "Listen Call Recording 🎙️",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isPlaying && audioDurationMs > 0) {
                                    val currentSec = audioPositionMs / 1000
                                    val totalSec = audioDurationMs / 1000
                                    Text(
                                        text = String.format(Locale.getDefault(), "%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (isPlaying) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val progress = if (audioDurationMs > 0) (audioPositionMs.toFloat() / audioDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
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
                        val lastCheckedStr = if (simInfo.lastUpdated > 0L) {
                            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(simInfo.lastUpdated))
                        } else "Recent"

                        InfoRow("Operator / Carrier", simInfo.operatorName.ifEmpty { "Detected" })
                        InfoRow("Country ISO", simInfo.countryIso.ifEmpty { "IN" }.uppercase())
                        InfoRow("SIM Status", simInfo.simState.ifEmpty { "Ready (Active)" })
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
                        val connectStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(activeNetwork.connectedAt))
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

@Composable
fun InfoRow(label: String, value: String) {
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
            color = MaterialTheme.colorScheme.onSurface
        )
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

@Composable
fun WhatsAppListItem(item: WhatsAppLogItem) {
    val timeStr = remember(item.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    val typeColor = when (item.type.uppercase()) {
        "STATUS" -> Color(0xFF00A884)
        "AUDIO" -> Color(0xFF34B7F1)
        "PHOTO", "VIDEO" -> Color(0xFF9C27B0)
        else -> Color(0xFF25D366)
    }

    val typeIcon = when (item.type.uppercase()) {
        "STATUS" -> Icons.Default.CameraAlt
        "AUDIO" -> Icons.Default.Mic
        "PHOTO" -> Icons.Default.Image
        "VIDEO" -> Icons.Default.Videocam
        else -> Icons.Default.Chat
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(typeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.senderName.ifEmpty { "WhatsApp Contact" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = CircleShape,
                            color = typeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = item.type.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = typeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.messageText,
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
}

