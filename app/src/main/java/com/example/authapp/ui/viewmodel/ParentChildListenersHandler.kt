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
import com.example.authapp.data.*

// ---------------------------------------------------------------------------
// Unified Listener Registry — eliminates 24 separate mutableMapOf declarations
// Each RTDB listener is registered by a unique "key" (= its RTDB path prefix).
// Adding a new feature listener = adding 1 entry to ACTIVITY_LISTENER_CONFIGS.
// ---------------------------------------------------------------------------
private class ListenerRegistry {
    /** key = "rtdb_path_prefix", value = { childUid -> ValueEventListener } */
    private val rtdbMaps = mutableMapOf<String, MutableMap<String, ValueEventListener>>()
    private val firestoreMaps = mutableMapOf<String, MutableMap<String, ListenerRegistration>>()

    fun getOrCreateRtdb(key: String): MutableMap<String, ValueEventListener> =
        rtdbMaps.getOrPut(key) { mutableMapOf() }

    fun getOrCreateFirestore(key: String): MutableMap<String, ListenerRegistration> =
        firestoreMaps.getOrPut(key) { mutableMapOf() }

    /** Check if a listener already exists for this key+childUid */
    fun hasRtdb(key: String, childUid: String): Boolean =
        rtdbMaps[key]?.containsKey(childUid) == true

    fun hasFirestore(key: String, childUid: String): Boolean =
        firestoreMaps[key]?.containsKey(childUid) == true

    /** Register a new RTDB listener */
    fun putRtdb(key: String, childUid: String, listener: ValueEventListener) {
        getOrCreateRtdb(key)[childUid] = listener
    }

    /** Register a new Firestore listener */
    fun putFirestore(key: String, childUid: String, registration: ListenerRegistration) {
        getOrCreateFirestore(key)[childUid] = registration
    }

    /** Remove a single child's RTDB listener and call Firebase remove */
    fun removeRtdb(key: String, childUid: String) {
        rtdbMaps[key]?.remove(childUid)?.let {
            FirebaseRepository.removeValueListener("$key/$childUid", it)
        }
    }

    /** Remove all RTDB listeners for a specific child across all keys */
    fun removeAllRtdbForChild(childUid: String) {
        rtdbMaps.forEach { (key, map) ->
            map.remove(childUid)?.let {
                FirebaseRepository.removeValueListener("$key/$childUid", it)
            }
        }
    }

    /** Remove all Firestore registrations for a specific child across all keys */
    fun removeAllFirestoreForChild(childUid: String) {
        firestoreMaps.values.forEach { map -> map.remove(childUid)?.remove() }
    }

    /** Cleanup everything */
    fun clearAll() {
        rtdbMaps.forEach { (key, map) ->
            map.forEach { (childId, listener) ->
                FirebaseRepository.removeValueListener("$key/$childId", listener)
            }
            map.clear()
        }
        firestoreMaps.forEach { (_, map) ->
            map.values.forEach { it.remove() }
            map.clear()
        }
    }

    /** Get all known child IDs across all RTDB maps */
    fun allTrackedChildIds(): Set<String> =
        rtdbMaps.values.flatMap { it.keys }.toSet()
}

// ---------------------------------------------------------------------------
// Config entry for a standard "listen to RTDB path → update UiState map" listener.
// Adding a new feature = adding 1 entry here — zero other code changes needed.
// ---------------------------------------------------------------------------
private data class ActivityListenerEntry<T>(
    val key: String,          // RTDB path prefix (e.g. "call_logs")
    val listenFn: (String, (T) -> Unit) -> ValueEventListener,
    val updater: (ParentUiState, String, T) -> ParentUiState
)

