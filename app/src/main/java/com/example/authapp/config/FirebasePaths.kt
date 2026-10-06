package com.example.authapp.config

/**
 * Canonical Firebase paths. Keep backend names here so a typo cannot silently
 * split a feature across two nodes (for example youtube_logs vs youtube_history).
 */
object FirebasePaths {
    const val USERS = "users"
    const val STREAMS = "streams"
    const val SIGNALING = "signaling"
    const val RECORDINGS = "recordings"
    const val ALERTS = "alerts"
    const val CALL_LOGS = "call_logs"
    const val SMS_LOGS = "sms_logs"
    const val NOTIFICATIONS = "notifications"
    const val WHATSAPP_LOGS = "whatsapp_logs"
    const val YOUTUBE_HISTORY = "youtube_history"
    const val LOCATION_HISTORY = "location_history"
    const val WEB_HISTORY = "web_history"
    const val NETWORK_HISTORY = "network_history"
    const val SCHEDULES = "schedules"
    const val SNAPSHOTS = "snapshots"
    const val DEVICE_HEALTH = "device_health"
    const val APP_USAGE = "app_usage"
    const val PARENT_CONTROLS = "parent_controls"
    const val COMMANDS = "commands"
    const val MEDIA_GALLERY = "media_gallery"
    const val FILE_EXPLORER = "file_explorer"
    const val STORAGE_RECORDINGS = "recordings"
    const val STORAGE_SNAPSHOTS = "snapshots"
}
