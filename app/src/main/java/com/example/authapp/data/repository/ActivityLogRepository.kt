package com.example.authapp.data.repository

import com.example.authapp.data.NotificationItem
import com.example.authapp.data.WhatsAppLogItem
import com.example.authapp.data.YouTubeLogItem
import com.google.firebase.database.FirebaseDatabase

object ActivityLogRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()

    fun pushNotification(childId: String, item: NotificationItem) {
        if (childId.isEmpty()) return
        database.reference.child("notifications").child(childId).push().setValue(item)
    }

    fun pushWhatsAppLog(childId: String, item: WhatsAppLogItem) {
        if (childId.isEmpty()) return
        database.reference.child("whatsapp_logs").child(childId).push().setValue(item)
    }

    fun pushYouTubeLog(childId: String, item: YouTubeLogItem) {
        if (childId.isEmpty()) return
        database.reference.child("youtube_logs").child(childId).push().setValue(item)
    }
}
