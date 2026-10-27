package com.example.authapp.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.authapp.data.FirebaseRepository
import kotlinx.coroutines.delay

@Composable
fun PairingCodeDialog(
    parentEmail: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentUid = FirebaseRepository.currentUser?.uid ?: ""
    var pairingCode by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var childCodeInput by remember { mutableStateOf("") }
    var isLinkingChild by remember { mutableStateOf(false) }
    var linkChildError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLinkedSuccess by remember { mutableStateOf(false) }

    fun linkChildDevice() {
        val trimmed = childCodeInput.trim()
        if (trimmed.length != 6) {
            linkChildError = "Please enter full 6-digit code from Child screen"
            return
        }
        isLinkingChild = true
        linkChildError = null
        FirebaseRepository.pairParentWithChildCode(currentUid, parentEmail, trimmed) { success, _, _, err ->
            isLinkingChild = false
            if (success) {
                isLinkedSuccess = true
            } else {
                linkChildError = err ?: "Failed to link child device"
            }
        }
    }

    fun loadCode() {
        if (currentUid.isEmpty()) {
            errorMessage = "Parent account not authenticated"
            isLoading = false
            return
        }
        isLoading = true
        errorMessage = null
        isLinkedSuccess = false
        FirebaseRepository.getOrGenerateParentPairingCode(currentUid, parentEmail) { code, err ->
            isLoading = false
            if (code != null) {
                pairingCode = code
            } else {
                errorMessage = err ?: "Failed to generate pairing code"
            }
        }
    }

    fun generateNewCode() {
        if (currentUid.isEmpty()) return
        isLoading = true
        errorMessage = null
        FirebaseRepository.generateNewParentPairingCode(currentUid, parentEmail) { code, err ->
            isLoading = false
            if (code != null) {
                pairingCode = code
                Toast.makeText(context, "New pairing code generated!", Toast.LENGTH_SHORT).show()
            } else {
                errorMessage = err ?: "Failed to generate pairing code"
            }
        }
    }

    LaunchedEffect(Unit) { loadCode() }

    DisposableEffect(pairingCode) {
        val code = pairingCode
        val listener = if (!code.isNullOrEmpty()) {
            FirebaseRepository.listenToPairingCodeStatus(code) { linkedChildUid ->
                if (linkedChildUid.isNotEmpty()) isLinkedSuccess = true
            }
        } else null
        onDispose {
            if (!code.isNullOrEmpty() && listener != null) {
                FirebaseRepository.removeValueListener("pairing_codes/$code/linkedChildUid", listener)
            }
        }
    }

    if (isLinkedSuccess) {
        LaunchedEffect(Unit) {
            delay(2000)
            onDismiss()
        }
    }

    fun copyToClipboard(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Pairing Code", code)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Pairing code $code copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Pair New Child Device",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Switcher
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Enter Child Code", style = MaterialTheme.typography.labelMedium) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("My Parent Code", style = MaterialTheme.typography.labelMedium) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLinkedSuccess) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Child Device Linked!",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF2E7D32),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Child phone has been successfully paired. Redirecting to dashboard...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1B5E20),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (selectedTab == 0) {
                    PairingCodeEnterChildTab(
                        childCodeInput = childCodeInput,
                        onChildCodeChange = { childCodeInput = it },
                        isLinkingChild = isLinkingChild,
                        linkChildError = linkChildError,
                        onVerifyAndLink = { linkChildDevice() }
                    )
                } else {
                    PairingCodeParentCodeTab(
                        pairingCode = pairingCode,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onCopyCode = { code -> copyToClipboard(code) },
                        onGenerateNewCode = { generateNewCode() },
                        onRetry = { loadCode() }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
