package com.example.authapp.webrtc

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.*

object WebRtcReceiverSession {

    fun createReceiverPeerConnection(
        appContext: Context,
        factory: PeerConnectionFactory?,
        iceServers: List<PeerConnection.IceServer>,
        onIceCandidate: (IceCandidate) -> Unit,
        onRemoteVideoTrack: (VideoTrack) -> Unit,
        onRemoteAudioTrack: (AudioTrack) -> Unit,
        isStoppedProvider: () -> Boolean
    ): PeerConnection? {
        if (isStoppedProvider()) return null
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            enableDscp = true
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
        }

        val pc = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Signaling State: $state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] ICE Connection State: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (!isStoppedProvider()) candidate?.let { onIceCandidate(it) }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                if (isStoppedProvider()) return
                when (val track = receiver?.track()) {
                    is VideoTrack -> {
                        FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Remote VideoTrack received")
                        track.setEnabled(true)
                        onRemoteVideoTrack(track)
                    }
                    is AudioTrack -> {
                        FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Remote AudioTrack received")
                        track.setEnabled(true)
                        track.setVolume(1.5)
                        onRemoteAudioTrack(track)
                    }
                    else -> {}
                }
            }
        })

        try {
            pc?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
            pc?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[WebRTC Receiver] Add transceiver exception: ${e.localizedMessage}")
        }
        return pc
    }

    fun setRemoteOfferAndCreateAnswer(
        peerConnection: PeerConnection?,
        offerSdpString: String,
        onAnswerCreated: (SessionDescription) -> Unit,
        onDrainPendingCandidates: () -> Unit,
        isStoppedProvider: () -> Boolean
    ) {
        if (isStoppedProvider() || peerConnection == null) return
        val offerDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdpString)
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                if (isStoppedProvider()) return
                onDrainPendingCandidates()
                val mediaConstraints = MediaConstraints()
                peerConnection.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        if (isStoppedProvider()) return
                        answerDesc?.let { originalAnswer ->
                            val opusFiltered = WebRtcSdpHelper.preferOpusHighQuality(originalAnswer.description)
                            val vp8Filtered = WebRtcSdpHelper.restrictVideoToVP8Only(opusFiltered)
                            val bandwidthLimited = WebRtcSdpHelper.applyBandwidthLimits(vp8Filtered, 550, 32)
                            val optimizedAnswer = SessionDescription(originalAnswer.type, bandwidthLimited)
                            peerConnection.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    if (!isStoppedProvider()) onAnswerCreated(optimizedAnswer)
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
}
