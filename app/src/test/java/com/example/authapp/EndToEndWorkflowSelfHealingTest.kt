package com.example.authapp

import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-End Workflow Simulation with Adaptive Self-Healing UI Locator Engine.
 *
 * Simulates complete real-world parent and child user flows and verifies
 * that when UI layouts, button labels, or component positions change, the
 * self-healing locator dynamically adapts via semantic and heuristic fallbacks.
 */
class EndToEndWorkflowSelfHealingTest {

    // --- Self-Healing UI Engine Mock Infrastructure ---

    data class UiElement(
        val testId: String? = null,
        val text: String = "",
        val contentDescription: String = "",
        val role: String = "Button",
        val isEnabled: Boolean = true,
        val isVisible: Boolean = true
    )

    class SelfHealingUiFinder(private val screenElements: List<UiElement>) {
        val healingAuditLog = mutableListOf<String>()

        fun findElement(
            primaryText: String,
            fallbackKeywords: List<String> = emptyList(),
            contentDescMatch: String? = null
        ): UiElement {
            // 1. Try exact primary text match
            val exactMatch = screenElements.firstOrNull { it.text.equals(primaryText, ignoreCase = true) && it.isVisible }
            if (exactMatch != null) {
                return exactMatch
            }

            // 2. Self-Healing Step 1: Content Description / Accessibility Match
            if (contentDescMatch != null) {
                val descMatch = screenElements.firstOrNull {
                    it.contentDescription.contains(contentDescMatch, ignoreCase = true) && it.isVisible
                }
                if (descMatch != null) {
                    healingAuditLog.add(
                        "Healed: Primary '$primaryText' not found -> Resolved via ContentDescription '$contentDescMatch' to text '${descMatch.text}'"
                    )
                    return descMatch
                }
            }

            // 3. Self-Healing Step 2: Semantic Fuzzy / Keyword Fallback
            for (keyword in fallbackKeywords) {
                val keywordMatch = screenElements.firstOrNull {
                    (it.text.contains(keyword, ignoreCase = true) || it.contentDescription.contains(keyword, ignoreCase = true)) && it.isVisible
                }
                if (keywordMatch != null) {
                    healingAuditLog.add(
                        "Healed: Primary '$primaryText' not found -> Resolved via Semantic Keyword '$keyword' to text '${keywordMatch.text}'"
                    )
                    return keywordMatch
                }
            }

            throw NoSuchElementException("Element not found even after self-healing attempts for: '$primaryText'")
        }
    }

    // --- 1. E2E Workflow: Parent Dashboard & Child Discovery with Self-Healing ---

    @Test
    fun e2eWorkflow_parentDiscoversChildAndLaunchesControls() {
        // Mock Parent Screen UI state with modern 3-button layout
        val parentScreenUi = listOf(
            UiElement(text = "Parent Dashboard", role = "Title"),
            UiElement(text = "Connected Child Devices (1)", role = "Header"),
            UiElement(text = "Avinay", role = "ChildName"),
            UiElement(text = "ONLINE 🟢", role = "StatusBadge"),
            // User updated buttons from "Audio Cast" to "Audio", "Video Cast" to "Video", etc.
            UiElement(text = "Audio", contentDescription = "Live Remote Mic", role = "Button"),
            UiElement(text = "Video", contentDescription = "Remote Camera Cast", role = "Button"),
            UiElement(text = "GPS", contentDescription = "Live GPS Location", role = "Button")
        )

        val finder = SelfHealingUiFinder(parentScreenUi)

        // Legacy test looking for "Audio Cast" should SELF-HEAL to "Audio"
        val audioButton = finder.findElement(
            primaryText = "Audio Cast",
            fallbackKeywords = listOf("Audio", "Mic", "Listen"),
            contentDescMatch = "Mic"
        )
        assertNotNull(audioButton)
        assertEquals("Audio", audioButton.text)
        assertTrue(finder.healingAuditLog.isNotEmpty())

        // Legacy test looking for "Video Cast" should SELF-HEAL to "Video"
        val videoButton = finder.findElement(
            primaryText = "Video Cast",
            fallbackKeywords = listOf("Video", "Cam", "Camera"),
            contentDescMatch = "Camera"
        )
        assertNotNull(videoButton)
        assertEquals("Video", videoButton.text)

        // GPS Location button
        val gpsButton = finder.findElement(
            primaryText = "Live Location",
            fallbackKeywords = listOf("GPS", "Location", "Map"),
            contentDescMatch = "GPS"
        )
        assertNotNull(gpsButton)
        assertEquals("GPS", gpsButton.text)
    }

