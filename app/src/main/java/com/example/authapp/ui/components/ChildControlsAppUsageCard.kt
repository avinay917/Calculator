package com.example.authapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.authapp.data.AppUsageInfo
import com.example.authapp.data.ParentControlSettings

@Composable
fun ChildControlsScreenTimeCard(
    appUsageList: List<AppUsageInfo>,
    modifier: Modifier = Modifier
) {
    val totalScreenTimeMinutes = appUsageList.sumOf { it.totalTimeInForegroundMinutes }
    val totalHours = totalScreenTimeMinutes / 60
    val totalMins = totalScreenTimeMinutes % 60

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Today's Screen Time",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (totalHours > 0) "${totalHours}h ${totalMins}m" else "${totalMins} mins",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (appUsageList.isEmpty()) {
                Text(
                    text = "App usage syncs automatically when child opens apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxUsage = appUsageList.maxOfOrNull { it.totalTimeInForegroundMinutes }?.coerceAtLeast(1L) ?: 1L
                appUsageList.take(5).forEach { app ->
                    val progress = (app.totalTimeInForegroundMinutes.toFloat() / maxUsage.toFloat()).coerceIn(0f, 1f)
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = app.appName.ifEmpty { app.packageName.substringAfterLast(".") },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val appH = app.totalTimeInForegroundMinutes / 60
                            val appM = app.totalTimeInForegroundMinutes % 60
                            Text(
                                text = if (appH > 0) "${appH}h ${appM}m" else "${appM}m",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (app.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChildControlsAppLockItem(
    app: AppUsageInfo,
    parentControls: ParentControlSettings?,
    onToggleAppBlock: (packageName: String, isCurrentlyBlocked: Boolean) -> Unit,
    onLaunchApp: (packageName: String) -> Unit,
    onCloseApp: (packageName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isBlocked = parentControls?.blockedPackages?.get(app.packageName) == true ||
            parentControls?.blockedPackages?.get(app.packageName.replace(".", "_")) == true ||
            app.isBlocked

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isBlocked) Color(0xFFC62828) else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBlocked) Icons.Default.Lock else Icons.Default.Apps,
                    contentDescription = null,
                    tint = if (isBlocked) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName.ifEmpty { app.packageName.substringAfterLast(".") },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isBlocked) "🔴 Blocked by Parent" else "Used ${app.totalTimeInForegroundMinutes} mins today",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBlocked) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onLaunchApp(app.packageName) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Open App", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = { onCloseApp(app.packageName) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Close App", tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(2.dp))

            FilledTonalButton(
                onClick = { onToggleAppBlock(app.packageName, isBlocked) },
                colors = if (isBlocked) ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFC62828), contentColor = Color.White)
                else ButtonDefaults.filledTonalButtonColors(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(if (isBlocked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isBlocked) "Unlock" else "Lock", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
