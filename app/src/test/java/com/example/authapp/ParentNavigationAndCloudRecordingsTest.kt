package com.example.authapp

import com.example.authapp.data.RecordingSession
import com.example.authapp.ui.viewmodel.ParentUiState
import com.example.authapp.ui.viewmodel.ParentViewModel
import org.junit.Assert.*
import org.junit.Test

class ParentNavigationAndCloudRecordingsTest {

    @Test
    fun parentUiState_navigationAndRecordingDefaults_areCorrect() {
        val state = ParentUiState()
        assertEquals(0, state.selectedTab)
        assertTrue(state.allRecordings.isEmpty())
        assertFalse(state.isLoadingRecordings)
        assertEquals("ALL", state.recordingFilter)
        assertNull(state.currentlyPlayingRecId)
    }

    @Test
    fun parentViewModel_selectTab_switchesTabCorrectly() {
        val viewModel = ParentViewModel()
        assertEquals(0, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(1)
        assertEquals(1, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(0)
        assertEquals(0, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun parentViewModel_setRecordingFilter_updatesFilters() {
        val viewModel = ParentViewModel()
        assertEquals("ALL", viewModel.uiState.value.recordingFilter)

        viewModel.setRecordingFilter("AUDIO")
        assertEquals("AUDIO", viewModel.uiState.value.recordingFilter)

        viewModel.setRecordingFilter("VIDEO")
        assertEquals("VIDEO", viewModel.uiState.value.recordingFilter)

        viewModel.setRecordingFilter("CALL")
        assertEquals("CALL", viewModel.uiState.value.recordingFilter)

        viewModel.setRecordingFilter("ALL")
        assertEquals("ALL", viewModel.uiState.value.recordingFilter)
    }

    @Test
    fun parentViewModel_currentlyPlayingRecId_updatesCorrectly() {
        val viewModel = ParentViewModel()
        assertNull(viewModel.uiState.value.currentlyPlayingRecId)

        viewModel.setCurrentlyPlayingRecId("rec_test_123")
        assertEquals("rec_test_123", viewModel.uiState.value.currentlyPlayingRecId)

        viewModel.setCurrentlyPlayingRecId(null)
        assertNull(viewModel.uiState.value.currentlyPlayingRecId)
    }

    @Test
    fun recordingSession_dataClassIntegrity() {
        val session = RecordingSession(
            id = "rec_001",
            childId = "child_xyz",
            parentId = "parent_abc",
            streamType = "audio",
            startTime = 1700000000000L,
            durationSeconds = 45L,
            localFilePath = "/storage/emulated/0/rec.m4a",
            storageUrl = "https://storage.firebase.com/rec.m4a"
        )
        assertEquals("rec_001", session.id)
        assertEquals("child_xyz", session.childId)
        assertEquals("parent_abc", session.parentId)
        assertEquals("audio", session.streamType)
        assertEquals(1700000000000L, session.startTime)
        assertEquals(45L, session.durationSeconds)
        assertEquals("/storage/emulated/0/rec.m4a", session.localFilePath)
        assertEquals("https://storage.firebase.com/rec.m4a", session.storageUrl)
    }
}
