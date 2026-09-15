package com.example.authapp.utils

import android.content.Context
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.SecurityAlert
import com.example.authapp.data.WebHistoryItem

object WebHistoryManager {

    private val unsafeKeywords = listOf(
        "casino", "betting", "gambling", "porn", "xxx", "adult", "dating", "drugs", "suicide", "hack"
    )

    fun logVisitedUrl(context: Context, url: String, title: String = "") {
        val childId = AppHealthTelemetry.getEffectiveUserId(context)
        if (childId.isEmpty() || url.isEmpty()) return

        val lowerUrl = url.lowercase()
        val isUnsafe = unsafeKeywords.any { lowerUrl.contains(it) }

        val item = WebHistoryItem(
            url = url,
            title = title.ifEmpty { url },
            timestamp = System.currentTimeMillis(),
            isBlocked = isUnsafe
        )
        FirebaseRepository.logWebHistoryItem(childId, item)

        if (isUnsafe) {
            val alert = SecurityAlert(
                type = "UNSAFE_WEB_VISIT",
                title = "Suspicious Website Visited",
                message = "Child visited potentially unsafe web page: $url",
                timestamp = System.currentTimeMillis(),
                severity = "WARNING"
            )
            FirebaseRepository.pushSecurityAlert(childId, alert)
        }
    }
}
