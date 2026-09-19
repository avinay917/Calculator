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
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.theme.AuthAppTheme

@Composable
fun ChildScreen(
    email: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentChildUid = com.example.authapp.data.FirebaseRepository.currentUser?.uid ?: ""
    var linkedParentId by remember { mutableStateOf("") }
    var pairingCodeInput by remember { mutableStateOf("") }
    var isPairingLoading by remember { mutableStateOf(false) }
    var pairingMessage by remember { mutableStateOf<String?>(null) }
    var isPairingError by remember { mutableStateOf(false) }
    var showChangePairingForm by remember { mutableStateOf(false) }
    var showPairingSuccessDialog by remember { mutableStateOf(false) }
    var pairedParentEmail by remember { mutableStateOf("") }

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
            Manifest.permission.READ_CALL_LOG
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

        // Card 0: Parent Device Pairing Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (linkedParentId.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (linkedParentId.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.tertiary
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (linkedParentId.isNotEmpty()) Icons.Default.Check else Icons.Default.Link,
                        contentDescription = null,
                        tint = if (linkedParentId.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (linkedParentId.isNotEmpty()) "Linked With Parent Account" else "Link Device With Parent",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (linkedParentId.isNotEmpty() && !showChangePairingForm) {
                    Text(
                        text = "This device is paired with Parent ID: ${linkedParentId.take(10)}... Telemetry & streaming requests are active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = { showChangePairingForm = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Change or Re-Link Parent Code", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Text(
                        text = "Enter the 6-digit Pairing Code generated from your Parent Dashboard to connect this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pairingCodeInput,
                        onValueChange = {
                            if (it.length <= 6) {
                                pairingCodeInput = it.filter { char -> char.isLetterOrDigit() }.uppercase()
                            }
                        },
                        label = { Text("6-Digit Pairing Code") },
                        placeholder = { Text("e.g. 849201") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            textAlign = TextAlign.Center,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    pairingMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = if (isPairingError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (pairingCodeInput.length != 6) {
                                    pairingMessage = "Please enter all 6 digits"
                                    isPairingError = true
                                    return@Button
                                }
                                isPairingLoading = true
                                pairingMessage = null
                                com.example.authapp.data.FirebaseRepository.pairChildWithCode(
                                    childUid = currentChildUid,
                                    code = pairingCodeInput
                                ) { success, parentEmail, errorMsg ->
                                    isPairingLoading = false
                                    if (success) {
                                        isPairingError = false
                                        pairedParentEmail = parentEmail ?: "Parent"
                                        pairingMessage = "Connected to $pairedParentEmail successfully!"
                                        showChangePairingForm = false
                                        showPairingSuccessDialog = true
                                        pairingCodeInput = ""
                                        com.example.authapp.analytics.AppHealthTelemetry.syncDeviceHealth(context)
                                    } else {
                                        isPairingError = true
                                        pairingMessage = errorMsg ?: "Pairing failed"
                                    }
                                }
                            },
                            enabled = !isPairingLoading && pairingCodeInput.length == 6,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isPairingLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect Device")
                            }
                        }

                        if (showChangePairingForm && linkedParentId.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showChangePairingForm = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val allSpecialGranted = hasOverlayPermission && isBatteryOptimizationIgnored && hasNotificationListenerPermission && hasExactAlarmPermission && hasUsageAccessPermission && hasAccessibilityPermission
        val allCoreGranted = hasStage1Permissions && hasCallPermissions

        if (allCoreGranted && allSpecialGranted) {
            // All Permissions & Settings are Fully Active - Modern Minimal Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
                            text = "Protection Active",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Live streaming, telemetry, and background services are fully operational.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Core or Special Settings missing
            if (!allCoreGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "App Permissions Required",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Allow camera, mic, location, and call access so your parent can connect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))
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

            if (!allSpecialGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Permission Setup Wizard",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enable these 5 essential system settings so protection and remote services run reliably:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 1. Usage Access
                        if (!hasUsageAccessPermission) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("1. Enable Usage Access")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 2. Notification Access
                        if (!hasNotificationListenerPermission) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("2. Enable Notification Access")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 3. Accessibility
                        if (!hasAccessibilityPermission) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("3. Enable Accessibility Service")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 4. Overlay
                        if (!hasOverlayPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("4. Allow 'Display Over Other Apps'")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 5. Battery Saver
                        if (!isBatteryOptimizationIgnored && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("5. Allow Unrestricted Background Battery")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 6. OEM Auto-Start Settings (Xiaomi, Vivo, Oppo, Realme, Samsung)
                        if (com.example.authapp.utils.AutoStartHelper.isOemAutoStartDevice()) {
                            val oemName = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                            OutlinedButton(
                                onClick = {
                                    com.example.authapp.utils.AutoStartHelper.openAutoStartSettings(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("6. Enable $oemName Auto-Start Protection")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Exact Alarm (Android 12+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Allow Exact Alarms (Schedules)")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

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
    }
}

@Preview(showBackground = true)
@Composable
fun ChildScreenPreview() {
    AuthAppTheme {
        ChildScreen(email = "child@example.com", onSignOut = {})
    }
}
