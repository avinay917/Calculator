package com.example.authapp.webrtc

import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.Locale

object WebRtcSdpHelper {

    fun getDefaultIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            // 1. PRIMARY: Free Google STUN Servers (Direct P2P First - ZERO Cost / 0 Bandwidth)
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.services.mozilla.com").createIceServer(),

            // 2. Dedicated Google Cloud Compute Engine (GCE) Coturn Server (Always Free Tier - us-central1)
            PeerConnection.IceServer.builder("stun:34.46.137.244:3478").createIceServer(),
            PeerConnection.IceServer.builder("turn:34.46.137.244:3478")
                .setUsername("apnasatthi")
                .setPassword("ApnaPass@2026")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:34.46.137.244:3478?transport=tcp")
                .setUsername("apnasatthi")
                .setPassword("ApnaPass@2026")
                .createIceServer(),

            // 3. Fallback Free TURN Servers (FreeSTUN UDP & TCP)
            PeerConnection.IceServer.builder("turn:freestun.net:3478")
                .setUsername("free")
                .setPassword("free")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:freestun.net:3478?transport=tcp")
                .setUsername("free")
                .setPassword("free")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:freestun.net:443?transport=tcp")
                .setUsername("free")
                .setPassword("free")
                .createIceServer()
        )
    }

    fun calculateSuperBoostGain(sensitivityPercent: Float): Double = 1.0 + ((sensitivityPercent / 100.0).coerceIn(0.0, 1.0) * 4.0)
    fun getGainMultiplierText(sensitivityPercent: Float): String = String.format(Locale.US, "%.1fx", calculateSuperBoostGain(sensitivityPercent))
    fun calculateSafeAudioGain(sensitivityPercent: Float): Double = 0.5 + ((sensitivityPercent / 100.0).coerceIn(0.0, 1.0) * 1.5)

    fun applyBandwidthLimits(sdp: String, videoKbps: Int = 550, audioKbps: Int = 32): String {
        var res = sdp
        if (res.contains("m=video")) {
            res = res.replace(Regex("m=video[^\r\n]*")) { match ->
                "${match.value}\r\nb=AS:$videoKbps\r\nb=TIAS:${videoKbps * 1000}"
            }
        }
        if (res.contains("m=audio")) {
            res = res.replace(Regex("m=audio[^\r\n]*")) { match ->
                "${match.value}\r\nb=AS:$audioKbps\r\nb=TIAS:${audioKbps * 1000}"
            }
        }
        return res
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
                    lines[i] = "$line;maxaveragebitrate=32000"
                }
                break
            }
        }
        val opusParameters = linkedMapOf(
            "minptime" to "10",
            "useinbandfec" to "1",
            "maxaveragebitrate" to "32000",
            "sprop-maxcapturerate" to "48000",
            "usedtx" to "1",
            "cbr" to "0",
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

    fun preferOpusHighQuality(sdp: String): String {
        val opusParams = "a=fmtp:111 minptime=10;ptime=10;useinbandfec=1;stereo=0;maxaveragebitrate=32000;cbr=0;usedtx=1"
        return when {
            sdp.contains("a=fmtp:111") -> sdp.replace(Regex("a=fmtp:111 .*"), opusParams)
            sdp.contains("a=rtpmap:111 opus/48000") -> sdp.replace("a=rtpmap:111 opus/48000", "a=rtpmap:111 opus/48000\r\n$opusParams")
            else -> sdp
        }
    }

    fun restrictVideoToVP8Only(sdpDescription: String): String {
        val lines = sdpDescription.split("\r\n")
        val result = mutableListOf<String>()
        var inVideoSection = false
        var videoMLine: String? = null

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

        val keepPayloads = if (vp8Payloads.isNotEmpty()) vp8Payloads + rtxPayloads else setOf("96", "97", "104", "106")

        for (line in lines) {
            when {
                line.startsWith("m=video") -> {
                    inVideoSection = true
                    videoMLine = line
                    result.add("__VIDEO_MLINE__")
                }
                line.startsWith("m=") && !line.startsWith("m=video") -> {
                    inVideoSection = false
                    result.add(line)
                }
                inVideoSection -> {
                    val payloadMatch = Regex("^a=(rtpmap|fmtp|rtcp-fb):(\\d+)").find(line)
                    if (payloadMatch != null) {
                        val payload = payloadMatch.groupValues[2]
                        if (payload in keepPayloads) result.add(line)
                    } else {
                        result.add(line)
                    }
                }
                else -> result.add(line)
            }
        }

        if (videoMLine != null) {
            val parts = videoMLine.split(" ")
            val filteredPayloads = parts.drop(3).filter { it in keepPayloads }
            val newMLine = (parts.take(3) + filteredPayloads).joinToString(" ")
            val idx = result.indexOf("__VIDEO_MLINE__")
            if (idx >= 0) result[idx] = newMLine
        }

        return result.joinToString("\r\n")
    }
}
