package com.example.authapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.authapp.data.SnapshotInfo
import com.example.authapp.data.User
import com.example.authapp.utils.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSnapshotDialog(
    childUser: User,
    snapshots: List<SnapshotInfo>,
    statusMessage: String?,
    isCapturing: Boolean = false,
    onRequestSnapshot: (cameraFacing: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSnapshot by remember { mutableStateOf<SnapshotInfo?>(null) }

    AppFullScreenDialog(
        title = "${childUser.name.ifEmpty { "Child" }} • Camera Snapshots",
        subtitle = "Silent Remote Photos (${snapshots.size} captured)",
        onDismiss = onDismiss
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Action Buttons Card & Status Alert
            ChildSnapshotCaptureCard(
                isCapturing = isCapturing,
                statusMessage = statusMessage,
                onRequestSnapshot = onRequestSnapshot
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Captured Gallery (${snapshots.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (snapshots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No snapshots captured yet.\nTap 'Rear Camera' or 'Front Selfie' above to take a photo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(snapshots) { snap ->
                        val timeStr = remember(snap.timestamp) { snap.timestamp.formatTime() }
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { selectedSnapshot = snap }
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                RemoteImage(
                                    url = snap.downloadUrl,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = if (snap.cameraFacing == "front") "Front" else "Rear",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Image Preview Zoom Dialog
    ChildSnapshotZoomDialog(
        snapshot = selectedSnapshot,
        onDismiss = { selectedSnapshot = null }
    )
}
