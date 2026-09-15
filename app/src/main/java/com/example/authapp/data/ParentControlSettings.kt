package com.example.authapp.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class AppUsageInfo(
    val packageName: String = "",
    val appName: String = "",
    val totalTimeInForegroundMinutes: Long = 0L,
    val lastTimeUsed: Long = 0L,
    val isBlocked: Boolean = false
)

@IgnoreExtraProperties
data class ParentControlSettings(
    val isStudyModeActive: Boolean = false,
    val studyModeMessage: String = "Study Mode is active. Focus on your studies!",
    val studyModeUntilTimestamp: Long = 0L,
    val blockedPackages: Map<String, Boolean> = emptyMap(),
    val dailyScreenTimeLimitMinutes: Int = 0,
    val lastUpdated: Long = 0L
)