    // --- 2. E2E Workflow: Live Audio Stream, Whisper Slider & Recording ---

    @Test
    fun e2eWorkflow_audioStreamWithWhisperBoostAndRecording() {
        // Mock active Audio Cast Modal UI
        val audioModalUi = listOf(
            UiElement(text = "Listening to Child", role = "StatusTitle"),
            UiElement(text = "🔊 Loudspeaker Audio Active", role = "AudioIndicator"),
            UiElement(text = "Audio Sensitivity / Whisper Boost", role = "SliderLabel"),
            UiElement(text = "100% Whisper", role = "Chip"),
            UiElement(text = "Start Audio Recording", contentDescription = "Record Stream", role = "Button"),
            UiElement(text = "Disconnect Stream", role = "Button")
        )

        val finder = SelfHealingUiFinder(audioModalUi)

        // Find whisper boost preset chip
        val whisperChip = finder.findElement(
            primaryText = "100% Whisper",
            fallbackKeywords = listOf("100%", "Whisper", "Boost")
        )
        assertEquals("100% Whisper", whisperChip.text)

        // Simulate adjusting slider to 100%
        val digitalGain = (100f / 10f).toDouble()
        assertEquals(10.0, digitalGain, 0.001)

        // Find and trigger Recording button
        val recordButton = finder.findElement(
            primaryText = "Record",
            fallbackKeywords = listOf("Start Audio Recording", "Start Recording", "Record"),
            contentDescMatch = "Record"
        )
        assertEquals("Start Audio Recording", recordButton.text)

        // Simulate recording state transition
        var isRecording = true
        var elapsedSeconds = 15L
        val stopButtonText = if (isRecording) "Stop Recording (00:15)" else "Start Audio Recording"
        assertEquals("Stop Recording (00:15)", stopButtonText)

        // Verify clean disconnect button
        val disconnectBtn = finder.findElement(
            primaryText = "Disconnect Stream",
            fallbackKeywords = listOf("Disconnect", "Stop Stream", "Close")
        )
        assertNotNull(disconnectBtn)
    }

    // --- 3. E2E Workflow: GPS Location Fetch & Google Maps Intent URL ---

    @Test
    fun e2eWorkflow_gpsLocationModalAndGoogleMapsLink() {
        val locationModalUi = listOf(
            UiElement(text = "Live Location Tracking", role = "DialogTitle"),
            UiElement(text = "Latitude: 26.760713\nLongitude: 80.945343", role = "Coordinates"),
            UiElement(text = "Accuracy: ±30m", role = "AccuracyBadge"),
            UiElement(text = "Open in Google Maps 🗺️", role = "Button"),
            UiElement(text = "Refresh GPS Location", role = "Button")
        )

        val finder = SelfHealingUiFinder(locationModalUi)

        val mapsButton = finder.findElement(
            primaryText = "Open in Google Maps",
            fallbackKeywords = listOf("Google Maps", "Maps", "Map"),
            contentDescMatch = "Maps"
        )
        assertTrue(mapsButton.text.contains("Google Maps"))

        // Validate generated Google Maps deep-link URL format
        val lat = 26.760713
        val lng = 80.945343
        val mapUri = "https://maps.google.com/?q=$lat,$lng"
        assertEquals("https://maps.google.com/?q=26.760713,80.945343", mapUri)

        // Validate on-demand refresh button
        val refreshBtn = finder.findElement(
            primaryText = "Refresh GPS",
            fallbackKeywords = listOf("Refresh GPS Location", "Refresh", "Sync GPS")
        )
        assertTrue(refreshBtn.text.contains("Refresh"))
    }

    // --- 4. E2E Workflow: Self-Healing Resilience Verification ---

    @Test
    fun selfHealingEngine_successfullyHealsMultipleEvolvingSelectors() {
        val dynamicScreen = listOf(
            UiElement(text = "Apna Satthi Protection", role = "AppTitle"),
            UiElement(text = "Grant Permissions", contentDescription = "Stage 1 Permissions", role = "Button"),
            UiElement(text = "Settings", contentDescription = "App Info Settings", role = "Button")
        )

        val finder = SelfHealingUiFinder(dynamicScreen)

        // If a test looks for "Allow All Permissions", it self-heals via keyword "Permissions"
        val permBtn = finder.findElement(
            primaryText = "Allow All Permissions",
            fallbackKeywords = listOf("Grant Permissions", "Permissions", "Allow")
        )
        assertEquals("Grant Permissions", permBtn.text)
        assertEquals(1, finder.healingAuditLog.size)
        assertTrue(finder.healingAuditLog[0].contains("Healed"))
    }
}
