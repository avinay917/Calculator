package com.example.authapp.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.authapp.utils.toast

@Composable
fun ParentSettingsOfflineSmsCard(
    modifier: Modifier = Modifier,
    onCopyText: ((text: String, label: String) -> Unit)? = null,
    onSendSms: ((body: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val copyAction: (String, String) -> Unit = onCopyText ?: { text, label ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        context.toast("\"$text\" copied")
    }
    val smsAction: (String) -> Unit = onSendSms ?: { body ->
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:")
                putExtra("sms_body", body)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            context.toast("SMS app not available")
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title + Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Offline SMS Commands",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "No Net Needed",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF059669),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Command 1: #CALC#LOC (Location)
            CompactSmsCommandRow(
                commandCode = "#CALC#LOC",
                label = "Location",
                badgeColor = MaterialTheme.colorScheme.primary,
                badgeBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                onCopy = { copyAction("#CALC#LOC", "Location Command") },
                onSend = { smsAction("#CALC#LOC") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Command 2: #CALC#NET (Turn on WiFi / Mobile Data)
            CompactSmsCommandRow(
                commandCode = "#CALC#NET",
                label = "Net ON",
                badgeColor = MaterialTheme.colorScheme.primary,
                badgeBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                onCopy = { copyAction("#CALC#NET", "Net ON Command") },
                onSend = { smsAction("#CALC#NET") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Command 3: #CALC#SIREN (Siren)
            CompactSmsCommandRow(
                commandCode = "#CALC#SIREN",
                label = "Siren",
                badgeColor = MaterialTheme.colorScheme.error,
                badgeBgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                onCopy = { copyAction("#CALC#SIREN", "Siren Command") },
                onSend = { smsAction("#CALC#SIREN") }
            )
        }
    }
}

@Composable
private fun CompactSmsCommandRow(
    commandCode: String,
    label: String,
    badgeColor: Color,
    badgeBgColor: Color,
    onCopy: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = commandCode,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = badgeColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = badgeBgColor
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
                FilledTonalButton(
                    onClick = onSend,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "Send",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
