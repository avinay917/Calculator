package com.example.authapp

import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.data.UserLocation
import org.junit.Assert.*
import org.junit.Test

class DataIntegrityTest {

    // --- 1. User Model Schema Integrity & Deserialization ---

    @Test
    fun userModel_deserializesFromCompleteFirebaseMap() {
        val map = mapOf(
            "uid" to "uid_test_123",
            "name" to "Parent User",
            "email" to "parent@example.com",
            "role" to "parent",
            "fcmToken" to "fcm_token_abc",
            "isOnline" to true,
            "lastSeen" to 1789232528054L,
            "location" to mapOf(
                "latitude" to 26.760713,
                "longitude" to 80.945343,
                "accuracy" to 10f,
                "timestamp" to 1789232981000L,
                "provider" to "gps"
            )
        )

        val locMap = map["location"] as? Map<*, *>
        val location = locMap?.let {
            UserLocation(
                latitude = (it["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (it["longitude"] as? Number)?.toDouble() ?: 0.0,
                accuracy = (it["accuracy"] as? Number)?.toFloat() ?: 0f,
                timestamp = (it["timestamp"] as? Number)?.toLong() ?: 0L,
                provider = it["provider"] as? String ?: ""
            )
        }

        val user = User(
            uid = map["uid"] as String,
            name = map["name"] as String,
            email = map["email"] as String,
            role = map["role"] as String,
            fcmToken = map["fcmToken"] as String,
            isOnline = map["isOnline"] as Boolean,
            lastSeen = map["lastSeen"] as Long,
            location = location
        )

        assertEquals("uid_test_123", user.uid)
        assertEquals("Parent User", user.name)
        assertEquals("parent@example.com", user.email)
        assertEquals("parent", user.role)
        assertTrue(user.isOnline)
        assertEquals(1789232528054L, user.lastSeen)
        assertNotNull(user.location)
        assertEquals(26.760713, user.location!!.latitude, 0.000001)
        assertEquals(80.945343, user.location!!.longitude, 0.000001)
        assertEquals("gps", user.location!!.provider)
    }

    // --- 2. Backward Compatibility for Legacy RTDB Records ---

    @Test
    fun userModel_handlesLegacyMissingFieldsGracefullyWithoutNPE() {
        // Legacy record without location, fcmToken, or lastSeen
        val legacyMap = mapOf(
            "uid" to "legacy_uid_999",
            "email" to "old_child@example.com",
            "name" to "Old Child Account"
        )

        val user = User(
            uid = legacyMap["uid"] as? String ?: "",
            name = legacyMap["name"] as? String ?: "",
            email = legacyMap["email"] as? String ?: "",
            role = legacyMap["role"] as? String ?: "child",
            fcmToken = legacyMap["fcmToken"] as? String ?: "",
            isOnline = legacyMap["isOnline"] as? Boolean ?: false,
            lastSeen = legacyMap["lastSeen"] as? Long ?: 0L,
            location = null
        )

        assertEquals("legacy_uid_999", user.uid)
        assertEquals("Old Child Account", user.name)
        assertEquals("child", user.role)
        assertFalse(user.isOnline)
        assertEquals(0L, user.lastSeen)
        assertNull(user.location)
    }

    // --- 3. UserLocation High-Precision Coordinate Preservation ---

    @Test
    fun userLocation_preservesCoordinatePrecisionWithoutRoundingLoss() {
        val originalLat = 26.76071311812848
        val originalLng = 80.94534315168858
        val originalAccuracy = 30.0f
        val originalTime = 1789232981000L

        val location = UserLocation(
            latitude = originalLat,
            longitude = originalLng,
            accuracy = originalAccuracy,
            timestamp = originalTime,
            provider = "gps"
        )

        // Simulate JSON / RTDB serialization roundtrip
        val serialized = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to location.accuracy,
            "timestamp" to location.timestamp,
            "provider" to location.provider
        )

        val deserialized = UserLocation(
            latitude = (serialized["latitude"] as Number).toDouble(),
            longitude = (serialized["longitude"] as Number).toDouble(),
            accuracy = (serialized["accuracy"] as Number).toFloat(),
            timestamp = (serialized["timestamp"] as Number).toLong(),
            provider = serialized["provider"] as String
        )

        assertEquals(originalLat, deserialized.latitude, 0.0000000000001)
        assertEquals(originalLng, deserialized.longitude, 0.0000000000001)
        assertEquals(originalAccuracy, deserialized.accuracy, 0.001f)
        assertEquals(originalTime, deserialized.timestamp)
    }

    // --- 4. Firebase RTDB Path Safety & Key Sanitization ---

    private fun isFirebaseKeySafe(key: String): Boolean {
        if (key.isBlank()) return false
        val illegalChars = listOf('.', '#', '$', '[', ']', '/')
        return illegalChars.none { key.contains(it) }
    }

    @Test
    fun firebaseKeySanitization_identifiesIllegalCharacters() {
        assertTrue(isFirebaseKeySafe("TLKAO3SDC5d9HbbnZOCXz4ca2so1"))
        assertTrue(isFirebaseKeySafe("session_1789232528"))
        assertTrue(isFirebaseKeySafe("rec_abc-123_456"))

        // Firebase forbids '.', '#', '$', '[', ']' in key names
        assertFalse(isFirebaseKeySafe("user.name@example.com"))
        assertFalse(isFirebaseKeySafe("path/with/slash"))
        assertFalse(isFirebaseKeySafe("key#123"))
        assertFalse(isFirebaseKeySafe("price$"))
        assertFalse(isFirebaseKeySafe("array[0]"))
    }

    // --- 5. RecordingSession Serialization Integrity ---

    @Test
    fun recordingSession_serializesAndDeserializesCorrectly() {
        val session = RecordingSession(
            id = "rec_99",
            childId = "child_01",
            parentId = "parent_01",
            streamType = "video",
            startTime = 1789232000000L,
            durationSeconds = 120,
            status = "SAVED"
        )

        val map = mapOf(
            "id" to session.id,
            "childId" to session.childId,
            "parentId" to session.parentId,
            "streamType" to session.streamType,
            "startTime" to session.startTime,
            "durationSeconds" to session.durationSeconds,
            "status" to session.status
        )

        val reconstituted = RecordingSession(
            id = map["id"] as String,
            childId = map["childId"] as String,
            parentId = map["parentId"] as String,
            streamType = map["streamType"] as String,
            startTime = map["startTime"] as Long,
            durationSeconds = (map["durationSeconds"] as Number).toLong(),
            status = map["status"] as String
        )

        assertEquals(session, reconstituted)
    }
}