@Suppress("UNCHECKED_CAST")
private val ACTIVITY_LISTENER_CONFIGS: List<ActivityListenerEntry<*>> = listOf(
    ActivityListenerEntry("app_usage",        FirebaseRepository::listenToAppUsage)         { s, id, d -> s.copy(appUsageMap          = s.appUsageMap          + (id to (d as List<AppUsageInfo>))) },
    ActivityListenerEntry("parent_controls",  FirebaseRepository::listenToParentControls)   { s, id, d -> s.copy(parentControlsMap    = s.parentControlsMap    + (id to (d as ParentControlSettings))) },
    ActivityListenerEntry("geofences",        FirebaseRepository::listenToGeofences)        { s, id, d -> s.copy(geofencesMap          = s.geofencesMap          + (id to (d as List<GeofenceZone>))) },
    ActivityListenerEntry("location_history", FirebaseRepository::listenToLocationHistory)  { s, id, d -> s.copy(locationHistoryMap   = s.locationHistoryMap   + (id to (d as List<UserLocation>))) },
    ActivityListenerEntry("call_logs",        FirebaseRepository::listenToCallLogs)         { s, id, d -> s.copy(callLogsMap           = s.callLogsMap           + (id to (d as List<CallLogItem>))) },
    ActivityListenerEntry("notifications",    FirebaseRepository::listenToNotifications)    { s, id, d -> s.copy(notificationsMap     = s.notificationsMap     + (id to (d as List<NotificationItem>))) },
    ActivityListenerEntry("sms_logs",         FirebaseRepository::listenToSmsLogs)          { s, id, d -> s.copy(smsLogsMap            = s.smsLogsMap            + (id to (d as List<SmsItem>))) },
    ActivityListenerEntry("sim_info",         FirebaseRepository::listenToSimCardInfo)      { s, id, d -> s.copy(simInfoMap            = s.simInfoMap            + (id to (d as SimCardInfo?))) },
    ActivityListenerEntry("whatsapp_logs",    FirebaseRepository::listenToWhatsAppLogs)     { s, id, d -> s.copy(whatsAppLogsMap      = s.whatsAppLogsMap      + (id to (d as List<WhatsAppLogItem>))) },
    ActivityListenerEntry("youtube_history",  FirebaseRepository::listenToYouTubeLogs)      { s, id, d -> s.copy(youtubeLogsMap       = s.youtubeLogsMap       + (id to (d as List<YouTubeLogItem>))) },
    ActivityListenerEntry("media_gallery",    FirebaseRepository::listenToMediaGallery)     { s, id, d -> s.copy(mediaGalleryMap      = s.mediaGalleryMap      + (id to (d as List<MediaGalleryItem>))) },
    ActivityListenerEntry("file_explorer",    FirebaseRepository::listenToFileExplorer)     { s, id, d -> s.copy(fileExplorerMap      = s.fileExplorerMap      + (id to (d as List<FileExplorerItem>))) },
    ActivityListenerEntry("clipboard_logs",   FirebaseRepository::listenToClipboardLogs)    { s, id, d -> s.copy(clipboardLogsMap     = s.clipboardLogsMap     + (id to (d as List<ClipboardLogItem>))) },
)

// ---------------------------------------------------------------------------
// Config entry for Firestore subcollection listeners
// ---------------------------------------------------------------------------
private data class FirestoreListenerEntry<T>(
    val key: String,
    val listenFn: (String, Long, (T) -> Unit) -> ListenerRegistration,
    val updater: (ParentUiState, String, T) -> ParentUiState
)

@Suppress("UNCHECKED_CAST")
private val FIRESTORE_LISTENER_CONFIGS: List<FirestoreListenerEntry<*>> = listOf(
    FirestoreListenerEntry("web_history",     FirebaseRepository::listenToChildWebHistoryFirestore)     { s, id, d -> s.copy(webHistoryMap     = s.webHistoryMap     + (id to (d as List<WebHistoryItem>))) },
    FirestoreListenerEntry("package_events",  FirebaseRepository::listenToChildPackageEventsFirestore)  { s, id, d -> s.copy(packageEventsMap  = s.packageEventsMap  + (id to (d as List<AppInstallEvent>))) },
    FirestoreListenerEntry("network_history", FirebaseRepository::listenToChildNetworkHistoryFirestore) { s, id, d -> s.copy(networkHistoryMap = s.networkHistoryMap + (id to (d as List<NetworkHistoryItem>))) },
    FirestoreListenerEntry("alerts",          FirebaseRepository::listenToChildSecurityAlertsFirestore) { s, id, d -> s.copy(securityAlertsMap = s.securityAlertsMap + (id to (d as List<SecurityAlert>))) },
    FirestoreListenerEntry("media_items",     FirebaseRepository::listenToMediaItemsFirestore)          { s, id, d -> s.copy(mediaGalleryMap   = s.mediaGalleryMap   + (id to (d as List<MediaItemInfo>).map { MediaGalleryItem(id = it.id, fileName = it.displayName, filePath = "", sizeBytes = it.sizeBytes, dateAdded = it.timestamp) })) }
)

// Detachable activity keys (detached when switching child in dialog)
private val DETACHABLE_ACTIVITY_KEYS = setOf(
    "call_logs", "notifications", "sms_logs", "whatsapp_logs",
    "web_history", "media_gallery", "file_explorer"
)

// Firestore registration keys that need cleanup on detach
private val FIRESTORE_DETACH_KEYS = listOf(
    "fs_notifications", "fs_media_gallery", "fs_whatsapp", "fs_call_logs",
    "fs_sms", "fs_web_history", "fs_file_explorer", "fs_clipboard"
)

