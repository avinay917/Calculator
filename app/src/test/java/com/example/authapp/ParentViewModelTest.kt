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

    @Test
    fun parentViewModel_phase1And2Dialogs_openAndCloseProperly() {
        val viewModel = ParentViewModel()
        val mockChild = User(uid = "child_456", email = "kid@test.com", name = "Kid", role = "child")

        // Snapshot Dialog
        viewModel.openSnapshotDialog(mockChild)
        assertEquals(mockChild, viewModel.uiState.value.activeSnapshotDialogChild)
        viewModel.closeSnapshotDialog()
        assertNull(viewModel.uiState.value.activeSnapshotDialogChild)

        // Activity Dialog
        viewModel.openActivityDialog(mockChild)
        assertEquals(mockChild, viewModel.uiState.value.activeActivityDialogChild)
        viewModel.closeActivityDialog()
        assertNull(viewModel.uiState.value.activeActivityDialogChild)

        // Alerts Dialog
        viewModel.openAlertsDialog(mockChild)
        assertEquals(mockChild, viewModel.uiState.value.activeAlertsDialogChild)
        viewModel.closeAlertsDialog()
        assertNull(viewModel.uiState.value.activeAlertsDialogChild)

        // Schedule Dialog
        viewModel.openScheduleDialog(mockChild)
        assertEquals(mockChild, viewModel.uiState.value.activeScheduleDialogChild)
        viewModel.closeScheduleDialog()
        assertNull(viewModel.uiState.value.activeScheduleDialogChild)
    }

    @Test
    fun childActivityModels_defaultsAreValid() {
        val callLog = com.example.authapp.data.CallLogItem(number = "1234567890", name = "Mom", type = "INCOMING", durationSeconds = 60)
        assertEquals("Mom", callLog.name)
        assertEquals("INCOMING", callLog.type)

        val notif = com.example.authapp.data.NotificationItem(packageName = "com.whatsapp", appName = "WhatsApp", title = "Friend", text = "Hello")
        assertEquals("WhatsApp", notif.appName)
        assertEquals("Hello", notif.text)

        val alert = com.example.authapp.data.SecurityAlert(type = "LOW_BATTERY", severity = "CRITICAL", title = "Low Battery")
        assertEquals("LOW_BATTERY", alert.type)
        assertEquals("CRITICAL", alert.severity)

        val sched = com.example.authapp.data.RecordingSchedule(hour = 21, minute = 30, durationMinutes = 10, isEnabled = true)
        assertEquals(21, sched.hour)
        assertEquals(10, sched.durationMinutes)
        assertTrue(sched.isEnabled)
    }

    @Test
    fun firebaseRepository_flowExtensions_instantiateValidFlows() {
        assertNotNull(com.example.authapp.data.FirebaseRepository.listenToChildUsersFlow())
        assertNotNull(com.example.authapp.data.FirebaseRepository.listenToDeviceHealthFlow("child_123"))
        assertNotNull(com.example.authapp.data.FirebaseRepository.listenToSecurityAlertsFlow("child_123"))
        assertNotNull(com.example.authapp.data.FirebaseRepository.listenToCallLogsFlow("child_123"))
    }
}
