package com.example.authapp.data

data class RecordingSession(
    val id: String = "",
    val childId: String = "",
    val parentId: String = "",
    val streamType: String = "audio",
    val startTime: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val status: String = "SAVED",
    val storageUrl: String = ""
)
