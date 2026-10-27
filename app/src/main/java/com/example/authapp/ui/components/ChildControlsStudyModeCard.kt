package com.example.authapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ChildControlsStudyModeCard(
    isStudyModeActive: Boolean,
    onToggleStudyMode: (isActive: Boolean, durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isStudyModeActive) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isStudyModeActive) Color(0xFFFF9800) else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isStudyModeActive) Icons.Default.Lock else Icons.Default.School,
                        contentDescription = null,
                        tint = if (isStudyModeActive) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Study Mode / Device Freeze",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isStudyModeActive) "🔒 Phone Screen Frozen for Studies" else "Freezes screen & blocks all non-essential apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isStudyModeActive) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isStudyModeActive,
                    onCheckedChange = { onToggleStudyMode(isStudyModeActive, 0) }
                )
            }

            AnimatedVisibility(visible = !isStudyModeActive) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Quick Freeze Duration:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(15 to "15 min", 30 to "30 min", 60 to "1 hr", 120 to "2 hr").forEach { (mins, label) ->
                            OutlinedButton(
                                onClick = { onToggleStudyMode(false, mins) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
