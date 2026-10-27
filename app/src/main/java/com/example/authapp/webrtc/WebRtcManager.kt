package com.example.authapp.webrtc

import android.content.Context
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.LoudnessEnhancer
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcManager(
    context: Context,
    private val isReceiverOnly: Boolean = false
) {
    private val appContext: Context = context.applicationContext
    val eglBase: EglBase = EglBase.create(null, EglBase.CONFIG_PLAIN)
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var hardwareAgc: AutomaticGainControl? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val pendingCandidates = mutableListOf<IceCandidate>()
    @Volatile
    private var isRemoteDescriptionSet = false
    @Volatile
    private var isCameraRunning = false
    @Volatile
    private var isStopped = false

    init {
        try {
            initializePcfIfNeeded(appContext)

            val encoderFactory = WebRtcCodecHelper.createEncoderFactory(eglBase.eglBaseContext)
            val decoderFactory = WebRtcCodecHelper.createDecoderFactory(eglBase.eglBaseContext)
            val adm = WebRtcCodecHelper.createAudioDeviceModule(appContext, isReceiverOnly)
            audioDeviceModule = adm

            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setOptions(PeerConnectionFactory.Options())
                .createPeerConnectionFactory()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[WebRtcManager] Initialization error: ${e.localizedMessage}")
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    // --- Broadcaster Mode (Child Device) ---

    fun startStream(
        streamType: String,
        iceServers: List<PeerConnection.IceServer>,
        onIceCandidate: (IceCandidate) -> Unit,
        onSdpCreated: (SessionDescription) -> Unit,
        onRemoteTrackAdded: (MediaStreamTrack) -> Unit = {},
        mediaProjectionData: android.content.Intent? = null
    ) {
        val result = WebRtcBroadcasterSession.startStream(
            appContext = appContext,
            eglBaseContext = eglBase.eglBaseContext,
            factory = factory,
            audioDeviceModule = audioDeviceModule,
            streamType = streamType,
            iceServers = iceServers,
            onIceCandidate = onIceCandidate,
            onSdpCreated = onSdpCreated,
            onRemoteTrackAdded = onRemoteTrackAdded,
            mediaProjectionData = mediaProjectionData,
            isStoppedChecker = { isStopped }
        ) ?: return

        peerConnection = result.peerConnection
        audioSource = result.audioSource
        audioTrack = result.audioTrack
        videoCapturer = result.videoCapturer
        surfaceTextureHelper = result.surfaceTextureHelper
        videoSource = result.videoSource
        videoTrack = result.videoTrack
        hardwareAgc = result.hardwareAgc
        isCameraRunning = result.isCameraRunning
    }

    fun setRemoteAnswer(sdpString: String, onSetSuccess: (() -> Unit)? = null) {
        WebRtcBroadcasterSession.setRemoteAnswer(
            peerConnection = peerConnection,
            sdpString = sdpString,
            drainPendingCandidates = { drainPendingCandidates() },
            isStoppedChecker = { isStopped },
            onSetSuccess = onSetSuccess
        )
    }

    // --- Receiver Mode (Parent Device) ---

    fun startReceiver(
        iceServers: List<PeerConnection.IceServer>,
        onIceCandidate: (IceCandidate) -> Unit,
        onRemoteVideoTrack: (VideoTrack) -> Unit,
        onRemoteAudioTrack: (AudioTrack) -> Unit = {}
    ) {
        if (isStopped) return
        peerConnection = WebRtcReceiverSession.createReceiverPeerConnection(
            appContext, factory, iceServers, onIceCandidate, onRemoteVideoTrack, onRemoteAudioTrack
        ) { isStopped }
    }

    fun setRemoteOfferAndCreateAnswer(
        offerSdpString: String,
        onAnswerCreated: (SessionDescription) -> Unit
    ) {
        WebRtcReceiverSession.setRemoteOfferAndCreateAnswer(
            peerConnection, offerSdpString, onAnswerCreated, { drainPendingCandidates() }
        ) { isStopped }
    }

    fun addRemoteCandidate(sdpMid: String?, sdpMLineIndex: Int, sdp: String) {
        if (isStopped) return
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        synchronized(pendingCandidates) {
            if (peerConnection?.remoteDescription != null) {
                peerConnection?.addIceCandidate(candidate)
            } else {
                pendingCandidates.add(candidate)
            }
        }
    }

    private fun drainPendingCandidates() {
        synchronized(pendingCandidates) {
            isRemoteDescriptionSet = true
            for (candidate in pendingCandidates) {
                if (!isStopped) peerConnection?.addIceCandidate(candidate)
            }
            pendingCandidates.clear()
        }
    }

    fun switchCamera(onSwitched: ((Boolean) -> Unit)? = null) {
        if (isStopped) return
        WebRtcCameraHelper.switchCamera(videoCapturer, isCameraRunning, onSwitched)
    }

    fun getCameraDeviceName(targetMode: String): String? = WebRtcCameraHelper.getCameraDeviceName(appContext, targetMode)

    fun switchCameraMode(targetMode: String, onSwitched: ((String) -> Unit)? = null) {
        if (isStopped) return
        WebRtcCameraHelper.switchCameraMode(appContext, videoCapturer, isCameraRunning, targetMode, onSwitched)
    }

    private var audioLevelHandler: android.os.Handler? = null
    private var audioLevelRunnable: Runnable? = null
    @Volatile
    private var currentAudioLevel: Float = 0f

    fun startAudioLevelMonitoring(onLevel: (Float) -> Unit) {
        if (isStopped) return
        stopAudioLevelMonitoring()
        audioLevelHandler = android.os.Handler(android.os.Looper.getMainLooper())
        audioLevelRunnable = object : Runnable {
            override fun run() {
                if (isStopped) return
                val pc = peerConnection
                if (pc != null) {
                    try {
                        pc.getStats { report ->
                            val level = WebRtcAudioHelper.parseAudioLevelFromStats(report)
                            if (level != null) {
                                currentAudioLevel = level
                                onLevel(level)
                            }
                        }
                    } catch (_: Throwable) {}
                }
                if (!isStopped) audioLevelHandler?.postDelayed(this, 100L)
            }
        }
        audioLevelHandler?.post(audioLevelRunnable!!)
    }

    fun stopAudioLevelMonitoring() {
        audioLevelHandler?.removeCallbacksAndMessages(null)
        audioLevelHandler = null
        audioLevelRunnable = null
        currentAudioLevel = 0f
    }

    fun enableLoudnessBooster(audioSessionId: Int, gainMilliBel: Int = 1200) {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = WebRtcAudioHelper.createLoudnessEnhancer(audioSessionId, gainMilliBel)
        } catch (_: Throwable) {}
    }

    fun setLoudnessGain(gainMilliBel: Int) {
        try { loudnessEnhancer?.setTargetGain(gainMilliBel.coerceIn(0, 3000)) } catch (_: Throwable) {}
    }

    @Synchronized
    fun stopStream() {
        if (isStopped) return
        isStopped = true
        isCameraRunning = false
        stopAudioLevelMonitoring()

        val oldCapturer = videoCapturer
        val oldSurfaceHelper = surfaceTextureHelper
        val oldVideoTrack = videoTrack
        val oldVideoSource = videoSource
        val oldAudioTrack = audioTrack
        val oldAudioSource = audioSource
        val oldPeerConnection = peerConnection
        val oldAdm = audioDeviceModule
        val oldFactory = factory

        videoCapturer = null
        surfaceTextureHelper = null
        videoTrack = null
        videoSource = null
        audioTrack = null
        audioSource = null
        peerConnection = null
        audioDeviceModule = null
        factory = null

        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try { hardwareAgc?.release(); loudnessEnhancer?.release() } catch (_: Throwable) {}
            try { oldCapturer?.stopCapture(); oldCapturer?.dispose(); oldSurfaceHelper?.dispose() } catch (_: Throwable) {}
            try { oldVideoTrack?.setEnabled(false); oldVideoTrack?.dispose(); oldVideoSource?.dispose() } catch (_: Throwable) {}
            try { oldAudioTrack?.setEnabled(false); oldAudioTrack?.dispose(); oldAudioSource?.dispose() } catch (_: Throwable) {}
            try { oldPeerConnection?.dispose(); oldAdm?.release(); oldFactory?.dispose() } catch (_: Throwable) {}
        }

        synchronized(pendingCandidates) {
            pendingCandidates.clear()
            isRemoteDescriptionSet = false
        }
    }

    fun releaseEglBase() {
        try { eglBase.release() } catch (_: Throwable) {}
    }

    companion object {
        @Volatile
        private var isPcfInitialized = false

        @Synchronized
        fun initializePcfIfNeeded(ctx: Context) {
            if (!isPcfInitialized) {
                try {
                    val options = PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
                    PeerConnectionFactory.initialize(options)
                    isPcfInitialized = true
                } catch (t: Throwable) {
                    FirebaseCrashlytics.getInstance().recordException(t)
                }
            }
        }

        fun calculateSuperBoostGain(sensitivityPercent: Float): Double =
            WebRtcSdpHelper.calculateSuperBoostGain(sensitivityPercent)

        fun getGainMultiplierText(sensitivityPercent: Float): String =
            WebRtcSdpHelper.getGainMultiplierText(sensitivityPercent)

        fun calculateSafeAudioGain(sensitivityPercent: Float): Double =
            WebRtcSdpHelper.calculateSafeAudioGain(sensitivityPercent)

        fun getDefaultIceServers(): List<PeerConnection.IceServer> =
            WebRtcSdpHelper.getDefaultIceServers()

        fun preferOpusHighQuality(sdpDescription: String): String =
            WebRtcSdpHelper.preferOpusHighQuality(sdpDescription)

        fun restrictVideoToVP8Only(sdpDescription: String): String =
            WebRtcSdpHelper.restrictVideoToVP8Only(sdpDescription)

        fun optimizeOpusSdp(sdp: String): String =
            WebRtcSdpHelper.optimizeOpusSdp(sdp)
    }
}
