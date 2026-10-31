package com.example.authapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onEnableInternet: () -> Unit = {},
    onSetUninstallPin: (String) -> Unit = {},
    onToggleAppBlock: (packageName: String, isCurrentlyBlocked: Boolean) -> Unit,
    onToggleStudyMode: (isActive: Boolean, durationMinutes: Int) -> Unit,
    onLaunchApp: (packageName: String) -> Unit = {},
    onCloseApp: (packageName: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (childUsers.isEmpty()) {
        EmptyStateCard(
            icon = Icons.Default.Security,
            title = "No Child Devices Connected",
            subtitle = "Connect a child device to manage app limits and study mode.",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val activeChild = selectedChild ?: childUsers.first()
    val isStudyModeActive = parentControls?.isStudyModeActive == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ChildSelectorRow(
            childUsers = childUsers,
            selectedChildId = activeChild.uid,
            onSelectChild = { uid -> childUsers.find { it.uid == uid }?.let(onSelectChild) }
        )
        if (childUsers.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ChildControlsStudyModeCard(
                    isStudyModeActive = isStudyModeActive,
                    onToggleStudyMode = onToggleStudyMode
                )
            }

            item {
                ChildControlsHardwareCard(
                    isTorchActive = isTorchActive,
                    isSirenActive = isSirenActive,
                    onToggleTorch = onToggleTorch,
                    onToggleSiren = onToggleSiren,
                    onEnableInternet = onEnableInternet
                )
            }

            item {
                ChildControlsPinCard(
                    parentControls = parentControls,
                    onSetUninstallPin = onSetUninstallPin
                )
            }

            item {
                ChildControlsScreenTimeCard(appUsageList = appUsageList)
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AppBlocking, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "App Controls & Lock (${appUsageList.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = { onCloseApp("") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Go Home", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (appUsageList.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
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
                    ChildControlsAppLockItem(
                        app = app,
                        parentControls = parentControls,
                        onToggleAppBlock = onToggleAppBlock,
                        onLaunchApp = onLaunchApp,
                        onCloseApp = onCloseApp
                    )
                }
            }
        }
    }
}
