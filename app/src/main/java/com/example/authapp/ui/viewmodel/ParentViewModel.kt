package com.example.authapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.User
import com.example.authapp.data.UserLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ParentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ParentUiState())
    val uiState: StateFlow<ParentUiState> = _uiState.asStateFlow()

    private var recordingTimerJob: Job? = null
    private val healthListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()

    fun loadChildUsers() {
        try {
            FirebaseRepository.listenToChildUsers { list ->
                _uiState.update { it.copy(childUsers = list, isLoadingChildren = false) }
                list.forEach { child ->
                    if (child.uid.isNotEmpty() && !healthListeners.containsKey(child.uid)) {
                        val listener = FirebaseRepository.listenToDeviceHealth(child.uid) { health ->
                            if (health != null) {
                                _uiState.update { current ->
                                    val updated = current.deviceHealthMap.toMutableMap()
                                    updated[child.uid] = health
                                    current.copy(deviceHealthMap = updated)
                                }
                            }
                        }
                        healthListeners[child.uid] = listener
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingChildren = false) }
        }
    }

    fun startStream(child: User, streamType: String) {
        val typeNormalized = if (streamType.equals("video", ignoreCase = true)) "Video" else "Audio"
        try {
            FirebaseRepository.requestStream(child.uid, streamType.lowercase()) { sessionId ->
                _uiState.update {
                    it.copy(
                        activeSessionId = sessionId,
                        activeStreamType = typeNormalized,
                        activeChildId = child.uid,
                        activeChildName = child.name.ifEmpty { "Child Device" },
                        streamStatusText = "Connecting to child device...",
                        isFrontCamera = true
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    activeStreamType = typeNormalized,
                    activeChildId = child.uid,
                    activeChildName = child.name.ifEmpty { "Child Device" },
                    streamStatusText = "Stream request failed"
                )
            }
        }
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
        } catch (e: Exception) {}
        _uiState.update { it.copy(isFrontCamera = newFacing) }
    }

    fun setAudioSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(10f, 100f)
        _uiState.update { it.copy(audioSensitivity = clamped) }
    }

    fun onRecordingStarted() {
        _uiState.update { it.copy(isRecording = true, recordingDurationSeconds = 0L) }
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update { it.copy(recordingDurationSeconds = it.recordingDurationSeconds + 1) }
            }
        }
    }

    fun onRecordingStopped() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        _uiState.update { it.copy(isRecording = false, recordingDurationSeconds = 0L) }
    }

    fun stopStream() {
        val current = _uiState.value
        val sessionId = current.activeSessionId
        val childId = current.activeChildId
        if (childId != null && sessionId != null) {
            try {
                FirebaseRepository.stopStream(childId, sessionId)
            } catch (e: Exception) {}
        }
        onRecordingStopped()
        _uiState.update {
            it.copy(
                activeSessionId = null,
                activeStreamType = null,
                activeChildId = null,
                activeChildName = null,
                streamStatusText = "Connecting to child device..."
            )
        }
    }

    fun openLocationDialog(child: User) {
        _uiState.update {
            it.copy(
                locationDialogChild = child,
                childLocation = null,
                isRefreshingLocation = false
            )
        }
    }

    fun updateChildLocation(location: UserLocation?) {
        _uiState.update { it.copy(childLocation = location, isRefreshingLocation = false) }
    }

    fun requestLocationRefresh(childId: String) {
        _uiState.update { it.copy(isRefreshingLocation = true) }
        try {
            FirebaseRepository.requestChildLocation(childId)
        } catch (e: Exception) {
            _uiState.update { it.copy(isRefreshingLocation = false) }
        }
    }

    fun closeLocationDialog() {
        _uiState.update {
            it.copy(
                locationDialogChild = null,
                childLocation = null,
                isRefreshingLocation = false
            )
        }
    }

    fun setCurrentlyPlayingRecId(recId: String?) {
        _uiState.update { it.copy(currentlyPlayingRecId = recId) }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
        if (tabIndex == 1) {
            loadAllRecordings()
        }
    }

    fun loadAllRecordings() {
        _uiState.update { it.copy(isLoadingRecordings = true) }
        try {
            FirebaseRepository.listenToAllRecordings { list ->
                _uiState.update { it.copy(allRecordings = list, isLoadingRecordings = false) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingRecordings = false) }
        }
    }

    fun setRecordingFilter(filter: String) {
        _uiState.update { it.copy(recordingFilter = filter) }
    }

    override fun onCleared() {
        super.onCleared()
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        healthListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("device_health/$childId", listener)
        }
        healthListeners.clear()
    }
}
