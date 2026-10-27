package com.example.authapp.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.authapp.data.FileExplorerItem
import com.example.authapp.data.MediaGalleryItem
import com.example.authapp.utils.formatAsFileSize
import com.example.authapp.utils.formatTime

@Composable
fun GalleryItemDetailDialog(
    media: MediaGalleryItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = media.fileType.equals("VIDEO", ignoreCase = true) || media.fileName.endsWith(".mp4", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isVideo) Icons.Default.Videocam else Icons.Default.Image,
                contentDescription = null,
                tint = if (isVideo) Color(0xFFE91E63) else Color(0xFF9C27B0),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = media.fileName.ifEmpty { "Gallery File" },
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("File Type", media.fileType.ifEmpty { "Image" })
                InfoRow("File Size", media.sizeBytes.formatAsFileSize().ifEmpty { "Unknown" })
                val dateStr = if (media.dateAdded > 0L) media.dateAdded.formatTime() else "Recent"
                InfoRow("Date Added", dateStr)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Child Device Path:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = media.filePath.ifEmpty { "Internal Storage" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("File Path", media.filePath)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Path")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun FileItemDetailDialog(
    file: FileExplorerItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory) Color(0xFFFFA000) else Color(0xFF1976D2),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = file.name.ifEmpty { file.path.substringAfterLast("/") },
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Item Type", if (file.isDirectory) "Directory / Folder" else "File")
                val sizeStr = if (file.isDirectory) "Directory" else file.sizeBytes.formatAsFileSize().ifEmpty { "Unknown" }
                InfoRow("Size", sizeStr)
                val modifiedStr = if (file.lastModified > 0L) file.lastModified.formatTime() else "Recent"
                InfoRow("Last Modified", modifiedStr)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Full Path on Phone:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = file.path.ifEmpty { "/" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("File Path", file.path)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Path")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
