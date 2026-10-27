package com.example.authapp.ui.activity

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.example.authapp.analytics.AppAnalytics
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.audio.AudioOutputRoute
import com.example.authapp.audio.AudioRouteManager
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.webrtc.CandidateBatcher
import com.example.authapp.webrtc.WebRtcManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack

class LiveStreamReceiverCoordinator(
    private val context: Context,
    private val sessionId: String,
    private val childId: String,
    private val streamType: String,
    private val audioRouteManager: AudioRouteManager
) {
    var webRtcManager: WebRtcManager? = null
        private set

    val remoteVideoTrackState = mutableStateOf<VideoTrack?>(null)
    val remoteAudioTrackState = mutableStateOf<AudioTrack?>(null)
    val streamStatusTextState = mutableStateOf("Connecting to child device...")
    val streamElapsedSecondsState = mutableLongStateOf(0L)
    val audioSensitivityState = mutableFloatStateOf(100f)
    val liveAudioLevelState = mutableFloatStateOf(0f)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var sdpOfferListener: ValueEventListener? = null
    private var candidateListener: ChildEventListener? = null
    private var candidateBatcher: CandidateBatcher? = null
    private var streamTimerHandler: Handler? = null
    private var streamTimerRunnable: Runnable? = null

    fun initReceiver() {
        try {
            streamStatusTextState.value = "Connecting to child device..."
            FirebaseCrashlytics.getInstance().log("[LiveStreamReceiverCoordinator] Initializing receiver for session: $sessionId")

            val manager = WebRtcManager(context, isReceiverOnly = true)
            webRtcManager = manager

            initAudio()

            FirebaseRepository.fetchIceServers { iceServers ->
                try {
                    audioRouteManager.checkAndAutoRoute()
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[LiveStreamReceiverCoordinator] audioRouteManager error: ${e.localizedMessage}")
                }

                candidateBatcher = CandidateBatcher(sessionId, isParent = true)
                manager.startReceiver(
                    iceServers = iceServers,
                    onIceCandidate = { candidate -> candidateBatcher?.addCandidate(candidate) },
                    onRemoteVideoTrack = { track -> handleRemoteVideoTrack(track) },
                    onRemoteAudioTrack = { track -> handleRemoteAudioTrack(track) }
                )

                manager.startAudioLevelMonitoring { level ->
                    mainHandler.post { liveAudioLevelState.floatValue = level }
                }

                sdpOfferListener = FirebaseRepository.listenToSdpOffer(sessionId) { sdpOffer ->
                    mainHandler.post { streamStatusTextState.value = "Child Offer Received, establishing connection..." }
                    try {
                        manager.setRemoteOfferAndCreateAnswer(sdpOffer) { sdpAnswer ->
                            FirebaseRepository.sendSdpAnswer(sessionId, sdpAnswer.description)
                        }
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }

                candidateListener = FirebaseRepository.listenToCandidates(sessionId, listenToParentCandidates = false) { sdpMid, sdpMLineIndex, sdp ->
                    try {
                        manager.addRemoteCandidate(sdpMid, sdpMLineIndex, sdp)
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
            }
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(t)
            AppHealthTelemetry.logDiagnostic(context, "LIVE_STREAM_RECEIVER", "FAILED", "LiveStreamActivity init failed: ${t.localizedMessage}", t.message)
            streamStatusTextState.value = "Failed to start stream: ${t.localizedMessage}"
        }
    }

    private fun initAudio() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                val maxMusicVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
                val maxVoiceVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoiceVol, 0)
                am.isSpeakerphoneOn = true
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[LiveStreamReceiverCoordinator] AudioManager init error: ${e.localizedMessage}")
        }
    }

    private fun handleRemoteVideoTrack(track: VideoTrack) {
        mainHandler.post {
            remoteVideoTrackState.value = track
            val isScreen = streamType.contains("screen", ignoreCase = true)
            val isAudioActive = remoteAudioTrackState.value != null
            streamStatusTextState.value = when {
                isScreen -> "Live Screen Streaming 🟢"
                isAudioActive -> "Live Camera & Audio Streaming 🟢"
                else -> "Live Video Streaming 🟢"
            }
            startStreamDurationTimer()
            AppAnalytics.logFeatureUsage(if (isScreen) "screen_cast" else "video_cast", "connected")
        }
    }

    private fun handleRemoteAudioTrack(track: AudioTrack) {
        mainHandler.post {
            remoteAudioTrackState.value = track
            val isVideoActive = remoteVideoTrackState.value != null
            val isScreen = streamType.contains("screen", ignoreCase = true)
            streamStatusTextState.value = when {
                streamType.equals("audio", ignoreCase = true) -> "Live Audio Streaming 🟢"
                isVideoActive && !isScreen -> "Live Camera & Audio Streaming 🟢"
                else -> streamStatusTextState.value
            }
            startStreamDurationTimer()
            try {
                track.setEnabled(true)
                val boostGain = WebRtcManager.calculateSuperBoostGain(audioSensitivityState.floatValue)
                track.setVolume(boostGain)
                if (!audioRouteManager.isBluetoothConnected.value) {
                    audioRouteManager.setRoute(AudioOutputRoute.SPEAKER)
                }
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.let {
                    val maxVol = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    it.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                    val maxCallVol = it.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    it.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCallVol, 0)
                    it.isSpeakerphoneOn = true
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            AppAnalytics.logFeatureUsage("audio_cast", "connected")
        }
    }

    fun startStreamDurationTimer() {
        if (streamTimerHandler != null) return
        streamElapsedSecondsState.longValue = 0L
        streamTimerHandler = Handler(Looper.getMainLooper())
        streamTimerRunnable = object : Runnable {
            override fun run() {
                streamElapsedSecondsState.longValue += 1
                streamTimerHandler?.postDelayed(this, 1000L)
            }
        }
        streamTimerRunnable?.let { streamTimerHandler?.post(it) }
    }

    fun stopStreamDurationTimer() {
        streamTimerHandler?.removeCallbacksAndMessages(null)
        streamTimerHandler = null
        streamTimerRunnable = null
        streamElapsedSecondsState.longValue = 0L
    }

    fun cleanup() {
        stopStreamDurationTimer()
        remoteVideoTrackState.value = null
        remoteAudioTrackState.value = null
        mainHandler.removeCallbacksAndMessages(null)
        candidateBatcher?.flush()
        candidateBatcher?.clear()
        candidateBatcher = null
        FirebaseRepository.cleanupSignalingData(sessionId, childId)
        try { sdpOfferListener?.let { FirebaseRepository.removeSdpOfferListener(sessionId, it) } } catch (_: Exception) {}
        try { candidateListener?.let { FirebaseRepository.removeCandidateListener(sessionId, false, it) } } catch (_: Exception) {}
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
        } catch (_: Exception) {}
        try { webRtcManager?.stopStream() } catch (_: Throwable) {}
        webRtcManager = null
    }
}
