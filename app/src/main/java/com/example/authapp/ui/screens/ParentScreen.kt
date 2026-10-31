package com.example.authapp.ui.screens

import android.Manifest
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.authapp.audio.AudioRouteManager
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.ui.activity.LiveStreamActivity
import com.example.authapp.ui.viewmodel.ParentViewModel
import com.example.authapp.utils.longToast
import com.example.authapp.utils.toast
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

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
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) audioRouteManager.checkAndAutoRoute()
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
            isForceUpdate = false
        )
    }

    LaunchedEffect(uiState.selectedChildForControls?.uid) {
        uiState.selectedChildForControls?.uid
            ?.takeIf { it.isNotEmpty() }
            ?.let(viewModel::attachBasicChildListeners)
    }

    LaunchedEffect(uiState.userFeedbackMessage) {
        uiState.userFeedbackMessage?.let { msg ->
            context.toast(msg)
            viewModel.clearUserFeedbackMessage()
        }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
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
            setOnCompletionListener { viewModel.setCurrentlyPlayingRecId(null) }
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
            } catch (_: Exception) {}
            mediaPlayer = null
            audioRouteManager.release()
        }
    }

    LaunchedEffect(uiState.activeSessionId) {
        val sessionId = uiState.activeSessionId
        val childId = uiState.activeChildId
        val childName = uiState.activeChildName ?: "Child Device"
        val streamType = uiState.activeStreamType?.lowercase() ?: "video"
        if (sessionId != null && childId != null) {
            val intent = LiveStreamActivity.createIntent(
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
            ParentNavigationDrawerContent(
                email = email,
                childUsers = uiState.childUsers,
                selectedChild = uiState.selectedChildForControls,
                drawerState = drawerState,
                scope = scope,
                onSelectChild = { child -> viewModel.setSelectedChildForControls(child) },
                onUnpairClick = { child -> childToUnpair = child },
                onPairClick = { showPairingDialog = true },
                onSignOut = onSignOut
            )
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                FloatingPillBottomBar(
                    selectedTab = uiState.selectedTab,
                    onTabSelected = viewModel::selectTab
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ParentTabsContent(
                    email = email,
                    uiState = uiState,
                    viewModel = viewModel,
                    context = context,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onPairClick = { showPairingDialog = true },
                    onSignOut = onSignOut,
                    onPlayRecording = { rec -> playRecording(rec) },
                    onStopPlayback = { stopPlayback() },
                    onUnpairClick = { child -> childToUnpair = child }
                )

                ParentDialogsHost(
                    email = email,
                    uiState = uiState,
                    viewModel = viewModel,
                    context = context,
                    showPairingDialog = showPairingDialog,
                    onDismissPairingDialog = { showPairingDialog = false },
                    childToUnpair = childToUnpair,
                    isUnpairing = isUnpairing,
                    onDismissUnpairDialog = { childToUnpair = null },
                    onConfirmUnpair = { targetChild ->
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
                    }
                )
            }
        }
    }
}

@Composable
fun FloatingPillBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.96f),
            shadowElevation = 10.dp,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingPillItem(
                    selected = selectedTab == 0,
                    icon = Icons.Default.Sensors,
                    label = "Live",
                    onClick = { onTabSelected(0) }
                )
                FloatingPillItem(
                    selected = selectedTab == 1,
                    icon = Icons.Default.Security,
                    label = "Controls",
                    onClick = { onTabSelected(1) }
                )
                FloatingPillItem(
                    selected = selectedTab == 2,
                    icon = Icons.Default.CloudQueue,
                    label = "History",
                    onClick = { onTabSelected(2) }
                )
                FloatingPillItem(
                    selected = selectedTab == 3,
                    icon = Icons.Default.Schedule,
                    label = "Schedule",
                    onClick = { onTabSelected(3) }
                )
                FloatingPillItem(
                    selected = selectedTab == 4,
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    onClick = { onTabSelected(4) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.FloatingPillItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val animBg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(220),
        label = "pill_bg"
    )
    val animContentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        animationSpec = tween(220),
        label = "pill_content"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = animBg,
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .padding(horizontal = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = animContentColor,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)
                ),
                color = animContentColor,
                maxLines = 1
            )
        }
    }
}
