package com.example.authapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.authapp.data.AppPreferences
import com.example.authapp.utils.DeviceAdminManager

@Composable
fun ChildDialogsHost(
    showChangePinDialog: Boolean,
    onDismissChangePin: () -> Unit,
    currentSecretPin: String,
    onPinUpdated: (String) -> Unit,
    showSettingsDialog: Boolean,
    onDismissSettings: () -> Unit,
    hasNotificationListener: Boolean,
    hasAccessibility: Boolean,
    hasOverlay: Boolean,
    hasUsageAccess: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    isDeviceAdminActive: Boolean,
    hasExactAlarm: Boolean,
    allCoreGranted: Boolean,
    onRequestCorePermissions: () -> Unit
) {
    val context = LocalContext.current
    var newPinInput by remember(currentSecretPin) { mutableStateOf(currentSecretPin) }

    if (showChangePinDialog) {
        AlertDialog(
            onDismissRequest = onDismissChangePin,
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Change Secret PIN", fontWeight = FontWeight.Bold) },
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length >= 4) {
                            AppPreferences.saveSecretPin(context, newPinInput)
                            onPinUpdated(newPinInput)
                            onDismissChangePin()
                            Toast.makeText(context, "Secret PIN updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissChangePin) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettingsDialog) {
        ChildPermissionsSettingsDialog(
            hasNotificationListener = hasNotificationListener,
            hasAccessibility = hasAccessibility,
            hasOverlay = hasOverlay,
            hasUsageAccess = hasUsageAccess,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
            isDeviceAdminActive = isDeviceAdminActive,
            hasExactAlarm = hasExactAlarm,
            hasCorePermissions = allCoreGranted,
            onRequestCorePermissions = onRequestCorePermissions,
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
            onOpenDeviceAdmin = {
                try { DeviceAdminManager.requestDeviceAdmin(context) } catch (_: Exception) {}
            },
            onOpenExactAlarm = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) try { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) } catch (_: Exception) {} },
            onDismiss = onDismissSettings
        )
    }
}
