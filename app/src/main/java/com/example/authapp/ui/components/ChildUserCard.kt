package com.example.authapp.ui.components

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import com.example.authapp.analytics.AppAnalytics
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChildUserCard(
    user: User,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRecordings by remember { mutableStateOf(false) }
    var recordings by remember { mutableStateOf<List<RecordingSession>>(emptyList()) }
    var currentlyPlayingRecId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(user.uid) {
        val listener = FirebaseRepository.listenToRecordings(user.uid) { list ->
            recordings = list
        }
        onDispose {
            FirebaseRepository.removeValueListener("recordings/${user.uid}", listener)
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {}
            mediaPlayer = null
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Child Info Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (user.isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = user.name.ifEmpty { "Child Device" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (user.isOnline) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = if (user.isOnline) "🟢 Online" else "⚪ Offline",
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

            if (!user.isOnline && user.lastSeen > 0L) {
                val lastSeenText = remember(user.lastSeen) {
                    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    sdf.format(Date(user.lastSeen))
                }
                Text(
                    text = "Last seen: $lastSeenText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Actions: Audio, Video, GPS
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
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
