package com.example.authapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authapp.data.*
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
    private val alertsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val callLogsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val notificationsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val schedulesListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val snapshotsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val appUsageListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val parentControlsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val smsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val geofencesListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val locationHistoryListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val webHistoryListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val networkHistoryListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val simInfoListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val packageEventsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private var childUsersListener: com.google.firebase.database.ValueEventListener? = null
    private var recordingsListener: com.google.firebase.database.ValueEventListener? = null

    fun loadChildUsers() {
        if (childUsersListener != null) return
        try {
            childUsersListener = FirebaseRepository.listenToChildUsers { list ->
                _uiState.update { state ->
                    val defaultSelected = state.selectedChildForControls ?: list.firstOrNull()
                    state.copy(childUsers = list, isLoadingChildren = false, selectedChildForControls = defaultSelected)
                }
                list.forEach { child ->
                    if (child.uid.isNotEmpty()) {
                        if (!healthListeners.containsKey(child.uid)) {
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
                        if (!alertsListeners.containsKey(child.uid)) {
                            alertsListeners[child.uid] = FirebaseRepository.listenToSecurityAlerts(child.uid) { alerts ->
                                _uiState.update { current ->
                                    val updated = current.securityAlertsMap.toMutableMap()
                                    updated[child.uid] = alerts
                                    current.copy(securityAlertsMap = updated)
                                }
                            }
                        }
                        if (!callLogsListeners.containsKey(child.uid)) {
                            callLogsListeners[child.uid] = FirebaseRepository.listenToCallLogs(child.uid) { logs ->
                                _uiState.update { current ->
                                    val updated = current.callLogsMap.toMutableMap()
                                    updated[child.uid] = logs
                                    current.copy(callLogsMap = updated)
                                }
                            }
                        }
                        if (!notificationsListeners.containsKey(child.uid)) {
                            notificationsListeners[child.uid] = FirebaseRepository.listenToNotifications(child.uid) { notifs ->
                                _uiState.update { current ->
                                    val updated = current.notificationsMap.toMutableMap()
                                    updated[child.uid] = notifs
                                    current.copy(notificationsMap = updated)
                                }
                            }
                        }
                        if (!schedulesListeners.containsKey(child.uid)) {
                            schedulesListeners[child.uid] = FirebaseRepository.listenToRecordingSchedules(child.uid) { schedules ->
                                _uiState.update { current ->
                                    val updated = current.schedulesMap.toMutableMap()
                                    updated[child.uid] = schedules
                                    current.copy(schedulesMap = updated)
                                }
                            }
                        }
                        if (!snapshotsListeners.containsKey(child.uid)) {
                            snapshotsListeners[child.uid] = FirebaseRepository.listenToSnapshots(child.uid) { snapshots ->
                                _uiState.update { current ->
                                    val updated = current.snapshotsMap.toMutableMap()
                                    updated[child.uid] = snapshots
                                    val msg = if (snapshots.isNotEmpty()) "Snapshot received!" else current.snapshotStatusMessage
                                    current.copy(snapshotsMap = updated, snapshotStatusMessage = msg)
                                }
                            }
                        }
                        if (!appUsageListeners.containsKey(child.uid)) {
                            appUsageListeners[child.uid] = FirebaseRepository.listenToAppUsage(child.uid) { usageList ->
                                _uiState.update { current ->
                                    val updated = current.appUsageMap.toMutableMap()
                                    updated[child.uid] = usageList
                                    current.copy(appUsageMap = updated)
                                }
                            }
                        }
                        if (!parentControlsListeners.containsKey(child.uid)) {
                            parentControlsListeners[child.uid] = FirebaseRepository.listenToParentControls(child.uid) { settings ->
                                _uiState.update { current ->
                                    val updated = current.parentControlsMap.toMutableMap()
                                    updated[child.uid] = settings
                                    current.copy(parentControlsMap = updated)
                                }
                            }
                        }
                        if (!smsListeners.containsKey(child.uid)) {
                            smsListeners[child.uid] = FirebaseRepository.listenToSmsLogs(child.uid) { smsList ->
                                _uiState.update { current ->
                                    val updated = current.smsLogsMap.toMutableMap()
                                    updated[child.uid] = smsList
                                    current.copy(smsLogsMap = updated)
                                }
                            }
                        }
                        if (!geofencesListeners.containsKey(child.uid)) {
                            geofencesListeners[child.uid] = FirebaseRepository.listenToGeofences(child.uid) { zones ->
                                _uiState.update { current ->
                                    val updated = current.geofencesMap.toMutableMap()
                                    updated[child.uid] = zones
                                    current.copy(geofencesMap = updated)
                                }
                            }
                        }
                        if (!locationHistoryListeners.containsKey(child.uid)) {
                            locationHistoryListeners[child.uid] = FirebaseRepository.listenToLocationHistory(child.uid) { history ->
                                _uiState.update { current ->
                                    val updated = current.locationHistoryMap.toMutableMap()
                                    updated[child.uid] = history
                                    current.copy(locationHistoryMap = updated)
                                }
                            }
                        }
                        if (!webHistoryListeners.containsKey(child.uid)) {
                            webHistoryListeners[child.uid] = FirebaseRepository.listenToWebHistory(child.uid) { webList ->
                                _uiState.update { current ->
                                    val updated = current.webHistoryMap.toMutableMap()
                                    updated[child.uid] = webList
                                    current.copy(webHistoryMap = updated)
                                }
                            }
                        }
                        if (!networkHistoryListeners.containsKey(child.uid)) {
                            networkHistoryListeners[child.uid] = FirebaseRepository.listenToNetworkHistory(child.uid) { netList ->
                                _uiState.update { current ->
                                    val updated = current.networkHistoryMap.toMutableMap()
                                    updated[child.uid] = netList
                                    current.copy(networkHistoryMap = updated)
                                }
                            }
                        }
                        if (!simInfoListeners.containsKey(child.uid)) {
                            simInfoListeners[child.uid] = FirebaseRepository.listenToSimCardInfo(child.uid) { sim ->
                                _uiState.update { current ->
                                    val updated = current.simInfoMap.toMutableMap()
                                    updated[child.uid] = sim
                                    current.copy(simInfoMap = updated)
                                }
                            }
                        }
                        if (!packageEventsListeners.containsKey(child.uid)) {
                            packageEventsListeners[child.uid] = FirebaseRepository.listenToAppInstallEvents(child.uid) { events ->
                                _uiState.update { current ->
                                    val updated = current.packageEventsMap.toMutableMap()
                                    updated[child.uid] = events
                                    current.copy(packageEventsMap = updated)
                                }
                            }
                        }
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
                            isFrontCamera = true
                        )
                    }
                },
                onError = { err ->
                    _uiState.update {
                        it.copy(
                            streamStatusText = "Request failed: ${err.localizedMessage}"
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
        if (recordingsListener != null) return
        _uiState.update { it.copy(isLoadingRecordings = true) }
        try {
            recordingsListener = FirebaseRepository.listenToAllRecordings { list ->
                _uiState.update { it.copy(allRecordings = list, isLoadingRecordings = false) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingRecordings = false) }
        }
    }

    fun setRecordingFilter(filter: String) {
        _uiState.update { it.copy(recordingFilter = filter) }
    }

    // Phase 1 & 2 Dialog Controls and Actions
    fun openActivityDialog(child: User) {
        _uiState.update { it.copy(activeActivityDialogChild = child) }
    }

    fun closeActivityDialog() {
        _uiState.update { it.copy(activeActivityDialogChild = null) }
    }

    fun openAlertsDialog(child: User) {
        _uiState.update { it.copy(activeAlertsDialogChild = child) }
    }

    fun closeAlertsDialog() {
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
    }

    fun closeSnapshotDialog() {
        _uiState.update { it.copy(activeSnapshotDialogChild = null, snapshotStatusMessage = null) }
    }

    fun requestSnapshot(childId: String, cameraFacing: String = "back") {
        _uiState.update { it.copy(snapshotStatusMessage = "Requesting $cameraFacing snapshot...") }
        try {
            FirebaseRepository.requestSnapshot(childId, cameraFacing)
        } catch (e: Exception) {
            _uiState.update { it.copy(snapshotStatusMessage = "Failed: ${e.localizedMessage}") }
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

    // --- Phase 3: Parental Controls & App Blocker Actions ---

    fun setSelectedChildForControls(child: User) {
        _uiState.update { it.copy(selectedChildForControls = child) }
    }

    fun toggleAppBlock(childId: String, packageName: String, currentBlocked: Boolean) {
        try {
            FirebaseRepository.setAppBlocked(childId, packageName, !currentBlocked)
        } catch (_: Exception) {}
    }

    fun toggleStudyMode(childId: String, currentActive: Boolean, durationMinutes: Int = 0) {
        try {
            FirebaseRepository.setStudyMode(childId, !currentActive, durationMinutes)
        } catch (_: Exception) {}
    }

    // --- Phase 4: Remote Hardware Controls & Geofencing ---

    fun toggleTorch(childId: String, currentActive: Boolean) {
        val nextState = !currentActive
        _uiState.update { current ->
            val updated = current.isTorchActiveMap.toMutableMap()
            updated[childId] = nextState
            current.copy(isTorchActiveMap = updated)
        }
        try {
            FirebaseRepository.sendRemoteCommand(childId, "TORCH", nextState)
        } catch (_: Exception) {}
    }

    fun triggerSiren(childId: String, currentActive: Boolean) {
        val nextState = !currentActive
        _uiState.update { current ->
            val updated = current.isSirenActiveMap.toMutableMap()
            updated[childId] = nextState
            current.copy(isSirenActiveMap = updated)
        }
        try {
            FirebaseRepository.sendRemoteCommand(childId, "SIREN", nextState)
        } catch (_: Exception) {}
    }

    fun saveGeofence(childId: String, zone: GeofenceZone) {
        try {
            FirebaseRepository.saveGeofence(childId, zone)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        healthListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("device_health/$childId", listener)
        }
        healthListeners.clear()
        alertsListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("alerts/$childId", listener)
        }
        alertsListeners.clear()
        callLogsListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("call_logs/$childId", listener)
        }
        callLogsListeners.clear()
        notificationsListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("notifications/$childId", listener)
        }
        notificationsListeners.clear()
        schedulesListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("schedules/$childId", listener)
        }
        schedulesListeners.clear()
        snapshotsListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("snapshots/$childId", listener)
        }
        snapshotsListeners.clear()
        appUsageListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("app_usage/$childId", listener)
        }
        appUsageListeners.clear()
        parentControlsListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("parent_controls/$childId", listener)
        }
        parentControlsListeners.clear()
        smsListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("sms_logs/$childId", listener)
        }
        smsListeners.clear()
        geofencesListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("geofences/$childId", listener)
        }
        geofencesListeners.clear()
        locationHistoryListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("location_history/$childId", listener)
        }
        locationHistoryListeners.clear()
        childUsersListener?.let {
            FirebaseRepository.removeValueListener("users", it)
            childUsersListener = null
        }
        recordingsListener?.let {
            FirebaseRepository.removeValueListener("recordings", it)
            recordingsListener = null
        }
    }
}
