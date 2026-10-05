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
import java.util.concurrent.TimeUnit

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

            // VP8-only encoder for compatibility
            val baseEncoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            val encoderFactory = object : VideoEncoderFactory {
                override fun createEncoder(info: VideoCodecInfo?): VideoEncoder? {
                    return if (info?.name?.uppercase() == "VP8") {
                        baseEncoderFactory.createEncoder(info)
                    } else {
                        null
                    }
                }
                override fun getSupportedCodecs(): Array<VideoCodecInfo> =
                    baseEncoderFactory.supportedCodecs
                        .filter { it.name.equals("VP8", ignoreCase = true) }
                        .toTypedArray()
            }

            // Safe decoder fallback chain
            val decoderFactory = object : VideoDecoderFactory {
                private val hardwareFactory = try {
                    DefaultVideoDecoderFactory(eglBase.eglBaseContext)
                } catch (_: Throwable) {
                    null
                }
                private val softwareFactory = try {
                    SoftwareVideoDecoderFactory()
                } catch (t: Throwable) {
                    FirebaseCrashlytics.getInstance().recordException(t)
                    null
                }

                override fun createDecoder(info: VideoCodecInfo?): VideoDecoder? {
                    return try {
                        hardwareFactory?.createDecoder(info) ?: softwareFactory?.createDecoder(info)
                    } catch (t: Throwable) {
                        FirebaseCrashlytics.getInstance().recordException(t)
                        softwareFactory?.createDecoder(info)
                    }
                }

                override fun getSupportedCodecs(): Array<VideoCodecInfo> {
                    val list = mutableListOf<VideoCodecInfo>()
                    try {
                        hardwareFactory?.supportedCodecs?.let { list.addAll(it) }
                    } catch (_: Throwable) {
                    }
                    try {
                        softwareFactory?.supportedCodecs?.let { list.addAll(it) }
                    } catch (_: Throwable) {
                    }
                    return list.distinctBy { it.name }.toTypedArray()
                }
            }

            val adm = if (isReceiverOnly) {
                JavaAudioDeviceModule.builder(appContext)
                    .setUseHardwareAcousticEchoCanceler(false)
                    .setUseHardwareNoiseSuppressor(false)
                    .createAudioDeviceModule()
            } else {
                val micSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    MediaRecorder.AudioSource.UNPROCESSED
                } else {
                    MediaRecorder.AudioSource.MIC
                }
                JavaAudioDeviceModule.builder(appContext)
                    .setUseHardwareAcousticEchoCanceler(false)
                    .setUseHardwareNoiseSuppressor(false)
                    .setAudioSource(micSource)
                    .setAudioFormat(AudioFormat.ENCODING_PCM_16BIT)
                    .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                        override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                            AppHealthTelemetry.logDiagnostic(
                                appContext,
                                "LIVE_AUDIO",
                                "FAILED",
                                "AudioRecord Init: $errorMessage",
                                errorMessage
                            )
                            FirebaseCrashlytics.getInstance()
                                .log("[WebRTC AudioRecord Init] $errorMessage")
                        }

                        override fun onWebRtcAudioRecordStartError(
                            errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                            errorMessage: String?
                        ) {
                            AppHealthTelemetry.logDiagnostic(
                                appContext,
                                "LIVE_AUDIO",
                                "FAILED",
                                "AudioRecord Start Error: $errorMessage",
                                errorMessage
                            )
                            FirebaseCrashlytics.getInstance()
                                .log("[WebRTC AudioRecord Start] $errorCode: $errorMessage")
                        }

                        override fun onWebRtcAudioRecordError(errorMessage: String?) {
                            AppHealthTelemetry.logDiagnostic(
                                appContext,
                                "LIVE_AUDIO",
                                "FAILED",
                                "AudioRecord: $errorMessage",
                                errorMessage
                            )
                            FirebaseCrashlytics.getInstance()
                                .log("[WebRTC AudioRecord] $errorMessage")
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
            FirebaseCrashlytics.getInstance().log("[WebRtcManager Init] ${e.localizedMessage}")
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    fun startStream(
        streamType: String,
        iceServers: List<PeerConnection.IceServer>,
        onIceCandidate: (IceCandidate) -> Unit,
        onSdpCreated: (SessionDescription) -> Unit,
        onRemoteTrackAdded: (MediaStreamTrack) -> Unit = {},
        mediaProjectionData: android.content.Intent? = null
    ) {
        if (isStopped) {
            FirebaseCrashlytics.getInstance().log("[WebRTC Stream] Attempted start on stopped instance")
            return
        }

        val perfTrace = FirebasePerformance.getInstance().newTrace("webrtc_stream_$streamType")
        perfTrace.start()

        try {
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                enableDscp = true
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                iceCandidatePoolSize = 10
            }

            peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            AppHealthTelemetry.logDiagnostic(
                                appContext,
                                "WEBRTC_CONNECTION",
                                "SUCCESS",
                                "WebRTC connected for $streamType"
                            )
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            AppHealthTelemetry.logDiagnostic(
                                appContext,
                                "WEBRTC_CONNECTION",
                                "FAILED",
                                "ICE connection failed"
                            )
                        }
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
                    if (!isStopped) receiver?.track()?.let { onRemoteTrackAdded(it) }
                }
            })

            // Audio Track
            setupAudioTrack()

            // Video Track (Camera or Screen)
            when {
                streamType.equals("video", ignoreCase = true) || streamType.equals(
                    "camera_video",
                    ignoreCase = true
                ) -> setupCameraVideo()
                streamType.equals("screen", ignoreCase = true) || streamType.equals(
                    "screen_mirror",
                    ignoreCase = true
                ) -> setupScreenCapture(mediaProjectionData)
            }

            // Create Offer
            val mediaConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            }

            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null && !isStopped) {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(sdp: SessionDescription?) {}
                            override fun onSetSuccess() {
                                onSdpCreated(sdp)
                                perfTrace.stop()
                            }
                            override fun onCreateFailure(error: String?) {
                                FirebaseCrashlytics.getInstance()
                                    .log("[WebRTC SetLocalDesc] $error")
                            }
                            override fun onSetFailure(error: String?) {
                                FirebaseCrashlytics.getInstance()
                                    .log("[WebRTC SetLocalDesc Failure] $error")
                            }
                        }, sdp)
                    }
                }
                override fun onCreateFailure(error: String?) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC CreateOffer] $error")
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            }, mediaConstraints)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            perfTrace.stop()
        }
    }

    private fun setupAudioTrack() {
        try {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            }
            audioSource = factory?.createAudioSource(audioConstraints)
            audioTrack = factory?.createAudioTrack("audio0", audioSource)
            audioTrack?.setEnabled(true)
            peerConnection?.addTrack(audioTrack, listOf("stream0"))
            AppHealthTelemetry.logDiagnostic(appContext, "LIVE_AUDIO", "READY", "Audio track added")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            AppHealthTelemetry.logDiagnostic(
                appContext,
                "LIVE_AUDIO",
                "FAILED",
                "Audio setup: ${e.localizedMessage}"
            )
        }
    }

    private fun setupCameraVideo() {
        try {
            var capturer: VideoCapturer? = null
            for (attempt in 1..3) {
                capturer = createVideoCapturer(preferFront = true)
                    ?: createVideoCapturer(preferFront = false)
                if (capturer != null) break
            }

            if (capturer != null) {
                surfaceTextureHelper =
                    SurfaceTextureHelper.create("CameraThread", eglBase.eglBaseContext)
                videoSource = factory?.createVideoSource(capturer.isScreencast)
                videoCapturer = capturer

                capturer.initialize(
                    surfaceTextureHelper,
                    appContext,
                    videoSource?.capturerObserver
                )
                capturer.startCapture(1280, 720, 30)
                isCameraRunning = true

                videoTrack = factory?.createVideoTrack("video0", videoSource)
                videoTrack?.setEnabled(true)
                peerConnection?.addTrack(videoTrack, listOf("stream0"))
                AppHealthTelemetry.logDiagnostic(
                    appContext,
                    "LIVE_VIDEO",
                    "SUCCESS",
                    "Camera video track active"
                )
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            AppHealthTelemetry.logDiagnostic(
                appContext,
                "LIVE_VIDEO",
                "FAILED",
                "Camera setup: ${e.localizedMessage}"
            )
        }
    }

    private fun setupScreenCapture(mediaProjectionData: android.content.Intent?) {
        try {
            if (mediaProjectionData == null) {
                AppHealthTelemetry.logDiagnostic(
                    appContext,
                    "LIVE_SCREEN",
                    "FAILED",
                    "No MediaProjection data"
                )
                return
            }

            val screenCapturer = ScreenCapturerAndroid(
                mediaProjectionData,
                object : android.media.projection.MediaProjection.Callback() {
                    override fun onStop() {
                        isCameraRunning = false
                    }
                }
            )

            surfaceTextureHelper =
                SurfaceTextureHelper.create("ScreenThread", eglBase.eglBaseContext)
            videoSource = factory?.createVideoSource(true)
            videoCapturer = screenCapturer

            screenCapturer.initialize(
                surfaceTextureHelper,
                appContext,
                videoSource?.capturerObserver
            )
            val dm = appContext.resources.displayMetrics
            val width = (dm.widthPixels).coerceAtLeast(640)
            val height = (dm.heightPixels).coerceAtLeast(480)
            screenCapturer.startCapture(width, height, 24)
            isCameraRunning = true

            videoTrack = factory?.createVideoTrack("screen0", videoSource)
            videoTrack?.setEnabled(true)
            peerConnection?.addTrack(videoTrack, listOf("stream0"))
            AppHealthTelemetry.logDiagnostic(
                appContext,
                "LIVE_SCREEN",
                "SUCCESS",
                "Screen capture active (${width}x${height})"
            )
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            AppHealthTelemetry.logDiagnostic(
                appContext,
                "LIVE_SCREEN",
                "FAILED",
                "Screen setup: ${e.localizedMessage}"
            )
        }
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        if (isStopped) return
        try {
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onSetSuccess() {
                    isRemoteDescriptionSet = true
                    pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
                    pendingCandidates.clear()
                }
                override fun onCreateFailure(error: String?) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC RemoteDesc] $error")
                }
                override fun onSetFailure(error: String?) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC RemoteDesc Failure] $error")
                }
            }, sdp)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    fun addIceCandidate(candidate: IceCandidate) {
        if (isStopped) return
        if (isRemoteDescriptionSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            synchronized(pendingCandidates) {
                pendingCandidates.add(candidate)
            }
        }
    }

    fun startReceiver(
        iceServers: List<PeerConnection.IceServer>,
        onIceCandidate: (IceCandidate) -> Unit,
        onRemoteTrackAdded: (MediaStreamTrack) -> Unit
    ) {
        if (isStopped) return

        try {
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                enableDscp = true
                continualGatheringPolicy =
                    PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                iceCandidatePoolSize = 10
            }

            peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
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
                    receiver?.track()?.let { onRemoteTrackAdded(it) }
                }
            })
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    fun stopStream() {
        isStopped = true
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        try { videoCapturer?.dispose() } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e) }
        try { audioTrack?.setEnabled(false) } catch (_: Exception) {}
        try { videoTrack?.setEnabled(false) } catch (_: Exception) {}
        try { peerConnection?.close() } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e) }
        try { audioSource?.dispose() } catch (_: Exception) {}
        try { audioTrack?.dispose() } catch (_: Exception) {}
        try { videoSource?.dispose() } catch (_: Exception) {}
        try { videoTrack?.dispose() } catch (_: Exception) {}
        try { surfaceTextureHelper?.dispose() } catch (_: Exception) {}
        try { audioDeviceModule?.release() } catch (_: Exception) {}
        try { hardwareAgc?.release() } catch (_: Exception) {}
        try { hardwareNs?.release() } catch (_: Exception) {}
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        peerConnection = null
        audioSource = null
        audioTrack = null
        videoSource = null
        videoTrack = null
        videoCapturer = null
        surfaceTextureHelper = null
        audioDeviceModule = null
    }

    fun releaseEglBase() {
        try {
            eglBase.release()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun createVideoCapturer(preferFront: Boolean): VideoCapturer? {
        return try {
            val enumerator = Camera2Enumerator(appContext)
            val deviceNames = enumerator.deviceNames
            for (deviceName in deviceNames) {
                val isFront = enumerator.isFrontFacing(deviceName)
                if (isFront == preferFront) {
                    return enumerator.createCapturer(deviceName, null)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun setRemoteAnswer(sdp: String) {
        setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun addRemoteCandidate(sdpMid: String?, sdpMLineIndex: Int, sdp: String) {
        addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
    }

    fun switchCamera() {
        try {
            (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    fun startAudioLevelMonitoring(onLevel: (Float) -> Unit) {
        // WebRTC's native AudioTrack does not expose a portable level meter.
        // Keep the callback API for UI compatibility; report silence until a
        // dedicated audio meter is introduced.
        onLevel(0f)
    }

    fun setRemoteOfferAndCreateAnswer(sdp: String, onAnswer: (SessionDescription) -> Unit) {
        if (isStopped) return
        val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
        try {
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                override fun onSetSuccess() {
                    isRemoteDescriptionSet = true
                    pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
                    pendingCandidates.clear()
                    peerConnection?.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(answer: SessionDescription?) {
                            if (answer == null || isStopped) return
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                                override fun onSetSuccess() { onAnswer(answer) }
                                override fun onCreateFailure(error: String?) { }
                                override fun onSetFailure(error: String?) { }
                            }, answer)
                        }
                        override fun onSetSuccess() = Unit
                        override fun onCreateFailure(error: String?) { FirebaseCrashlytics.getInstance().log("[WebRTC CreateAnswer] $error") }
                        override fun onSetFailure(error: String?) { }
                    }, MediaConstraints())
                }
                override fun onCreateFailure(error: String?) { }
                override fun onSetFailure(error: String?) { FirebaseCrashlytics.getInstance().log("[WebRTC RemoteOffer] $error") }
            }, offer)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    companion object {
        fun getDefaultIceServers(): List<PeerConnection.IceServer> = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        fun calculateSuperBoostGain(sensitivityPercent: Float): Double {
            val normalized = (sensitivityPercent / 100.0).coerceIn(0.0, 1.0)
            return 0.5 + (normalized * 3.0)
        }

        fun optimizeOpusSdp(sdp: String): String {
            if (sdp.isBlank()) return sdp
            val lines = sdp.lines().toMutableList()
            var opusFmtpIndex = -1
            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("a=fmtp:111")) {
                    opusFmtpIndex = i
                    if (!line.contains("maxaveragebitrate=", ignoreCase = true)) {
                        lines[i] = "$line;maxaveragebitrate=64000"
                    }
                    break
                }
            }
            val opusParameters = linkedMapOf(
                "minptime" to "10",
                "useinbandfec" to "1",
                "maxaveragebitrate" to "64000",
                "sprop-maxcapturerate" to "48000",
                "usedtx" to "1",
                "stereo" to "0"
            )
            if (opusFmtpIndex >= 0) {
                val prefix = "a=fmtp:111"
                val existing = lines[opusFmtpIndex]
                    .removePrefix(prefix).trim().removePrefix(";")
                    .split(";").filter { it.isNotBlank() }
                    .mapNotNull { parameter ->
                        val key = parameter.substringBefore("=").trim().lowercase()
                        if (key in opusParameters) key to parameter.substringAfter("=", "") else null
                    }.toMap()
                val merged = opusParameters.map { (key, value) ->
                    key + "=" + (existing[key] ?: value)
                }
                lines[opusFmtpIndex] = prefix + " " + merged.joinToString(";")
            } else {
                val opusPayload = lines.indexOfFirst {
                    it.contains("a=rtpmap:111", ignoreCase = true) &&
                        it.contains("opus/48000", ignoreCase = true)
                }
                if (opusPayload >= 0) {
                    val params = opusParameters.entries.joinToString(";") { it.key + "=" + it.value }
                    lines.add(opusPayload + 1, "a=fmtp:111 " + params)
                }
            }
            return lines.joinToString("\n")
        }

        /**
         * Safe UI gain mapping used by tests and callers.
         * Clamps the requested sensitivity to 0..100% and maps it to 0.5x..2.0x.
         */
        fun calculateSafeAudioGain(sensitivityPercent: Float): Double {
            val normalized = (sensitivityPercent / 100.0).coerceIn(0.0, 1.0)
            return 0.5 + (normalized * 1.5)
        }

        @Volatile private var pcfInitialized = false

        @Synchronized
        private fun initializePcfIfNeeded(context: Context) {
            if (!pcfInitialized) {
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .createInitializationOptions()
                    .let { PeerConnectionFactory.initialize(it) }
                pcfInitialized = true
            }
        }
    }
}
