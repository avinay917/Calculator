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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import com.example.authapp.recorder.StreamAudioRecorder
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Sensors
import com.example.authapp.data.RecordingSession
import com.example.authapp.ui.components.ChildLocationDialog
import com.example.authapp.ui.components.ChildUserCard
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
    WebRtcManager.calculateSafeAudioGain(sensitivityPercent)

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

    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteAudioTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var liveAudioLevel by remember { mutableFloatStateOf(0f) }

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

    LaunchedEffect(Unit) {
        viewModel.loadChildUsers()
        viewModel.loadAllRecordings()
    }

    LaunchedEffect(uiState.audioSensitivity, remoteAudioTrack) {
        remoteAudioTrack?.setVolume(calculateSafeAudioGain(uiState.audioSensitivity))
    }

    // WebRTC connection lifecycle tied to activeSessionId
    DisposableEffect(uiState.activeSessionId) {
        val sessionId = uiState.activeSessionId
        val childId = uiState.activeChildId
        if (sessionId != null && childId != null) {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            viewModel.updateStreamStatus("Connecting to child device...")

            var sdpOfferListener: com.google.firebase.database.ValueEventListener? = null
            var candidateListener: com.google.firebase.database.ChildEventListener? = null
            var manager: WebRtcManager? = null

            try {
                manager = WebRtcManager(context, isReceiverOnly = true)
                webRtcManager = manager

                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager?.isSpeakerphoneOn = true
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ParentScreen] AudioManager init error: ${e.localizedMessage}")
                }

                val iceServers = WebRtcManager.getDefaultIceServers()
                try {
                    audioRouteManager.checkAndHandleBluetooth()
                    if (!isBluetoothConnected) {
                        audioRouteManager.setRoute(AudioOutputRoute.SPEAKER)
                    }
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ParentScreen] audioRouteManager error: ${e.localizedMessage}")
                }

                manager.startReceiver(
                    iceServers = iceServers,
                    onIceCandidate = { candidate ->
                        try {
                            val candMap = mapOf(
                                "sdpMid" to candidate.sdpMid,
                                "sdpMLineIndex" to candidate.sdpMLineIndex,
                                "sdp" to candidate.sdp
                            )
                            FirebaseRepository.sendIceCandidate(sessionId, candMap, isParent = true)
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().recordException(e)
                        }
                    },
                    onRemoteVideoTrack = { track ->
                        mainHandler.post {
                            viewModel.updateStreamStatus("Live Video Streaming 🟢")
                            remoteVideoTrack = track
                            AppAnalytics.logFeatureUsage("video_cast", "connected")
                        }
                    },
                    onRemoteAudioTrack = { track ->
                        mainHandler.post {
                            viewModel.updateStreamStatus("Live Audio Streaming 🟢")
                            remoteAudioTrack = track
                            try {
                                track.setEnabled(true)
                                track.setVolume(calculateSafeAudioGain(uiState.audioSensitivity))
                                if (!isBluetoothConnected) {
                                    audioRouteManager.setRoute(AudioOutputRoute.SPEAKER)
                                }
                            } catch (e: Exception) {
                                FirebaseCrashlytics.getInstance().recordException(e)
                            }
                            AppAnalytics.logFeatureUsage("audio_cast", "connected")
                        }
                    }
                )

                manager.startAudioLevelMonitoring { level ->
                    mainHandler.post {
                        liveAudioLevel = level
                    }
                }

                // Listen for SDP Offer from Child
                sdpOfferListener = FirebaseRepository.listenToSdpOffer(sessionId) { sdpOffer ->
                    mainHandler.post {
                        viewModel.updateStreamStatus("Child Offer Received, establishing connection...")
                    }
                    try {
                        manager.setRemoteOfferAndCreateAnswer(sdpOffer) { sdpAnswer ->
                            FirebaseRepository.sendSdpAnswer(sessionId, sdpAnswer.description)
                        }
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }

                // Listen for ICE Candidates from Child
                candidateListener = FirebaseRepository.listenToCandidates(sessionId, listenToParentCandidates = false) { sdpMid, sdpMLineIndex, sdp ->
                    try {
                        manager.addRemoteCandidate(sdpMid, sdpMLineIndex, sdp)
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
            } catch (t: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(t)
                AppHealthTelemetry.logDiagnostic(context, "LIVE_STREAM_RECEIVER", "FAILED", "Receiver initialization failed: ${t.localizedMessage}", t.message)
                viewModel.updateStreamStatus("Failed to start stream: ${t.localizedMessage}")
            }

            onDispose {
                liveAudioLevel = 0f
                remoteVideoTrack = null
                remoteAudioTrack = null
                webRtcManager = null
                try {
                    sdpOfferListener?.let {
                        FirebaseRepository.removeSdpOfferListener(sessionId, it)
                    }
                } catch (_: Exception) {}
                try {
                    candidateListener?.let {
                        FirebaseRepository.removeCandidateListener(sessionId, listenToParentCandidates = false, it)
                    }
                } catch (_: Exception) {}
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.mode = AudioManager.MODE_NORMAL
                    audioManager?.isSpeakerphoneOn = false
                } catch (_: Exception) {}
                try {
                    if (uiState.isRecording) {
                        FirebaseRepository.requestRemoteRecording(childId, false, uiState.activeStreamType?.lowercase() ?: "audio")
                        viewModel.onRecordingStopped()
                    }
                } catch (_: Exception) {}
                try {
                    manager?.stopStream()
                } catch (_: Throwable) {}
            }
        } else {
            onDispose { }
        }
    }

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
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupervisorAccount,
                                contentDescription = null,
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

                        IconButton(onClick = onSignOut) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Connected Child Devices (${uiState.childUsers.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

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
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No child accounts registered yet.\nNew accounts will automatically show here as 'child' role.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
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
                                    onAudioClick = {
                                        viewModel.startStream(child, "audio")
                                        Toast.makeText(context, "Requesting Audio Stream from ${child.name}...", Toast.LENGTH_SHORT).show()
                                    },
                                    onVideoClick = {
                                        viewModel.startStream(child, "video")
                                        Toast.makeText(context, "Requesting Video Stream from ${child.name}...", Toast.LENGTH_SHORT).show()
                                    },
                                    onLocationClick = {
                                        viewModel.openLocationDialog(child)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                CloudRecordingsView(
                    recordings = uiState.allRecordings,
                    childUsers = uiState.childUsers,
                    isLoading = uiState.isLoadingRecordings,
                    currentFilter = uiState.recordingFilter,
                    onFilterChange = { viewModel.setRecordingFilter(it) },
                    currentlyPlayingRecId = uiState.currentlyPlayingRecId,
                    onPlayRecording = { playRecording(it) },
                    onStopPlayback = { stopPlayback() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Live Location Modal Dialog
            uiState.locationDialogChild?.let { childUser ->
                DisposableEffect(childUser.uid) {
                    val locListener = FirebaseRepository.listenToChildLocation(childUser.uid) { loc ->
                        viewModel.updateChildLocation(loc)
                    }
                    onDispose {
                        FirebaseRepository.removeValueListener("users/${childUser.uid}/location", locListener)
                    }
                }

                ChildLocationDialog(
                    childUser = childUser,
                    childLocation = uiState.childLocation,
                    isRefreshingLocation = uiState.isRefreshingLocation,
                    onDismiss = { viewModel.closeLocationDialog() },
                    onRefreshLocation = {
                        viewModel.requestLocationRefresh(childUser.uid)
                        Toast.makeText(context, "Real-time location refresh requested...", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Active Stream Fullscreen Screen (Overlay directly in Activity Window)
        uiState.activeSessionId?.let {
            LiveStreamDialog(
                activeStreamType = uiState.activeStreamType,
                activeChildName = uiState.activeChildName,
                streamStatusText = uiState.streamStatusText,
                isFrontCamera = uiState.isFrontCamera,
                isRecording = uiState.isRecording,
                recordingDurationSeconds = uiState.recordingDurationSeconds,
                audioSensitivity = uiState.audioSensitivity,
                remoteVideoTrack = remoteVideoTrack,
                webRtcManager = webRtcManager,
                audioLevel = liveAudioLevel,
                onDismiss = {
                    if (uiState.isRecording) {
                        toggleRecording(uiState.activeStreamType ?: "audio")
                    }
                    viewModel.stopStream()
                    AppAnalytics.logButtonClick("disconnect_stream", "ParentScreen")
                },
                onFlipCamera = {
                    viewModel.toggleCameraFacing()
                    Toast.makeText(context, "Switching Camera...", Toast.LENGTH_SHORT).show()
                    AppAnalytics.logButtonClick("flip_camera", "ParentScreen")
                },
                onToggleRecording = { type -> toggleRecording(type) },
                onSensitivityChange = { viewModel.setAudioSensitivity(it) },
                currentAudioRoute = currentAudioRoute,
                isBluetoothConnected = isBluetoothConnected,
                onAudioRouteSelect = { route -> audioRouteManager.setRoute(route) }
            )
        }
    }
}
