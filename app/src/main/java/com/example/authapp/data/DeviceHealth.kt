package com.example.authapp.data

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class DeviceInfo(
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0
)

@IgnoreExtraProperties
data class ServiceHealth(
    @get:PropertyName("isForegroundServiceRunning") @set:PropertyName("isForegroundServiceRunning")
    var isForegroundServiceRunning: Boolean = false,
    val currentServiceState: String = "STOPPED",
    val lastHeartbeat: Long = 0L,
    val batteryPercent: Int = -1,
    @get:PropertyName("isCharging") @set:PropertyName("isCharging")
    var isCharging: Boolean = false,
    @get:PropertyName("isBatteryOptimizationIgnored") @set:PropertyName("isBatteryOptimizationIgnored")
    var isBatteryOptimizationIgnored: Boolean = false
)

@IgnoreExtraProperties
data class AppVersionInfo(
    val versionCode: Long = 0L,
    val versionName: String = ""
)

@IgnoreExtraProperties
data class DeviceHealth(
    val userId: String = "",
    val role: String = "",
    val deviceInfo: DeviceInfo? = null,
    val appVersion: AppVersionInfo? = null,
    val serviceHealth: ServiceHealth? = null,
    val permissions: Map<String, Boolean> = emptyMap(),
    val lastUpdated: Long = 0L
)
