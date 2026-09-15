package com.example.authapp.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class CallLogItem(
    val id: String = "",
    val number: String = "",
    val name: String = "",
    val type: String = "INCOMING", // INCOMING, OUTGOING, MISSED, REJECTED
    val timestamp: Long = 0L,
    val durationSeconds: Long = 0L
)

@IgnoreExtraProperties
data class NotificationItem(
    val id: String = "",
    val packageName: String = "",
    val appName: String = "",
    val title: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

@IgnoreExtraProperties
data class SecurityAlert(
    val id: String = "",
    val type: String = "", // BATTERY_LOW, SIM_CHANGED, KEYWORD_DETECTED
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val severity: String = "WARNING" // INFO, WARNING, CRITICAL
)

@IgnoreExtraProperties
data class RecordingSchedule(
    val id: String = "",
    val hour: Int = 0,
    val minute: Int = 0,
    val durationMinutes: Int = 5,
    val isEnabled: Boolean = true,
    val repeatDaily: Boolean = true,
    val label: String = ""
)

@IgnoreExtraProperties
data class SnapshotInfo(
    val id: String = "",
    val downloadUrl: String = "",
    val timestamp: Long = 0L,
    val cameraFacing: String = "back"
)

@IgnoreExtraProperties
data class SmsItem(
    val id: String = "",
    val address: String = "",
    val body: String = "",
    val timestamp: Long = 0L,
    val type: String = "INCOMING" // INCOMING, OUTGOING
)

@IgnoreExtraProperties
data class GeofenceZone(
    val id: String = "",
    val name: String = "Home",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Float = 200f,
    val isEnabled: Boolean = true
)

@IgnoreExtraProperties
data class MediaItemInfo(
    val id: String = "",
    val displayName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val timestamp: Long = 0L,
    val folderName: String = ""
)


