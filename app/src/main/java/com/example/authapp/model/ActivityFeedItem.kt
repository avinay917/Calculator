package com.example.authapp.model

import androidx.annotation.Keep

@Keep
data class ActivityFeedItem(
    val id: String = "",
    val category: String = "", // "CALL", "SMS", "WHATSAPP", "WEB", "NOTIFICATION", "APP", "SECURITY"
    val title: String = "",
    val subtitle: String = "",
    val message: String = "",
    val badge: String = "",    // "INCOMING", "OUTGOING", "MISSED", "CHAT", "STATUS", "ALERT", "LAUNCHED"
    val timestamp: Long = 0L,
    val extras: Map<String, String> = emptyMap()
)

