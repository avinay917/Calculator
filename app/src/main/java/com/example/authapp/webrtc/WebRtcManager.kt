package com.example.authapp.webrtc

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.NoiseSuppressor
import com.example.authapp.analytics.AppHealthTelemetry
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import android.os.Build
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
    private var hardwareNs: NoiseSuppressor? = null
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

            // Force VP8-only encoder: Child phone (A54) advertises ONLY VP8 in SDP offer.
            // This prevents H264/AV1 codec negotiation which causes RTC_CHECK(decoder!=nullptr)
            // SIGABRT on Parent phone (A21s, Exynos 850) where SoftwareVideoDecoderFactory
            // returns null for H264 → native abort in libc.so.
            val baseEncoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, false)
            val encoderFactory = object : VideoEncoderFactory {
                override fun createEncoder(info: VideoCodecInfo?): VideoEncoder? =
                    baseEncoderFactory.createEncoder(info)
                override fun getSupportedCodecs(): Array<VideoCodecInfo> =
                    baseEncoderFactory.supportedCodecs
                        .filter { it.name.equals("VP8", ignoreCase = true) }
                        .toTypedArray()
            }
            val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            val decoderFactory = object : VideoDecoderFactory {
                // On Samsung devices (especially Exynos chips like A21s), hardware OMX decoding causes native SIGABRT in libc.so.
                // SoftwareVideoDecoderFactory (libvpx VP8/VP9) is completely crash-safe and lightweight.
                private val hardwareFactory = if (isSamsung) null else try { DefaultVideoDecoderFactory(eglBase.eglBaseContext) } catch (_: Throwable) { null }
                private val softwareFactory = SoftwareVideoDecoderFactory()

                override fun createDecoder(info: VideoCodecInfo?): VideoDecoder? {
                    return if (isSamsung) {
                        softwareFactory.createDecoder(info)
                    } else {
                        try {
                            hardwareFactory?.createDecoder(info) ?: softwareFactory.createDecoder(info)
                        } catch (_: Throwable) {
                            softwareFactory.createDecoder(info)
                        }
                    }
                }

                override fun getSupportedCodecs(): Array<VideoCodecInfo> {
                    val list = mutableListOf<VideoCodecInfo>()
                    if (!isSamsung) {
                        try { hardwareFactory?.supportedCodecs?.let { list.addAll(it) } } catch (_: Throwable) {}
                    }
                    try { softwareFactory.supportedCodecs?.let { list.addAll(it) } } catch (_: Throwable) {}
                    return list.distinctBy { it.name }.toTypedArray()
                }
            }

            val adm = if (isReceiverOnly) {
                // Receiver only needs audio output/playback — no mic hardware access
                JavaAudioDeviceModule.builder(appContext)
                    .setUseHardwareAcousticEchoCanceler(false)
                    .setUseHardwareNoiseSuppressor(false)
                    .createAudioDeviceModule()
            } else {
                JavaAudioDeviceModule.builder(appContext)
                    .setUseHardwareAcousticEchoCanceler(false) // One-way surveillance: disable hardware AEC so mic gain is not suppressed
                    .setUseHardwareNoiseSuppressor(false)      // Disable aggressive hardware gating so quiet whispers are captured
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION) // Hardware beamforming
                    .setAudioFormat(AudioFormat.ENCODING_PCM_16BIT)
                    .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                        override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                            AppHealthTelemetry.logDiagnostic(appContext, "LIVE_AUDIO", "FAILED", "AudioRecord Init Error: $errorMessage", errorMessage)
                            FirebaseCrashlytics.getInstance().log("[WebRTC AudioRecord Init Error] $errorMessage")
                        }
                        override fun onWebRtcAudioRecordStartError(errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?, errorMessage: String?) {
                            AppHealthTelemetry.logDiagnostic(appContext, "LIVE_AUDIO", "FAILED", "AudioRecord Start Error $errorCode: $errorMessage", errorMessage)
                            FirebaseCrashlytics.getInstance().log("[WebRTC AudioRecord Start Error] $errorCode: $errorMessage")
                        }
                        override fun onWebRtcAudioRecordError(errorMessage: String?) {
                            AppHealthTelemetry.logDiagnostic(appContext, "LIVE_AUDIO", "FAILED", "AudioRecord Error: $errorMessage", errorMessage)
                            FirebaseCrashlytics.getInstance().log("[WebRTC AudioRecord Error] $errorMessage")
                        }
                    })
                    .createAudioDeviceModule()
            }
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
        streamType: String, // "audio" or "video"
        iceServers: List<PeerConnection.IceServer>,
        onIceCandidate: (IceCandidate) -> Unit,
        onSdpCreated: (SessionDescription) -> Unit,
        onRemoteTrackAdded: (MediaStreamTrack) -> Unit = {}
    ) {
        if (isStopped) return
        val perfTrace = FirebasePerformance.getInstance().newTrace("webrtc_stream_init_$streamType")
        perfTrace.start()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            enableDscp = true // QoS Expedited Forwarding priority for voice packets
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Broadcaster] ICE Connection State: $state")
                if (state == PeerConnection.IceConnectionState.FAILED) {
                    AppHealthTelemetry.logDiagnostic(appContext, "WEBRTC_CONNECTION", "FAILED", "ICE connection failed (NAT/Firewall block)")
                    FirebaseCrashlytics.getInstance().recordException(Exception("WebRTC ICE Connection Failed"))
                } else if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    AppHealthTelemetry.logDiagnostic(appContext, "WEBRTC_CONNECTION", "SUCCESS", "WebRTC peer connection active and streaming")
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (!isStopped) candidate?.let { onIceCandidate(it) }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                if (!isStopped) receiver?.track()?.let { onRemoteTrackAdded(it) }
            }
        })

        // Audio Track with High-Sensitivity Whisper Capture & Digital AGC2
        val audioConstraints = MediaConstraints().apply {
            // One-way listening: disable AEC so microphone input sensitivity is 100% full
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
            // WebRTC software AGC2: automatically boosts distant and whispered voices (+25 dB)
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
            // Noise suppression tuned to preserve human voice band
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
            // Disable highpass filter to keep deep/low vocal fundamentals (100Hz-300Hz) intact
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
            // Suppress screen taps and physical vibration
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
        }
        audioSource = factory?.createAudioSource(audioConstraints)
        audioTrack = factory?.createAudioTrack("ARDAMSa0", audioSource)
        audioTrack?.setEnabled(true)
        audioTrack?.let { at ->
            peerConnection?.addTransceiver(
                at,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
            )
        }
        enableHardwareAudioEffects()

        // Video Track (if requested)
        if (streamType.equals("video", ignoreCase = true)) {
            var capturer: VideoCapturer? = null
            for (attempt in 1..3) {
                capturer = createVideoCapturer(preferFront = true)
                    ?: createVideoCapturer(preferFront = false)
                if (capturer != null) break
                FirebaseCrashlytics.getInstance().log("[WebRTC] Camera capturer attempt $attempt returned null, retrying in 250ms...")
                try { Thread.sleep(250L) } catch (_: InterruptedException) {}
            }
            if (capturer != null) {
                videoCapturer = capturer
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                videoSource = factory?.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, appContext, videoSource?.capturerObserver)

                var started = false
                val resolutions = listOf(
                    Triple(640, 480, 30),
                    Triple(640, 480, 15),
                    Triple(480, 360, 24),
                    Triple(320, 240, 15)
                )
                for ((w, h, fps) in resolutions) {
                    try {
                        capturer.startCapture(w, h, fps)
                        FirebaseCrashlytics.getInstance().log("[WebRTC] Camera capture started: ${w}x${h}@$fps")
                        started = true
                        break
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC] Camera ${w}x${h}@$fps failed: ${e.localizedMessage}")
                    }
                }

                if (!started) {
                    try { capturer.dispose() } catch (_: Exception) {}
                    try { surfaceTextureHelper?.dispose() } catch (_: Exception) {}
                    surfaceTextureHelper = null
                    videoCapturer = null

                    val fallbackCapturer = createVideoCapturer(preferFront = false)
                    if (fallbackCapturer != null) {
                        videoCapturer = fallbackCapturer
                        val newSurfaceHelper = SurfaceTextureHelper.create("CaptureThreadFallback", eglBase.eglBaseContext)
                        surfaceTextureHelper = newSurfaceHelper
                        fallbackCapturer.initialize(newSurfaceHelper, appContext, videoSource?.capturerObserver)
                        for ((w, h, fps) in resolutions) {
                            try {
                                fallbackCapturer.startCapture(w, h, fps)
                                FirebaseCrashlytics.getInstance().log("[WebRTC] Fallback Camera capture started: ${w}x${h}@$fps")
                                started = true
                                break
                            } catch (_: Exception) {}
                        }
                    }
                }

                isCameraRunning = started
                if (started && videoSource != null) {
                    AppHealthTelemetry.logDiagnostic(appContext, "LIVE_VIDEO", "SUCCESS", "Camera capturer active and video track attached")
                    videoTrack = factory?.createVideoTrack("ARDAMSv0", videoSource)
                    videoTrack?.setEnabled(true)
                    videoTrack?.let { vt ->
                        val transceiver = peerConnection?.addTransceiver(
                            vt,
                            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
                        )
                        // Adaptive Bitrate Configuration: Limit video stream max bitrate to 1Mbps to prevent network congestion
                        try {
                            val sender = transceiver?.sender
                            val parameters = sender?.parameters
                            if (parameters != null && parameters.encodings.isNotEmpty()) {
                                for (encoding in parameters.encodings) {
                                    encoding.maxBitrateBps = 1000 * 1000 // 1 Mbps max bitrate for 480p/720p
                                    encoding.minBitrateBps = 150 * 1000  // 150 Kbps fallback for weak network
                                }
                                sender.parameters = parameters
                            }
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().log("[WebRTC] Adaptive bitrate config notice: ${e.localizedMessage}")
                        }
                    }
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Video track added to PeerConnection via transceiver (started=$started)")
                } else {
                    AppHealthTelemetry.logDiagnostic(appContext, "LIVE_VIDEO", "FAILED", "Camera startCapture failed for all fallback resolutions")
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Camera startCapture failed: video track omitted")
                }
            } else {
                AppHealthTelemetry.logDiagnostic(appContext, "LIVE_VIDEO", "FAILED", "No camera capturer could be created (Camera hardware busy or CAMERA permission missing)")
                FirebaseCrashlytics.getInstance().log("[WebRTC] ERROR: No camera capturer could be created!")
            }
        }

        // Offer banate waqt empty constraints do, legacy flags mat do (Fix 5)
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (isStopped) return
                desc?.let { originalOffer ->
                    val opusFiltered = preferOpusHighQuality(originalOffer.description)
                    val vp8Filtered = restrictVideoToVP8Only(opusFiltered)
                    val optimizedOffer = SessionDescription(originalOffer.type, vp8Filtered)
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            if (!isStopped) {
                                perfTrace.stop()
                                onSdpCreated(optimizedOffer)
                            }
                        }
                        override fun onCreateFailure(p0: String?) {
                            FirebaseCrashlytics.getInstance().log("[WebRTC] Local SDP Create Failure: $p0")
                        }
                        override fun onSetFailure(p0: String?) {
                            FirebaseCrashlytics.getInstance().log("[WebRTC] Local SDP Set Failure: $p0")
                        }
                    }, optimizedOffer)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Offer Create Failure: $error")
            }
            override fun onSetFailure(error: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Offer Set Failure: $error")
            }
        }, MediaConstraints())
    }

    fun setRemoteAnswer(sdpString: String, onSetSuccess: (() -> Unit)? = null) {
        if (isStopped) return
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Remote Answer set successfully.")
                drainPendingCandidates()
                if (!isStopped) onSetSuccess?.invoke()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Remote Answer set failure: $p0")
            }
        }, sdp)
    }

    // --- Receiver Mode (Parent Device) ---

    fun startReceiver(
        iceServers: List<PeerConnection.IceServer>,
        onIceCandidate: (IceCandidate) -> Unit,
        onRemoteVideoTrack: (VideoTrack) -> Unit,
        onRemoteAudioTrack: (AudioTrack) -> Unit = {}
    ) {
        if (isStopped) return
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            enableDscp = true // QoS Expedited Forwarding packet tagging
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Signaling State: $state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] ICE Connection State: $state")
                when (state) {
                    PeerConnection.IceConnectionState.FAILED ->
                        AppHealthTelemetry.logDiagnostic(appContext, "WEBRTC_RECEIVER", "FAILED", "Receiver ICE connection failed")
                    PeerConnection.IceConnectionState.CONNECTED ->
                        AppHealthTelemetry.logDiagnostic(appContext, "WEBRTC_RECEIVER", "SUCCESS", "Receiver ICE connected")
                    PeerConnection.IceConnectionState.DISCONNECTED ->
                        FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] ICE disconnected")
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (!isStopped) candidate?.let { onIceCandidate(it) }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                if (isStopped) return
                val track = receiver?.track()
                when (track) {
                    is VideoTrack -> {
                        FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Remote VideoTrack received")
                        track.setEnabled(true)
                        onRemoteVideoTrack(track)
                    }
                    is AudioTrack -> {
                        FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Remote AudioTrack received")
                        track.setEnabled(true)
                        track.setVolume(1.5) // Default slight boost on parent receiver
                        onRemoteAudioTrack(track)
                    }
                    else -> {}
                }
            }
        })

        // Add RECV_ONLY transceivers so SDP answer includes audio + video slots
        try {
            peerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
            peerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Add transceiver exception: ${e.localizedMessage}")
        }
    }

    fun setRemoteOfferAndCreateAnswer(
        offerSdpString: String,
        onAnswerCreated: (SessionDescription) -> Unit
    ) {
        if (isStopped) return
        val offerDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                if (isStopped) return
                drainPendingCandidates()
                val mediaConstraints = MediaConstraints()
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        if (isStopped) return
                        answerDesc?.let { originalAnswer ->
                            val opusFiltered = preferOpusHighQuality(originalAnswer.description)
                            val vp8Filtered = restrictVideoToVP8Only(opusFiltered)
                            val optimizedAnswer = SessionDescription(originalAnswer.type, vp8Filtered)
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    if (!isStopped) onAnswerCreated(optimizedAnswer)
                                }
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(p0: String?) {}
                            }, optimizedAnswer)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Create Answer Failure: $p0")
                    }
                    override fun onSetFailure(p0: String?) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Set Answer Failure: $p0")
                    }
                }, mediaConstraints)
            }
            override fun onCreateFailure(p0: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Remote Offer Set Failure: $p0")
            }
            override fun onSetFailure(p0: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Remote Offer Set Failure: $p0")
            }
        }, offerDesc)
    }

    fun addRemoteCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        if (isStopped) return
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        synchronized(pendingCandidates) {
            // Gate on actual remote description being set, not just the flag
            if (peerConnection?.remoteDescription != null) {
                val added = peerConnection?.addIceCandidate(candidate)
                FirebaseCrashlytics.getInstance().log("[WebRTC] Added remote ICE candidate directly: $added")
            } else {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Queued remote ICE candidate (pending remote description)")
                pendingCandidates.add(candidate)
            }
        }
    }

    private fun drainPendingCandidates() {
        synchronized(pendingCandidates) {
            isRemoteDescriptionSet = true
            FirebaseCrashlytics.getInstance().log("[WebRTC] Draining ${pendingCandidates.size} queued ICE candidates")
            for (candidate in pendingCandidates) {
                if (!isStopped) peerConnection?.addIceCandidate(candidate)
            }
            pendingCandidates.clear()
        }
    }

    private fun createVideoCapturer(preferFront: Boolean = true): VideoCapturer? {
        val cameraEventsHandler = object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(errorDescription: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Camera Error] $errorDescription")
                AppHealthTelemetry.logDiagnostic(appContext, "LIVE_VIDEO", "FAILED", "Camera hardware error: $errorDescription")
                if (!isStopped) {
                    try {
                        val capturer = videoCapturer as? CameraVideoCapturer
                        capturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                                FirebaseCrashlytics.getInstance().log("[WebRTC] Auto-switched to alternate camera lens (isFront=$isFrontCamera)")
                            }
                            override fun onCameraSwitchError(switchError: String?) {
                                FirebaseCrashlytics.getInstance().log("[WebRTC] Auto-switch camera error: $switchError")
                            }
                        })
                    } catch (_: Exception) {}
                }
            }
            override fun onCameraDisconnected() {
                FirebaseCrashlytics.getInstance().log("[WebRTC Camera Disconnected]")
                AppHealthTelemetry.logDiagnostic(appContext, "LIVE_VIDEO", "FAILED", "Camera disconnected by Android OS")
            }
            override fun onCameraFreezed(errorDescription: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Camera Freezed] $errorDescription")
                AppHealthTelemetry.logDiagnostic(appContext, "LIVE_VIDEO", "WARNING", "Camera frozen: $errorDescription")
            }
            override fun onCameraOpening(cameraName: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Camera Opening] $cameraName")
                AppHealthTelemetry.logDiagnostic(appContext, "LIVE_VIDEO", "OPENING", "Opening camera: $cameraName")
            }
            override fun onFirstFrameAvailable() {
                FirebaseCrashlytics.getInstance().log("[WebRTC First Frame Available]")
                AppHealthTelemetry.logDiagnostic(appContext, "LIVE_VIDEO", "FRAME_PRODUCED", "First video frame captured & encoding!")
            }
            override fun onCameraClosed() {
                FirebaseCrashlytics.getInstance().log("[WebRTC Camera Closed]")
            }
        }

        // 1. Try Camera2Enumerator first
        try {
            val enumerator = Camera2Enumerator(appContext)
            val capturer = findCapturer(enumerator, preferFront, cameraEventsHandler)
            if (capturer != null) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Created Camera2Capturer successfully (preferFront=$preferFront)")
                return capturer
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] Camera2Enumerator failed: ${e.localizedMessage}")
        }

        // 2. Fallback to Camera1Enumerator
        try {
            val enumerator = Camera1Enumerator(false)
            val capturer = findCapturer(enumerator, preferFront, cameraEventsHandler)
            if (capturer != null) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Created Camera1Capturer fallback successfully (preferFront=$preferFront)")
                return capturer
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] Camera1Enumerator failed: ${e.localizedMessage}")
        }

        FirebaseCrashlytics.getInstance().log("[WebRTC] ERROR: No camera capturer could be created!")
        return null
    }

    private fun findCapturer(
        enumerator: CameraEnumerator,
        preferFront: Boolean,
        handler: CameraVideoCapturer.CameraEventsHandler
    ): VideoCapturer? {
        val deviceNames = enumerator.deviceNames ?: return null
        // First pass: try preferred facing
        for (deviceName in deviceNames) {
            val isFront = enumerator.isFrontFacing(deviceName)
            if (isFront == preferFront) {
                try {
                    val capturer = enumerator.createCapturer(deviceName, handler)
                    if (capturer != null) return capturer
                } catch (e: Throwable) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC] createCapturer ($deviceName) failed: ${e.localizedMessage}")
                }
            }
        }
        // Second pass: try alternative facing
        for (deviceName in deviceNames) {
            val isFront = enumerator.isFrontFacing(deviceName)
            if (isFront != preferFront) {
                try {
                    val capturer = enumerator.createCapturer(deviceName, handler)
                    if (capturer != null) return capturer
                } catch (e: Throwable) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC] createCapturer alternate ($deviceName) failed: ${e.localizedMessage}")
                }
            }
        }

        // Third pass: fallback to any first available camera device
        for (deviceName in deviceNames) {
            try {
                val capturer = enumerator.createCapturer(deviceName, handler)
                if (capturer != null) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Created capturer for first available device ($deviceName)")
                    return capturer
                }
            } catch (_: Throwable) {}
        }
        return null
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
                    FirebaseCrashlytics.getInstance().log("[WebRtcManager] PeerConnectionFactory statically initialized")
                } catch (t: Throwable) {
                    FirebaseCrashlytics.getInstance().recordException(t)
                }
            }
        }

        fun getDefaultIceServers(): List<PeerConnection.IceServer> {
            return listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun.services.mozilla.com").createIceServer(),
                PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer(),
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer(),
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer(),
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer()
            )
        }

        fun preferOpusHighQuality(sdpDescription: String): String {
            val opusParams = "a=fmtp:111 minptime=10;ptime=10;useinbandfec=1;stereo=0;maxaveragebitrate=64000;cbr=1"
            return if (sdpDescription.contains("a=fmtp:111")) {
                sdpDescription.replace(
                    Regex("a=fmtp:111 .*"),
                    opusParams
                )
            } else if (sdpDescription.contains("a=rtpmap:111 opus/48000")) {
                sdpDescription.replace(
                    "a=rtpmap:111 opus/48000",
                    "a=rtpmap:111 opus/48000\r\n$opusParams"
                )
            } else {
                sdpDescription
            }
        }

        fun optimizeOpusSdp(sdpDescription: String): String = preferOpusHighQuality(sdpDescription)

        /**
         * CRITICAL FIX: Strip all video codecs except VP8 from SDP.
         *
         * Root cause of SIGABRT on Samsung A21s (Exynos 850):
         * - Child phone (A54) offers H264 as preferred codec.
         * - Parent phone (A21s) uses SoftwareVideoDecoderFactory (VP8/VP9 only).
         * - SoftwareVideoDecoderFactory.createDecoder(H264) returns null.
         * - WebRTC C++ layer: RTC_CHECK(decoder != nullptr) → SIGABRT via libc.so abort().
         * - This crash CANNOT be caught in Kotlin — it is a native abort.
         *
         * Fix: Remove H264 (100/101), AV1 (35/36), VP9 (98/99), H265 (127/103) from SDP m=video
         * section so only VP8 (96) + RTX (97) + RED (104) + ULPFEC (106) remain.
         * Both Child offer and Parent answer are filtered before being sent via Firebase.
         */
        fun restrictVideoToVP8Only(sdpDescription: String): String {
            val lines = sdpDescription.split("\r\n")
            val result = mutableListOf<String>()
            var inVideoSection = false
            var videoMLine: String? = null

            // First pass: Dynamically discover payload types for VP8 and redundancy (RED, ULPFEC)
            val vp8Payloads = mutableSetOf<String>()
            var inVid = false
            for (line in lines) {
                if (line.startsWith("m=video")) inVid = true
                else if (line.startsWith("m=")) inVid = false
                if (inVid) {
                    val match = Regex("^a=rtpmap:(\\d+)\\s+(VP8|red|ulpfec)/", RegexOption.IGNORE_CASE).find(line)
                    if (match != null) {
                        vp8Payloads.add(match.groupValues[1])
                    }
                }
            }
            // Second pass: Find RTX payload types associated with VP8
            val rtxPayloads = mutableSetOf<String>()
            inVid = false
            for (line in lines) {
                if (line.startsWith("m=video")) inVid = true
                else if (line.startsWith("m=")) inVid = false
                if (inVid) {
                    val match = Regex("^a=fmtp:(\\d+)\\s+apt=(\\d+)", RegexOption.IGNORE_CASE).find(line)
                    if (match != null) {
                        val pt = match.groupValues[1]
                        val apt = match.groupValues[2]
                        if (apt in vp8Payloads) {
                            rtxPayloads.add(pt)
                        }
                    }
                }
            }

            // Payload IDs to KEEP (dynamically discovered, with safe fallback)
            val keepPayloads = if (vp8Payloads.isNotEmpty()) {
                vp8Payloads + rtxPayloads
            } else {
                setOf("96", "97", "104", "106")
            }

            for (line in lines) {
                when {
                    line.startsWith("m=video") -> {
                        inVideoSection = true
                        videoMLine = line
                        // Will be rewritten after scanning – defer
                        result.add("__VIDEO_MLINE__")
                    }
                    line.startsWith("m=") && !line.startsWith("m=video") -> {
                        inVideoSection = false
                        result.add(line)
                    }
                    inVideoSection -> {
                        // Drop rtpmap/fmtp/rtcp-fb lines for non-VP8 payloads
                        val payloadMatch = Regex("^a=(rtpmap|fmtp|rtcp-fb):(\\d+)").find(line)
                        if (payloadMatch != null) {
                            val payload = payloadMatch.groupValues[2]
                            if (payload in keepPayloads) result.add(line)
                            // else: silently drop H264/AV1/VP9/H265 attribute lines
                        } else {
                            result.add(line)
                        }
                    }
                    else -> result.add(line)
                }
            }

            // Rewrite m=video line: keep only allowed payload IDs in the format list
            if (videoMLine != null) {
                val parts = videoMLine.split(" ")
                // parts[0]=m=video, parts[1]=port, parts[2]=proto, parts[3..]=payload IDs
                val filteredPayloads = parts.drop(3).filter { it in keepPayloads }
                val newMLine = (parts.take(3) + filteredPayloads).joinToString(" ")
                val idx = result.indexOf("__VIDEO_MLINE__")
                if (idx >= 0) result[idx] = newMLine
            }

            return result.joinToString("\r\n")
        }

        fun calculateSafeAudioGain(sensitivityPercent: Float): Double {
            val clamped = sensitivityPercent.coerceIn(0f, 100f)
            return 0.5 + (clamped / 100.0) * 1.5
        }

        /**
         * Ultra-high sensitivity boost curve for distant whisper monitoring.
         * Scales smoothly up to 3.5x digital gain without clipping.
         */
        fun calculateSuperBoostGain(sensitivityPercent: Float): Double {
            val clamped = sensitivityPercent.coerceIn(0f, 100f)
            return if (clamped <= 50f) {
                0.5 + (clamped / 50.0) * 1.5 // 0.5x -> 2.0x
            } else {
                2.0 + ((clamped - 50.0) / 50.0) * 1.5 // 2.0x -> 3.5x
            }
        }
    }

    fun switchCamera(onSwitched: ((Boolean) -> Unit)? = null) {
        if (isStopped) return
        val capturer = videoCapturer as? CameraVideoCapturer
        if (capturer == null || !isCameraRunning) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] switchCamera ignored: camera is not running")
            return
        }
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Camera switched successfully. Front: $isFrontCamera")
                onSwitched?.invoke(isFrontCamera)
            }
            override fun onCameraSwitchError(errorDescription: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Camera switch notice: $errorDescription")
            }
        })
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
                if (isStopped || peerConnection == null) return
                try {
                    peerConnection?.getStats { report ->
                        for (stats in report.statsMap.values) {
                            if (stats.type == "inbound-rtp") {
                                val kind = stats.members["kind"] ?: stats.members["mediaType"]
                                if (kind == "audio" || stats.id.contains("audio", ignoreCase = true)) {
                                    val levelObj = stats.members["audioLevel"]
                                    if (levelObj != null) {
                                        val rawLevel = when (levelObj) {
                                            is Number -> levelObj.toFloat()
                                            else -> levelObj.toString().toFloatOrNull() ?: 0f
                                        }
                                        currentAudioLevel = rawLevel.coerceIn(0f, 1f)
                                        onLevel(currentAudioLevel)
                                        return@getStats
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}
                audioLevelHandler?.postDelayed(this, 100L)
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

    /**
     * Boosts remote audio signal using DSP LoudnessEnhancer for whispered/distant sound.
     * @param audioSessionId The audio session to apply the effect to.
     * @param gainMilliBel Target gain in mB (e.g., 1200 mB = +12dB boost).
     */
    fun enableLoudnessBooster(audioSessionId: Int, gainMilliBel: Int = 1200) {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(gainMilliBel.coerceIn(0, 3000))
                enabled = true
            }
            FirebaseCrashlytics.getInstance().log("[WebRTC] LoudnessEnhancer enabled with gain: ${gainMilliBel}mB for session: $audioSessionId")
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] LoudnessEnhancer init failed: ${t.localizedMessage}")
        }
    }

    fun setLoudnessGain(gainMilliBel: Int) {
        try {
            loudnessEnhancer?.setTargetGain(gainMilliBel.coerceIn(0, 3000))
        } catch (_: Throwable) {}
    }

    private fun enableHardwareAudioEffects() {
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val sessionId = getAudioSessionIdFromAdm() ?: audioManager?.generateAudioSessionId()
            if (sessionId != null && sessionId != 0) {
                if (AutomaticGainControl.isAvailable()) {
                    try {
                        hardwareAgc = AutomaticGainControl.create(sessionId)?.apply {
                            enabled = true
                        }
                        FirebaseCrashlytics.getInstance().log("[WebRTC] Hardware AutomaticGainControl enabled (sessionId=$sessionId)")
                    } catch (e: Throwable) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC] Hardware AGC error: ${e.localizedMessage}")
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    try {
                        hardwareNs = NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                        }
                        FirebaseCrashlytics.getInstance().log("[WebRTC] Hardware NoiseSuppressor enabled (sessionId=$sessionId)")
                    } catch (e: Throwable) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC] Hardware NS error: ${e.localizedMessage}")
                    }
                }
            }
            try {
                audioDeviceModule?.setNoiseSuppressorEnabled(true)
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] enableHardwareAudioEffects notice: ${e.localizedMessage}")
        }
    }

    private fun getAudioSessionIdFromAdm(): Int? {
        return try {
            val audioInputField = audioDeviceModule?.javaClass?.getDeclaredField("audioInput")
            audioInputField?.isAccessible = true
            val audioInput = audioInputField?.get(audioDeviceModule)
            val audioRecordField = audioInput?.javaClass?.getDeclaredField("audioRecord")
            audioRecordField?.isAccessible = true
            val audioRecord = audioRecordField?.get(audioInput) as? android.media.AudioRecord
            audioRecord?.audioSessionId
        } catch (_: Throwable) {
            null
        }
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

        // Offload native C++ cleanup to background executor to prevent UI thread blocking
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try { hardwareAgc?.release() } catch (_: Throwable) {}
            try { hardwareNs?.release() } catch (_: Throwable) {}
            try { loudnessEnhancer?.release() } catch (_: Throwable) {}

            try { oldCapturer?.stopCapture() } catch (_: Throwable) {}
            try { oldCapturer?.dispose() } catch (_: Throwable) {}
            try { oldSurfaceHelper?.dispose() } catch (_: Throwable) {}

            try { oldVideoTrack?.setEnabled(false); oldVideoTrack?.dispose() } catch (_: Throwable) {}
            try { oldVideoSource?.dispose() } catch (_: Throwable) {}
            try { oldAudioTrack?.setEnabled(false); oldAudioTrack?.dispose() } catch (_: Throwable) {}
            try { oldAudioSource?.dispose() } catch (_: Throwable) {}

            try { oldPeerConnection?.dispose() } catch (_: Throwable) {}
            try { oldAdm?.release() } catch (_: Throwable) {}
            try { oldFactory?.dispose() } catch (_: Throwable) {}
            try { eglBase.release() } catch (_: Throwable) {}
        }

        synchronized(pendingCandidates) {
            pendingCandidates.clear()
            isRemoteDescriptionSet = false
        }

        FirebaseCrashlytics.getInstance().log("[WebRTC] stopStream() scheduled on background thread")
    }
}
