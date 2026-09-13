package com.example.authapp.webrtc

import android.content.Context
import com.example.authapp.analytics.AppHealthTelemetry
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcManager(
    private val context: Context,
    private val isReceiverOnly: Boolean = false
) {
    val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null

    private val pendingCandidates = mutableListOf<IceCandidate>()
    @Volatile
    private var isRemoteDescriptionSet = false
    @Volatile
    private var isCameraRunning = false

    init {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

            val adm = if (isReceiverOnly) {
                // Receiver only needs audio output/playback, no recording hardware access
                JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(false)
                    .setUseHardwareNoiseSuppressor(false)
                    .createAudioDeviceModule()
            } else {
                val useNs = JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()
                JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(false)
                    .setUseHardwareNoiseSuppressor(useNs)
                    .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                    .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                        override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                            AppHealthTelemetry.logDiagnostic(context, "LIVE_AUDIO", "FAILED", "AudioRecord Init Error: $errorMessage", errorMessage)
                            FirebaseCrashlytics.getInstance().log("[WebRTC AudioRecord Init Error] $errorMessage")
                        }
                        override fun onWebRtcAudioRecordStartError(errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?, errorMessage: String?) {
                            AppHealthTelemetry.logDiagnostic(context, "LIVE_AUDIO", "FAILED", "AudioRecord Start Error $errorCode: $errorMessage", errorMessage)
                            FirebaseCrashlytics.getInstance().log("[WebRTC AudioRecord Start Error] $errorCode: $errorMessage")
                        }
                        override fun onWebRtcAudioRecordError(errorMessage: String?) {
                            AppHealthTelemetry.logDiagnostic(context, "LIVE_AUDIO", "FAILED", "AudioRecord Error: $errorMessage", errorMessage)
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
        val perfTrace = FirebasePerformance.getInstance().newTrace("webrtc_stream_init_$streamType")
        perfTrace.start()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Broadcaster] ICE Connection State: $state")
                if (state == PeerConnection.IceConnectionState.FAILED) {
                    AppHealthTelemetry.logDiagnostic(context, "WEBRTC_CONNECTION", "FAILED", "ICE connection failed (NAT/Firewall block)")
                    FirebaseCrashlytics.getInstance().recordException(Exception("WebRTC ICE Connection Failed"))
                } else if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    AppHealthTelemetry.logDiagnostic(context, "WEBRTC_CONNECTION", "SUCCESS", "WebRTC peer connection active and streaming")
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { onIceCandidate(it) }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                receiver?.track()?.let { onRemoteTrackAdded(it) }
            }
        })

        // Audio Track with Clean Studio Surveillance & Vibration Filter
        val audioConstraints = MediaConstraints().apply {
            // Standard Automatic Gain Control boosts soft speech cleanly
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            // Noise Suppression eliminates stationary background hum (Fan, AC, humming noise)
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            // High-pass filter cuts sub-80Hz low rumble, table vibrations, and motor hum
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            // Broadcaster does not play receiver audio, so disable echo cancellation
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "false"))
        }
        audioSource = factory?.createAudioSource(audioConstraints)
        audioTrack = factory?.createAudioTrack("ARDAMSa0", audioSource)
        audioTrack?.setEnabled(true)
        peerConnection?.addTrack(audioTrack, listOf("ARDAMS"))

        // Video Track (if requested)
        if (streamType.equals("video", ignoreCase = true)) {
            var capturer = createVideoCapturer(preferFront = true)
            if (capturer != null) {
                videoCapturer = capturer
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                videoSource = factory?.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)

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

                // If preferred camera failed to startCapture, try fallback to back camera!
                if (!started) {
                    try {
                        capturer.dispose()
                    } catch (e: Exception) {}
                    try {
                        surfaceTextureHelper?.dispose()
                    } catch (e: Exception) {}
                    surfaceTextureHelper = null

                    val fallbackCapturer = createVideoCapturer(preferFront = false)
                    if (fallbackCapturer != null) {
                        videoCapturer = fallbackCapturer
                        val newSurfaceHelper = SurfaceTextureHelper.create("CaptureThreadFallback", eglBase.eglBaseContext)
                        surfaceTextureHelper = newSurfaceHelper
                        fallbackCapturer.initialize(newSurfaceHelper, context, videoSource?.capturerObserver)
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
                    AppHealthTelemetry.logDiagnostic(context, "LIVE_VIDEO", "SUCCESS", "Camera capturer active and video track attached")
                    videoTrack = factory?.createVideoTrack("ARDAMSv0", videoSource)
                    videoTrack?.setEnabled(true)
                    peerConnection?.addTrack(videoTrack, listOf("ARDAMS"))
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Video track added to PeerConnection (started=$started)")
                } else {
                    AppHealthTelemetry.logDiagnostic(context, "LIVE_VIDEO", "FAILED", "Camera startCapture failed for all fallback resolutions")
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Camera startCapture failed: video track omitted")
                }
            } else {
                AppHealthTelemetry.logDiagnostic(context, "LIVE_VIDEO", "FAILED", "No camera capturer could be created (Camera hardware busy or CAMERA permission missing)")
                FirebaseCrashlytics.getInstance().log("[WebRTC] ERROR: No camera capturer could be created!")
            }
        }

        // Create SDP Offer (Broadcaster produces streams for the Parent)
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { originalOffer ->
                    val optimizedOffer = SessionDescription(originalOffer.type, optimizeOpusSdp(originalOffer.description))
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            perfTrace.stop()
                            onSdpCreated(optimizedOffer)
                        }
                        override fun onCreateFailure(p0: String?) {
                            FirebaseCrashlytics.getInstance().log("[WebRTC] Local SDP Create Failure: $p0")
                            FirebaseCrashlytics.getInstance().recordException(Exception("Local SDP Create Failure: $p0"))
                        }
                        override fun onSetFailure(p0: String?) {
                            FirebaseCrashlytics.getInstance().log("[WebRTC] Local SDP Set Failure: $p0")
                            FirebaseCrashlytics.getInstance().recordException(Exception("Local SDP Set Failure: $p0"))
                        }
                    }, optimizedOffer)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Offer Create Failure: $error")
                FirebaseCrashlytics.getInstance().recordException(Exception("Offer Create Failure: $error"))
            }
            override fun onSetFailure(error: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Offer Set Failure: $error")
                FirebaseCrashlytics.getInstance().recordException(Exception("Offer Set Failure: $error"))
            }
        }, mediaConstraints)
    }

    fun setRemoteAnswer(sdpString: String, onSetSuccess: (() -> Unit)? = null) {
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Remote Answer set successfully.")
                drainPendingCandidates()
                onSetSuccess?.invoke()
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
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] ICE Connection State: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { onIceCandidate(it) }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Remote VideoTrack received")
                    onRemoteVideoTrack(track)
                } else if (track is AudioTrack) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Remote AudioTrack received")
                    track.setEnabled(true)
                    track.setVolume(1.0)
                    onRemoteAudioTrack(track)
                }
            }
        })

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
        val offerDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                drainPendingCandidates()
                val mediaConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        answerDesc?.let { originalAnswer ->
                            val optimizedAnswer = SessionDescription(originalAnswer.type, optimizeOpusSdp(originalAnswer.description))
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    onAnswerCreated(optimizedAnswer)
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
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        synchronized(pendingCandidates) {
            if (isRemoteDescriptionSet && peerConnection?.remoteDescription != null) {
                val added = peerConnection?.addIceCandidate(candidate)
                FirebaseCrashlytics.getInstance().log("[WebRTC] Added remote ICE candidate: $added")
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
                peerConnection?.addIceCandidate(candidate)
            }
            pendingCandidates.clear()
        }
    }

    private fun createVideoCapturer(preferFront: Boolean = true): VideoCapturer? {
        val cameraEventsHandler = object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(errorDescription: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Camera Error] $errorDescription")
            }
            override fun onCameraDisconnected() {
                FirebaseCrashlytics.getInstance().log("[WebRTC Camera Disconnected]")
            }
            override fun onCameraFreezed(errorDescription: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Camera Freezed] $errorDescription")
            }
            override fun onCameraOpening(cameraName: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Camera Opening] $cameraName")
            }
            override fun onFirstFrameAvailable() {
                FirebaseCrashlytics.getInstance().log("[WebRTC First Frame Available]")
            }
            override fun onCameraClosed() {
                FirebaseCrashlytics.getInstance().log("[WebRTC Camera Closed]")
            }
        }

        // 1. Try Camera2Enumerator first if supported
        try {
            if (Camera2Enumerator.isSupported(context)) {
                val enumerator = Camera2Enumerator(context)
                val capturer = findCapturer(enumerator, preferFront, cameraEventsHandler)
                if (capturer != null) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Created Camera2Capturer successfully (preferFront=$preferFront)")
                    return capturer
                }
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] Camera2Enumerator failed: ${e.localizedMessage}")
        }

        // 2. Fallback to Camera1Enumerator
        try {
            val enumerator = Camera1Enumerator(true)
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
        return null
    }

    companion object {
        fun getDefaultIceServers(): List<PeerConnection.IceServer> {
            return listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun.services.mozilla.com").createIceServer(),
                PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer()
            )
        }

        fun optimizeOpusSdp(sdpDescription: String): String {
            val hasFmtp = sdpDescription.lines().any { it.startsWith("a=fmtp:111") }
            return sdpDescription.lines().joinToString("\r\n") { line ->
                if (line.startsWith("a=fmtp:111") || (line.startsWith("a=fmtp:") && line.contains("opus", ignoreCase = true))) {
                    var modified = line
                    if (!modified.contains("maxaveragebitrate=")) {
                        modified += ";maxaveragebitrate=64000"
                    }
                    if (!modified.contains("sprop-maxcapturerate=")) {
                        modified += ";sprop-maxcapturerate=48000"
                    }
                    if (!modified.contains("usedtx=")) {
                        modified += ";usedtx=1"
                    }
                    if (!modified.contains("useinbandfec=")) {
                        modified += ";useinbandfec=1"
                    }
                    if (!modified.contains("stereo=")) {
                        modified += ";stereo=0"
                    }
                    modified
                } else if (!hasFmtp && line.startsWith("a=rtpmap:111 opus/48000")) {
                    "$line\r\na=fmtp:111 minptime=10;useinbandfec=1;maxaveragebitrate=64000;sprop-maxcapturerate=48000;usedtx=1;stereo=0"
                } else {
                    line
                }
            }
        }

        fun calculateSafeAudioGain(sensitivityPercent: Float): Double {
            val clamped = sensitivityPercent.coerceIn(0f, 100f)
            return 0.5 + (clamped / 100.0) * 1.5
        }
    }

    fun switchCamera(onSwitched: ((Boolean) -> Unit)? = null) {
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

    fun stopStream() {
        isCameraRunning = false
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            videoCapturer?.dispose()
        } catch (_: Exception) {}
        videoCapturer = null
        try {
            surfaceTextureHelper?.dispose()
        } catch (_: Exception) {}
        surfaceTextureHelper = null
        try {
            videoTrack?.setEnabled(false)
            videoTrack?.dispose()
        } catch (_: Exception) {}
        videoTrack = null
        try {
            audioTrack?.setEnabled(false)
            audioTrack?.dispose()
        } catch (_: Exception) {}
        audioTrack = null
        try {
            peerConnection?.close()
        } catch (_: Throwable) {}
        peerConnection = null
        try {
            audioDeviceModule?.release()
        } catch (_: Throwable) {}
        audioDeviceModule = null
        try {
            factory?.dispose()
        } catch (_: Throwable) {}
        factory = null
        try {
            eglBase.release()
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] eglBase release error: ${t.localizedMessage}")
        }
        synchronized(pendingCandidates) {
            pendingCandidates.clear()
            isRemoteDescriptionSet = false
        }
    }
}
