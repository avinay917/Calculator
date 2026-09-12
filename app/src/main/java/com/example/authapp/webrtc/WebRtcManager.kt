package com.example.authapp.webrtc

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcManager(private val context: Context) {
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

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        val useAec = JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()
        val useNs = JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()

        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(useAec)
            .setUseHardwareNoiseSuppressor(useNs)
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC AudioRecord Init Error] $errorMessage")
                }
                override fun onWebRtcAudioRecordStartError(errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?, errorMessage: String?) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC AudioRecord Start Error] $errorCode: $errorMessage")
                }
                override fun onWebRtcAudioRecordError(errorMessage: String?) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC AudioRecord Error] $errorMessage")
                }
            })
            .createAudioDeviceModule()
        audioDeviceModule = adm

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
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
                    FirebaseCrashlytics.getInstance().recordException(Exception("WebRTC ICE Connection Failed"))
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

        // Audio Track
        audioSource = factory?.createAudioSource(MediaConstraints())
        audioTrack = factory?.createAudioTrack("ARDAMSa0", audioSource)
        audioTrack?.setEnabled(true)
        peerConnection?.addTrack(audioTrack, listOf("ARDAMS"))

        // Video Track (if requested)
        if (streamType.equals("video", ignoreCase = true)) {
            val capturer = createVideoCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                videoSource = factory?.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                try {
                    capturer.startCapture(640, 480, 30)
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Camera capture started: 640x480@30")
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Camera 640x480 failed, trying 480x360: ${e.localizedMessage}")
                    try {
                        capturer.startCapture(480, 360, 24)
                    } catch (e2: Exception) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC] Camera startCapture failed: ${e2.localizedMessage}")
                        FirebaseCrashlytics.getInstance().recordException(e2)
                    }
                }

                videoTrack = factory?.createVideoTrack("ARDAMSv0", videoSource)
                videoTrack?.setEnabled(true)
                peerConnection?.addTrack(videoTrack, listOf("ARDAMS"))
            }
        }

        // Create SDP Offer (Broadcaster produces streams for the Parent)
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            perfTrace.stop()
                            onSdpCreated(it)
                        }
                        override fun onCreateFailure(p0: String?) {
                            FirebaseCrashlytics.getInstance().log("[WebRTC] Local SDP Create Failure: $p0")
                            FirebaseCrashlytics.getInstance().recordException(Exception("Local SDP Create Failure: $p0"))
                        }
                        override fun onSetFailure(p0: String?) {
                            FirebaseCrashlytics.getInstance().log("[WebRTC] Local SDP Set Failure: $p0")
                            FirebaseCrashlytics.getInstance().recordException(Exception("Local SDP Set Failure: $p0"))
                        }
                    }, it)
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
                    track.setVolume(10.0)
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
                        answerDesc?.let { answer ->
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    onAnswerCreated(answer)
                                }
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(p0: String?) {}
                            }, answer)
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

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        for (deviceName in enumerator.deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
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
    }

    fun switchCamera(onSwitched: ((Boolean) -> Unit)? = null) {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Camera switched successfully. Front: $isFrontCamera")
                onSwitched?.invoke(isFrontCamera)
            }
            override fun onCameraSwitchError(errorDescription: String?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Camera switch error: $errorDescription")
                FirebaseCrashlytics.getInstance().recordException(Exception("Camera switch error: $errorDescription"))
            }
        })
    }

    fun stopStream() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        peerConnection?.close()
        peerConnection = null
        audioDeviceModule?.release()
        audioDeviceModule = null
        factory?.dispose()
        factory = null
        eglBase.release()
        synchronized(pendingCandidates) {
            pendingCandidates.clear()
            isRemoteDescriptionSet = false
        }
    }
}
