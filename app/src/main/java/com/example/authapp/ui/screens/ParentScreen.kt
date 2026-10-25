package com.example.authapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.authapp.analytics.AppAnalytics
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.audio.AudioOutputRoute
import com.example.authapp.audio.AudioRouteManager
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.FirebaseControlRepository
import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.recorder.StreamAudioRecorder
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import com.example.authapp.ui.components.PairingCodeDialog
import com.example.authapp.ui.components.ChildControlsView
import com.example.authapp.ui.components.ChildLocationDialog
import com.example.authapp.ui.components.ChildUserCard
import com.example.authapp.ui.components.ChildSnapshotDialog
import com.example.authapp.ui.components.ChildActivityDialog
import com.example.authapp.ui.components.ChildAlertsDialog
import com.example.authapp.ui.components.ChildScheduleDialog
import com.example.authapp.ui.components.ChildSchedulesView
import com.example.authapp.ui.components.CloudRecordingsView
import com.example.authapp.ui.viewmodel.ParentViewModel
import com.example.authapp.webrtc.WebRtcManager
import java.io.File
import java.io.FileInputStream
import com.example.authapp.utils.toast
import com.example.authapp.utils.longToast
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack

@Composable
fun ParentScreen(
    email: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ParentViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val audioRouteManager = remember { AudioRouteManager(context) }
    val currentAudioRoute by audioRouteManager.currentRoute.collectAsStateWithLifecycle()
    val isBluetoothConnected by audioRouteManager.isBluetoothConnected.collectAsStateWithLifecycle()
    val isHeadsetConnected by audioRouteManager.isHeadsetConnected.collectAsStateWithLifecycle()

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            audioRouteManager.checkAndAutoRoute()
        }
    }

    var showPairingDialog by remember { mutableStateOf(false) }
    var childToUnpair by remember { mutableStateOf<User?>(null) }
    var isUnpairing by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!audioRouteManager.hasBluetoothPermission()) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        viewModel.loadChildUsers(context)
        viewModel.loadAllRecordings()

        // Auto-publish latest App Update info to Firebase RTDB for child devices
        // NOTE: versionCode dynamically read kiya — CI/CD se jo bhi version build hua wo push hoga
        val liveVersionCode = try {
            com.example.authapp.updater.UpdateManager.getCurrentVersionCode(context)
        } catch (_: Exception) {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }
        val liveVersionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "v$liveVersionCode"
        } catch (_: Exception) { "v$liveVersionCode" }

        FirebaseRepository.publishAppUpdate(
            versionCode = liveVersionCode,
            versionName = liveVersionName,
            apkUrl = "https://github.com/avinay917/Calculator/releases/download/latest/Calculator-latest.apk",
            releaseNotes = "$liveVersionName: Latest build with live video fix & security improvements",
            isForceUpdate = false  // BUG FIX: false rakho — users ke liye graceful optional update
        )
    }

    LaunchedEffect(uiState.selectedChildForControls?.uid) {
        uiState.selectedChildForControls?.uid
            ?.takeIf { it.isNotEmpty() }
            ?.let(viewModel::attachChildActivityListeners)
    }

    LaunchedEffect(uiState.userFeedbackMessage) {
        uiState.userFeedbackMessage?.let { msg ->
            context.toast(msg)
            viewModel.clearUserFeedbackMessage()
        }
    }

    fun toggleRecording(streamType: String) {
        val childId = uiState.activeChildId ?: return
        if (uiState.isRecording) {
            viewModel.onRecordingStopped()
            FirebaseControlRepository.requestRemoteRecording(childId, false, streamType.lowercase())
            context.toast("Recording stopped. Syncing to Cloud History...")
        } else {
            viewModel.onRecordingStarted()
            FirebaseControlRepository.requestRemoteRecording(childId, true, streamType.lowercase())
            context.toast("High-quality master recording started on child device...")
        }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        viewModel.setCurrentlyPlayingRecId(null)
    }

    fun playRecording(session: RecordingSession) {
        stopPlayback()
        val localFile = if (session.localFilePath.isNotEmpty()) File(session.localFilePath) else null
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnCompletionListener {
                viewModel.setCurrentlyPlayingRecId(null)
            }
            setOnErrorListener { _, _, _ ->
                viewModel.setCurrentlyPlayingRecId(null)
                true
            }
        }

        try {
            if (localFile != null && localFile.exists()) {
                val fis = FileInputStream(localFile)
                mp.setDataSource(fis.fd)
                fis.close()
                mp.prepare()
                mp.start()
                viewModel.setCurrentlyPlayingRecId(session.id)
                mediaPlayer = mp
            } else if (session.storageUrl.isNotEmpty()) {
                mp.setDataSource(session.storageUrl)
                mp.prepareAsync()
                mp.setOnPreparedListener {
                    it.start()
                    viewModel.setCurrentlyPlayingRecId(session.id)
                }
                mediaPlayer = mp
            } else {
                context.toast("No playable file or storage URL found")
            }
        } catch (e: Exception) {
            context.toast("Playback error: ${e.localizedMessage}")
            viewModel.setCurrentlyPlayingRecId(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {}
            mediaPlayer = null
            audioRouteManager.release()
        }
    }

    // Dedicated Activity Launcher for Live Video/Audio Stream
    LaunchedEffect(uiState.activeSessionId) {
        val sessionId = uiState.activeSessionId
        val childId = uiState.activeChildId
        val childName = uiState.activeChildName ?: "Child Device"
        val streamType = uiState.activeStreamType?.lowercase() ?: "video"
        if (sessionId != null && childId != null) {
            val intent = com.example.authapp.ui.activity.LiveStreamActivity.createIntent(
                context = context,
                sessionId = sessionId,
                childId = childId,
                childName = childName,
                streamType = streamType
            )
            context.startActivity(intent)
            viewModel.clearActiveSessionId()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Profile Header
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SupervisorAccount,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "User Settings",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "CONNECTED CHILD DEVICES",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.childUsers.isEmpty()) {
                        Text(
                            text = "No child devices paired yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            uiState.childUsers.forEach { child ->
                                val isSelected = uiState.selectedChildForControls?.uid == child.uid
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    onClick = {
                                        viewModel.setSelectedChildForControls(child)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = child.name.ifEmpty { "Child Device" },
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = if (child.isOnline) "● Online" else "● Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (child.isOnline) androidx.compose.ui.graphics.Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) {
                                            Text("Active", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        IconButton(
                                            onClick = { childToUnpair = child },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Unpair Child",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            showPairingDialog = true
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("+ Pair New Child Device")
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "APP & SYSTEM SETTINGS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Manual App Update Trigger Button
                    OutlinedButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSignOut()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = uiState.selectedTab == 0,
                            onClick = { viewModel.selectTab(0) },
                            icon = { Icon(Icons.Default.Sensors, contentDescription = "Live Monitor") },
                            label = { Text("Live") }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == 1,
                            onClick = { viewModel.selectTab(1) },
                            icon = { Icon(Icons.Default.Security, contentDescription = "Controls") },
                            label = { Text("Controls") }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == 2,
                            onClick = { viewModel.selectTab(2) },
                            icon = { Icon(Icons.Default.CloudQueue, contentDescription = "Cloud History") },
                            label = { Text("History") }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == 3,
                            onClick = { viewModel.selectTab(3) },
                            icon = { Icon(Icons.Default.Schedule, contentDescription = "Schedule") },
                            label = { Text("Schedule") }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (uiState.selectedTab == 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            // Header (Clean, Non-Overlapping Layout)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            scope.launch { drawerState.open() }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SupervisorAccount,
                                        contentDescription = "User Settings",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Parent Dashboard",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(onClick = { showPairingDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = "Pair Child Device",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(onClick = onSignOut) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Sign Out",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                    Spacer(modifier = Modifier.height(16.dp))

                    val anyChildMissingPermissions = uiState.childUsers.any { child ->
                        val health = uiState.deviceHealthMap[child.uid]
                        val perms = health?.permissions
                        perms != null && perms.isNotEmpty() && perms.values.any { !it }
                    }

                    if (anyChildMissingPermissions) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFF3E0),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "⚠️ Child Permission Alert",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFE65100)
                                    )
                                    Text(
                                        text = "One or more essential system permissions have been revoked on a child phone. Check device status.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF5D4037)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connected Child Devices (${uiState.childUsers.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        FilledTonalButton(
                            onClick = { showPairingDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pair Device", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.isLoadingChildren) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Checking connected child devices...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (uiState.childUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Child Devices Connected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Pair your child's phone with a 6-digit code to enable live tracking & monitoring.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showPairingDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Get 6-Digit Pairing Code")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(uiState.childUsers) { child ->
                                ChildUserCard(
                                    user = child,
                                    deviceHealth = uiState.deviceHealthMap[child.uid],
                                    alertsCount = uiState.securityAlertsMap[child.uid]?.size ?: 0,
                                    onAudioClick = {
                                        viewModel.startStream(child, com.example.authapp.utils.Constants.StreamTypes.AUDIO_ONLY)
                                        context.toast("Requesting Audio Stream from ${child.name}...")
                                    },
                                    onVideoClick = {
                                        viewModel.startStream(child, com.example.authapp.utils.Constants.StreamTypes.CAMERA_VIDEO)
                                        context.toast("Requesting Video Stream from ${child.name}...")
                                    },
                                    onScreenClick = {
                                        viewModel.startStream(child, com.example.authapp.utils.Constants.StreamTypes.SCREEN_MIRROR)
                                        context.toast("Requesting Live Screen Mirror from ${child.name}...")
                                    },
                                    onLocationClick = {
                                        viewModel.openLocationDialog(child)
                                    },
                                    onSnapshotClick = {
                                        viewModel.openSnapshotDialog(child)
                                    },
                                    onActivityClick = {
                                        viewModel.openActivityDialog(child, initialTab = 0)
                                    },
                                    onWhatsAppClick = {
                                        viewModel.openActivityDialog(child, initialTab = 2)
                                    },
                                    onAlertsClick = {
                                        viewModel.openAlertsDialog(child)
                                    },
                                    onScheduleClick = {
                                        viewModel.openScheduleDialog(child)
                                    }
                                )
                            }
                        }
                    }
                }
            } else if (uiState.selectedTab == 1) {
                val selectedChild = uiState.selectedChildForControls ?: uiState.childUsers.firstOrNull()
                val targetChildId = selectedChild?.uid ?: ""
                ChildControlsView(
                    childUsers = uiState.childUsers,
                    selectedChild = selectedChild,
                    onSelectChild = viewModel::setSelectedChildForControls,
                    appUsageList = uiState.appUsageMap[targetChildId] ?: emptyList(),
                    parentControls = uiState.parentControlsMap[targetChildId],
                    isTorchActive = uiState.isTorchActiveMap[targetChildId] == true,
                    isSirenActive = uiState.isSirenActiveMap[targetChildId] == true,
                    onToggleTorch = {
                        if (targetChildId.isNotEmpty()) {
                            val cur = uiState.isTorchActiveMap[targetChildId] == true
                            viewModel.toggleTorch(targetChildId, cur)
                            context.toast(if (!cur) "Turning Torch ON..." else "Turning Torch OFF...")
                        }
                    },
                    onToggleSiren = {
                        if (targetChildId.isNotEmpty()) {
                            val cur = uiState.isSirenActiveMap[targetChildId] == true
                            viewModel.triggerSiren(targetChildId, cur)
                            context.toast(if (!cur) "Triggering Emergency Siren..." else "Stopping Siren...")
                        }
                    },
                    onEnableInternet = {
                        if (targetChildId.isNotEmpty()) {
                            viewModel.enableRemoteInternet(targetChildId)
                            context.toast("Triggering Internet/Wi-Fi ON on Child Device...")
                        }
                    },
                    onSetUninstallPin = { newPin ->
                        if (targetChildId.isNotEmpty()) {
                            viewModel.setUninstallPin(targetChildId, newPin)
                            context.toast("Uninstall PIN updated to $newPin")
                        }
                    },
                    onToggleAppBlock = { pkg, isBlocked ->
                        if (targetChildId.isNotEmpty()) {
                            viewModel.toggleAppBlock(targetChildId, pkg, isBlocked)
                            context.toast("App ${if (isBlocked) "Unlocked" else "Locked"} successfully")
                        }
                    },
                    onToggleStudyMode = { isActive, durationMins ->
                        if (targetChildId.isNotEmpty()) {
                            viewModel.toggleStudyMode(targetChildId, isActive, durationMins)
                            context.toast("Study Mode ${if (isActive) "Deactivated" else "Activated"}")
                        }
                    },
                    onLaunchApp = { pkg ->
                        if (targetChildId.isNotEmpty()) {
                            viewModel.launchRemoteApp(targetChildId, pkg)
                            context.toast("Opening app on child device...")
                        }
                    },
                    onCloseApp = { pkg ->
                        if (targetChildId.isNotEmpty()) {
                            viewModel.closeRemoteApp(targetChildId, pkg)
                            context.toast(if (pkg.isEmpty()) "Returning child device to Home..." else "Closing app on child device...")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (uiState.selectedTab == 2) {
                CloudRecordingsView(
                    recordings = uiState.allRecordings,
                    childUsers = uiState.childUsers,
                    isLoading = uiState.isLoadingRecordings,
                    currentFilter = uiState.recordingFilter,
                    onFilterChange = { filter -> viewModel.setRecordingFilter(filter) },
                    currentlyPlayingRecId = uiState.currentlyPlayingRecId,
                    onPlayRecording = { rec -> playRecording(rec) },
                    onStopPlayback = { stopPlayback() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ChildSchedulesView(
                    childUsers = uiState.childUsers,
                    schedulesMap = uiState.schedulesMap,
                    onSaveSchedule = { childId, schedule ->
                        viewModel.saveRecordingSchedule(childId, schedule)
                        context.toast("Schedule saved! Offline active.")
                    },
                    onDeleteSchedule = { childId, scheduleId ->
                        viewModel.deleteRecordingSchedule(childId, scheduleId)
                        context.toast("Schedule deleted")
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Live Location Map/Dialog (Phase 1 & 2)
            uiState.locationDialogChild?.let { childUser ->
                ChildLocationDialog(
                    childUser = childUser,
                    childLocation = uiState.childLocation,
                    isRefreshingLocation = uiState.isRefreshingLocation,
                    onDismiss = { viewModel.closeLocationDialog() },
                    onRefreshLocation = { viewModel.requestLocationRefresh(childUser.uid) }
                )
            }

            // Remote Photo Snapshot Dialog (Phase 2 & 5)
            uiState.activeSnapshotDialogChild?.let { childUser ->
                val snapshots = uiState.snapshotsMap[childUser.uid] ?: emptyList()
                ChildSnapshotDialog(
                    childUser = childUser,
                    snapshots = snapshots,
                    statusMessage = uiState.snapshotStatusMessage,
                    isCapturing = uiState.isSnapshotCapturing,
                    onRequestSnapshot = { facing ->
                        viewModel.requestSnapshot(childUser.uid, facing)
                    },
                    onDismiss = { viewModel.closeSnapshotDialog() }
                )
            }

            // Call Logs, SMS, WhatsApp, Web, Wi-Fi, Apps & SIM Activity Dialog (Phase 2, 4 & 5)
            uiState.activeActivityDialogChild?.let { childUser ->
                ChildActivityDialog(
                    childUser = childUser,
                    callLogs = uiState.callLogsMap[childUser.uid] ?: emptyList(),
                    notifications = uiState.notificationsMap[childUser.uid] ?: emptyList(),
                    smsLogs = uiState.smsLogsMap[childUser.uid] ?: emptyList(),
                    webHistory = uiState.webHistoryMap[childUser.uid] ?: emptyList(),
                    networkHistory = uiState.networkHistoryMap[childUser.uid] ?: emptyList(),
                    simInfo = uiState.simInfoMap[childUser.uid],
                    packageEvents = uiState.packageEventsMap[childUser.uid] ?: emptyList(),
                    whatsAppLogs = uiState.whatsAppLogsMap[childUser.uid] ?: emptyList(),
                    youtubeLogs = uiState.youtubeLogsMap[childUser.uid] ?: emptyList(),
                    mediaGallery = uiState.mediaGalleryMap[childUser.uid] ?: emptyList(),
                    fileExplorer = uiState.fileExplorerMap[childUser.uid] ?: emptyList(),
                    initialTab = uiState.initialActivityTab,
                    onDismiss = { viewModel.closeActivityDialog() }
                )
            }

            // Security Alerts Dialog (Phase 1 & 2)
            uiState.activeAlertsDialogChild?.let { childUser ->
                ChildAlertsDialog(
                    childUser = childUser,
                    alerts = uiState.securityAlertsMap[childUser.uid] ?: emptyList(),
                    onDismiss = { viewModel.closeAlertsDialog() }
                )
            }

            // Auto-Recording Schedules Dialog (Phase 1)
            uiState.activeScheduleDialogChild?.let { childUser ->
                ChildScheduleDialog(
                    childUser = childUser,
                    schedules = uiState.schedulesMap[childUser.uid] ?: emptyList(),
                    onSaveSchedule = { schedule ->
                        viewModel.saveRecordingSchedule(childUser.uid, schedule)
                        context.toast("Recording schedule saved")
                    },
                    onDeleteSchedule = { scheduleId ->
                        viewModel.deleteRecordingSchedule(childUser.uid, scheduleId)
                        context.toast("Recording schedule removed")
                    },
                    onDismiss = { viewModel.closeScheduleDialog() }
                )
            }

            if (showPairingDialog) {
                PairingCodeDialog(
                    parentEmail = email,
                    onDismiss = { showPairingDialog = false }
                )
            }

            childToUnpair?.let { targetChild ->
                AlertDialog(
                    onDismissRequest = { if (!isUnpairing) childToUnpair = null },
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Unpair Child Device?") },
                    text = {
                        Text("Are you sure you want to unpair \"${targetChild.name.ifEmpty { "Child Device" }}\"? This will disconnect monitoring, remote controls, and real-time streaming.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isUnpairing = true
                                viewModel.unpairChild(targetChild.uid) { success, error ->
                                    isUnpairing = false
                                    childToUnpair = null
                                    if (success) {
                                        context.toast("Child device successfully unpaired")
                                    } else {
                                        context.longToast("Unpair failed: $error")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !isUnpairing
                        ) {
                            Text(if (isUnpairing) "Unpairing..." else "Unpair")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { childToUnpair = null },
                            enabled = !isUnpairing
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
}
}

