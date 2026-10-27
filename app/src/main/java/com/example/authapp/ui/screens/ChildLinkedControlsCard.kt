package com.example.authapp.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.CallRecordingPreferences
import com.example.authapp.utils.DeviceAdminManager
import com.example.authapp.utils.UninstallProtectionManager

@Composable
fun ChildLinkedControlsCard(
    isStealthMode: Boolean,
    onStealthModeChange: (Boolean) -> Unit,
    currentSecretPin: String,
    onChangePinClick: () -> Unit,
    onLockNowClick: () -> Unit,
    callRecordingEnabled: Boolean,
    onCallRecordingChange: (Boolean) -> Unit,
    isDeviceAdminActive: Boolean,
    onDeviceAdminStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDisableAdminPinDialog by remember { mutableStateOf(false) }
    var disableAdminPinInput by remember { mutableStateOf("") }
    var disableAdminPinError by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Status Card
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

        // 2. Unified Device Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Stealth Calculator
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
                        onCheckedChange = onStealthModeChange
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onChangePinClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Change PIN")
                    }

                    Button(
                        onClick = onLockNowClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Lock Now")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Call Recording
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
                        onCheckedChange = onCallRecordingChange
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Uninstall Protection
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
                            onClick = { DeviceAdminManager.requestDeviceAdmin(context) },
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
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
                            val isOk = UninstallProtectionManager.verifyPin(context, disableAdminPinInput)
                            if (isOk) {
                                DeviceAdminManager.deactivateDeviceAdmin(context)
                                onDeviceAdminStateChange(false)
                                showDisableAdminPinDialog = false
                                disableAdminPinInput = ""
                                disableAdminPinError = null
                                Toast.makeText(context, "Device Admin Deactivated", Toast.LENGTH_SHORT).show()
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
}
