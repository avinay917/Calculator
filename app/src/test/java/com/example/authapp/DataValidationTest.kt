package com.example.authapp

import com.example.authapp.data.RecordingSession
import com.example.authapp.data.UserLocation
import org.junit.Assert.*
import org.junit.Test

class DataValidationTest {

    // --- 1. Email Format & Security Validation ---

    private fun isValidEmail(email: String): Boolean {
        if (email.isBlank() || email.length > 254) return false
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()
        return emailRegex.matches(email)
    }

    @Test
    fun emailValidation_acceptsValidEmails() {
        assertTrue(isValidEmail("user@example.com"))
        assertTrue(isValidEmail("parent.safe@domain.org"))
        assertTrue(isValidEmail("child_123@sub.domain.co.in"))
    }

    @Test
    fun emailValidation_rejectsInvalidOrMaliciousEmails() {
        assertFalse(isValidEmail(""))
        assertFalse(isValidEmail("plainaddress"))
        assertFalse(isValidEmail("@missingusername.com"))
        assertFalse(isValidEmail("username@.com"))
        assertFalse(isValidEmail("user@domain..com"))
        assertFalse(isValidEmail("user<script>@domain.com"))
    }

    // --- 2. GPS Coordinate Boundary Validation ---

    private fun isGpsCoordinateValid(location: UserLocation): Boolean {
        val validLat = location.latitude in -90.0..90.0
        val validLng = location.longitude in -180.0..180.0
        val validAccuracy = location.accuracy >= 0f && location.accuracy <= 5000f
        val validTimestamp = location.timestamp > 0L
        return validLat && validLng && validAccuracy && validTimestamp
    }

    @Test
    fun gpsLocation_acceptsValidRealWorldCoordinates() {
        val validLoc = UserLocation(
            latitude = 26.760713,
            longitude = 80.945343,
            accuracy = 12.5f,
            timestamp = System.currentTimeMillis(),
            provider = "gps"
        )
        assertTrue(isGpsCoordinateValid(validLoc))
    }

    @Test
    fun gpsLocation_rejectsOutOfBoundCoordinates() {
        // Invalid latitude (> 90)
        val badLat = UserLocation(latitude = 95.123456, longitude = 80.0, accuracy = 10f, timestamp = 1000L)
        assertFalse(isGpsCoordinateValid(badLat))

        // Invalid longitude (< -180)
        val badLng = UserLocation(latitude = 20.0, longitude = -185.0, accuracy = 10f, timestamp = 1000L)
        assertFalse(isGpsCoordinateValid(badLng))

        // Invalid accuracy (negative)
        val badAccuracy = UserLocation(latitude = 20.0, longitude = 80.0, accuracy = -5f, timestamp = 1000L)
        assertFalse(isGpsCoordinateValid(badAccuracy))

        // Invalid timestamp (0)
        val badTimestamp = UserLocation(latitude = 20.0, longitude = 80.0, accuracy = 10f, timestamp = 0L)
        assertFalse(isGpsCoordinateValid(badTimestamp))
    }

    // --- 3. User Role Bounds & Normalization ---

    private fun normalizeUserRole(role: String): String {
        return when (role.trim().lowercase()) {
            "parent" -> "parent"
            "child" -> "child"
            else -> "child" // Safe default fallback: never allow unauthorized elevation
        }
    }

    @Test
    fun roleValidation_strictlyAllowsParentAndChildOnly() {
        assertEquals("parent", normalizeUserRole("parent"))
        assertEquals("parent", normalizeUserRole("PARENT"))
        assertEquals("child", normalizeUserRole("child"))
        assertEquals("child", normalizeUserRole("CHILD"))

        // Unauthorized or corrupted role strings fallback securely to "child"
        assertEquals("child", normalizeUserRole("admin"))
        assertEquals("child", normalizeUserRole("superadmin"))
        assertEquals("child", normalizeUserRole(""))
        assertEquals("child", normalizeUserRole("unknown"))
    }

    // --- 4. Audio Sensitivity Bounds & Scale Conversion ---

    private fun computeVolumeScale(sensitivityPercent: Float): Double {
        val clamped = sensitivityPercent.coerceIn(10f, 100f)
        return (clamped / 10f).toDouble()
    }

    @Test
    fun audioSensitivity_computesExactDigitalGain() {
        // 10% -> 1.0x (normal unity gain)
        assertEquals(1.0, computeVolumeScale(10f), 0.001)

        // 30% -> 3.0x (normal monitoring boost)
        assertEquals(3.0, computeVolumeScale(30f), 0.001)

        // 60% -> 6.0x (high boost for distant room)
        assertEquals(6.0, computeVolumeScale(60f), 0.001)

        // 100% -> 10.0x (maximum whisper amplification)
        assertEquals(10.0, computeVolumeScale(100f), 0.001)

        // Clamping check
        assertEquals(1.0, computeVolumeScale(0f), 0.001)
        assertEquals(10.0, computeVolumeScale(150f), 0.001)
    }

    // --- 5. Recording Session Validation ---

    private fun isValidRecordingSession(session: RecordingSession): Boolean {
        if (session.id.isBlank() || session.childId.isBlank()) return false
        if (session.streamType != "audio" && session.streamType != "video") return false
        if (session.durationSeconds < 0) return false
        if (session.startTime <= 0L) return false
        return true
    }

    @Test
    fun recordingSession_validatesSuccessfully() {
        val session = RecordingSession(
            id = "rec_123",
            childId = "child_456",
            streamType = "audio",
            startTime = System.currentTimeMillis(),
            durationSeconds = 45
        )
        assertTrue(isValidRecordingSession(session))
    }

    @Test
    fun recordingSession_rejectsInvalidTypesOrNegativeDuration() {
        val badType = RecordingSession(id = "1", childId = "2", streamType = "screen", durationSeconds = 10)
        assertFalse(isValidRecordingSession(badType))

        val badDuration = RecordingSession(id = "1", childId = "2", streamType = "audio", durationSeconds = -5)
        assertFalse(isValidRecordingSession(badDuration))

        val blankId = RecordingSession(id = "", childId = "2", streamType = "video", durationSeconds = 10)
        assertFalse(isValidRecordingSession(blankId))
    }
}
