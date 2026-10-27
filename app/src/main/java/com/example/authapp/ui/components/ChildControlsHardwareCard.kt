package com.example.authapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ChildControlsHardwareCard(
    isTorchActive: Boolean,
    isSirenActive: Boolean,
    onToggleTorch: () -> Unit,
    onToggleSiren: () -> Unit,
    onEnableInternet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
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
                listOf(
                    Triple(isTorchActive, onToggleTorch, (if (isTorchActive) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff) to (if (isTorchActive) "Torch ON" else "Torch OFF")),
                    Triple(isSirenActive, onToggleSiren, (if (isSirenActive) Icons.Default.VolumeUp else Icons.Default.Emergency) to (if (isSirenActive) "Stop Siren" else "Play Siren")),
                    Triple(false, onEnableInternet, Icons.Default.Wifi to "Net ON")
                ).forEach { (isActive, onClick, iconLabel) ->
                    val (icon, label) = iconLabel
                    val isSiren = label.contains("Siren")
                    val colors = if (isActive) {
                        if (isSiren) ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White)
                        else ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black)
                    } else ButtonDefaults.filledTonalButtonColors()
                    FilledTonalButton(
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}
