package com.example.authapp.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.data.ClipboardLogItem
import com.example.authapp.utils.formatTime

@Composable
fun ClipboardListItem(item: ClipboardLogItem) {
    val context = LocalContext.current
    val timeStr = remember(item.timestamp) { item.timestamp.formatTime() }
    val displayTitle = remember(item.text) {
        val clean = item.text.trim()
        if (clean.length > 50) clean.take(47) + "..." else clean.ifEmpty { "Empty Clipboard Text" }
    }

    val copyAction = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", item.text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    ActivityLogCard(
        icon = Icons.Default.ContentCopy,
        iconTint = Color(0xFF00897B),
        title = displayTitle,
        subtitle = "${item.text.length} characters",
        timestampStr = timeStr,
        badgeText = "Clipboard",
        badgeColor = Color(0xFF00897B),
        actionIcon = Icons.Default.ContentCopy,
        onActionClick = copyAction,
        onClick = copyAction,
        extraContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Color(0xFF141419), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF272733), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFFE0E0E6)
                )
            }
        }
    )
}
