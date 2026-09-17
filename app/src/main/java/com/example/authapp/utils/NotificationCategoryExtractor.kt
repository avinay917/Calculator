package com.example.authapp.utils

/**
 * Notification Category Extractor (Point 11).
 * Centralizes notification text categorization for WhatsApp, Business, & Messaging apps.
 */
object NotificationCategoryExtractor {
    const val TYPE_CHAT = "CHAT"
    const val TYPE_STATUS = "STATUS"
    const val TYPE_AUDIO = "AUDIO"
    const val TYPE_PHOTO = "PHOTO"
    const val TYPE_VIDEO = "VIDEO"

    fun extractCategory(messageText: String, title: String): String {
        val lowerText = messageText.lowercase()
        val lowerTitle = title.lowercase()

        return when {
            lowerText.contains("voice message") || lowerText.contains("audio message") || lowerText.contains("🔊 audio") -> TYPE_AUDIO
            lowerText.contains("photo") || lowerText.contains("📷 photo") || lowerText.contains("image") -> TYPE_PHOTO
            lowerText.contains("video") || lowerText.contains("📹 video") -> TYPE_VIDEO
            lowerTitle.contains("status") || lowerText.contains("status update") -> TYPE_STATUS
            else -> TYPE_CHAT
        }
    }
}
