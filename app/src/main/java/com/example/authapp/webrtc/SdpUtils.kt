package com.example.authapp.webrtc

/**
 * WebRTC SDP Manipulation Helpers (Point 15).
 * Enforces VP8 video & Opus high-quality audio priority in WebRTC Session Description.
 * NOTE: WebRtcManager.preferOpusHighQuality() aur restrictVideoToVP8Only() advanced versions hain.
 * Yeh SdpUtils sirf backward-compat ke liye raha hai.
 */
object SdpUtils {
    fun restrictVideoToVP8Only(sdpDescription: String): String {
        return sdpDescription.replace(
            Regex("(m=video \\d+ RTP/SAVPF)(.+)"),
            "$1 96"
        )
    }

    fun preferOpusHighQuality(sdpDescription: String): String {
        // BUG FIX: Already modified hai toh dobara inject mat karo (idempotent guard)
        if (sdpDescription.contains("maxaveragebitrate=128000")) return sdpDescription
        return sdpDescription.replace(
            "useinbandfec=1",
            "useinbandfec=1;usedtx=0;maxaveragebitrate=128000"
        )
    }
}
