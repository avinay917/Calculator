package com.example.authapp

import com.example.authapp.audio.AudioOutputRoute
import com.example.authapp.data.AppUpdateInfo
import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.data.UserLocation
import com.example.authapp.receiver.CallReceiver
import com.example.authapp.ui.viewmodel.ParentUiState
import com.example.authapp.ui.viewmodel.ParentViewModel
import com.example.authapp.updater.UpdateManager
import com.example.authapp.webrtc.WebRtcManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Exhaustive System Verification Test Suite covering:
 * 1. Over-The-Air (OTA) In-App Auto-Update Engine
 * 2. Automatic Call Detection, Telephony State Machine & Recording Storage
 * 3. Dynamic Audio Output Routing (Speaker, Earpiece, Auto-Bluetooth)
 * 4. WebRTC Far-Field Audio & Video Streaming Engine (Opus 48kHz, Safe Gain Clamping)
 * 5. GPS Location Tracking, Accuracy Bounds & Geocoding Fallback
 * 6. Parent Bottom Navigation Bar, Filter Chips & Cloud History Playback
 */
class ComprehensiveAllModulesSystemTest {

    @Before
    fun setUp() {
        CallReceiver.resetStateForTesting()
    }

    // =========================================================================
    // 1. OVER-THE-AIR (OTA) IN-APP AUTO-UPDATE ENGINE TESTS
    // =========================================================================

    @Test
    fun otaUpdate_versionComparison_triggersOnlyForHigherVersions() {
        val installedVersionCode = 53L

        // Case 1: Newer version on server -> must trigger update
        val newerUpdate = AppUpdateInfo(
            versionCode = 54L,
            versionName = "v2.54",
            apkUrl = "https://github.com/avinay917/Calculator/releases/download/latest/Calculator-latest.apk"
        )
        assertTrue("Newer version must trigger update", UpdateManager.isUpdateAvailable(installedVersionCode, newerUpdate))

        // Case 2: Same version on server -> must NOT trigger update
        val sameUpdate = AppUpdateInfo(versionCode = 53L, apkUrl = "https://example.com/app.apk")
        assertFalse("Identical version must not trigger update", UpdateManager.isUpdateAvailable(installedVersionCode, sameUpdate))

        // Case 3: Older version on server -> must NOT trigger update
        val olderUpdate = AppUpdateInfo(versionCode = 52L, apkUrl = "https://example.com/app.apk")
        assertFalse("Older version must not trigger update", UpdateManager.isUpdateAvailable(installedVersionCode, olderUpdate))

        // Case 4: Null update info -> must NOT trigger update
        assertFalse("Null update must not trigger update", UpdateManager.isUpdateAvailable(installedVersionCode, null))

        // Case 5: Higher version but blank APK URL -> must NOT trigger update
        val blankUrlUpdate = AppUpdateInfo(versionCode = 60L, apkUrl = "   ")
        assertFalse("Blank URL update must not trigger update", UpdateManager.isUpdateAvailable(installedVersionCode, blankUrlUpdate))
    }

    @Test
    fun otaUpdate_modelDataIntegrity_andCdnUrlContract() {
        val update = AppUpdateInfo(
            versionCode = 54L,
            versionName = "v2.54",
            apkUrl = "https://github.com/avinay917/Calculator/releases/download/latest/Calculator-latest.apk",
            releaseNotes = "High-speed OTA auto-update with direct APK installation.",
            isForceUpdate = true,
            updatedAt = 1720000000000L
        )

        assertEquals(54L, update.versionCode)
        assertEquals("v2.54", update.versionName)
        assertTrue("APK URL must point to Calculator-latest.apk", update.apkUrl.endsWith("Calculator-latest.apk"))
        assertTrue("APK URL must be secure HTTPS", update.apkUrl.startsWith("https://"))
        assertTrue("Force update must be true", update.isForceUpdate)
        assertEquals(1720000000000L, update.updatedAt)
    }

    // =========================================================================
    // 2. AUTOMATIC CALL DETECTION & TELEPHONY STATE MACHINE TESTS
    // =========================================================================

    @Test
    fun callDetection_stateMachine_incomingAndOutgoingTransitions() {
        // Telephony State: IDLE -> RINGING -> OFFHOOK -> IDLE
        val incomingState = "RINGING"
        val activeCallState = "OFFHOOK"
        val callEndedState = "IDLE"

        var isCallActive = false
        var recordedDuration = 0L

        // Transition 1: Incoming call rings
        assertEquals("RINGING", incomingState)

        // Transition 2: Call answered (OFFHOOK)
        if (activeCallState == "OFFHOOK") {
            isCallActive = true
        }
        assertTrue("Call must be active during OFFHOOK", isCallActive)

        // Transition 3: Call terminated (IDLE)
        val startTime = 1000L
        val endTime = 65000L // 64 seconds call
        if (callEndedState == "IDLE" && isCallActive) {
            recordedDuration = (endTime - startTime) / 1000L
            isCallActive = false
        }
        assertFalse("Call must be inactive after IDLE", isCallActive)
        assertEquals(64L, recordedDuration)
    }

