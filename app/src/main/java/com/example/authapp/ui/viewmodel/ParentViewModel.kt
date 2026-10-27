package com.example.authapp.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authapp.data.*
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ParentViewModel : ViewModel() {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        FirebaseCrashlytics.getInstance().recordException(throwable)
        _uiState.update { it.copy(userFeedbackMessage = "Network operation failed: ${throwable.localizedMessage}") }
    }

    private val _uiState = MutableStateFlow(ParentUiState())
    val uiState: StateFlow<ParentUiState> = _uiState.asStateFlow()

    private val listenersHandler = ParentChildListenersHandler(_uiState)
    private val actionsHandler = ParentRemoteActionsHandler(_uiState)

    private var recordingTimerJob: Job? = null
    private var snapshotTimeoutJob: Job? = null

    // --- Listener Management Delegates ---
    fun loadChildUsers(context: Context? = null) = listenersHandler.loadChildUsers(context, viewModelScope)
    fun attachBasicChildListeners(childUid: String) = listenersHandler.attachBasicChildListeners(childUid)
    fun detachChildActivityListeners(childUid: String) = listenersHandler.detachChildActivityListeners(childUid)
    fun attachChildActivityListeners(childUid: String) = listenersHandler.attachChildActivityListeners(childUid)

    // --- Stream & WebRTC Controls ---
    fun startStream(child: User, streamType: String) {
        val typeNormalized = if (streamType.contains("video", ignoreCase = true)) "Video" else "Audio"
        try {
            FirebaseRepository.requestStream(
                childId = child.uid,
                streamType = streamType.lowercase(),
                onComplete = { sessionId: String ->
                    _uiState.update {
                        it.copy(
                            activeSessionId = sessionId,
                            activeStreamType = typeNormalized,
                            activeChildId = child.uid,
                            activeChildName = child.name.ifEmpty { "Child Device" },
                            streamStatusText = "Connecting to child device...",
                            isFrontCamera = false
                        )
                    }
                },
                onError = { err ->
                    _uiState.update {
                        it.copy(
                            streamStatusText = "Request failed: ${err.localizedMessage}",
                            userFeedbackMessage = "Stream request failed: ${err.localizedMessage ?: "Device error"}"
                        )
                    }
                }
            )
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    activeStreamType = typeNormalized,
                    activeChildId = child.uid,
                    activeChildName = child.name.ifEmpty { "Child Device" },
                    streamStatusText = "Stream request failed: ${e.localizedMessage}",
                    userFeedbackMessage = "Stream request error: ${e.localizedMessage}"
                )
            }
        }
    }

    fun clearUserFeedbackMessage() {
        _uiState.update { it.copy(userFeedbackMessage = null) }
    }

    fun updateStreamStatus(status: String) {
        _uiState.update { it.copy(streamStatusText = status) }
    }

    fun toggleCameraFacing() {
        val current = _uiState.value
        val sessionId = current.activeSessionId ?: return
        val newFacing = !current.isFrontCamera
        try {
            FirebaseRepository.toggleCameraFacing(sessionId, newFacing)
        } catch (_: Exception) {}
        _uiState.update { it.copy(isFrontCamera = newFacing) }
    }

    fun setAudioSensitivity(sensitivity: Float) {
        _uiState.update { it.copy(audioSensitivity = sensitivity) }
    }

    fun onRecordingStarted() {
        _uiState.update { it.copy(isRecording = true, recordingDurationSeconds = 0) }
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _uiState.update { it.copy(recordingDurationSeconds = it.recordingDurationSeconds + 1) }
            }
        }
    }

    fun onRecordingStopped() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        _uiState.update { it.copy(isRecording = false) }
    }

    fun clearActiveSessionId() {
        _uiState.update { it.copy(activeSessionId = null) }
    }

    fun stopStream() {
        val current = _uiState.value
        val childId = current.activeChildId
        val sessionId = current.activeSessionId
        if (childId != null) {
            try {
                FirebaseRepository.stopStream(childId, sessionId)
            } catch (_: Exception) {}
        }
        _uiState.update {
            it.copy(
                activeSessionId = null,
                activeChildId = null,
                activeChildName = null,
                activeStreamType = null,
                streamStatusText = "Stream disconnected",
                isRecording = false
            )
        }
    }

    // --- Location Controls ---
    fun openLocationDialog(child: User) {
        _uiState.update { it.copy(locationDialogChild = child, isRefreshingLocation = false) }
        try {
            FirebaseRepository.listenToChildLocation(child.uid) { loc ->
                if (loc != null) updateChildLocation(child.uid, loc)
            }
            FirebaseRepository.requestChildLocation(child.uid)
        } catch (_: Exception) {}
    }

    fun updateChildLocation(childId: String, location: UserLocation) {
        if (_uiState.value.locationDialogChild?.uid == childId) {
            _uiState.update { it.copy(childLocation = location, isRefreshingLocation = false) }
        }
    }

    fun requestLocationRefresh(childId: String) {
        _uiState.update { it.copy(isRefreshingLocation = true) }
        try {
            FirebaseRepository.requestChildLocation(childId)
        } catch (_: Exception) {}
    }

    fun closeLocationDialog() {
        _uiState.update { it.copy(locationDialogChild = null, childLocation = null, isRefreshingLocation = false) }
    }

    // --- Cloud Recordings & Playback ---
    fun setCurrentlyPlayingRecId(recId: String?) {
        _uiState.update { it.copy(currentlyPlayingRecId = recId) }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun loadAllRecordings() {
        val children = _uiState.value.childUsers
        if (children.isNotEmpty()) {
            listenersHandler.loadParentRecordings(children)
        } else {
            _uiState.update { it.copy(isLoadingRecordings = true) }
            try {
                FirebaseRepository.listenToAllRecordings { list ->
                    _uiState.update { it.copy(allRecordings = list, isLoadingRecordings = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingRecordings = false) }
            }
        }
    }

    fun setRecordingFilter(filter: String) {
        _uiState.update { it.copy(recordingFilter = filter) }
    }

    // --- Dialogs Management ---
    fun openActivityDialog(child: User, initialTab: Int = 0) {
        _uiState.update { it.copy(activeActivityDialogChild = child, initialActivityTab = initialTab) }
        attachChildActivityListeners(child.uid)
    }

    fun closeActivityDialog() {
        val activeUid = _uiState.value.activeActivityDialogChild?.uid
        if (activeUid != null) detachChildActivityListeners(activeUid)
        _uiState.update { it.copy(activeActivityDialogChild = null) }
    }

    fun openAlertsDialog(child: User) {
        _uiState.update { it.copy(activeAlertsDialogChild = child) }
        attachChildActivityListeners(child.uid)
    }

    fun closeAlertsDialog() {
        val activeUid = _uiState.value.activeAlertsDialogChild?.uid
        if (activeUid != null && _uiState.value.activeActivityDialogChild?.uid != activeUid) {
            detachChildActivityListeners(activeUid)
        }
        _uiState.update { it.copy(activeAlertsDialogChild = null) }
    }

    fun openScheduleDialog(child: User) {
        _uiState.update { it.copy(activeScheduleDialogChild = child) }
    }

    fun closeScheduleDialog() {
        _uiState.update { it.copy(activeScheduleDialogChild = null) }
    }

    fun openSnapshotDialog(child: User) {
        _uiState.update { it.copy(activeSnapshotDialogChild = child, snapshotStatusMessage = null) }
        attachChildActivityListeners(child.uid)
    }

    fun closeSnapshotDialog() {
        snapshotTimeoutJob?.cancel()
        val activeUid = _uiState.value.activeSnapshotDialogChild?.uid
        if (activeUid != null && _uiState.value.activeActivityDialogChild?.uid != activeUid) {
            detachChildActivityListeners(activeUid)
        }
        _uiState.update { it.copy(activeSnapshotDialogChild = null, isSnapshotCapturing = false) }
    }

    fun requestSnapshot(childId: String, cameraFacing: String = "back") {
        if (childId.isEmpty()) return
        _uiState.update { it.copy(isSnapshotCapturing = true, snapshotStatusMessage = "Sending Snapshot Request...") }
        snapshotTimeoutJob?.cancel()
        snapshotTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(20000)
            if (_uiState.value.isSnapshotCapturing) {
                _uiState.update { it.copy(isSnapshotCapturing = false, snapshotStatusMessage = "Snapshot Timeout. Target phone may be offline.") }
            }
        }
        try {
            FirebaseControlRepository.requestSnapshot(childId, cameraFacing)
        } catch (e: Exception) {
            snapshotTimeoutJob?.cancel()
            _uiState.update { it.copy(isSnapshotCapturing = false, snapshotStatusMessage = "Request Failed: ${e.localizedMessage}") }
        }
    }

    fun saveRecordingSchedule(childId: String, schedule: RecordingSchedule) {
        try {
            FirebaseRepository.saveRecordingSchedule(childId, schedule)
        } catch (_: Exception) {}
    }

    fun deleteRecordingSchedule(childId: String, scheduleId: String) {
        try {
            FirebaseRepository.deleteRecordingSchedule(childId, scheduleId)
        } catch (_: Exception) {}
    }

    // --- Remote Hardware & Safety Control Delegates ---
    fun setSelectedChildForControls(child: User) = actionsHandler.setSelectedChildForControls(child)
    fun toggleAppBlock(childId: String, packageName: String, currentBlocked: Boolean) = actionsHandler.toggleAppBlock(childId, packageName, currentBlocked)
    fun toggleStudyMode(childId: String, currentActive: Boolean, durationMinutes: Int = 0) = actionsHandler.toggleStudyMode(childId, currentActive, durationMinutes)
    fun toggleTorch(childId: String, currentActive: Boolean) = actionsHandler.toggleTorch(childId, currentActive)
    fun triggerSiren(childId: String, currentActive: Boolean) = actionsHandler.triggerSiren(childId, currentActive)
    fun enableRemoteInternet(childId: String) = actionsHandler.enableRemoteInternet(childId)
    fun setUninstallPin(childId: String, pin: String) = actionsHandler.setUninstallPin(childId, pin)
    fun launchRemoteApp(childId: String, packageName: String) = actionsHandler.launchRemoteApp(childId, packageName)
    fun closeRemoteApp(childId: String, packageName: String = "") = actionsHandler.closeRemoteApp(childId, packageName)
    fun saveGeofence(childId: String, zone: GeofenceZone) = actionsHandler.saveGeofence(childId, zone)
    fun unpairChild(childUid: String, onComplete: (Boolean, String?) -> Unit) = actionsHandler.unpairChild(childUid, onComplete)
    fun cleanCloudStorage(childId: String, retentionDays: Int = 7) = actionsHandler.cleanCloudStorage(childId, retentionDays)

    override fun onCleared() {
        super.onCleared()
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        snapshotTimeoutJob?.cancel()
        snapshotTimeoutJob = null
        listenersHandler.cleanupAllListeners()
    }
}
