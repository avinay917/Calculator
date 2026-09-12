package com.example.authapp.webrtc

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import org.webrtc.*

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

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

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
                FirebaseCrashlytics.getInstance().log("[WebRTC] ICE Connection State: $state")
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
        peerConnection?.addTrack(audioTrack, listOf("ARDAMS"))

        // Video Track (if requested)
        if (streamType.equals("video", ignoreCase = true)) {
            val capturer = createVideoCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                videoSource = factory?.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                capturer.startCapture(1280, 720, 30)

                videoTrack = factory?.createVideoTrack("ARDAMSv0", videoSource)
                peerConnection?.addTrack(videoTrack, listOf("ARDAMS"))
            }
        }

        // Create SDP Offer
        val mediaConstraints = MediaConstraints()
        if (streamType.equals("video", ignoreCase = true)) {
            mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

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

    fun startAudioStream(
        iceServers: List<PeerConnection.IceServer>,
        onIceCandidate: (IceCandidate) -> Unit,
        onSdpCreated: (SessionDescription) -> Unit
    ) {
        startStream("audio", iceServers, onIceCandidate, onSdpCreated)
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
        surfaceTextureHelper?.dispose()
        peerConnection?.close()
        factory?.dispose()
        eglBase.release()
    }
}
