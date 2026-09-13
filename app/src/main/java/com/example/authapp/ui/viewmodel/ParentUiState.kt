package com.example.authapp.ui.viewmodel

import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.data.UserLocation

data class ParentUiState(
    // Bottom Navigation Tab (0 = Live Monitor, 1 = Cloud History)
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
    val recordingFilter: String = "ALL" // "ALL", "AUDIO", "VIDEO"
)
