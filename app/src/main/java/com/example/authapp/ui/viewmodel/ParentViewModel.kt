package com.example.authapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.authapp.data.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ParentViewModel : ViewModel() {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(throwable)
        _uiState.update { it.copy(userFeedbackMessage = "Network operation failed: ${throwable.localizedMessage}") }
    }

    private val _uiState = MutableStateFlow(ParentUiState())
    val uiState: StateFlow<ParentUiState> = _uiState.asStateFlow()

    private var recordingTimerJob: Job? = null
    private val healthListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val alertsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val callLogsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val notificationsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val schedulesListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val snapshotsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val snapshotStatusListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val appUsageListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val parentControlsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val smsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val geofencesListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val locationHistoryListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val webHistoryListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val networkHistoryListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val simInfoListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val packageEventsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val whatsAppListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val commandsListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val childRecordingsMap = mutableMapOf<String, List<RecordingSession>>()
    private val firestoreRegistrations = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
    private var childUsersListener: com.google.firebase.database.ValueEventListener? = null
    private var recordingsListener: com.google.firebase.database.ValueEventListener? = null

    fun loadChildUsers(context: android.content.Context? = null) {
        if (context != null) {
            val cached = FirebaseRepository.getCachedChildUsers(context)
            if (cached.isNotEmpty()) {
                _uiState.update { current ->
                    val defaultSelected = current.selectedChildForControls ?: cached.firstOrNull()
                    current.copy(
                        childUsers = cached,
                        isLoadingChildren = false,
                        selectedChildForControls = defaultSelected
                    )
                }
            }
        }

        // Safety fallback timer: Ensure infinite spinner is killed after 3.5 seconds
        viewModelScope.launch {
            delay(3500)
            if (_uiState.value.isLoadingChildren) {
                _uiState.update { it.copy(isLoadingChildren = false) }
            }
        }

        if (childUsersListener != null) return
        try {
            childUsersListener = FirebaseRepository.listenToChildUsers { list ->
                if (context != null && list.isNotEmpty()) {
                    FirebaseRepository.saveCachedChildUsers(context, list)
                }
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
                            val fsAlerts = FirebaseRepository.listenToChildSecurityAlertsFirestore(child.uid) { alerts ->
                                if (alerts.isNotEmpty()) {
                                    _uiState.update { current ->
                                        val updated = current.securityAlertsMap.toMutableMap()
                                        updated[child.uid] = alerts
                                        current.copy(securityAlertsMap = updated)
                                    }
                                }
                            }
                            firestoreRegistrations.add(fsAlerts)
                        }
                        if (!callLogsListeners.containsKey(child.uid)) {
                            callLogsListeners[child.uid] = FirebaseRepository.listenToCallLogs(child.uid) { logs ->
                                _uiState.update { current ->
                                    val updated = current.callLogsMap.toMutableMap()
                                    updated[child.uid] = logs
                                    current.copy(callLogsMap = updated)
                                }
                            }
                            val fsCalls = FirebaseRepository.listenToChildCallLogsFirestore(child.uid) { logs ->
                                if (logs.isNotEmpty()) {
                                    _uiState.update { current ->
                                        val updated = current.callLogsMap.toMutableMap()
                                        updated[child.uid] = logs
                                        current.copy(callLogsMap = updated)
                                    }
                                }
                            }
                            firestoreRegistrations.add(fsCalls)
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
                                    val oldSize = current.snapshotsMap[child.uid]?.size ?: 0
                                    updated[child.uid] = snapshots
                                    val msg = if (snapshots.size > oldSize && current.isSnapshotCapturing) {
                                        "New photo captured successfully!"
                                    } else current.snapshotStatusMessage
                                    current.copy(
                                        snapshotsMap = updated,
                                        snapshotStatusMessage = msg,
                                        isSnapshotCapturing = if (snapshots.size > oldSize) false else current.isSnapshotCapturing
                                    )
                                }
                            }
                        }
                        if (!snapshotStatusListeners.containsKey(child.uid)) {
                            snapshotStatusListeners[child.uid] = FirebaseRepository.listenToSnapshotRequestStatus(child.uid) { status, error ->
                                if (status == "FAILED") {
                                    snapshotTimeoutJob?.cancel()
                                    _uiState.update { it.copy(
                                        isSnapshotCapturing = false,
                                        snapshotStatusMessage = "Snapshot failed: ${error ?: "Camera error or device busy"}"
                                    ) }
                                } else if (status == "PROCESSING") {
                                    _uiState.update { it.copy(
                                        snapshotStatusMessage = "Child device is capturing photo..."
                                    ) }
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
                            val fsSms = FirebaseRepository.listenToChildSmsLogsFirestore(child.uid) { smsList ->
                                if (smsList.isNotEmpty()) {
                                    _uiState.update { current ->
                                        val updated = current.smsLogsMap.toMutableMap()
                                        updated[child.uid] = smsList
                                        current.copy(smsLogsMap = updated)
                                    }
                                }
                            }
                            firestoreRegistrations.add(fsSms)
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
                            val fsWeb = FirebaseRepository.listenToChildWebHistoryFirestore(child.uid) { webList ->
                                if (webList.isNotEmpty()) {
                                    _uiState.update { current ->
                                        val updated = current.webHistoryMap.toMutableMap()
                                        updated[child.uid] = webList
                                        current.copy(webHistoryMap = updated)
                                    }
                                }
                            }
                            firestoreRegistrations.add(fsWeb)
                        }
                        if (!networkHistoryListeners.containsKey(child.uid)) {
                            networkHistoryListeners[child.uid] = FirebaseRepository.listenToNetworkHistory(child.uid) { netList ->
                                _uiState.update { current ->
                                    val updated = current.networkHistoryMap.toMutableMap()
                                    updated[child.uid] = netList
                                    current.copy(networkHistoryMap = updated)
                                }
                            }
                            val fsNet = FirebaseRepository.listenToChildNetworkHistoryFirestore(child.uid) { netList ->
                                if (netList.isNotEmpty()) {
                                    _uiState.update { current ->
                                        val updated = current.networkHistoryMap.toMutableMap()
                                        updated[child.uid] = netList
                                        current.copy(networkHistoryMap = updated)
                                    }
                                }
                            }
                            firestoreRegistrations.add(fsNet)
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
                            val fsPkg = FirebaseRepository.listenToChildPackageEventsFirestore(child.uid) { events ->
                                if (events.isNotEmpty()) {
                                    _uiState.update { current ->
                                        val updated = current.packageEventsMap.toMutableMap()
                                        updated[child.uid] = events
                                        current.copy(packageEventsMap = updated)
                                    }
                                }
                            }
                            firestoreRegistrations.add(fsPkg)
                        }
                        if (!whatsAppListeners.containsKey(child.uid)) {
                            whatsAppListeners[child.uid] = FirebaseRepository.listenToWhatsAppLogs(child.uid) { waList ->
                                _uiState.update { current ->
                                    val updated = current.whatsAppLogsMap.toMutableMap()
                                    updated[child.uid] = waList
                                    current.copy(whatsAppLogsMap = updated)
                                }
                            }
                        }
                        if (!commandsListeners.containsKey(child.uid)) {
                            val cmdListener = FirebaseRepository.listenToRemoteCommands(child.uid) { command, value ->
                                val isActive = (value == true || value == "true")
                                _uiState.update { current ->
                                    when (command) {
                                        "TORCH" -> {
                                            val updated = current.isTorchActiveMap.toMutableMap()
                                            updated[child.uid] = isActive
                                            current.copy(isTorchActiveMap = updated)
                                        }
                                        "SIREN" -> {
                                            val updated = current.isSirenActiveMap.toMutableMap()
                                            updated[child.uid] = isActive
                                            current.copy(isSirenActiveMap = updated)
                                        }
                                        else -> current
                                    }
                                }
                            }
                            commandsListeners[child.uid] = cmdListener
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
        recordingTimerJob = viewModelScope.launch(coroutineExceptionHandler) {
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

    fun clearActiveSessionId() {
        _uiState.update { it.copy(activeSessionId = null) }
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
        if (tabIndex == 2) {
            loadAllRecordings()
        }
    }

    fun loadAllRecordings() {
        val children = _uiState.value.childUsers
        if (children.isEmpty()) {
            _uiState.update { it.copy(allRecordings = emptyList(), isLoadingRecordings = false) }
            return
        }
        _uiState.update { it.copy(isLoadingRecordings = true) }
        var pendingCount = children.size
        children.forEach { child ->
            if (child.uid.isNotEmpty()) {
                FirebaseRepository.listenToRecordings(child.uid) { list ->
                    synchronized(childRecordingsMap) {
                        childRecordingsMap[child.uid] = list
                        val merged = childRecordingsMap.values.flatten().sortedByDescending { it.startTime }
                        _uiState.update { it.copy(allRecordings = merged, isLoadingRecordings = false) }
                    }
                }
            } else {
                pendingCount--
                if (pendingCount <= 0) {
                    _uiState.update { it.copy(isLoadingRecordings = false) }
                }
            }
        }
    }

    fun setRecordingFilter(filter: String) {
        _uiState.update { it.copy(recordingFilter = filter) }
    }

    // Phase 1 & 2 Dialog Controls and Actions
    fun openActivityDialog(child: User, initialTab: Int = 0) {
        _uiState.update { it.copy(activeActivityDialogChild = child, initialActivityTab = initialTab) }
    }

    fun closeActivityDialog() {
        _uiState.update { it.copy(activeActivityDialogChild = null, initialActivityTab = 0) }
    }

    fun checkForUpdatesManually(context: Context) {
        FirebaseRepository.listenToAppUpdate { updateInfo ->
            val curCode = com.example.authapp.updater.UpdateManager.getCurrentVersionCode(context)
            if (updateInfo != null && updateInfo.versionCode > curCode) {
                _uiState.update { current ->
                    current.copy(userFeedbackMessage = "New update available: ${updateInfo.versionName}! Starting update...")
                }
            } else {
                _uiState.update { current ->
                    current.copy(userFeedbackMessage = "App is up to date (Current version code: $curCode).")
                }
            }
        }
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

    private var snapshotTimeoutJob: kotlinx.coroutines.Job? = null

    fun openSnapshotDialog(child: User) {
        _uiState.update { it.copy(activeSnapshotDialogChild = child, snapshotStatusMessage = null, isSnapshotCapturing = false) }
    }

    fun closeSnapshotDialog() {
        snapshotTimeoutJob?.cancel()
        _uiState.update { it.copy(activeSnapshotDialogChild = null, snapshotStatusMessage = null, isSnapshotCapturing = false) }
    }

    fun requestSnapshot(childId: String, cameraFacing: String = "back") {
        if (_uiState.value.isSnapshotCapturing) return // Debounce: prevent duplicate parallel requests
        _uiState.update { it.copy(
            snapshotStatusMessage = "Requesting $cameraFacing photo from child device...",
            isSnapshotCapturing = true
        ) }
        snapshotTimeoutJob?.cancel()
        snapshotTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(18000) // 18 seconds timeout
            if (_uiState.value.isSnapshotCapturing) {
                _uiState.update { it.copy(
                    isSnapshotCapturing = false,
                    snapshotStatusMessage = "Child device did not respond within 18s. Verify child phone is online."
                ) }
            }
        }
        try {
            FirebaseRepository.requestSnapshot(childId, cameraFacing)
        } catch (e: Exception) {
            snapshotTimeoutJob?.cancel()
            _uiState.update { it.copy(
                isSnapshotCapturing = false,
                snapshotStatusMessage = "Request Failed: ${e.localizedMessage}"
            ) }
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
        snapshotStatusListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("streams/$childId/snapshotRequest", listener)
        }
        snapshotStatusListeners.clear()
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
        webHistoryListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("web_history/$childId", listener)
        }
        webHistoryListeners.clear()
        networkHistoryListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("network_history/$childId", listener)
        }
        networkHistoryListeners.clear()
        simInfoListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("sim_info/$childId", listener)
        }
        simInfoListeners.clear()
        packageEventsListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("package_events/$childId", listener)
        }
        packageEventsListeners.clear()
        whatsAppListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("whatsapp_logs/$childId", listener)
        }
        whatsAppListeners.clear()
        commandsListeners.forEach { (childId, listener) ->
            FirebaseRepository.removeValueListener("commands/$childId", listener)
        }
        commandsListeners.clear()
        firestoreRegistrations.forEach { it.remove() }
        firestoreRegistrations.clear()
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