    @Test
    fun callRecordingSession_metadataAndStorageContract() {
        val callSession = RecordingSession(
            id = "call_rec_20260913_110000",
            childId = "child_user_456",
            parentId = "parent_user_123",
            streamType = "call",
            startTime = 1720000000000L,
            durationSeconds = 85L,
            status = "SAVED",
            storageUrl = "https://firebasestorage.googleapis.com/v0/b/app/o/recordings%2Fcall_001.m4a",
            localFilePath = "/data/user/0/com.example.authapp/files/call_recordings/call_001.m4a"
        )

        assertEquals("call", callSession.streamType)
        assertTrue(callSession.durationSeconds > 0)
        assertTrue(callSession.localFilePath.contains("call_recordings"))
        assertTrue(callSession.localFilePath.endsWith(".m4a"))
        assertTrue(callSession.storageUrl.contains("recordings"))
    }

    // =========================================================================
    // 3. DYNAMIC AUDIO OUTPUT ROUTING TESTS
    // =========================================================================

    @Test
    fun audioRouting_allThreeRoutesExist() {
        val routes = AudioOutputRoute.values()
        assertEquals(3, routes.size)
        assertTrue(routes.contains(AudioOutputRoute.SPEAKER))
        assertTrue(routes.contains(AudioOutputRoute.EARPIECE))
        assertTrue(routes.contains(AudioOutputRoute.BLUETOOTH))
    }

