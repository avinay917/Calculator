package com.example.authapp.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.media.AudioManager
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.theme.AuthAppTheme
import com.example.authapp.webrtc.WebRtcManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

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

    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }
    var isFrontCamera by remember { mutableStateOf(true) }

    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var streamStatusText by remember { mutableStateOf("Connecting to child device...") }

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
                    }
                },
                onRemoteAudioTrack = { _ ->
                    mainHandler.post {
                        streamStatusText = "Live Audio Streaming 🟢"
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
                try {
                    audioManager?.mode = AudioManager.MODE_NORMAL
                    audioManager?.isSpeakerphoneOn = false
                } catch (e: Exception) {}
                FirebaseRepository.removeValueListener("signaling/$sessionId/sdpOffer", sdpOfferListener)
                FirebaseRepository.stopStream(childId, sessionId)
                manager.stopStream()
                webRtcManager = null
                remoteVideoTrack = null
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

    // Timer effect for stream recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0L
            while (isRecording) {
                delay(1000L)
                recordingSeconds++
            }
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
                        }
                    )
                }
            }
        }

        // Active Monitor Overlay Dialog
        activeSessionId?.let { sessionId ->
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", recordingSeconds / 60, recordingSeconds % 60)
            val disconnectAction = {
                if (isRecording && activeChildId != null && activeStreamType != null) {
                    FirebaseRepository.saveRecordingSession(activeChildId!!, activeStreamType!!.lowercase(), recordingSeconds) {
                        Toast.makeText(context, "Recording saved (${recordingSeconds}s)!", Toast.LENGTH_SHORT).show()
                    }
                }
                isRecording = false
                activeChildId?.let { childId ->
                    FirebaseRepository.stopStream(childId, sessionId)
                }
                activeSessionId = null
                activeStreamType = null
                activeChildId = null
                activeChildName = null
            }

            AlertDialog(
                onDismissRequest = disconnectAction,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Live ${activeStreamType ?: "Media"} Stream")
                        if (isRecording) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "REC $formattedTime",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.Red, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                },
                text = {
                    Column {
                        Text("Device: ${activeChildName ?: "Child"}", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = streamStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Video Stream Display via SurfaceViewRenderer
                        if (activeStreamType.equals("video", ignoreCase = true)) {
                            if (remoteVideoTrack != null && webRtcManager != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        SurfaceViewRenderer(ctx).apply {
                                            init(webRtcManager?.eglBase?.eglBaseContext, null)
                                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                                            setEnableHardwareScaler(true)
                                            setMirror(false)
                                            setZOrderMediaOverlay(true)
                                            remoteVideoTrack?.addSink(this)
                                        }
                                    },
                                    update = { view ->
                                        remoteVideoTrack?.addSink(view)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
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

                            // Live Camera Switch Control (Front/Back)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    isFrontCamera = !isFrontCamera
                                    FirebaseRepository.toggleCameraFacing(sessionId, isFrontCamera)
                                    Toast.makeText(context, "Switching Camera (Front/Back)...", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = "Switch Camera")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📷 Flip Camera (${if (isFrontCamera) "Front -> Back" else "Back -> Front"})")
                            }
                        } else {
                            // Audio Stream Visualizer Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Live Microphone Active",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Sound playing through loudspeaker",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Record Toggle Button
                        if (!isRecording) {
                            Button(
                                onClick = { isRecording = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔴 Start Cloud Recording")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    if (activeChildId != null && activeStreamType != null) {
                                        FirebaseRepository.saveRecordingSession(activeChildId!!, activeStreamType!!.lowercase(), recordingSeconds) {
                                            Toast.makeText(context, "Stream recording saved to Cloud!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    isRecording = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("⏹️ Stop Recording ($formattedTime)")
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = disconnectAction,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Disconnect Stream")
                    }
                }
            )
        }
    }
}

@Composable
fun ChildUserCard(
    user: User,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit
) {
    var recordings by remember { mutableStateOf<List<RecordingSession>>(emptyList()) }
    var showRecordings by remember { mutableStateOf(false) }
    val context = LocalContext.current

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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (!user.isOnline) {
                            Toast.makeText(context, "Child device is offline (No Internet). Wake-up request sent.", Toast.LENGTH_SHORT).show()
                        }
                        onAudioClick()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Audio Cast")
                }

                FilledTonalButton(
                    onClick = {
                        if (!user.isOnline) {
                            Toast.makeText(context, "Child device is offline (No Internet). Wake-up request sent.", Toast.LENGTH_SHORT).show()
                        }
                        onVideoClick()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Video Cast")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable Saved Recordings section
            OutlinedButton(
                onClick = { showRecordings = !showRecordings },
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
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (rec.streamType.equals("audio", ignoreCase = true)) Icons.Default.Mic else Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${rec.streamType.replaceFirstChar { it.uppercase() }} Stream Recording",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "${dateFormat.format(Date(rec.startTime))} • ${rec.durationSeconds}s",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = rec.storageUrl,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = {
                                        Toast.makeText(context, "Playing recording metadata from Cloud Storage", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = MaterialTheme.colorScheme.primary
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
fun VideoStreamRenderer(
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBaseContext, null)
                setEnableHardwareScaler(true)
                setMirror(false)
            }
        },
        update = { renderer ->
            videoTrack?.addSink(renderer)
        },
        modifier = modifier
    )
}
