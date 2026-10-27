package com.example.authapp.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.authapp.service.ChildForegroundService
import com.example.authapp.sync.KeepAliveWorker

object ChildPermissionsHelper {
    fun isMicrophoneAndCameraGranted(context: Context): Boolean {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val cam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val loc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return mic && cam && loc
    }

    fun checkExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() == true
        } else true
    }

    fun checkNotificationListenerPermission(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    fun checkUsageAccessPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    fun checkAccessibilityPermission(context: Context): Boolean {
        return try {
            val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            services.contains(context.packageName)
        } catch (_: Exception) { false }
    }

    fun startMonitoringService(context: Context) {
        val serviceIntent = Intent(context, ChildForegroundService::class.java).apply {
            action = ChildForegroundService.ACTION_START_MONITORING
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            KeepAliveWorker.schedule(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCorePermissionsList(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }

    fun checkAllPermissions(context: Context, childUid: String): ChildPermissionState {
        val hasStage1 = isMicrophoneAndCameraGranted(context)
        val hasCall = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        val isBatteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        } else true

        val hasExactAlarm = checkExactAlarmPermission(context)
        val hasNotification = checkNotificationListenerPermission(context)
        val hasUsage = checkUsageAccessPermission(context)
        val hasAccessibility = checkAccessibilityPermission(context)
        val isDeviceAdmin = com.example.authapp.utils.DeviceAdminManager.isDeviceAdminActive(context)

        if (hasCall && childUid.isNotEmpty()) {
            com.example.authapp.data.CallLogSyncManager.syncIfDue(context, childUid)
        }
        com.example.authapp.analytics.AppHealthTelemetry.syncDeviceHealth(context)
        if (hasStage1) {
            startMonitoringService(context)
        }

        return ChildPermissionState(
            hasStage1 = hasStage1,
            hasCall = hasCall,
            hasOverlay = hasOverlay,
            isBatteryOptimized = isBatteryOptimized,
            hasExactAlarm = hasExactAlarm,
            hasNotificationListener = hasNotification,
            hasUsageAccess = hasUsage,
            hasAccessibility = hasAccessibility,
            isDeviceAdmin = isDeviceAdmin
        )
    }
}

data class ChildPermissionState(
    val hasStage1: Boolean,
    val hasCall: Boolean,
    val hasOverlay: Boolean,
    val isBatteryOptimized: Boolean,
    val hasExactAlarm: Boolean,
    val hasNotificationListener: Boolean,
    val hasUsageAccess: Boolean,
    val hasAccessibility: Boolean,
    val isDeviceAdmin: Boolean
)

