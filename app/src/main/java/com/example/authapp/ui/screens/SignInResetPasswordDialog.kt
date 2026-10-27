package com.example.authapp.ui.screens

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.utils.longToast

@Composable
fun SignInResetPasswordDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (!showDialog) return

    val context = LocalContext.current
    var resetEmail by remember { mutableStateOf("") }
    var resetLoading by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!resetLoading) {
                onDismiss()
                resetError = null
            }
        },
        title = {
            Text(text = "Reset Password", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Enter your registered email address. We will send an email with a link to reset your password.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = {
                        resetEmail = it
                        resetError = null
                    },
                    label = { Text("Email Address") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Email, contentDescription = "Email Icon")
                    },
                    singleLine = true,
                    isError = resetError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (resetError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = resetError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = resetEmail.trim()
                    if (trimmed.isBlank()) {
                        resetError = "Email is required"
                        return@Button
                    }
                    if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
                        resetError = "Please enter a valid email address"
                        return@Button
                    }
                    resetLoading = true
                    resetError = null
                    FirebaseRepository.sendPasswordResetEmail(trimmed) { success, err ->
                        resetLoading = false
                        if (success) {
                            context.longToast("Password reset link sent to $trimmed! Please check your inbox.")
                            onDismiss()
                        } else {
                            resetError = err ?: "Failed to send reset email. Please try again."
                        }
                    }
                },
                enabled = !resetLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (resetLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send Link")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    resetError = null
                },
                enabled = !resetLoading
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
