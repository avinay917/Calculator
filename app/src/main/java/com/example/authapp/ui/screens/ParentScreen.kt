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

    val streamRecorder = remember { StreamAudioRecorder(context) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission is required to record stream audio", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleRecording(streamType: String) {
        if (uiState.isRecording) {
            val childId = uiState.activeChildId ?: ""
            val durationSec = uiState.recordingDurationSeconds
            val recordedFile = streamRecorder.stopRecording()
            viewModel.onRecordingStopped()

            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                val filePath = recordedFile.absolutePath
                FirebaseRepository.saveRecordingSession(
                    childId = childId,
                    streamType = streamType.lowercase(),
                    durationSeconds = durationSec,
                    localFilePath = filePath
                ) {
                    Toast.makeText(context, "Recording saved to device! (${recordedFile.name})", Toast.LENGTH_SHORT).show()
                }

                // Background cloud sync to Firebase Storage
                try {
                    val fileUri = Uri.fromFile(recordedFile)
                    FirebaseRepository.uploadRecordingFile(
                        childId = childId,
                        fileUri = fileUri,
                        streamType = streamType.lowercase(),
                        durationSeconds = durationSec,
                        localFilePath = filePath
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            } else {
                FirebaseRepository.saveRecordingSession(
                    childId = childId,
                    streamType = streamType.lowercase(),
                    durationSeconds = durationSec
                ) {
                    Toast.makeText(context, "Recording saved to history", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                Toast.makeText(context, "Microphone permission required for recording", Toast.LENGTH_SHORT).show()
                return
            }

            val file = streamRecorder.startRecording(streamType)
            if (file != null) {
                viewModel.onRecordingStarted()
                Toast.makeText(context, "Recording started to ${file.name}...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
            }
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
            val manager = WebRtcManager(context)
            webRtcManager = manager

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true

            val iceServers = WebRtcManager.getDefaultIceServers()
            audioRouteManager.checkAndHandleBluetooth()
            if (!isBluetoothConnected) {
                audioRouteManager.setRoute(AudioOutputRoute.SPEAKER)
            }
            manager.startReceiver(
                iceServers = iceServers,
                onIceCandidate = { candidate ->
                    val candMap = mapOf(
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "sdp" to candidate.sdp
                    )
                    FirebaseRepository.sendIceCandidate(sessionId, candMap, isParent = true)
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
                        track.setEnabled(true)
                        track.setVolume(calculateSafeAudioGain(uiState.audioSensitivity))
                        AppAnalytics.logFeatureUsage("audio_cast", "connected")
                    }
                }
            )

            // Listen for SDP Offer from Child
            val sdpOfferListener = FirebaseRepository.listenToSdpOffer(sessionId) { sdpOffer ->
                mainHandler.post {
                    viewModel.updateStreamStatus("Child Offer Received, establishing connection...")
                }
                manager.setRemoteOfferAndCreateAnswer(sdpOffer) { sdpAnswer ->
                    FirebaseRepository.sendSdpAnswer(sessionId, sdpAnswer.description)
                }
            }

            // Listen for ICE Candidates from Child
            val candidateListener = FirebaseRepository.listenToCandidates(sessionId, listenToParentCandidates = false) { sdpMid, sdpMLineIndex, sdp ->
                manager.addRemoteCandidate(sdpMid, sdpMLineIndex, sdp)
            }

            onDispose {
                remoteVideoTrack = null
                remoteAudioTrack = null
                webRtcManager = null
                audioRouteManager.release()
                FirebaseRepository.removeValueListener("signaling/$sessionId/sdpOffer", sdpOfferListener)
                if (streamRecorder.isRecording) {
                    streamRecorder.stopRecording()
                }
                manager.stopStream()
            }
        } else {
            onDispose { }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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

            // Active Stream Modal Dialog
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
}
