package com.example.authapp.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.audio.AmbientAudioStreamer
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.recorder.StreamAudioRecorder
import com.example.authapp.sync.SyncScheduler
import com.example.authapp.webrtc.CandidateBatcher
import com.example.authapp.webrtc.WebRtcManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener

class ChildStreamCoordinator(private val context: Context) {

    var webRtcManager: WebRtcManager? = null
        private set
    var ambientAudioStreamer: AmbientAudioStreamer? = null
        private set
    var streamAudioRecorder: StreamAudioRecorder? = null
        private set
    private var candidateBatcher: CandidateBatcher? = null

    var currentSessionId: String? = null
        private set
    var isStreaming = false
        private set
    @Volatile
    var isStartingStream = false
        private set
    private var lastStreamStartTime = 0L
    private var lastStartedSessionId = ""

    private var sdpAnswerListener: ValueEventListener? = null
    private var parentCandidateListener: ChildEventListener? = null
    private var cameraFacingListener: ValueEventListener? = null

    fun isDuplicateStreamRequest(sessionId: String): Boolean {
        if ((isStreaming || isStartingStream) && currentSessionId == sessionId) return true
        if (System.currentTimeMillis() - lastStreamStartTime < 4000L && lastStartedSessionId == sessionId) return true
        return false
    }

    fun startAmbientAudioStream(
        uid: String,
        sessionId: String,
        streamType: String,
        onAcquireWakeLock: () -> Unit,
        onStreamStarted: () -> Unit,
        onStreamFailed: () -> Unit
    ) {
        onAcquireWakeLock()
        isStreaming = true
        currentSessionId = sessionId

        ambientAudioStreamer?.stopStreaming(uid)
        val streamer = AmbientAudioStreamer(context.applicationContext)
        ambientAudioStreamer = streamer
        streamer.startStreaming(
            childId = uid,
            onStarted = {
                FirebaseRepository.updateStreamStatus(uid, "STREAMING", streamType, sessionId)
                FirebaseCrashlytics.getInstance().log("[ChildService] AmbientAudioStreamer active and streaming")
                onStreamStarted()
            },
            onError = { err ->
                FirebaseCrashlytics.getInstance().log("[ChildService] AmbientAudioStreamer error: $err")
                FirebaseRepository.updateStreamStatus(uid, "FAILED", streamType, sessionId)
                onStreamFailed()
            }
        )
    }

    fun startWebRtcStream(
        sessionId: String,
        streamType: String,
        projectionData: Intent? = null,
        onEnsureOverlay: () -> Unit,
        onAcquireWakeLock: () -> Unit,
        onReleaseWakeLock: () -> Unit,
        onRemoveOverlay: () -> Unit
    ) {
        synchronized(this) {
            if ((isStreaming || isStartingStream) && currentSessionId == sessionId) {
                FirebaseCrashlytics.getInstance().log("[ChildService] startWebRtcStream duplicate ignored for $sessionId")
                return
            }
            if (System.currentTimeMillis() - lastStreamStartTime < 4000L && lastStartedSessionId == sessionId) {
                FirebaseCrashlytics.getInstance().log("[ChildService] startWebRtcStream 4s debounce ignored for $sessionId")
                return
            }
        }

        if (isStreaming || webRtcManager != null) {
            stopStream(onReleaseWakeLock, onRemoveOverlay)
        }

        synchronized(this) {
            lastStreamStartTime = System.currentTimeMillis()
            lastStartedSessionId = sessionId
            isStartingStream = true
            currentSessionId = sessionId
            isStreaming = true
        }
        onAcquireWakeLock()

        val uid = AppHealthTelemetry.getEffectiveUserId(context.applicationContext)
        if (uid.isNotEmpty()) {
            try {
                FirebaseRepository.updateStreamStatus(uid, "STREAMING", streamType, sessionId)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildService] STREAMING status update failed: ${e.localizedMessage}")
            }
        }

        if (streamType.equals("video", ignoreCase = true)) {
            onEnsureOverlay()
        }

