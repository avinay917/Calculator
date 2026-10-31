package com.example.authapp.webrtc

import android.content.Context
import android.content.Intent
import android.media.audiofx.AutomaticGainControl
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import org.webrtc.*

data class BroadcasterStreamResult(
    val peerConnection: PeerConnection?,
    val audioSource: AudioSource?,
    val audioTrack: AudioTrack?,
    val videoCapturer: VideoCapturer?,
    val surfaceTextureHelper: SurfaceTextureHelper?,
    val videoSource: VideoSource?,
    val videoTrack: VideoTrack?,
    val hardwareAgc: AutomaticGainControl?,
    val isCameraRunning: Boolean
)

object WebRtcBroadcasterSession {

    fun startStream(
        appContext: Context,
        eglBaseContext: EglBase.Context,
        factory: PeerConnectionFactory?,
        audioDeviceModule: org.webrtc.audio.JavaAudioDeviceModule?,
        streamType: String,
        iceServers: List<PeerConnection.IceServer>,
        onIceCandidate: (IceCandidate) -> Unit,
        onSdpCreated: (SessionDescription) -> Unit,
        onRemoteTrackAdded: (MediaStreamTrack) -> Unit = {},
        mediaProjectionData: Intent? = null,
        preferFront: Boolean = false,
        isStoppedChecker: () -> Boolean
    ): BroadcasterStreamResult? {
        if (isStoppedChecker() || factory == null) return null
        val perfTrace = FirebasePerformance.getInstance().newTrace("webrtc_stream_init_$streamType")
        perfTrace.start()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            enableDscp = true
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
        }

        var pc: PeerConnection? = null
        pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Broadcaster] ICE Connection State: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (!isStoppedChecker()) candidate?.let { onIceCandidate(it) }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                if (!isStoppedChecker()) receiver?.track()?.let { onRemoteTrackAdded(it) }
            }
        })

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
        }
        val audioSource = factory.createAudioSource(audioConstraints)
        val audioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        audioTrack.setEnabled(true)
        pc?.addTransceiver(audioTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
        val hardwareAgc = WebRtcAudioHelper.enableHardwareAudioEffects(appContext, audioDeviceModule).first

        val isScreen = streamType.equals("screen", ignoreCase = true) || streamType.equals("screen_mirror", ignoreCase = true)
        val isCamera = streamType.equals("video", ignoreCase = true) || streamType.equals("camera_video", ignoreCase = true)

        var videoCapturer: VideoCapturer? = null
        var surfaceTextureHelper: SurfaceTextureHelper? = null
        var videoSource: VideoSource? = null
        var videoTrack: VideoTrack? = null
        var isCameraRunning = false

        if (isScreen && mediaProjectionData != null) {
            val res = WebRtcScreenCapturerHelper.startScreenCapture(appContext, eglBaseContext, factory, mediaProjectionData)
            if (res != null) {
                videoCapturer = res.capturer
                surfaceTextureHelper = res.surfaceTextureHelper
                videoSource = res.videoSource
                isCameraRunning = res.isRunning
                videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)
                videoTrack.setEnabled(true)
                pc?.addTransceiver(videoTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
            }
        } else if (isCamera) {
            val cameraEventsHandler = object : CameraVideoCapturer.CameraEventsHandler {
                override fun onCameraError(errorDescription: String?) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Camera error/hardware conflict: $errorDescription")
                }
                override fun onCameraDisconnected() {
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Camera disconnected by system/other app, releasing hardware")
                }
                override fun onCameraFreezed(errorDescription: String?) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Camera freezed: $errorDescription")
                }
                override fun onCameraOpening(cameraName: String?) {}
                override fun onFirstFrameAvailable() {}
                override fun onCameraClosed() {
                    FirebaseCrashlytics.getInstance().log("[WebRTC] Camera hardware closed gracefully")
                }
            }
            val res = WebRtcBroadcasterVideoHelper.startCameraCapture(appContext, eglBaseContext, factory, cameraEventsHandler, preferFront = preferFront)
            if (res != null) {
                videoCapturer = res.capturer
                surfaceTextureHelper = res.surfaceTextureHelper
                videoSource = res.videoSource
                isCameraRunning = res.isRunning
                videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)
                videoTrack.setEnabled(true)
                val tx = pc?.addTransceiver(videoTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
                WebRtcBroadcasterVideoHelper.configureAdaptiveBitrate(tx)
            }
        }

        pc?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (isStoppedChecker()) return
                desc?.let { originalOffer ->
                    val opusFiltered = WebRtcSdpHelper.preferOpusHighQuality(originalOffer.description)
                    val vp8Filtered = WebRtcSdpHelper.restrictVideoToVP8Only(opusFiltered)
                    val bandwidthLimited = WebRtcSdpHelper.applyBandwidthLimits(vp8Filtered, 550, 32)
                    val optimizedOffer = SessionDescription(originalOffer.type, bandwidthLimited)
                    pc?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            if (!isStoppedChecker()) {
                                perfTrace.stop()
                                onSdpCreated(optimizedOffer)
                            }
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, optimizedOffer)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())

        return BroadcasterStreamResult(
            peerConnection = pc,
            audioSource = audioSource,
            audioTrack = audioTrack,
            videoCapturer = videoCapturer,
            surfaceTextureHelper = surfaceTextureHelper,
            videoSource = videoSource,
            videoTrack = videoTrack,
            hardwareAgc = hardwareAgc,
            isCameraRunning = isCameraRunning
        )
    }

    fun setRemoteAnswer(
        peerConnection: PeerConnection?,
        sdpString: String,
        drainPendingCandidates: () -> Unit,
        isStoppedChecker: () -> Boolean,
        onSetSuccess: (() -> Unit)? = null
    ) {
        if (isStoppedChecker() || peerConnection == null) return
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                drainPendingCandidates()
                if (!isStoppedChecker()) onSetSuccess?.invoke()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }
}
