package com.example.authapp.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class AppUpdateInfo(
    val versionCode: Long = 0L,
    val versionName: String = "",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val isForceUpdate: Boolean = false,
    val updatedAt: Long = 0L
)