        try {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.mode = AudioManager.MODE_NORMAL
                audioManager?.isMicrophoneMute = false
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildService] AudioManager mode error: ${e.localizedMessage}")
            }

            webRtcManager = WebRtcManager(context.applicationContext)
            candidateBatcher = CandidateBatcher(sessionId, isParent = false)
            FirebaseRepository.fetchIceServers { iceServers ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Starting WebRTC stream ($streamType) with ${iceServers.size} ICE servers for session: $sessionId")

                webRtcManager?.startStream(
                    streamType = streamType,
                    iceServers = iceServers,
                    mediaProjectionData = projectionData,
                    onIceCandidate = { candidate ->
                        candidateBatcher?.addCandidate(candidate)
                    },
                    onSdpCreated = { sdp ->
                        isStartingStream = false
                        FirebaseCrashlytics.getInstance().log("[ChildService] Sending SDP Offer to RTDB")
                        FirebaseRepository.sendSdpOffer(sessionId, sdp.description)
                    }
                )

                sdpAnswerListener = FirebaseRepository.listenToSdpAnswer(sessionId) { sdpAnswer ->
                    FirebaseCrashlytics.getInstance().log("[ChildService] Received SDP Answer from Parent")
                    webRtcManager?.setRemoteAnswer(sdpAnswer)
                    sdpAnswerListener?.let { l ->
                        FirebaseRepository.removeSdpAnswerListener(sessionId, l)
                        sdpAnswerListener = null
                    }
                }

                parentCandidateListener = FirebaseRepository.listenToCandidates(sessionId, listenToParentCandidates = true) { sdpMid, sdpMLineIndex, sdp ->
                    FirebaseCrashlytics.getInstance().log("[ChildService] Received Parent ICE Candidate")
                    webRtcManager?.addRemoteCandidate(sdpMid, sdpMLineIndex, sdp)
                }

                cameraFacingListener = FirebaseRepository.listenToCameraFacingMode(sessionId) { facingMode ->
                    FirebaseCrashlytics.getInstance().log("[ChildService] Camera switch requested: mode=$facingMode")
                    webRtcManager?.switchCameraMode(facingMode)
                }
            }
        } catch (t: Throwable) {
            isStartingStream = false
            FirebaseCrashlytics.getInstance().recordException(t)
            AppHealthTelemetry.logDiagnostic(context.applicationContext, "WEBRTC_STREAM", "FAILED", "Failed to start WebRTC stream on child: ${t.localizedMessage}", t.message)
            stopStream(onReleaseWakeLock, onRemoveOverlay)
        }
    }

    fun handleRemoteRecording(uid: String, isRecording: Boolean, recType: String) {
        if (isRecording) {
            if (streamAudioRecorder == null) {
                streamAudioRecorder = StreamAudioRecorder(context.applicationContext)
            }
            streamAudioRecorder?.startRecording(recType)
        } else {
            val durationSec = streamAudioRecorder?.recordingStartTimeMillis?.let {
                if (it > 0L) (System.currentTimeMillis() - it) / 1000L else 0L
            } ?: 0L
            val recordedFile = streamAudioRecorder?.stopRecording()
            streamAudioRecorder = null
            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                SyncScheduler.enqueueUpload(
                    context = context.applicationContext,
                    file = recordedFile,
                    childId = uid,
                    streamType = recType,
                    durationSeconds = durationSec
                )
                FirebaseCrashlytics.getInstance().log("[ChildService] Remote stream recording enqueued (offline-safe)")
            }
        }
    }

    fun stopStream(onReleaseWakeLock: () -> Unit, onRemoveOverlay: () -> Unit) {
        val oldSessionId = currentSessionId
        isStreaming = false
        isStartingStream = false
        currentSessionId = null

        if (!oldSessionId.isNullOrEmpty()) {
            sdpAnswerListener?.let { FirebaseRepository.removeSdpAnswerListener(oldSessionId, it) }
            parentCandidateListener?.let { FirebaseRepository.removeCandidateListener(oldSessionId, listenToParentCandidates = true, it) }
            cameraFacingListener?.let { FirebaseRepository.removeCameraFacingListener(oldSessionId, it) }
            FirebaseRepository.deleteSignalingSession(oldSessionId)
        }
        candidateBatcher?.flush()
        candidateBatcher?.clear()
        candidateBatcher = null

        sdpAnswerListener = null
        parentCandidateListener = null
        cameraFacingListener = null

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] audioManager reset error: ${e.localizedMessage}")
        }
        try {
            webRtcManager?.stopStream()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        webRtcManager = null

        try {
            val uid = AppHealthTelemetry.getEffectiveUserId(context.applicationContext)
            ambientAudioStreamer?.stopStreaming(uid)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        ambientAudioStreamer = null

        onReleaseWakeLock()
        onRemoveOverlay()
    }
}
