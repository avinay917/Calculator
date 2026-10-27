package com.example.authapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ChildSnapshotCaptureCard(
    isCapturing: Boolean,
    statusMessage: String?,
    onRequestSnapshot: (cameraFacing: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFacing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isCapturing) {
        if (!isCapturing) activeFacing = null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Take Silent Photo Now",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Instantly capture environment without preview on child's screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            activeFacing = "back"
                            onRequestSnapshot("back")
                        },
                        enabled = !isCapturing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isCapturing && activeFacing == "back") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Capturing...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rear Camera")
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            activeFacing = "front"
                            onRequestSnapshot("front")
                        },
                        enabled = !isCapturing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isCapturing && activeFacing == "front") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Capturing...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Front Selfie")
                        }
                    }
                }
            }
        }

        if (!statusMessage.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val isError = statusMessage.contains("Failed", ignoreCase = true) ||
                    statusMessage.contains("Error", ignoreCase = true) ||
                    statusMessage.contains("not respond", ignoreCase = true)
            val isSuccess = statusMessage.contains("successfully", ignoreCase = true) ||
                    statusMessage.contains("received", ignoreCase = true)

            val containerColor = when {
                isError -> MaterialTheme.colorScheme.errorContainer
                isSuccess -> Color(0xFFE8F5E9)
                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            }
            val contentColor = when {
                isError -> MaterialTheme.colorScheme.onErrorContainer
                isSuccess -> Color(0xFF2E7D32)
                else -> MaterialTheme.colorScheme.primary
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = containerColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = contentColor
                        )
                    } else if (isSuccess) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                    } else if (isError) {
                        Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor
                    )
                }
            }
        }
    }
}
