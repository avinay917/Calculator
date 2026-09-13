package com.example.authapp

import com.example.authapp.data.User
import com.example.authapp.data.UserLocation
import com.example.authapp.ui.viewmodel.ParentUiState
import com.example.authapp.ui.viewmodel.ParentViewModel
import org.junit.Assert.*
import org.junit.Test

class ParentViewModelTest {

    @Test
    fun parentUiState_initialState_hasCorrectDefaults() {
        val state = ParentUiState()
        assertTrue(state.childUsers.isEmpty())
        assertTrue(state.isLoadingChildren)
        assertNull(state.activeSessionId)
        assertNull(state.activeStreamType)
        assertNull(state.activeChildId)
        assertNull(state.activeChildName)
        assertEquals(100f, state.audioSensitivity, 0.001f)
        assertFalse(state.isRecording)
        assertEquals(0L, state.recordingDurationSeconds)
        assertTrue(state.isFrontCamera)
        assertNull(state.locationDialogChild)
        assertNull(state.childLocation)
        assertFalse(state.isRefreshingLocation)
        assertNull(state.currentlyPlayingRecId)
        assertEquals(0, state.selectedTab)
        assertTrue(state.allRecordings.isEmpty())
        assertFalse(state.isLoadingRecordings)
        assertEquals("ALL", state.recordingFilter)
    }

    @Test
    fun parentViewModel_setAudioSensitivity_clampsBetween10And100() {
        val viewModel = ParentViewModel()

        viewModel.setAudioSensitivity(50f)
        assertEquals(50f, viewModel.uiState.value.audioSensitivity, 0.001f)

        // Below minimum boundary
        viewModel.setAudioSensitivity(-20f)
        assertEquals(10f, viewModel.uiState.value.audioSensitivity, 0.001f)

        // Above maximum boundary
        viewModel.setAudioSensitivity(200f)
        assertEquals(100f, viewModel.uiState.value.audioSensitivity, 0.001f)
    }

    @Test
    fun parentViewModel_recordingLifecycle_startsAndStopsProperly() {
        val viewModel = ParentViewModel()

        viewModel.onRecordingStarted()
        assertTrue(viewModel.uiState.value.isRecording)
        assertEquals(0L, viewModel.uiState.value.recordingDurationSeconds)

        viewModel.onRecordingStopped()
        assertFalse(viewModel.uiState.value.isRecording)
        assertEquals(0L, viewModel.uiState.value.recordingDurationSeconds)
    }

    @Test
    fun parentViewModel_locationDialog_opensAndClosesProperly() {
        val viewModel = ParentViewModel()
        val mockChild = User(uid = "child_123", email = "child@test.com", name = "Test Child", role = "child")

        viewModel.openLocationDialog(mockChild)
        assertEquals(mockChild, viewModel.uiState.value.locationDialogChild)
        assertNull(viewModel.uiState.value.childLocation)
        assertFalse(viewModel.uiState.value.isRefreshingLocation)

        val mockLocation = UserLocation(latitude = 26.85, longitude = 80.94, accuracy = 15f, timestamp = 123456789L)
        viewModel.updateChildLocation(mockLocation)
        assertEquals(mockLocation, viewModel.uiState.value.childLocation)

        viewModel.closeLocationDialog()
        assertNull(viewModel.uiState.value.locationDialogChild)
        assertNull(viewModel.uiState.value.childLocation)
    }

    @Test
    fun parentViewModel_stopStream_cleansUpActiveSession() {
        val viewModel = ParentViewModel()
        viewModel.onRecordingStarted()
        assertTrue(viewModel.uiState.value.isRecording)

        viewModel.stopStream()
        assertNull(viewModel.uiState.value.activeSessionId)
        assertNull(viewModel.uiState.value.activeStreamType)
        assertNull(viewModel.uiState.value.activeChildId)
        assertFalse(viewModel.uiState.value.isRecording)
    }
}
