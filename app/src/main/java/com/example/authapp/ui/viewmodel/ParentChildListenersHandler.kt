package com.example.authapp.ui.viewmodel

import android.content.Context
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.RecordingSession
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.authapp.data.User

class ParentChildListenersHandler(
    private val uiStateFlow: MutableStateFlow<ParentUiState>
) {
    private val healthListeners = mutableMapOf<String, ValueEventListener>()
    private val alertsListeners = mutableMapOf<String, ValueEventListener>()
    private val schedulesListeners = mutableMapOf<String, ValueEventListener>()
    private val snapshotsListeners = mutableMapOf<String, ValueEventListener>()
    private val snapshotStatusListeners = mutableMapOf<String, ValueEventListener>()
    private val appUsageListeners = mutableMapOf<String, ValueEventListener>()
    private val parentControlsListeners = mutableMapOf<String, ValueEventListener>()
    private val geofencesListeners = mutableMapOf<String, ValueEventListener>()
    private val locationHistoryListeners = mutableMapOf<String, ValueEventListener>()
    private val networkHistoryListeners = mutableMapOf<String, ValueEventListener>()
    private val simInfoListeners = mutableMapOf<String, ValueEventListener>()
    private val packageEventsListeners = mutableMapOf<String, ValueEventListener>()
    private val commandsListeners = mutableMapOf<String, ValueEventListener>()
    private val youtubeListeners = mutableMapOf<String, ValueEventListener>()
    private val clipboardListeners = mutableMapOf<String, ValueEventListener>()
    private val recordingsListeners = mutableMapOf<String, ValueEventListener>()
    private val childRecordingsMap = mutableMapOf<String, List<RecordingSession>>()
    private val firestoreRegistrations = mutableListOf<ListenerRegistration>()
    private val firestoreAlertRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val firestoreNotificationRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val firestoreMediaGalleryRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val firestoreWhatsAppRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val firestoreCallLogsRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val firestoreSmsRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val firestoreWebHistoryRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val firestoreFileExplorerRegistrations = mutableMapOf<String, ListenerRegistration>()
    private val firestoreClipboardRegistrations = mutableMapOf<String, ListenerRegistration>()
    private var childUsersListener: ValueEventListener? = null
    private var recordingsListener: ValueEventListener? = null

    private fun <T> setupMapListener(
        listenersMap: MutableMap<String, ValueEventListener>,
        childUid: String,
        listenBlock: (String, (T) -> Unit) -> ValueEventListener,
        onDataReceived: (T) -> Unit
    ) {
        if (!listenersMap.containsKey(childUid)) {
            listenersMap[childUid] = listenBlock(childUid, onDataReceived)
        }
    }

    private inline fun setupFirestoreListener(
        registrationsMap: MutableMap<String, ListenerRegistration>,
        childUid: String,
        listenBlock: () -> ListenerRegistration
    ) {
        if (!registrationsMap.containsKey(childUid)) registrationsMap[childUid] = listenBlock()
    }

    fun loadChildUsers(context: Context? = null, scope: CoroutineScope) {
        if (context != null) {
            val cached = FirebaseRepository.getCachedChildUsers(context)
            if (cached.isNotEmpty()) {
                uiStateFlow.update { current ->
                    val defaultSelected = current.selectedChildForControls ?: cached.firstOrNull()
                    current.copy(
                        childUsers = cached,
                        isLoadingChildren = false,
                        selectedChildForControls = defaultSelected
                    )
                }
            }
        }

        scope.launch {
            delay(3500)
            if (uiStateFlow.value.isLoadingChildren) {
                uiStateFlow.update { it.copy(isLoadingChildren = false) }
            }
        }

        if (childUsersListener != null) return
        try {
            childUsersListener = FirebaseRepository.listenToChildUsers { list ->
                if (context != null && list.isNotEmpty()) {
                    FirebaseRepository.saveCachedChildUsers(context, list)
                }
                uiStateFlow.update { state ->
                    val defaultSelected = state.selectedChildForControls ?: list.firstOrNull()
                    state.copy(childUsers = list, isLoadingChildren = false, selectedChildForControls = defaultSelected)
                }
                val currentChildIds = list.mapNotNull { it.uid.takeIf(String::isNotEmpty) }.toSet()
                val staleChildIds = healthListeners.keys.union(alertsListeners.keys).union(appUsageListeners.keys)
                    .union(parentControlsListeners.keys).union(geofencesListeners.keys)
                    .union(locationHistoryListeners.keys).union(firestoreAlertRegistrations.keys)
                    .filterNot { it in currentChildIds }
                staleChildIds.forEach { removeChildListeners(it) }
                list.forEach { child ->
                    if (child.uid.isNotEmpty()) {
                        setupMapListener(healthListeners, child.uid, FirebaseRepository::listenToDeviceHealth) { health ->
                            if (health != null) uiStateFlow.update { it.copy(deviceHealthMap = it.deviceHealthMap + (child.uid to health)) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            uiStateFlow.update { it.copy(isLoadingChildren = false) }
        }
    }

    fun attachBasicChildListeners(childUid: String) {
        if (childUid.isEmpty()) return
        setupMapListener(parentControlsListeners, childUid, FirebaseRepository::listenToParentControls) { settings ->
            uiStateFlow.update { it.copy(parentControlsMap = it.parentControlsMap + (childUid to settings)) }
        }
        if (!commandsListeners.containsKey(childUid)) {
            val cmdListener = FirebaseRepository.listenToRemoteCommands(childUid) { command, value ->
                val isActive = (value == true || value == "true")
                uiStateFlow.update { current ->
                    when (command) {
                        "TORCH" -> current.copy(isTorchActiveMap = current.isTorchActiveMap + (childUid to isActive))
                        "SIREN" -> current.copy(isSirenActiveMap = current.isSirenActiveMap + (childUid to isActive))
                        else -> current
                    }
                }
            }
            commandsListeners[childUid] = cmdListener
        }
    }

    fun detachChildActivityListeners(childUid: String) {
        listOf(
            firestoreNotificationRegistrations, firestoreMediaGalleryRegistrations,
            firestoreWhatsAppRegistrations, firestoreCallLogsRegistrations, firestoreSmsRegistrations,
            firestoreWebHistoryRegistrations, firestoreFileExplorerRegistrations, firestoreClipboardRegistrations
        ).forEach { it.remove(childUid)?.remove() }
    }

    fun loadParentRecordings(children: List<User>) {
        if (children.isEmpty()) {
            uiStateFlow.update { it.copy(allRecordings = emptyList(), isLoadingRecordings = false) }
            return
        }
        uiStateFlow.update { it.copy(isLoadingRecordings = true) }
        children.forEach { child ->
            val childUid = child.uid
            if (childUid.isNotEmpty() && !recordingsListeners.containsKey(childUid)) {
                val listener = FirebaseRepository.listenToRecordings(childUid) { recList ->
                    childRecordingsMap[childUid] = recList
                    val merged = childRecordingsMap.values.flatten().sortedByDescending { it.startTime }
                    uiStateFlow.update { it.copy(allRecordings = merged, isLoadingRecordings = false) }
                }
                recordingsListeners[childUid] = listener
            }
        }
        val merged = childRecordingsMap.values.flatten().sortedByDescending { it.startTime }
        uiStateFlow.update { it.copy(allRecordings = merged, isLoadingRecordings = false) }
    }

    fun attachChildActivityListeners(childUid: String) {
        if (childUid.isEmpty()) return
        setupMapListener(alertsListeners, childUid, FirebaseRepository::listenToSecurityAlerts) { alerts ->
            uiStateFlow.update { it.copy(securityAlertsMap = it.securityAlertsMap + (childUid to alerts)) }
        }
        if (!firestoreAlertRegistrations.containsKey(childUid)) {
            firestoreAlertRegistrations[childUid] = FirebaseRepository.listenToChildSecurityAlertsFirestore(childUid) { alerts ->
                if (alerts.isNotEmpty()) uiStateFlow.update { it.copy(securityAlertsMap = it.securityAlertsMap + (childUid to alerts)) }
            }
        }
        setupMapListener(appUsageListeners, childUid, FirebaseRepository::listenToAppUsage) { usageList ->
            uiStateFlow.update { it.copy(appUsageMap = it.appUsageMap + (childUid to usageList)) }
        }
        setupMapListener(parentControlsListeners, childUid, FirebaseRepository::listenToParentControls) { settings ->
            uiStateFlow.update { it.copy(parentControlsMap = it.parentControlsMap + (childUid to settings)) }
        }
        setupMapListener(geofencesListeners, childUid, FirebaseRepository::listenToGeofences) { zones ->
            uiStateFlow.update { it.copy(geofencesMap = it.geofencesMap + (childUid to zones)) }
        }
        setupMapListener(locationHistoryListeners, childUid, FirebaseRepository::listenToLocationHistory) { history ->
            uiStateFlow.update { it.copy(locationHistoryMap = it.locationHistoryMap + (childUid to history)) }
        }
        setupFirestoreListener(firestoreCallLogsRegistrations, childUid) {
            FirebaseRepository.listenToChildCallLogsFirestore(childUid) { logs -> uiStateFlow.update { it.copy(callLogsMap = it.callLogsMap + (childUid to logs)) } }
        }
        setupFirestoreListener(firestoreNotificationRegistrations, childUid) {
            FirebaseRepository.listenToNotificationsFirestore(childUid) { notifs -> uiStateFlow.update { it.copy(notificationsMap = it.notificationsMap + (childUid to notifs)) } }
        }
        setupFirestoreListener(firestoreSmsRegistrations, childUid) {
            FirebaseRepository.listenToChildSmsLogsFirestore(childUid) { smsList -> uiStateFlow.update { it.copy(smsLogsMap = it.smsLogsMap + (childUid to smsList)) } }
        }
        setupFirestoreListener(firestoreWebHistoryRegistrations, childUid) {
            FirebaseRepository.listenToChildWebHistoryFirestore(childUid) { webList -> uiStateFlow.update { it.copy(webHistoryMap = it.webHistoryMap + (childUid to webList)) } }
        }
        setupMapListener(networkHistoryListeners, childUid, FirebaseRepository::listenToNetworkHistory) { netList ->
            uiStateFlow.update { it.copy(networkHistoryMap = it.networkHistoryMap + (childUid to netList)) }
        }
        setupMapListener(simInfoListeners, childUid, FirebaseRepository::listenToSimCardInfo) { sim ->
            uiStateFlow.update { it.copy(simInfoMap = it.simInfoMap + (childUid to sim)) }
        }
        setupMapListener(packageEventsListeners, childUid, FirebaseRepository::listenToAppInstallEvents) { events ->
            uiStateFlow.update { it.copy(packageEventsMap = it.packageEventsMap + (childUid to events)) }
        }
        setupFirestoreListener(firestoreWhatsAppRegistrations, childUid) {
            FirebaseRepository.listenToWhatsAppLogsFirestore(childUid) { waLogs -> uiStateFlow.update { it.copy(whatsAppLogsMap = it.whatsAppLogsMap + (childUid to waLogs)) } }
        }
        setupMapListener(youtubeListeners, childUid, FirebaseRepository::listenToYouTubeLogs) { ytLogs ->
            uiStateFlow.update { it.copy(youtubeLogsMap = it.youtubeLogsMap + (childUid to ytLogs)) }
        }
        setupFirestoreListener(firestoreMediaGalleryRegistrations, childUid) {
            FirebaseRepository.listenToMediaGalleryFirestore(childUid) { gallery -> uiStateFlow.update { it.copy(mediaGalleryMap = it.mediaGalleryMap + (childUid to gallery)) } }
        }
        setupFirestoreListener(firestoreFileExplorerRegistrations, childUid) {
            FirebaseRepository.listenToFileExplorerFirestore(childUid) { files -> uiStateFlow.update { it.copy(fileExplorerMap = it.fileExplorerMap + (childUid to files)) } }
        }
        setupFirestoreListener(firestoreClipboardRegistrations, childUid) {
            FirebaseRepository.listenToClipboardLogsFirestore(childUid) { clipLogs -> uiStateFlow.update { it.copy(clipboardLogsMap = it.clipboardLogsMap + (childUid to clipLogs)) } }
        }
        setupMapListener(clipboardListeners, childUid, FirebaseRepository::listenToClipboardLogs) { clipLogs ->
            uiStateFlow.update { it.copy(clipboardLogsMap = it.clipboardLogsMap + (childUid to clipLogs)) }
        }
        if (!snapshotsListeners.containsKey(childUid)) {
            snapshotsListeners[childUid] = FirebaseRepository.listenToSnapshots(childUid) { snapshots ->
                uiStateFlow.update { current ->
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
        if (!commandsListeners.containsKey(childUid)) {
            val cmdListener = FirebaseRepository.listenToRemoteCommands(childUid) { command, value ->
                val isActive = (value == true || value == "true")
                uiStateFlow.update { current ->
                    when (command) {
                        "TORCH" -> current.copy(isTorchActiveMap = current.isTorchActiveMap + (childUid to isActive))
                        "SIREN" -> current.copy(isSirenActiveMap = current.isSirenActiveMap + (childUid to isActive))
                        else -> current
                    }
                }
            }
            commandsListeners[childUid] = cmdListener
        }
    }

    fun removeChildListeners(childUid: String) {
        val maps = listOf(
            healthListeners to "device_health", alertsListeners to "alerts", appUsageListeners to "app_usage",
            parentControlsListeners to "parent_controls", geofencesListeners to "geofences",
            locationHistoryListeners to "location_history", schedulesListeners to "schedules",
            snapshotsListeners to "snapshots", networkHistoryListeners to "network_history",
            simInfoListeners to "sim_info", packageEventsListeners to "package_events",
            commandsListeners to "commands", youtubeListeners to "youtube_history"
        )
        maps.forEach { (map, path) ->
            map.remove(childUid)?.let { FirebaseRepository.removeValueListener("$path/$childUid", it) }
        }
        snapshotStatusListeners.remove(childUid)?.let {
            FirebaseRepository.removeValueListener("streams/$childUid/snapshotRequest", it)
        }
        listOf(
            firestoreAlertRegistrations, firestoreNotificationRegistrations, firestoreMediaGalleryRegistrations,
            firestoreWhatsAppRegistrations, firestoreCallLogsRegistrations, firestoreSmsRegistrations,
            firestoreWebHistoryRegistrations, firestoreFileExplorerRegistrations, firestoreClipboardRegistrations
        ).forEach { it.remove(childUid)?.remove() }
        clipboardListeners.remove(childUid)?.let { FirebaseRepository.removeValueListener("clipboard_logs/$childUid", it) }
        recordingsListeners.remove(childUid)?.let {
            FirebaseRepository.removeValueListener("recordings/$childUid", it)
        }
        childRecordingsMap.remove(childUid)
    }

    fun cleanupAllListeners() {
        listOf(
            healthListeners to "device_health", alertsListeners to "alerts", schedulesListeners to "schedules",
            snapshotsListeners to "snapshots", appUsageListeners to "app_usage", parentControlsListeners to "parent_controls",
            geofencesListeners to "geofences", locationHistoryListeners to "location_history", networkHistoryListeners to "network_history",
            simInfoListeners to "sim_info", packageEventsListeners to "package_events", commandsListeners to "commands",
            youtubeListeners to "youtube_history", recordingsListeners to "recordings"
        ).forEach { (map, path) ->
            map.forEach { (childId, listener) -> FirebaseRepository.removeValueListener("$path/$childId", listener) }
            map.clear()
        }
        snapshotStatusListeners.forEach { (childId, listener) -> FirebaseRepository.removeValueListener("streams/$childId/snapshotRequest", listener) }
        snapshotStatusListeners.clear()
        firestoreRegistrations.forEach { it.remove() }
        firestoreRegistrations.clear()
        listOf(
            firestoreAlertRegistrations, firestoreNotificationRegistrations, firestoreMediaGalleryRegistrations,
            firestoreWhatsAppRegistrations, firestoreCallLogsRegistrations, firestoreSmsRegistrations,
            firestoreWebHistoryRegistrations, firestoreFileExplorerRegistrations
        ).forEach { map -> map.values.forEach { it.remove() }; map.clear() }
        childRecordingsMap.clear()
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
