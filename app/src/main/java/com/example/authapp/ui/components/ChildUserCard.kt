package com.example.authapp.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.authapp.analytics.AppAnalytics
import com.example.authapp.data.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChildUserCard(
    user: User,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Child Info Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (user.isOnline) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = if (user.isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.name.ifEmpty { "Child Device" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (user.isOnline) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = if (user.isOnline) "🟢 Online" else "⚪ Offline",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (user.isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (!user.isOnline && user.lastSeen > 0L) {
                val lastSeenText = remember(user.lastSeen) {
                    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    sdf.format(Date(user.lastSeen))
                }
                Text(
                    text = "Last seen: $lastSeenText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 54.dp, top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Actions: Audio, Video, GPS (all styled consistently with safe padding)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = {
                        AppAnalytics.logButtonClick("audio_cast", "ParentScreen", mapOf("childId" to user.uid))
                        if (!user.isOnline) {
                            Toast.makeText(context, "Child device is offline (No Internet). Wake-up request sent.", Toast.LENGTH_SHORT).show()
                            AppAnalytics.logActionFailure("audio_cast", "ParentScreen", "Child device is offline")
                        }
                        onAudioClick()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Audio", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }

                FilledTonalButton(
                    onClick = {
                        AppAnalytics.logButtonClick("video_cast", "ParentScreen", mapOf("childId" to user.uid))
                        if (!user.isOnline) {
                            Toast.makeText(context, "Child device is offline (No Internet). Wake-up request sent.", Toast.LENGTH_SHORT).show()
                            AppAnalytics.logActionFailure("video_cast", "ParentScreen", "Child device is offline")
                        }
                        onVideoClick()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Video", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }

                FilledTonalButton(
                    onClick = {
                        AppAnalytics.logButtonClick("gps_location", "ParentScreen", mapOf("childId" to user.uid))
                        onLocationClick()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("GPS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}