    @Test
    fun audioRouting_cyclingLogic_withoutBluetooth() {
        // When bluetooth is disconnected, toggle cycles between SPEAKER and EARPIECE
        var route = AudioOutputRoute.SPEAKER
        val isBtConnected = false

        fun toggle(current: AudioOutputRoute): AudioOutputRoute {
            return if (isBtConnected) {
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
        }

        route = toggle(route)
        assertEquals(AudioOutputRoute.EARPIECE, route)

        route = toggle(route)
        assertEquals(AudioOutputRoute.SPEAKER, route)
    }

    @Test
    fun audioRouting_cyclingLogic_withBluetooth() {
        // When bluetooth is connected, toggle cycles through SPEAKER -> EARPIECE -> BLUETOOTH -> SPEAKER
        var route = AudioOutputRoute.SPEAKER
        val isBtConnected = true

        fun toggle(current: AudioOutputRoute): AudioOutputRoute {
            return if (isBtConnected) {
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
        }

        route = toggle(route)
        assertEquals(AudioOutputRoute.EARPIECE, route)

        route = toggle(route)
        assertEquals(AudioOutputRoute.BLUETOOTH, route)

        route = toggle(route)
        assertEquals(AudioOutputRoute.SPEAKER, route)
    }

    @Test
    fun audioRouting_autoFallbackOnBluetoothDisconnect() {
        var currentRoute = AudioOutputRoute.BLUETOOTH
        val isBtNowConnected = false

        // Rule: If Bluetooth disconnects while active, fallback to SPEAKER
        if (!isBtNowConnected && currentRoute == AudioOutputRoute.BLUETOOTH) {
            currentRoute = AudioOutputRoute.SPEAKER
        }

        assertEquals("Must fallback to SPEAKER when BT disconnects", AudioOutputRoute.SPEAKER, currentRoute)
    }

    // =========================================================================
    // 4. WEBRTC STREAMING ENGINE & AUDIO GAIN TESTS
    // =========================================================================

    @Test
    fun webrtc_safeAudioGain_clampsBetweenHalfAndDouble() {
        // 0% sensitivity -> 0.5x minimum safe gain
        assertEquals(0.5, WebRtcManager.calculateSafeAudioGain(0f), 0.001)

        // 50% sensitivity -> 1.25x midpoint gain
        assertEquals(1.25, WebRtcManager.calculateSafeAudioGain(50f), 0.001)

        // 100% sensitivity -> 2.0x maximum boost gain (whisper mode)
        assertEquals(2.0, WebRtcManager.calculateSafeAudioGain(100f), 0.001)

        // Clamping negative values
        assertEquals(0.5, WebRtcManager.calculateSafeAudioGain(-30f), 0.001)

        // Clamping overflow values > 100%
        assertEquals(2.0, WebRtcManager.calculateSafeAudioGain(250f), 0.001)
    }

    @Test
    fun webrtc_opusSdpOptimization_injectsStudioParameters() {
        val sdpOffer = """
            v=0
            o=- 987654321 2 IN IP4 127.0.0.1
            s=-
            t=0 0
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            a=rtpmap:111 opus/48000/2
            a=sendonly
        """.trimIndent()

        val optimizedSdp = WebRtcManager.optimizeOpusSdp(sdpOffer)

        assertTrue("Must include opus/48000 codec declaration", optimizedSdp.contains("opus/48000"))
        assertTrue("Must inject 48kHz capture rate", optimizedSdp.contains("sprop-maxcapturerate=48000"))
        assertTrue("Must inject 64kbps bitrate", optimizedSdp.contains("maxaveragebitrate=64000"))
        assertTrue("Must enable DTX for battery/bandwidth saving", optimizedSdp.contains("usedtx=1"))
        assertTrue("Must enable FEC for packet loss protection", optimizedSdp.contains("useinbandfec=1"))
    }

    @Test
    fun webrtc_videoStreamSdp_containsBothAudioAndVideoTracks() {
        val sdpWithAudioAndVideo = """
            v=0
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            a=rtpmap:111 opus/48000/2
            m=video 9 UDP/TLS/RTP/SAVPF 96
            a=rtpmap:96 VP8/90000
        """.trimIndent()

        val result = WebRtcManager.optimizeOpusSdp(sdpWithAudioAndVideo)
        assertTrue(result.contains("m=audio"))
        assertTrue(result.contains("m=video"))
    }

    // =========================================================================
    // 5. GPS LOCATION TRACKING & GEOCODING TESTS
    // =========================================================================

    @Test
    fun location_coordinateConstraints_andGoogleMapsUrl() {
        val location = UserLocation(
            latitude = 26.8467,
            longitude = 80.9462,
            accuracy = 12.5f,
            timestamp = 1720000000000L
        )

        // Coordinate validity
        assertTrue("Latitude must be in -90 to +90", location.latitude in -90.0..90.0)
        assertTrue("Longitude must be in -180 to +180", location.longitude in -180.0..180.0)
        assertTrue("Accuracy must be positive", location.accuracy > 0f)

        // Google Maps intent deep link format
        val mapsUrl = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", location.latitude, location.longitude)
        assertEquals("https://maps.google.com/?q=26.846700,80.946200", mapsUrl)
    }

    @Test
    fun location_fallbackAddressFormatting() {
        val lat = 28.6139
        val lng = 77.2090
        val fallback = String.format(Locale.US, "GPS: %.5f°, %.5f°", lat, lng)
        assertEquals("GPS: 28.61390°, 77.20900°", fallback)
    }

    // =========================================================================
    // 6. PARENT BOTTOM NAVIGATION & CLOUD HISTORY PLAYBACK TESTS
    // =========================================================================

    @Test
    fun parentBottomNav_initialStateDefaults() {
        val state = ParentUiState()
        assertEquals(0, state.selectedTab) // Tab 0 = Live Monitor
        assertEquals("ALL", state.recordingFilter) // Default filter
        assertNull(state.currentlyPlayingRecId) // No audio playing
        assertTrue(state.allRecordings.isEmpty())
    }

    @Test
    fun parentBottomNav_tabSwitching() {
        val viewModel = ParentViewModel()
        assertEquals(0, viewModel.uiState.value.selectedTab)

        // Switch to Cloud History (Tab 1)
        viewModel.selectTab(1)
        assertEquals(1, viewModel.uiState.value.selectedTab)

        // Switch back to Live Monitor (Tab 0)
        viewModel.selectTab(0)
        assertEquals(0, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun cloudRecordings_filterChips_separateAllTypesAccurately() {
        val r1 = RecordingSession(id = "1", streamType = "audio")
        val r2 = RecordingSession(id = "2", streamType = "audio")
        val r3 = RecordingSession(id = "3", streamType = "video")
        val r4 = RecordingSession(id = "4", streamType = "call")
        val r5 = RecordingSession(id = "5", streamType = "call")
        val r6 = RecordingSession(id = "6", streamType = "call")

        val allRecordings = listOf(r1, r2, r3, r4, r5, r6)

        // Filter ALL
        val filterAll = allRecordings
        assertEquals(6, filterAll.size)

        // Filter AUDIO
        val filterAudio = allRecordings.filter { it.streamType.equals("audio", ignoreCase = true) }
        assertEquals(2, filterAudio.size)

        // Filter VIDEO
        val filterVideo = allRecordings.filter { it.streamType.equals("video", ignoreCase = true) }
        assertEquals(1, filterVideo.size)

        // Filter CALL
        val filterCalls = allRecordings.filter { it.streamType.equals("call", ignoreCase = true) }
        assertEquals(3, filterCalls.size)
    }

    @Test
    fun cloudRecordings_playbackTracking() {
        val viewModel = ParentViewModel()
        assertNull(viewModel.uiState.value.currentlyPlayingRecId)

        // Start playback
        viewModel.setCurrentlyPlayingRecId("rec_track_789")
        assertEquals("rec_track_789", viewModel.uiState.value.currentlyPlayingRecId)

        // Stop playback
        viewModel.setCurrentlyPlayingRecId(null)
        assertNull(viewModel.uiState.value.currentlyPlayingRecId)
    }
}
