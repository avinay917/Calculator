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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SupervisorAccount
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
import com.example.authapp.data.RecordingSession
import com.example.authapp.recorder.StreamAudioRecorder
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.QrCode
import com.example.authapp.ui.components.PairingCodeDialog
import com.example.authapp.ui.components.ChildControlsView
import com.example.authapp.ui.components.ChildLocationDialog
import com.example.authapp.ui.components.ChildUserCard
import com.example.authapp.ui.components.ChildSnapshotDialog
import com.example.authapp.ui.components.ChildActivityDialog
import com.example.authapp.ui.components.ChildAlertsDialog
import com.example.authapp.ui.components.ChildScheduleDialog
import com.example.authapp.ui.components.CloudRecordingsView
import com.example.authapp.ui.components.LiveStreamDialog
import com.example.authapp.ui.viewmodel.ParentViewModel
import com.example.authapp.webrtc.WebRtcManager
import java.io.File
import java.io.FileInputStream
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack

fun calculateSafeAudioGain(sensitivityPercent: Float): Double =
    WebRtcManager.calculateSuperBoostGain(sensitivityPercent)

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
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
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
            isForceUpdate = true
        )
    }

    LaunchedEffect(uiState.userFeedbackMessage) {
        uiState.userFeedbackMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUserFeedbackMessage()
        }
    }

    fun toggleRecording(streamType: String) {
        val childId = uiState.activeChildId ?: return
        if (uiState.isRecording) {
            viewModel.onRecordingStopped()
            FirebaseRepository.requestRemoteRecording(childId, false, streamType.lowercase())
            Toast.makeText(context, "Recording stopped. Syncing to Cloud History...", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.onRecordingStarted()
            FirebaseRepository.requestRemoteRecording(childId, true, streamType.lowercase())
            Toast.makeText(context, "High-quality master recording started on child device...", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "No playable file or storage URL found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Playback error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                    val curVersionCode = remember { com.example.authapp.updater.UpdateManager.getCurrentVersionCode(context) }
                    Button(
                        onClick = {
                            scope.launch { drawerState.close() }
                            viewModel.checkForUpdatesManually(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for App Updates (v$curVersionCode)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                            label = { Text("Live Monitor") }
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
                            label = { Text("Cloud History") }
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
                            // Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            scope.launch { drawerState.open() }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SupervisorAccount,
                                        contentDescription = "User Settings & Navigation Drawer",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Parent Dashboard",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "PARENT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { showPairingDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Pair Child Device",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = onSignOut) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

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
                                        Toast.makeText(context, "Requesting Audio Stream from ${child.name}...", Toast.LENGTH_SHORT).show()
                                    },
                                    onVideoClick = {
                                        viewModel.startStream(child, com.example.authapp.utils.Constants.StreamTypes.CAMERA_VIDEO)
                                        Toast.makeText(context, "Requesting Video Stream from ${child.name}...", Toast.LENGTH_SHORT).show()
                                    },
                                    onScreenClick = {
                                        viewModel.startStream(child, com.example.authapp.utils.Constants.StreamTypes.SCREEN_MIRROR)
                                        Toast.makeText(context, "Requesting Live Screen Mirror from ${child.name}...", Toast.LENGTH_SHORT).show()
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
                val activeUsage = if (selectedChild != null) uiState.appUsageMap[selectedChild.uid] ?: emptyList() else emptyList()
                val activeSettings = if (selectedChild != null) uiState.parentControlsMap[selectedChild.uid] else null

                ChildControlsView(
                    childUsers = uiState.childUsers,
                    selectedChild = selectedChild,
                    onSelectChild = { viewModel.setSelectedChildForControls(it) },
                    appUsageList = activeUsage,
                    parentControls = activeSettings,
                    isTorchActive = if (selectedChild != null) uiState.isTorchActiveMap[selectedChild.uid] == true else false,
                    isSirenActive = if (selectedChild != null) uiState.isSirenActiveMap[selectedChild.uid] == true else false,
                    onToggleTorch = {
                        if (selectedChild != null) {
                            val cur = uiState.isTorchActiveMap[selectedChild.uid] == true
                            viewModel.toggleTorch(selectedChild.uid, cur)
                            Toast.makeText(context, if (!cur) "Turning Torch ON..." else "Turning Torch OFF...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleSiren = {
                        if (selectedChild != null) {
                            val cur = uiState.isSirenActiveMap[selectedChild.uid] == true
                            viewModel.triggerSiren(selectedChild.uid, cur)
                            Toast.makeText(context, if (!cur) "Triggering Emergency Siren..." else "Stopping Siren...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleAppBlock = { pkg, isBlocked ->
                        if (selectedChild != null) {
                            viewModel.toggleAppBlock(selectedChild.uid, pkg, isBlocked)
                            val action = if (isBlocked) "Unlocked" else "Locked"
                            Toast.makeText(context, "App $action successfully", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleStudyMode = { isActive, durationMins ->
                        if (selectedChild != null) {
                            viewModel.toggleStudyMode(selectedChild.uid, isActive, durationMins)
                            val stateText = if (isActive) "Deactivated" else "Activated"
                            Toast.makeText(context, "Study Mode $stateText", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
                        Toast.makeText(context, "Recording schedule saved", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteSchedule = { scheduleId ->
                        viewModel.deleteRecordingSchedule(childUser.uid, scheduleId)
                        Toast.makeText(context, "Recording schedule removed", Toast.LENGTH_SHORT).show()
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
        }
    }
}
}
}


