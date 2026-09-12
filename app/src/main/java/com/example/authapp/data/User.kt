package com.example.authapp.data

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "child", // Default role assigned to all new sign-ups
    val fcmToken: String = "",
    @get:PropertyName("isOnline") @set:PropertyName("isOnline")
    var isOnline: Boolean = false,
    val lastSeen: Long = 0L
)
