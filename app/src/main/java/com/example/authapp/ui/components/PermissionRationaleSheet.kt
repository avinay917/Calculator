package com.example.authapp.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PermissionStepType {
    CORE_MEDIA,
    CALL_LOGS,
    OVERLAY,
    BATTERY_OPTIMIZATION
}

data class PermissionItemInfo(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val isRequired: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRationaleSheet(
    stepType: PermissionStepType,
    onGrantClick: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val (title, headerSubtitle, icon, items) = when (stepType) {
        PermissionStepType.CORE_MEDIA -> {
            Quadruple(
                "Essential Protection Access",
                "To protect the child and allow parent audio/video live monitoring, the following permissions are required:",
                Icons.Default.Security,
                listOf(
                    PermissionItemInfo(
                        title = "Microphone Access",
                        description = "Enables parents to listen to surroundings during emergencies.",
                        icon = Icons.Default.Mic,
                        isGranted = false
                    ),
                    PermissionItemInfo(
                        title = "Camera Access",
                        description = "Provides live video view of the child's environment when requested.",
                        icon = Icons.Default.Videocam,
                        isGranted = false
                    ),
                    PermissionItemInfo(
                        title = "Precise Location",
                        description = "Helps locate the child on GPS in case they need assistance.",
                        icon = Icons.Default.LocationOn,
                        isGranted = false
                    ),
                    PermissionItemInfo(
                        title = "Notifications",
                        description = "Required to keep background protection service active on Android.",
                        icon = Icons.Default.Notifications,
                        isGranted = false
                    )
                )
            )
        }
        PermissionStepType.CALL_LOGS -> {
            Quadruple(
                "Call Safety Protection",
                "Keep track of incoming and outgoing calls to protect the child from unknown callers:",
                Icons.Default.Phone,
                listOf(
                    PermissionItemInfo(
                        title = "Phone State",
                        description = "Detects when a phone call starts or ends.",
                        icon = Icons.Default.Call,
                        isGranted = false
                    ),
                    PermissionItemInfo(
                        title = "Call Log Access",
                        description = "Records caller identity and timestamp for parent dashboard review.",
                        icon = Icons.Default.ListAlt,
                        isGranted = false
                    )
                )
            )
        }
        PermissionStepType.OVERLAY -> {
            Quadruple(
                "Display Over Other Apps",
                "This allows the app to activate camera or audio even when the screen is locked or another app is open.",
                Icons.Default.Layers,
                listOf(
                    PermissionItemInfo(
                        title = "System Alert Window",
                        description = "Wakes up remote streaming UI smoothly without manual unlock.",
                        icon = Icons.Default.ScreenShare,
                        isGranted = false
                    )
                )
            )
        }
        PermissionStepType.BATTERY_OPTIMIZATION -> {
            Quadruple(
                "Unrestricted Background Running",
                "Android battery savers (MIUI, OneUI, ColorOS) can aggressively kill background protection.",
                Icons.Default.BatteryChargingFull,
                listOf(
                    PermissionItemInfo(
                        title = "Disable Battery Saver for Child App",
                        description = "Ensures parent live requests connect immediately without connection drops.",
                        icon = Icons.Default.PowerSettingsNew,
                        isGranted = false
                    )
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = headerSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onGrantClick()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Continue & Allow",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("Maybe Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                        onOpenSettings?.invoke()
                    }
                ) {
                    Text("App Settings", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
