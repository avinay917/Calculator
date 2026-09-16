package com.example.authapp.data

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class UserLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Double = 0.0,
    val timestamp: Long = 0L,
    val provider: String = ""
)

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "child", // Default role assigned to all new sign-ups
    val fcmToken: String = "",
    val parentId: String = "",
    val familyCode: String = "",
    @get:PropertyName("isOnline") @set:PropertyName("isOnline")
    var isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val location: UserLocation? = null
)
