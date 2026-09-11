package com.example.authapp.data

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "child", // Default role assigned to all new sign-ups
    val fcmToken: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)
