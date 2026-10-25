package com.example.authapp.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized, thread-safe Date & Time formatting utilities.
 * Eliminates duplicate SimpleDateFormat allocations across UI dialogs.
 */
object DateTimeUtils {
    private val standardDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    }

    private val timeOnlyFormat = ThreadLocal.withInitial {
        SimpleDateFormat("hh:mm a", Locale.getDefault())
    }

    private val detailedDateTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault())
    }

    fun formatDateTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return standardDateFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatTimeOnly(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return timeOnlyFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatDetailedDateTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return detailedDateTimeFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatDuration(durationSeconds: Long): String {
        if (durationSeconds <= 0L) return "0s"
        val mins = durationSeconds / 60
        val secs = durationSeconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }
}

fun Long.formatTime(): String = DateTimeUtils.formatDateTime(this)
fun Long.formatSecs(): String = DateTimeUtils.formatDuration(this)
fun Long.formatAsFileSize(): String {
    if (this <= 0L) return ""
    return if (this >= 1024 * 1024) {
        String.format(Locale.getDefault(), "%.1f MB", this / (1024f * 1024f))
    } else {
        String.format(Locale.getDefault(), "%.0f KB", this / 1024f)
    }
}
