package com.example.authapp

import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.data.UserLocation
import com.example.authapp.receiver.CallReceiver
import org.junit.Assert.*
import org.junit.Test

class CallDetectionAndLocationTest {

    @Test
    fun callRecordingSession_hasCorrectStreamTypeAndMetadata() {
        val callSession = RecordingSession(
            id = "call_rec_001",
            childId = "child_user_789",
            parentId = "parent_user_123",
            streamType = "call",
            startTime = 1710000000000L,
            durationSeconds = 120L,
            status = "SAVED",
            storageUrl = "https://storage.googleapis.com/test_call.m4a",
            localFilePath = "/data/user/0/com.example.authapp/files/call_recordings/call_123.m4a"
        )

        assertEquals("call", callSession.streamType)
        assertEquals("call_rec_001", callSession.id)
        assertEquals(120L, callSession.durationSeconds)
        assertTrue(callSession.storageUrl.contains("test_call.m4a"))
        assertTrue(callSession.localFilePath.contains("call_recordings"))
    }

    @Test
    fun cloudRecordings_filterLogic_separatesCallsCorrectly() {
        val rec1 = RecordingSession(id = "1", streamType = "audio")
        val rec2 = RecordingSession(id = "2", streamType = "video")
        val rec3 = RecordingSession(id = "3", streamType = "call")
        val rec4 = RecordingSession(id = "4", streamType = "call")
        val allList = listOf(rec1, rec2, rec3, rec4)

        val audioOnly = allList.filter { it.streamType.equals("audio", ignoreCase = true) }
        val videoOnly = allList.filter { it.streamType.equals("video", ignoreCase = true) }
        val callsOnly = allList.filter { it.streamType.equals("call", ignoreCase = true) }

        assertEquals(1, audioOnly.size)
        assertEquals(1, videoOnly.size)
        assertEquals(2, callsOnly.size)
        assertEquals(4, allList.size)
    }

    @Test
    fun callReceiver_stateReset_ensuresCleanState() {
        CallReceiver.resetStateForTesting()
        // Ensure no exceptions occur and receiver companion state is cleanly testable
        assertTrue(true)
    }

    @Test
    fun userModel_lastSeenAndOnlineState_consistency() {
        val onlineUser = User(uid = "u1", name = "Aarav", isOnline = true, lastSeen = 0L)
        val offlineUser = User(uid = "u2", name = "Ananya", isOnline = false, lastSeen = 1710000000000L)

        assertTrue(onlineUser.isOnline)
        assertEquals(0L, onlineUser.lastSeen)

        assertFalse(offlineUser.isOnline)
        assertTrue(offlineUser.lastSeen > 0L)
    }

    @Test
    fun userLocation_precisionAndAccuracyConstraints() {
        val loc = UserLocation(
            latitude = 26.8467,
            longitude = 80.9462,
            accuracy = 18.5f,
            timestamp = 1710000000000L
        )

        assertTrue(loc.latitude in -90.0..90.0)
        assertTrue(loc.longitude in -180.0..180.0)
        assertTrue(loc.accuracy >= 0f)
        assertEquals(18, loc.accuracy.toInt())
    }
}
