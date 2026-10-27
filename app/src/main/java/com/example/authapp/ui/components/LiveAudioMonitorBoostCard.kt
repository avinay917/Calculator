package com.example.authapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.authapp.webrtc.WebRtcManager

@Composable
fun LiveAudioMonitorBoostCard(
    audioSensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Far-Voice Boost (दूर की आवाज़)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${WebRtcManager.getGainMultiplierText(audioSensitivity)} (${audioSensitivity.toInt()}%)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF10B981)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = audioSensitivity,
                onValueChange = onSensitivityChange,
                valueRange = 10f..100f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF10B981),
                    activeTrackColor = Color(0xFF10B981)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Sensitivity preset chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { onSensitivityChange(10f) },
                    label = { Text("1.0x Normal", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { onSensitivityChange(50f) },
                    label = { Text("3.0x Far Speech", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = { onSensitivityChange(100f) },
                    label = { Text("5.0x Whisper", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
