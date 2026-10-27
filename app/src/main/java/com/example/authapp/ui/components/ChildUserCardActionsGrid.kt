package com.example.authapp.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.authapp.analytics.AppAnalytics
import com.example.authapp.data.User

@Composable
fun ChildUserCardActionsGrid(
    user: User,
    alertsCount: Int,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSnapshotClick: () -> Unit,
    onScreenClick: () -> Unit,
    onActivityClick: () -> Unit,
    onWhatsAppClick: (() -> Unit)?,
    onAlertsClick: () -> Unit,
    onScheduleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // Row 1: Live Audio & Live Video
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FeatureActionButton(
                icon = Icons.Default.Mic,
                label = "Live Audio",
                containerColor = Color(0xFF0F766E),
                contentColor = Color.White,
                modifier = Modifier.weight(1f),
                onClick = {
                    AppAnalytics.logButtonClick("audio_cast", "ParentScreen", mapOf("childId" to user.uid))
                    if (!user.isOnline) {
                        Toast.makeText(context, "${user.name.ifEmpty { "Child device" }} is currently offline.", Toast.LENGTH_SHORT).show()
                        return@FeatureActionButton
                    }
                    onAudioClick()
                }
            )

            FeatureActionButton(
                icon = Icons.Default.Videocam,
                label = "Live Video",
                containerColor = Color(0xFF1D4ED8),
                contentColor = Color.White,
                modifier = Modifier.weight(1f),
                onClick = {
                    AppAnalytics.logButtonClick("video_cast", "ParentScreen", mapOf("childId" to user.uid))
                    if (!user.isOnline) {
                        Toast.makeText(context, "${user.name.ifEmpty { "Child device" }} is currently offline.", Toast.LENGTH_SHORT).show()
                        return@FeatureActionButton
                    }
                    onVideoClick()
                }
            )
        }

        // Row 2: Screen Cast & Snapshot
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FeatureActionButton(
                icon = Icons.Default.Smartphone,
                label = "Screen Cast",
                containerColor = Color(0xFF4338CA),
                contentColor = Color.White,
                modifier = Modifier.weight(1f),
                onClick = {
                    AppAnalytics.logButtonClick("screen_cast", "ParentScreen", mapOf("childId" to user.uid))
                    if (!user.isOnline) {
                        Toast.makeText(context, "${user.name.ifEmpty { "Child device" }} is currently offline.", Toast.LENGTH_SHORT).show()
                        return@FeatureActionButton
                    }
                    onScreenClick()
                }
            )

            FeatureActionButton(
                icon = Icons.Default.CameraAlt,
                label = "Snapshot",
                containerColor = Color(0xFFC2410C),
                contentColor = Color.White,
                modifier = Modifier.weight(1f),
                onClick = {
                    AppAnalytics.logButtonClick("snapshot", "ParentScreen", mapOf("childId" to user.uid))
                    onSnapshotClick()
                }
            )
        }

        // Row 3: GPS Track & Activity Logs
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FeatureActionButton(
                icon = Icons.Default.LocationOn,
                label = "GPS Track",
                containerColor = Color(0xFFB91C1C),
                contentColor = Color.White,
                modifier = Modifier.weight(1f),
                onClick = {
                    AppAnalytics.logButtonClick("gps_location", "ParentScreen", mapOf("childId" to user.uid))
                    onLocationClick()
                }
            )

            FeatureActionButton(
                icon = Icons.Default.Call,
                label = "Activity Logs",
                containerColor = Color(0xFF334155),
                contentColor = Color.White,
                modifier = Modifier.weight(1f),
                onClick = onActivityClick
            )
        }

        // Row 4: WhatsApp, Alerts & Schedules
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FeatureActionButton(
                icon = Icons.Default.Chat,
                label = "WhatsApp",
                containerColor = Color(0xFF15803D),
                contentColor = Color.White,
                modifier = Modifier.weight(1f),
                onClick = { onWhatsAppClick?.invoke() ?: onActivityClick() }
            )

            FeatureActionButton(
                icon = Icons.Default.Security,
                label = "Alerts",
                containerColor = if (alertsCount > 0) Color(0xFF991B1B) else Color(0xFF475569),
                contentColor = Color.White,
                badgeCount = alertsCount,
                modifier = Modifier.weight(1f),
                onClick = onAlertsClick
            )

            FeatureActionButton(
                icon = Icons.Default.Schedule,
                label = "Schedules",
                containerColor = Color(0xFF7E22CE),
                contentColor = Color.White,
                modifier = Modifier.weight(1f),
                onClick = onScheduleClick
            )
        }
    }
}
