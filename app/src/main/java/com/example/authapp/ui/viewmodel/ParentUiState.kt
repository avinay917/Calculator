package com.example.authapp.ui.viewmodel

import com.example.authapp.data.*

data class ParentUiState(
    // Bottom Navigation Tab (0 = Live Monitor, 1 = Activity Logs, 2 = Security Alerts, 3 = Cloud History)
    val selectedTab: Int = 0,

    val childUsers: List<User> = emptyList(),
    val isLoadingChildren: Boolean = true,

    // Live Stream Session
    val activeSessionId: String? = null,
    val activeStreamType: String? = null, // "Audio" or "Video"
    val activeChildId: String? = null,
    val activeChildName: String? = null,
    val streamStatusText: String = "Connecting to child device...",
    val audioSensitivity: Float = 100f,
    val isFrontCamera: Boolean = true,

    // Recording state
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Long = 0L,

    // Location tracking dialog
    val locationDialogChild: User? = null,
    val childLocation: UserLocation? = null,
    val isRefreshingLocation: Boolean = false,

    // Playback state for recordings
    val currentlyPlayingRecId: String? = null,

    // Cloud Recordings Tab state
    val allRecordings: List<RecordingSession> = emptyList(),
    val isLoadingRecordings: Boolean = false,
    val recordingFilter: String = "ALL", // "ALL", "AUDIO", "VIDEO"

    // Real-time Device Health & Telemetry for each child
    val deviceHealthMap: Map<String, DeviceHealth> = emptyMap(),

    // Phase 1 & 2 Features
    val callLogsMap: Map<String, List<CallLogItem>> = emptyMap(),
    val notificationsMap: Map<String, List<NotificationItem>> = emptyMap(),
    val securityAlertsMap: Map<String, List<SecurityAlert>> = emptyMap(),
    val schedulesMap: Map<String, List<RecordingSchedule>> = emptyMap(),
    val snapshotsMap: Map<String, List<SnapshotInfo>> = emptyMap(),

    // Active Dialogs
    val activeActivityDialogChild: User? = null,
    val activeAlertsDialogChild: User? = null,
    val activeScheduleDialogChild: User? = null,
    val activeSnapshotDialogChild: User? = null,
    val snapshotStatusMessage: String? = null,

    // Phase 3 Features: App Usage & Parental Control (App Lock, Study Mode)
    val appUsageMap: Map<String, List<AppUsageInfo>> = emptyMap(),
    val parentControlsMap: Map<String, ParentControlSettings> = emptyMap(),
    val selectedChildForControls: User? = null,

    // Phase 4 Features: SMS Logs, Geofences, Location History, Hardware Actions
    val smsLogsMap: Map<String, List<SmsItem>> = emptyMap(),
    val geofencesMap: Map<String, List<GeofenceZone>> = emptyMap(),
    val locationHistoryMap: Map<String, List<UserLocation>> = emptyMap(),
    val isTorchActiveMap: Map<String, Boolean> = emptyMap(),
    val isSirenActiveMap: Map<String, Boolean> = emptyMap()
)
