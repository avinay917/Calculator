package com.example.authapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.authapp.analytics.AppAnalytics
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.theme.AuthAppTheme
import com.example.authapp.webrtc.WebRtcManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
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

    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var currentRecordingFile by remember { mutableStateOf<File?>(null) }

    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var streamStatusText by remember { mutableStateOf("Connecting to child device...") }

    fun startAudioRecordingInternal() {
        try {
            val dir = File(context.filesDir, "audio_recordings")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            isRecording = true
            Toast.makeText(context, "🔴 Live audio recording started...", Toast.LENGTH_SHORT).show()
            AppAnalytics.logButtonClick("start_audio_recording", "ParentScreen")
            AppAnalytics.logFeatureUsage("audio_recording", "started")
        } catch (e: Exception) {
            Toast.makeText(context, "Could not start audio recorder: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            AppAnalytics.logActionFailure("start_audio_recording", "ParentScreen", e.localizedMessage ?: "Recorder start failed", e)
        }
    }

    fun stopAudioRecordingInternal() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false

        val savedFile = currentRecordingFile
        if (savedFile != null && savedFile.exists() && savedFile.length() > 0) {
            if (activeChildId != null && activeStreamType != null) {
                FirebaseRepository.saveRecordingSession(
                    childId = activeChildId!!,
                    streamType = activeStreamType!!.lowercase(),
                    durationSeconds = recordingSeconds,
                    localFilePath = savedFile.absolutePath
                ) {
                    Toast.makeText(context, "✅ Audio recording saved (${recordingSeconds}s)! Play below.", Toast.LENGTH_LONG).show()
                }
            }
            AppAnalytics.logButtonClick("stop_audio_recording", "ParentScreen", mapOf("duration" to recordingSeconds))
            AppAnalytics.logFeatureUsage("audio_recording", "saved", mapOf("duration" to recordingSeconds))
        } else {
            Toast.makeText(context, "Audio recording was empty or too short", Toast.LENGTH_SHORT).show()
        }
        currentRecordingFile = null
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startAudioRecordingInternal()
        } else {
            Toast.makeText(context, "Microphone permission required to record live audio", Toast.LENGTH_SHORT).show()
            AppAnalytics.logActionFailure("record_audio", "ParentScreen", "Microphone permission denied")
        }
    }

    val onToggleRecordingClick = {
        if (!isRecording) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startAudioRecordingInternal()
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            stopAudioRecordingInternal()
        }
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
                onRemoteAudioTrack = { _ ->
                    mainHandler.post {
                        streamStatusText = "Live Audio Streaming 🟢"
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
                try {
                    audioManager?.mode = AudioManager.MODE_NORMAL
                    audioManager?.isSpeakerphoneOn = false
                } catch (e: Exception) {}
                if (isRecording) {
                    stopAudioRecordingInternal()
                }
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
                if (isRecording) {
                    stopAudioRecordingInternal()
                }
                activeChildId?.let { childId ->
                    FirebaseRepository.stopStream(childId, sessionId)
                }
                activeSessionId = null
                activeStreamType = null
                activeChildId = null
                activeChildName = null
                AppAnalytics.logButtonClick("disconnect_stream", "ParentScreen")
            }

            AlertDialog(
                onDismissRequest = disconnectAction,
                shape = RoundedCornerShape(24.dp),
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
                                text = "Live ${activeStreamType ?: "Media"} Cast",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Device: ${activeChildName ?: "Child"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isRecording) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Red.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "REC $formattedTime",
                                    color = Color.Red,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
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
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(16.dp))
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
                                    AppAnalytics.logButtonClick("flip_camera", "ParentScreen")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = "Switch Camera")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📷 Flip Camera (${if (isFrontCamera) "Front -> Back" else "Back -> Front"})")
                            }
                        } else {
                            // High-tech Live Audio Visualizer
                            LiveAudioVisualizer(
                                isRecording = isRecording,
                                formattedTime = formattedTime
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Record Audio Toggle Button
                        if (!isRecording) {
                            Button(
                                onClick = onToggleRecordingClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔴 Record Live Audio", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onToggleRecordingClick,
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFD32F2F)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color(0xFFD32F2F))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("⏹️ Stop & Save Recording ($formattedTime)", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                            }
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
fun LiveAudioVisualizer(
    isRecording: Boolean,
    formattedTime: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioWave")
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
        initialValue = 0.5f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )
    val recBlink by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recBlink"
    )

    // Dynamic Equalizer bars animation
    val bar1 by infiniteTransition.animateFloat(initialValue = 16f, targetValue = 44f, animationSpec = infiniteRepeatable(tween(450, delayMillis = 40), RepeatMode.Reverse), label = "b1")
    val bar2 by infiniteTransition.animateFloat(initialValue = 24f, targetValue = 58f, animationSpec = infiniteRepeatable(tween(520, delayMillis = 80), RepeatMode.Reverse), label = "b2")
    val bar3 by infiniteTransition.animateFloat(initialValue = 14f, targetValue = 50f, animationSpec = infiniteRepeatable(tween(400, delayMillis = 120), RepeatMode.Reverse), label = "b3")
    val bar4 by infiniteTransition.animateFloat(initialValue = 28f, targetValue = 64f, animationSpec = infiniteRepeatable(tween(600, delayMillis = 60), RepeatMode.Reverse), label = "b4")
    val bar5 by infiniteTransition.animateFloat(initialValue = 18f, targetValue = 42f, animationSpec = infiniteRepeatable(tween(480, delayMillis = 100), RepeatMode.Reverse), label = "b5")

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
            // Live pulsing microphone avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(96.dp)
            ) {
                // Outer glowing pulse ring
                Box(
                    modifier = Modifier
                        .size(92.dp * pulseScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha))
                )
                // Center mic circle
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Live Mic",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Equalizer wave bars
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(68.dp)
            ) {
                listOf(bar1, bar2, bar3, bar4, bar5, bar3, bar2, bar1).forEach { heightVal ->
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(heightVal.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Loudspeaker status badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LOUDSPEAKER LIVE 🔊",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (isRecording) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Red.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.6f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = recBlink))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RECORDING LIVE AUDIO • $formattedTime",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Red
                            )
                        )
                    }
                }
            }
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Audio Cast")
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
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Video Cast")
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
                            val hasLocalFile = rec.localFilePath.isNotEmpty() && File(rec.localFilePath).exists()
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
                                        Text(
                                            text = "${dateFormat.format(Date(rec.startTime))} • ${rec.durationSeconds}s",
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
                                    if (hasLocalFile) {
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
                                                            setDataSource(rec.localFilePath)
                                                            prepare()
                                                            start()
                                                            setOnCompletionListener {
                                                                currentlyPlayingRecId = null
                                                            }
                                                        }
                                                        mediaPlayer = mp
                                                        currentlyPlayingRecId = rec.id
                                                        Toast.makeText(context, "Playing recorded audio...", Toast.LENGTH_SHORT).show()
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
