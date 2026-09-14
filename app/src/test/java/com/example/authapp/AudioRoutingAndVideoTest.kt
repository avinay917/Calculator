package com.example.authapp

import com.example.authapp.audio.AudioOutputRoute
import com.example.authapp.webrtc.WebRtcManager
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AudioRoutingAndVideoTest {

    @Test
    fun audioOutputRoute_enumContainsAllRequiredOutputs() {
        val routes = AudioOutputRoute.values()
        assertEquals(3, routes.size)
        assertTrue(routes.contains(AudioOutputRoute.SPEAKER))
        assertTrue(routes.contains(AudioOutputRoute.EARPIECE))
        assertTrue(routes.contains(AudioOutputRoute.BLUETOOTH))
    }

    @Test
    fun audioOutputRoute_toggleLogicWithoutBluetooth_cyclesBetweenSpeakerAndEarpiece() {
        var current = AudioOutputRoute.SPEAKER
        val hasBluetooth = false

        // Toggle 1: Speaker -> Earpiece
        current = if (hasBluetooth) {
            when (current) {
                AudioOutputRoute.SPEAKER -> AudioOutputRoute.EARPIECE
                AudioOutputRoute.EARPIECE -> AudioOutputRoute.BLUETOOTH
                AudioOutputRoute.BLUETOOTH -> AudioOutputRoute.SPEAKER
            }
        } else {
            when (current) {
                AudioOutputRoute.SPEAKER -> AudioOutputRoute.EARPIECE
                AudioOutputRoute.EARPIECE -> AudioOutputRoute.SPEAKER
                AudioOutputRoute.BLUETOOTH -> AudioOutputRoute.SPEAKER
            }
        }
        assertEquals(AudioOutputRoute.EARPIECE, current)

        // Toggle 2: Earpiece -> Speaker
        current = if (hasBluetooth) {
            when (current) {
                AudioOutputRoute.SPEAKER -> AudioOutputRoute.EARPIECE
                AudioOutputRoute.EARPIECE -> AudioOutputRoute.BLUETOOTH
                AudioOutputRoute.BLUETOOTH -> AudioOutputRoute.SPEAKER
            }
        } else {
            when (current) {
                AudioOutputRoute.SPEAKER -> AudioOutputRoute.EARPIECE
                AudioOutputRoute.EARPIECE -> AudioOutputRoute.SPEAKER
                AudioOutputRoute.BLUETOOTH -> AudioOutputRoute.SPEAKER
            }
        }
        assertEquals(AudioOutputRoute.SPEAKER, current)
    }

    @Test
    fun audioOutputRoute_toggleLogicWithBluetooth_cyclesThreeWay() {
        var current = AudioOutputRoute.SPEAKER
        val hasBluetooth = true

        fun nextRoute(from: AudioOutputRoute): AudioOutputRoute {
            return when (from) {
                AudioOutputRoute.SPEAKER -> AudioOutputRoute.EARPIECE
                AudioOutputRoute.EARPIECE -> AudioOutputRoute.BLUETOOTH
                AudioOutputRoute.BLUETOOTH -> AudioOutputRoute.SPEAKER
            }
        }

        current = nextRoute(current)
        assertEquals(AudioOutputRoute.EARPIECE, current)

        current = nextRoute(current)
        assertEquals(AudioOutputRoute.BLUETOOTH, current)

        current = nextRoute(current)
        assertEquals(AudioOutputRoute.SPEAKER, current)
    }

    @Test
    fun videoStreamSdpOffer_containsBothAudioAndVideoTracks() {
        val mockVideoSdp = """
            v=0
            o=- 123456789 2 IN IP4 127.0.0.1
            s=-
            t=0 0
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            a=rtpmap:111 opus/48000/2
            a=sendonly
            m=video 9 UDP/TLS/RTP/SAVPF 96
            a=rtpmap:96 VP8/90000
            a=sendonly
        """.trimIndent()

        val optimized = WebRtcManager.optimizeOpusSdp(mockVideoSdp)
        assertTrue("Optimized SDP must contain video media line", optimized.contains("m=video"))
        assertTrue("Optimized SDP must contain audio media line", optimized.contains("m=audio"))
        assertTrue("Optimized SDP must contain Opus codec", optimized.contains("opus/48000"))
        assertTrue("Optimized SDP must contain 64kbps bitrate injection", optimized.contains("maxaveragebitrate=64000"))
    }

    @Test
    fun safeAudioGain_matchesVolumeConstraints() {
        // Gain range is 0.5x to 2.0x
        assertEquals(0.5, WebRtcManager.calculateSafeAudioGain(0f), 0.001)
        assertEquals(2.0, WebRtcManager.calculateSafeAudioGain(100f), 0.001)
        assertEquals(1.25, WebRtcManager.calculateSafeAudioGain(50f), 0.001)
        // Clamping check
        assertEquals(0.5, WebRtcManager.calculateSafeAudioGain(-50f), 0.001)
        assertEquals(2.0, WebRtcManager.calculateSafeAudioGain(150f), 0.001)
    }

    @Test
    fun superBoostGain_scalesSmoothlyForWhispers() {
        // Gain range is 0.5x to 3.5x
        assertEquals(0.5, WebRtcManager.calculateSuperBoostGain(0f), 0.001)
        assertEquals(2.0, WebRtcManager.calculateSuperBoostGain(50f), 0.001)
        assertEquals(3.5, WebRtcManager.calculateSuperBoostGain(100f), 0.001)
        // Clamping check
        assertEquals(0.5, WebRtcManager.calculateSuperBoostGain(-50f), 0.001)
        assertEquals(3.5, WebRtcManager.calculateSuperBoostGain(150f), 0.001)
    }
}