class ParentChildListenersHandler(
    private val uiStateFlow: MutableStateFlow<ParentUiState>
) {
    private val registry = ListenerRegistry()
    private val childRecordingsMap = mutableMapOf<String, List<RecordingSession>>()
    private val firestoreRegistrations = mutableListOf<ListenerRegistration>()
    private var childUsersListener: ValueEventListener? = null
    private var recordingsListener: ValueEventListener? = null

    // --- Helper: attach a config-driven listener if not already attached ---
    @Suppress("UNCHECKED_CAST")
    private fun <T> attachConfigListener(entry: ActivityListenerEntry<T>, childUid: String) {
        if (registry.hasRtdb(entry.key, childUid)) return
        val listener = entry.listenFn(childUid) { data ->
            uiStateFlow.update { entry.updater(it, childUid, data) }
        }
        registry.putRtdb(entry.key, childUid, listener)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> attachFirestoreConfigListener(entry: FirestoreListenerEntry<T>, childUid: String) {
        if (registry.hasFirestore(entry.key, childUid)) return
        val registration = entry.listenFn(childUid, 50) { data ->
            uiStateFlow.update { entry.updater(it, childUid, data) }
        }
        registry.putFirestore(entry.key, childUid, registration)
    }

    // --- Helper: attach commands listener (special — handles TORCH/SIREN dispatch) ---
    private fun attachCommandsListener(childUid: String) {
        if (registry.hasRtdb("commands", childUid)) return
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
        registry.putRtdb("commands", childUid, cmdListener)
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
                // Cleanup stale child listeners
                val currentChildIds = list.mapNotNull { it.uid.takeIf(String::isNotEmpty) }.toSet()
                val staleChildIds = registry.allTrackedChildIds().filterNot { it in currentChildIds }
                staleChildIds.forEach { removeChildListeners(it) }
                // Auto-attach health listener for each child
                list.forEach { child ->
                    if (child.uid.isNotEmpty()) {
                        val healthEntry = ActivityListenerEntry<com.example.authapp.data.DeviceHealth?>(
                            "device_health", FirebaseRepository::listenToDeviceHealth
                        ) { s, id, d -> if (d != null) s.copy(deviceHealthMap = s.deviceHealthMap + (id to d)) else s }
                        attachConfigListener(healthEntry, child.uid)
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
        // Parent controls
        ACTIVITY_LISTENER_CONFIGS.firstOrNull { it.key == "parent_controls" }?.let {
            @Suppress("UNCHECKED_CAST")
            attachConfigListener(it as ActivityListenerEntry<Any?>, childUid)
        }
        attachCommandsListener(childUid)
    }

    fun detachChildActivityListeners(childUid: String) {
        DETACHABLE_ACTIVITY_KEYS.forEach { key -> registry.removeRtdb(key, childUid) }
        FIRESTORE_DETACH_KEYS.forEach { key ->
            registry.getOrCreateFirestore(key).remove(childUid)?.remove()
        }
    }

    fun loadParentRecordings(children: List<User>) {
        if (children.isEmpty()) {
            uiStateFlow.update { it.copy(allRecordings = emptyList(), isLoadingRecordings = false) }
            return
        }
        uiStateFlow.update { it.copy(isLoadingRecordings = true) }
        children.forEach { child ->
            val childUid = child.uid
            if (childUid.isNotEmpty() && !registry.hasRtdb("recordings", childUid)) {
                val listener = FirebaseRepository.listenToRecordings(childUid) { recList ->
                    childRecordingsMap[childUid] = recList
                    val merged = childRecordingsMap.values.flatten().sortedByDescending { it.startTime }
                    uiStateFlow.update { it.copy(allRecordings = merged, isLoadingRecordings = false) }
                }
                registry.putRtdb("recordings", childUid, listener)
            }
        }
        val merged = childRecordingsMap.values.flatten().sortedByDescending { it.startTime }
        uiStateFlow.update { it.copy(allRecordings = merged, isLoadingRecordings = false) }
    }

    @Suppress("UNCHECKED_CAST")
    fun attachChildActivityListeners(childUid: String) {
        if (childUid.isEmpty()) return

        // Attach ALL config-driven listeners in one loop (replaces 90+ lines of copy-paste)
        for (entry in ACTIVITY_LISTENER_CONFIGS) {
            attachConfigListener(entry as ActivityListenerEntry<Any?>, childUid)
        }

        // Attach Firestore-driven listeners (Web history, package events, network history, alerts, media)
        for (entry in FIRESTORE_LISTENER_CONFIGS) {
            attachFirestoreConfigListener(entry as FirestoreListenerEntry<Any?>, childUid)
        }

        // Activity feed (special — has limit param)
        if (!registry.hasRtdb("activity_feed", childUid)) {
            registry.putRtdb("activity_feed", childUid,
                FirebaseRepository.listenToActivityFeed(childUid, limit = 50) { events ->
                    uiStateFlow.update { it.copy(activityFeedMap = it.activityFeedMap + (childUid to events)) }
                }
            )
        }

        // Snapshots (special — has complex state update with capture status tracking)
        if (!registry.hasRtdb("snapshots", childUid)) {
            registry.putRtdb("snapshots", childUid,
                FirebaseRepository.listenToSnapshots(childUid) { snapshots ->
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
            )
        }

        // Commands (TORCH/SIREN)
        attachCommandsListener(childUid)
    }

    fun removeChildListeners(childUid: String) {
        registry.removeAllRtdbForChild(childUid)
        registry.removeAllFirestoreForChild(childUid)
        childRecordingsMap.remove(childUid)
    }

    fun cleanupAllListeners() {
        registry.clearAll()
        firestoreRegistrations.forEach { it.remove() }
        firestoreRegistrations.clear()
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
