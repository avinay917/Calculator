package com.example.authapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.example.authapp.data.NotificationItem
import com.example.authapp.data.SmsItem
import com.example.authapp.data.WhatsAppLogItem
import com.example.authapp.utils.formatTime

@Composable
fun SmsListItem(sms: SmsItem) {
    val isIncoming = sms.type.uppercase() == "INCOMING"
    val tint = if (isIncoming) Color(0xFF388E3C) else Color(0xFF1976D2)
    ActivityLogCard(
        icon = if (isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade,
        iconTint = tint,
        title = sms.address.ifEmpty { "Unknown" },
        subtitle = sms.body,
        timestampStr = remember(sms.timestamp) { sms.timestamp.formatTime() },
        badgeText = if (isIncoming) "Received" else "Sent",
        badgeColor = tint
    )
}

@Composable
fun NotificationListItem(notif: NotificationItem) {
    ActivityLogCard(
        icon = Icons.Default.Notifications,
        iconTint = MaterialTheme.colorScheme.primary,
        title = notif.appName.ifEmpty { notif.packageName },
        subtitle = listOf(notif.title, notif.text).filter { it.isNotEmpty() }.joinToString(" • "),
        timestampStr = remember(notif.timestamp) { notif.timestamp.formatTime() }
    )
}

@Composable
fun WhatsAppListItem(item: WhatsAppLogItem) {
    val typeColor = when (item.type.uppercase()) {
        "STATUS" -> Color(0xFF00A884)
        "AUDIO" -> Color(0xFF34B7F1)
        "PHOTO", "VIDEO" -> Color(0xFF9C27B0)
        else -> Color(0xFF25D366)
    }
    val typeIcon = when (item.type.uppercase()) {
        "STATUS" -> Icons.Default.CameraAlt
        "AUDIO" -> Icons.Default.Mic
        "PHOTO" -> Icons.Default.Image
        "VIDEO" -> Icons.Default.Videocam
        else -> Icons.Default.Chat
    }

    ActivityLogCard(
        icon = typeIcon,
        iconTint = typeColor,
        title = item.senderName.ifEmpty { "WhatsApp Contact" },
        subtitle = item.messageText,
        timestampStr = remember(item.timestamp) { item.timestamp.formatTime() },
        badgeText = item.type.uppercase(),
        badgeColor = typeColor
    )
}
