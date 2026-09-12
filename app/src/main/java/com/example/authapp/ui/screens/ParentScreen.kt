package com.example.authapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.authapp.analytics.AppAnalytics
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.data.UserLocation
import com.example.authapp.theme.AuthAppTheme
import com.example.authapp.webrtc.WebRtcManager
import org.webrtc.*
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.authapp.recorder.StreamAudioRecorder

fun calculateSafeAudioGain(sensitivityPercent: Float): Double =
    WebRtcManager.calculateSafeAudioGain(sensitivityPercent)

@Composable
fun ParentScreen(
    email: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var childUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var activeStreamType by remember { mutableStateOf<String?>(null) }
    var activeChildId by remember { mutableStateOf<String?>(null) }
    var activeChildName by remember { mutableStateOf<String?>(null) }

    var isFrontCamera by remember { mutableStateOf(true) }
    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteAudioTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var audioSensitivity by remember { mutableFloatStateOf(100f) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableLongStateOf(0L) }
    var showLocationDialogForChild by remember { mutableStateOf<User?>(null) }
    var streamStatusText by remember { mutableStateOf("Connecting to child device...") }

    val streamRecorder = remember { StreamAudioRecorder(context) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission is required to record stream audio", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleRecording(streamType: String) {
        if (isRecording) {
            val childId = activeChildId ?: ""
            val durationSec = recordingDurationSeconds
            val recordedFile = streamRecorder.stopRecording()
            isRecording = false

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
                isRecording = true
                Toast.makeText(context, "Recording started to ${file.name}...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSeconds = 0L
            while (isRecording) {
                delay(1000L)
                recordingDurationSeconds++
            }
        } else {
            recordingDurationSeconds = 0L
        }
    }

    LaunchedEffect(audioSensitivity, remoteAudioTrack) {
        remoteAudioTrack?.setVolume(calculateSafeAudioGain(audioSensitivity))
    }

    DisposableEffect(activeSessionId) {
        val sessionId = activeSessionId
        val childId = activeChildId
        if (sessionId != null && childId != null) {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            streamStatusText = "Connecting to child device..."
            val manager = WebRtcManager(context)
            webRtcManager = manager

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true

            val iceServers = WebRtcManager.getDefaultIceServers()
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
                        streamStatusText = "Live Video Streaming 🟢"
                        remoteVideoTrack = track
                        AppAnalytics.logFeatureUsage("video_cast", "connected")
                    }
                },
                onRemoteAudioTrack = { track ->
                    mainHandler.post {
                        streamStatusText = "Live Audio Streaming 🟢"
                        remoteAudioTrack = track
                        track.setEnabled(true)
                        track.setVolume(calculateSafeAudioGain(audioSensitivity))
                        AppAnalytics.logFeatureUsage("audio_cast", "connected")
                    }
                }
            )

            // Listen for SDP Offer from Child
            val sdpOfferListener = FirebaseRepository.listenToSdpOffer(sessionId) { sdpOffer ->
                mainHandler.post {
                    streamStatusText = "Child Offer Received, establishing connection..."
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
                try {
                    audioManager?.mode = AudioManager.MODE_NORMAL
                    audioManager?.isSpeakerphoneOn = false
                } catch (e: Exception) {}
                FirebaseRepository.removeValueListener("signaling/$sessionId/sdpOffer", sdpOfferListener)
                FirebaseRepository.stopStream(childId, sessionId)
                if (streamRecorder.isRecording) {
                    streamRecorder.stopRecording()
                }
                isRecording = false
                manager.stopStream()
            }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(Unit) {
        FirebaseRepository.listenToChildUsers { list ->
            childUsers = list
        }
    }

    Column(
        modifier = modifier
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
                Text(
                    text = "Parent Dashboard",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Role: PARENT | Account: $email",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            text = "Connected Child Devices (${childUsers.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (childUsers.isEmpty()) {
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
                items(childUsers) { child ->
                    ChildUserCard(
                        user = child,
                        onAudioClick = {
                            FirebaseRepository.requestStream(child.uid, "audio") { sessionId ->
                                activeSessionId = sessionId
                                activeStreamType = "Audio"
                                activeChildId = child.uid
                                activeChildName = child.name.ifEmpty { "Child Device" }
                                Toast.makeText(context, "Requesting Audio Stream from ${child.name}...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onVideoClick = {
                            FirebaseRepository.requestStream(child.uid, "video") { sessionId ->
                                activeSessionId = sessionId
                                activeStreamType = "Video"
                                activeChildId = child.uid
                                activeChildName = child.name.ifEmpty { "Child Device" }
                                Toast.makeText(context, "Requesting Video Stream from ${child.name}...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLocationClick = {
                            showLocationDialogForChild = child
                        }
                    )
                }
            }
        }

        // Live Location Modal Dialog
        showLocationDialogForChild?.let { childUser ->
            var childLoc by remember { mutableStateOf<UserLocation?>(null) }
            var isRefreshingLoc by remember { mutableStateOf(false) }

            DisposableEffect(childUser.uid) {
                val locListener = FirebaseRepository.listenToChildLocation(childUser.uid) { loc ->
                    childLoc = loc
                    isRefreshingLoc = false
                }
                onDispose {
                    FirebaseRepository.removeValueListener("users/${childUser.uid}/location", locListener)
                }
            }

            AlertDialog(
                onDismissRequest = { showLocationDialogForChild = null },
                shape = RoundedCornerShape(24.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE3F2FD)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFF1976D2),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Live Location Tracking",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = childUser.name.ifEmpty { "Child Device" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (childLoc != null) {
                            val loc = childLoc!!
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "📍 GPS Coordinates",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Latitude: ${String.format(Locale.US, "%.6f", loc.latitude)}\nLongitude: ${String.format(Locale.US, "%.6f", loc.longitude)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFE8F5E9)
                                        ) {
                                            Text(
                                                text = "Accuracy: ±${loc.accuracy.toInt()}m",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF2E7D32),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val timeStr = remember(loc.timestamp) {
                                            if (loc.timestamp > 0L) {
                                                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(loc.timestamp))
                                            } else "Recent"
                                        }
                                        Text(
                                            text = "Time: $timeStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val geoUri = Uri.parse("https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open Maps: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open in Google Maps 🗺️")
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Fetching GPS fix from child device...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                isRefreshingLoc = true
                                FirebaseRepository.requestChildLocation(childUser.uid)
                                Toast.makeText(context, "Real-time location refresh requested...", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !isRefreshingLoc,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isRefreshingLoc) "Refreshing GPS..." else "Refresh GPS Location")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLocationDialogForChild = null }) {
                        Text("Close")
                    }
                }
            )
        }

        // Active Monitor Overlay Dialog
        activeSessionId?.let { sessionId ->
            val disconnectAction = {
                activeChildId?.let { childId ->
                    FirebaseRepository.stopStream(childId, sessionId)
                }
                if (isRecording) {
                    val childId = activeChildId ?: ""
                    val streamType = (activeStreamType ?: "audio").lowercase()
                    val durationSec = recordingDurationSeconds
                    val recordedFile = streamRecorder.stopRecording()
                    isRecording = false

                    if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                        val filePath = recordedFile.absolutePath
                        FirebaseRepository.saveRecordingSession(
                            childId = childId,
                            streamType = streamType,
                            durationSeconds = durationSec,
                            localFilePath = filePath
                        ) {}
                        try {
                            val fileUri = Uri.fromFile(recordedFile)
                            FirebaseRepository.uploadRecordingFile(
                                childId = childId,
                                fileUri = fileUri,
                                streamType = streamType,
                                durationSeconds = durationSec,
                                localFilePath = filePath
                            )
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().recordException(e)
                        }
                    } else {
                        FirebaseRepository.saveRecordingSession(
                            childId = childId,
                            streamType = streamType,
                            durationSeconds = durationSec
                        ) {}
                    }
                }
                activeSessionId = null
                activeStreamType = null
                activeChildId = null
                activeChildName = null
                remoteVideoTrack = null
                remoteAudioTrack = null
                isRecording = false
                AppAnalytics.logButtonClick("disconnect_stream", "ParentScreen")
            }

            AlertDialog(
                onDismissRequest = disconnectAction,
                properties = DialogProperties(usePlatformDefaultWidth = false),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 12.dp),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (activeStreamType.equals("video", ignoreCase = true))
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else
                                        MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (activeStreamType.equals("video", ignoreCase = true)) Icons.Default.Videocam else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (activeStreamType.equals("video", ignoreCase = true)) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live ${activeStreamType?.replaceFirstChar { it.uppercase() } ?: "Media"} Cast",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Device: ${activeChildName ?: "Child"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = streamStatusText,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Video Stream Display via SafeSurfaceViewRenderer
                        if (activeStreamType.equals("video", ignoreCase = true)) {
                            if (remoteVideoTrack != null && webRtcManager != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SafeSurfaceViewRenderer(
                                        videoTrack = remoteVideoTrack,
                                        eglContext = webRtcManager?.eglBase?.eglBaseContext,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Connecting to child camera...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Video Controls: Camera Switch & Record
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isFrontCamera = !isFrontCamera
                                        FirebaseRepository.toggleCameraFacing(sessionId, isFrontCamera)
                                        Toast.makeText(context, "Switching Camera...", Toast.LENGTH_SHORT).show()
                                        AppAnalytics.logButtonClick("flip_camera", "ParentScreen")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isFrontCamera) "Flip (Back)" else "Flip (Front)", style = MaterialTheme.typography.labelMedium)
                                }

                                Button(
                                    onClick = { toggleRecording("video") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.errorContainer,
                                        contentColor = if (isRecording) Color.White else MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val recTimeStr = remember(recordingDurationSeconds) {
                                        val m = recordingDurationSeconds / 60
                                        val s = recordingDurationSeconds % 60
                                        String.format(Locale.getDefault(), "%02d:%02d", m, s)
                                    }
                                    Text(if (isRecording) "Stop ($recTimeStr)" else "Record", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            // Real-time Live Audio Monitor with Whisper Boost & Recording
                            LiveAudioMonitorView(
                                childName = activeChildName ?: "Child",
                                statusText = streamStatusText,
                                audioSensitivity = audioSensitivity,
                                onSensitivityChange = { audioSensitivity = it },
                                isRecording = isRecording,
                                recordingDurationSeconds = recordingDurationSeconds,
                                onToggleRecording = { toggleRecording("audio") }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = disconnectAction,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Disconnect Stream")
                    }
                }
            )
        }
    }
}

@Composable
fun LiveAudioMonitorView(
    childName: String,
    statusText: String,
    audioSensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    isRecording: Boolean,
    recordingDurationSeconds: Long,
    onToggleRecording: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioRadar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp * pulseScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Live Remote Mic",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Listening to $childName",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            val profileText = when {
                audioSensitivity >= 80f -> "⚡ Whisper Surveillance Mode (Clean Boost)"
                audioSensitivity >= 40f -> "🎙️ Balanced Studio Mode (Anti-Scratch Filter ON)"
                else -> "👤 Natural Clear Mode (Unity Gain)"
            }
            val profileColor = when {
                audioSensitivity >= 80f -> Color(0xFFC62828)
                audioSensitivity >= 40f -> Color(0xFF1565C0)
                else -> Color(0xFF2E7D32)
            }
            val profileBg = when {
                audioSensitivity >= 80f -> Color(0xFFFFEBEE)
                audioSensitivity >= 40f -> Color(0xFFE3F2FD)
                else -> Color(0xFFE8F5E9)
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = profileBg,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = profileText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = profileColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Dual-Mic Beamforming Active • 80Hz Rumble & Fan Filter ON",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFE8F5E9),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🔊 Loudspeaker Audio Active",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1B5E20)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Sensitivity & Whisper Boost Slider Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Audio Sensitivity / Whisper Boost",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${audioSensitivity.toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = audioSensitivity,
                        onValueChange = onSensitivityChange,
                        valueRange = 10f..100f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Sensitivity preset chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AssistChip(
                            onClick = { onSensitivityChange(30f) },
                            label = { Text("30% Normal", style = MaterialTheme.typography.labelSmall) }
                        )
                        AssistChip(
                            onClick = { onSensitivityChange(60f) },
                            label = { Text("60% Boost", style = MaterialTheme.typography.labelSmall) }
                        )
                        AssistChip(
                            onClick = { onSensitivityChange(100f) },
                            label = { Text("100% Whisper", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "💡 Slide to 100% to amplify distant room whispers. Hardware noise suppression eliminates ceiling fan & AC humming.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Start / Stop Recording Button
            Button(
                onClick = onToggleRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (isRecording) Color.White else MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val recTimeStr = remember(recordingDurationSeconds) {
                    val m = recordingDurationSeconds / 60
                    val s = recordingDurationSeconds % 60
                    String.format(Locale.getDefault(), "%02d:%02d", m, s)
                }
                Text(
                    text = if (isRecording) "Stop Recording ($recTimeStr)" else "Start Audio Recording",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun ChildUserCard(
    user: User,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    var recordings by remember { mutableStateOf<List<RecordingSession>>(emptyList()) }
    var showRecordings by remember { mutableStateOf(false) }
    var currentlyPlayingRecId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {}
            mediaPlayer = null
        }
    }

    LaunchedEffect(user.uid) {
        FirebaseRepository.listenToRecordings(user.uid) { list ->
            recordings = list
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (user.isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = user.name.ifEmpty { "Child Device" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (user.isOnline) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = if (user.isOnline) "ONLINE 🟢" else "OFFLINE ⚪",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (user.isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp)
            )

            if (user.isOnline) {
                Text(
                    text = "Internet Connected • Ready for streaming",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                )
            } else if (user.lastSeen > 0L) {
                val lastSeenText = remember(user.lastSeen) {
                    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    sdf.format(Date(user.lastSeen))
                }
                Text(
                    text = "Internet Disconnected • Last seen: $lastSeenText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                )
            } else {
                Text(
                    text = "Internet Disconnected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        AppAnalytics.logButtonClick("audio_cast", "ParentScreen", mapOf("childId" to user.uid))
                        if (!user.isOnline) {
                            Toast.makeText(context, "Child device is offline (No Internet). Wake-up request sent.", Toast.LENGTH_SHORT).show()
                            AppAnalytics.logActionFailure("audio_cast", "ParentScreen", "Child device is offline")
                        }
                        onAudioClick()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Audio", style = MaterialTheme.typography.labelSmall)
                }

                FilledTonalButton(
                    onClick = {
                        AppAnalytics.logButtonClick("video_cast", "ParentScreen", mapOf("childId" to user.uid))
                        if (!user.isOnline) {
                            Toast.makeText(context, "Child device is offline (No Internet). Wake-up request sent.", Toast.LENGTH_SHORT).show()
                            AppAnalytics.logActionFailure("video_cast", "ParentScreen", "Child device is offline")
                        }
                        onVideoClick()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Video", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        AppAnalytics.logButtonClick("gps_location", "ParentScreen", mapOf("childId" to user.uid))
                        onLocationClick()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("GPS", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable Saved Recordings section
            OutlinedButton(
                onClick = {
                    showRecordings = !showRecordings
                    AppAnalytics.logButtonClick("toggle_saved_recordings", "ParentScreen")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showRecordings) "Hide Saved Recordings (${recordings.size})" else "View Saved Recordings (${recordings.size})")
            }

            AnimatedVisibility(visible = showRecordings) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (recordings.isEmpty()) {
                        Text(
                            text = "No saved stream recordings yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
                        recordings.forEach { rec ->
                            val localFile = if (rec.localFilePath.isNotEmpty()) File(rec.localFilePath) else null
                            val hasLocalFile = localFile != null && localFile.exists() && localFile.length() > 0
                            val hasRemoteUrl = rec.storageUrl.isNotEmpty() && (rec.storageUrl.startsWith("http://") || rec.storageUrl.startsWith("https://"))
                            val isPlayable = hasLocalFile || hasRemoteUrl
                            val isPlayingThis = currentlyPlayingRecId == rec.id

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPlayingThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (rec.streamType.equals("audio", ignoreCase = true)) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.secondaryContainer
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (rec.streamType.equals("audio", ignoreCase = true)) Icons.Default.Mic else Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = if (rec.streamType.equals("audio", ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${rec.streamType.replaceFirstChar { it.uppercase() }} Stream Recording",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        val sizeMb = if (hasLocalFile && localFile != null) localFile.length().toFloat() / (1024 * 1024) else 0f
                                        val sizeStr = if (hasLocalFile && localFile != null) {
                                            if (sizeMb >= 0.1f) String.format(Locale.getDefault(), " • %.1f MB", sizeMb)
                                            else " • ${localFile.length() / 1024} KB"
                                        } else ""
                                        Text(
                                            text = "${dateFormat.format(Date(rec.startTime))} • ${rec.durationSeconds}s$sizeStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isPlayingThis) {
                                            Text(
                                                text = "▶️ Playing on loudspeaker...",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (isPlayable) {
                                        IconButton(
                                            onClick = {
                                                if (isPlayingThis) {
                                                    try {
                                                        mediaPlayer?.stop()
                                                        mediaPlayer?.release()
                                                    } catch (e: Exception) {}
                                                    mediaPlayer = null
                                                    currentlyPlayingRecId = null
                                                } else {
                                                    try {
                                                        mediaPlayer?.stop()
                                                        mediaPlayer?.release()
                                                    } catch (e: Exception) {}
                                                    try {
                                                        val mp = MediaPlayer().apply {
                                                            if (hasLocalFile && localFile != null) {
                                                                val fis = FileInputStream(localFile)
                                                                setDataSource(fis.fd)
                                                                fis.close()
                                                            } else {
                                                                setDataSource(rec.storageUrl)
                                                            }
                                                            setOnPreparedListener { player ->
                                                                player.start()
                                                                currentlyPlayingRecId = rec.id
                                                            }
                                                            setOnCompletionListener {
                                                                currentlyPlayingRecId = null
                                                            }
                                                            setOnErrorListener { _, _, _ ->
                                                                currentlyPlayingRecId = null
                                                                Toast.makeText(context, "Playback error", Toast.LENGTH_SHORT).show()
                                                                true
                                                            }
                                                            prepareAsync()
                                                        }
                                                        mediaPlayer = mp
                                                        Toast.makeText(context, "Buffering and playing stream...", Toast.LENGTH_SHORT).show()
                                                        AppAnalytics.logButtonClick("play_recording", "ParentScreen")
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Playback failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        AppAnalytics.logActionFailure("play_recording", "ParentScreen", e.localizedMessage ?: "Playback error", e)
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlayingThis) "Stop" else "Play",
                                                tint = if (isPlayingThis) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "No media file",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
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
fun SafeSurfaceViewRenderer(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    modifier: Modifier = Modifier
) {
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    DisposableEffect(videoTrack, rendererRef, isInitialized) {
        val renderer = rendererRef
        if (renderer != null && videoTrack != null && isInitialized) {
            try {
                videoTrack.addSink(renderer)
                FirebaseCrashlytics.getInstance().log("[WebRTC UI] Remote videoTrack attached to SurfaceViewRenderer sink")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
        onDispose {
            if (renderer != null && videoTrack != null && isInitialized) {
                try {
                    videoTrack.removeSink(renderer)
                    FirebaseCrashlytics.getInstance().log("[WebRTC UI] Remote videoTrack detached from SurfaceViewRenderer sink")
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                setZOrderMediaOverlay(true)
                setEnableHardwareScaler(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setMirror(false)
                if (eglContext != null) {
                    try {
                        init(eglContext, object : RendererCommon.RendererEvents {
                            override fun onFirstFrameRendered() {
                                FirebaseCrashlytics.getInstance().log("[WebRTC UI] First video frame rendered on SurfaceViewRenderer")
                            }
                            override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                                FirebaseCrashlytics.getInstance().log("[WebRTC UI] Frame resolution changed: ${videoWidth}x${videoHeight}, rot=$rotation")
                            }
                        })
                        isInitialized = true
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
                rendererRef = this
            }
        },
        update = { renderer ->
            rendererRef = renderer
            if (eglContext != null && !isInitialized) {
                try {
                    renderer.init(eglContext, null)
                    isInitialized = true
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        },
        onRelease = { renderer ->
            try {
                renderer.clearImage()
                renderer.release()
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            rendererRef = null
            isInitialized = false
        },
        modifier = modifier
    )
}
