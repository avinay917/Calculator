package com.example.authapp.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.authapp.data.AppUsageInfo
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.SecurityAlert
import com.example.authapp.utils.MediaGalleryUtils
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.Executor

class ChildDeviceStateMonitor(private val context: Context) {

    private var mediaContentObserver: ContentObserver? = null
    private var mediaDebounceRunnable: Runnable? = null
    private var telephonyCallback: Any? = null
    private var activeCallState = TelephonyManager.CALL_STATE_IDLE
    private var permissionShieldHandler: Handler? = null
    private var permissionShieldRunnable: Runnable? = null

    fun syncAppUsageStats(uid: String, force: Boolean = false) {
        if (uid.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences("app_usage_sync_prefs", Context.MODE_PRIVATE)
        val lastSync = prefs.getLong("last_sync_$uid", 0L)
        val now = System.currentTimeMillis()
        if (!force && (now - lastSync < 4 * 60 * 60 * 1000L)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val startTime = cal.timeInMillis
                val endTime = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                val pm = context.packageManager
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
                    prefs.edit().putLong("last_sync_$uid", now).apply()
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildService] App Usage sync notice: ${e.localizedMessage}")
            }
        }
    }

    fun syncInstalledApps(uid: String) {
        if (uid.isEmpty()) return
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)
            val userApps = installedApps.filter {
                (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName
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
                FirebaseDatabase.getInstance().reference.child("installed_apps").child(uid).setValue(appList)
            }
            FirebaseCrashlytics.getInstance().log("[ChildService] Synced ${userApps.size} installed apps to installed_apps node")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] syncInstalledApps notice: ${e.localizedMessage}")
        }
    }

    fun registerMediaObserver(uid: String, coroutineScope: CoroutineScope, mainHandler: Handler) {
        if (uid.isEmpty()) return
        // Do not register continuous ContentObserver on MediaStore.
        // Android triggers EXTERNAL_CONTENT_URI dozens of times per hour for thumbnail caching, WhatsApp media, etc.
        // Firing scanAndSyncMediaGallery on every trigger causes thousands of billable writes per day.
        // Media scanning is now performed safely on-demand or throttled with 24h cooldown.
        FirebaseCrashlytics.getInstance().log("[ChildService] MediaStore continuous observer disabled to protect database quota")
    }

    fun registerTelephonyCallStateListener(uid: String, mainExecutor: Executor) {
        if (telephonyCallback != null || uid.isEmpty()) return
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handlePhoneCallStateChanged(state)
                    }
                }
                tm.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val listener = object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handlePhoneCallStateChanged(state)
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

    private fun handlePhoneCallStateChanged(state: Int) {
        if (state == activeCallState) return
        activeCallState = state
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                val startIntent = Intent(context.applicationContext, ChildForegroundService::class.java).apply {
                    action = ChildForegroundService.ACTION_START_CALL_RECORDING
                    putExtra(ChildForegroundService.EXTRA_PHONE_NUMBER, "active_call")
                }
                try { ContextCompat.startForegroundService(context.applicationContext, startIntent) } catch (_: Exception) {}
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                val stopIntent = Intent(context.applicationContext, ChildForegroundService::class.java).apply {
                    action = ChildForegroundService.ACTION_STOP_CALL_RECORDING
                }
                try { ContextCompat.startForegroundService(context.applicationContext, stopIntent) } catch (_: Exception) {}
            }
        }
    }

    fun startPermissionShieldGuard(
        uid: String,
        hasOverlay: () -> Boolean,
        hasLoc: () -> Boolean,
        hasMic: () -> Boolean,
        hasCam: () -> Boolean
    ) {
        if (uid.isEmpty() || permissionShieldHandler != null) return
        permissionShieldHandler = Handler(android.os.Looper.getMainLooper())
        var lastOverlay = hasOverlay()
        var lastLoc = hasLoc()
        var lastMic = hasMic()
        var lastCam = hasCam()

        permissionShieldRunnable = object : Runnable {
            override fun run() {
                try {
                    val currOverlay = hasOverlay()
                    val currLoc = hasLoc()
                    val currMic = hasMic()
                    val currCam = hasCam()

                    fun notifyRevoked(name: String, msg: String) {
                        FirebaseRepository.pushSecurityAlert(
                            uid,
                            SecurityAlert(
                                type = "PERMISSION_REVOKED",
                                title = "⚠️ Critical: $name Revoked",
                                message = msg,
                                timestamp = System.currentTimeMillis(),
                                severity = "CRITICAL"
                            )
                        )
                    }
                    if (lastOverlay && !currOverlay) notifyRevoked("Overlay Permission", "Floating Window / Overlay permission was disabled in Settings on Child Device!")
                    if (lastLoc && !currLoc) notifyRevoked("GPS Location", "Location permission was turned off in Settings on Child Device!")
                    if (lastMic && !currMic) notifyRevoked("Microphone", "Microphone access was revoked in Settings on Child Device!")
                    if (lastCam && !currCam) notifyRevoked("Camera", "Camera permission was revoked in Settings on Child Device!")

                    lastOverlay = currOverlay
                    lastLoc = currLoc
                    lastMic = currMic
                    lastCam = currCam
                } catch (_: Exception) {}
                permissionShieldHandler?.postDelayed(this, 45000L)
            }
        }
        permissionShieldHandler?.postDelayed(permissionShieldRunnable!!, 45000L)
    }

    fun cleanup() {
        try { mediaDebounceRunnable?.let { Handler(android.os.Looper.getMainLooper()).removeCallbacks(it) } } catch (_: Exception) {}
        try { mediaContentObserver?.let { context.contentResolver.unregisterContentObserver(it) } } catch (_: Exception) {}
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback is TelephonyCallback) {
                tm?.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            } else if (telephonyCallback is android.telephony.PhoneStateListener) {
                @Suppress("DEPRECATION")
                tm?.listen(telephonyCallback as android.telephony.PhoneStateListener, android.telephony.PhoneStateListener.LISTEN_NONE)
            }
        } catch (_: Exception) {}
        permissionShieldRunnable?.let { permissionShieldHandler?.removeCallbacks(it) }
        permissionShieldHandler = null
    }
}
