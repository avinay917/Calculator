package com.example.authapp.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.*

class ActivityAudioPlayerState {
    var currentlyPlayingAudioUrl by mutableStateOf<String?>(null)
    var isAudioBuffering by mutableStateOf(false)
    var audioPositionMs by mutableIntStateOf(0)
    var audioDurationMs by mutableIntStateOf(0)
    var audioPlaybackSpeed by mutableFloatStateOf(1.0f)
    var mediaPlayer by mutableStateOf<MediaPlayer?>(null)

    fun togglePlaybackSpeed() {
        audioPlaybackSpeed = when (audioPlaybackSpeed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    val params = mp.playbackParams
                    params.speed = audioPlaybackSpeed
                    mp.playbackParams = params
                }
            }
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Int) {
        try {
            val target = positionMs.coerceIn(0, audioDurationMs)
            mediaPlayer?.seekTo(target)
            audioPositionMs = target
        } catch (_: Exception) {}
    }

    fun forward(seconds: Int = 10) {
        seekTo(audioPositionMs + (seconds * 1000))
    }

    fun rewind(seconds: Int = 10) {
        seekTo(audioPositionMs - (seconds * 1000))
    }

    fun downloadAudio(context: android.content.Context, url: String, fileName: String = "Call_Recording.m4a") {
        if (url.isBlank()) return
        try {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle(safeName)
                .setDescription("Downloading call audio recording...")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, safeName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
            dm?.enqueue(request)
            android.widget.Toast.makeText(context, "Download started: $safeName", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, "Cannot open download link", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        if (currentlyPlayingAudioUrl == url && mediaPlayer?.isPlaying == true) {
            stopAudio()
            return
        }
        stopAudio()
        currentlyPlayingAudioUrl = url
        isAudioBuffering = true
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    isAudioBuffering = false
                    audioDurationMs = mp.duration
                    try {
                        val params = mp.playbackParams
                        params.speed = audioPlaybackSpeed
                        mp.playbackParams = params
                    } catch (_: Exception) {}
                    mp.start()
                }
                setOnCompletionListener {
                    stopAudio()
                }
                setOnErrorListener { _, _, _ ->
                    stopAudio()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (_: Exception) {
            stopAudio()
        }
    }
}

@Composable
fun rememberActivityAudioPlayer(): ActivityAudioPlayerState {
    val state = remember { ActivityAudioPlayerState() }

    DisposableEffect(Unit) {
        onDispose { state.stopAudio() }
    }

    LaunchedEffect(state.currentlyPlayingAudioUrl, state.mediaPlayer) {
        while (state.currentlyPlayingAudioUrl != null && state.mediaPlayer != null) {
            try {
                state.mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        state.audioPositionMs = mp.currentPosition
                        state.audioDurationMs = mp.duration
                    }
                }
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(500)
        }
    }

    return state
}
