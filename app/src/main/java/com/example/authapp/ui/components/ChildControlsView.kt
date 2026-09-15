package com.example.authapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.data.AppUsageInfo
import com.example.authapp.data.ParentControlSettings
import com.example.authapp.data.User

@Composable
fun ChildControlsView(
    childUsers: List<User>,
    selectedChild: User?,
    onSelectChild: (User) -> Unit,
    appUsageList: List<AppUsageInfo>,
    parentControls: ParentControlSettings?,
    isTorchActive: Boolean = false,
    isSirenActive: Boolean = false,
    onToggleTorch: () -> Unit = {},
    onToggleSiren: () -> Unit = {},
    onToggleAppBlock: (packageName: String, isCurrentlyBlocked: Boolean) -> Unit,
    onToggleStudyMode: (isActive: Boolean, durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (childUsers.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Child Devices Connected",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Connect a child device to manage app limits and study mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val activeChild = selectedChild ?: childUsers.first()
    val isStudyModeActive = parentControls?.isStudyModeActive == true
    val totalScreenTimeMinutes = appUsageList.sumOf { it.totalTimeInForegroundMinutes }
    val totalHours = totalScreenTimeMinutes / 60
    val totalMins = totalScreenTimeMinutes % 60

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Child Device Selector (Chips)
        if (childUsers.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(childUsers) { child ->
                    val isSelected = child.uid == activeChild.uid
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectChild(child) },
                        label = { Text(child.name.ifEmpty { "Child Device" }) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Card 1: Remote Device Freeze / Study Mode
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isStudyModeActive) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isStudyModeActive) Color(0xFFFF9800) else MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isStudyModeActive) Icons.Default.Lock else Icons.Default.School,
                                    contentDescription = null,
                                    tint = if (isStudyModeActive) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Study Mode / Device Freeze",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (isStudyModeActive) "🔒 Phone Screen Frozen for Studies" else "Freezes screen & blocks all non-essential apps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isStudyModeActive) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isStudyModeActive,
                                onCheckedChange = { onToggleStudyMode(isStudyModeActive, 0) }
                            )
                        }

                        AnimatedVisibility(visible = !isStudyModeActive) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "Quick Freeze Duration:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf(15 to "15 min", 30 to "30 min", 60 to "1 hr", 120 to "2 hr").forEach { (mins, label) ->
                                        OutlinedButton(
                                            onClick = { onToggleStudyMode(false, mins) },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(label, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Remote Hardware Controls: Flashlight & Siren (Features 5 & 6)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashlightOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Remote Hardware Controls",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Flashlight Button
                            FilledTonalButton(
                                onClick = onToggleTorch,
                                modifier = Modifier.weight(1f),
                                colors = if (isTorchActive) {
                                    ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black)
                                } else {
                                    ButtonDefaults.filledTonalButtonColors()
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTorchActive) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTorchActive) "Torch ON" else "Torch OFF",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            // Emergency Siren Button
                            FilledTonalButton(
                                onClick = onToggleSiren,
                                modifier = Modifier.weight(1f),
                                colors = if (isSirenActive) {
                                    ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White)
                                } else {
                                    ButtonDefaults.filledTonalButtonColors()
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSirenActive) Icons.Default.VolumeUp else Icons.Default.Emergency,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSirenActive) "Stop Siren" else "Play Siren",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Today's Total Screen Time & Usage
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
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

            // Card 3: App Lock & Blocker Management
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AppBlocking,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "App Lock & Blocker (${appUsageList.size} Apps)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (appUsageList.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Installed apps will appear here as they are used on child phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(appUsageList) { app ->
                    val isBlocked = parentControls?.blockedPackages?.get(app.packageName) == true ||
                            parentControls?.blockedPackages?.get(app.packageName.replace(".", "_")) == true ||
                            app.isBlocked

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBlocked) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
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

                            FilledTonalButton(
                                onClick = { onToggleAppBlock(app.packageName, isBlocked) },
                                colors = if (isBlocked) {
                                    ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFC62828), contentColor = Color.White)
                                } else {
                                    ButtonDefaults.filledTonalButtonColors()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isBlocked) "Unlock" else "Lock",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
