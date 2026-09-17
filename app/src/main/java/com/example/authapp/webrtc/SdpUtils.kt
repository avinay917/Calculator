package com.example.authapp.webrtc

/**
 * WebRTC SDP Manipulation Helpers (Point 15).
 * Enforces VP8 video & Opus high-quality audio priority in WebRTC Session Description.
 */
object SdpUtils {
    fun restrictVideoToVP8Only(sdpDescription: String): String {
        return sdpDescription.replace(
            Regex("(m=video \\d+ RTP/SAVPF)(.+)"),
            "$1 96"
        )
    }

    fun preferOpusHighQuality(sdpDescription: String): String {
        return sdpDescription.replace(
            "useinbandfec=1",
            "useinbandfec=1;usedtx=0;maxaveragebitrate=128000"
        )
    }
}
