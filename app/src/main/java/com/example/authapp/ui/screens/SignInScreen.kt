package com.example.authapp.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.authapp.analytics.AppAnalytics
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.utils.toast

@Composable
fun SignInScreen(
    onSignInSuccess: (email: String) -> Unit,
    onNavigateToSignUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authPrefs = remember { context.getSharedPreferences("auth_login_remember", Context.MODE_PRIVATE) }
    val initialRemember = remember { authPrefs.getBoolean("remember_me", false) }
    var rememberMe by remember { mutableStateOf(initialRemember) }
    var email by remember { mutableStateOf(if (initialRemember) authPrefs.getString("saved_email", "") ?: "" else "") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var isUnverifiedEmail by remember { mutableStateOf(false) }
    var resendCooldown by remember { mutableStateOf(0) }

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            kotlinx.coroutines.delay(1000L)
            resendCooldown -= 1
        }
    }

    fun handleResendVerification() {
        if (resendCooldown > 0) return
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            errorMessage = "Kripya email aur password bharein"
            return
        }
        com.google.firebase.auth.FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(trimmedEmail, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result?.user?.sendEmailVerification()
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    resendCooldown = 60
                    context.toast("Naya verification link aapke email par bhej diya gaya hai!")
                } else {
                    errorMessage = task.exception?.localizedMessage ?: "Failed to resend verification link"
                }
            }
    }

    fun validateAndSubmit() {
        if (isLoading) return
        var isValid = true
        emailError = null
        passwordError = null
        errorMessage = null
        isUnverifiedEmail = false

        if (email.isBlank()) {
            emailError = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Please enter a valid email address"
            isValid = false
        }

        if (password.isBlank()) {
            passwordError = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordError = "Password must be at least 6 characters"
            isValid = false
        }

        if (isValid) {
            isLoading = true
            AppAnalytics.logButtonClick("sign_in", "SignInScreen")
            FirebaseRepository.signIn(email, password) { success, errorMsg ->
                if (success) {
                    if (rememberMe) {
                        authPrefs.edit().putBoolean("remember_me", true).putString("saved_email", email.trim()).apply()
                    } else {
                        authPrefs.edit().clear().apply()
                    }
                    context.toast("Sign In Successful! Loading dashboard...")
                    AppAnalytics.logFeatureUsage("sign_in", "success", mapOf("email" to email))
                    onSignInSuccess(email)
                } else {
                    isLoading = false
                    errorMessage = errorMsg ?: "Authentication failed"
                    if (errorMsg?.contains("verify", ignoreCase = true) == true) {
                        isUnverifiedEmail = true
                        resendCooldown = 60
                    }
                    AppAnalytics.logActionFailure("sign_in", "SignInScreen", errorMsg ?: "Authentication failed")
                }
            }
        } else {
            errorMessage = "Please fix errors below to continue"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = "App Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Sign in to access your account",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        SignInFormInputs(
            email = email,
            onEmailChange = {
                email = it
                if (emailError != null) emailError = null
            },
            emailError = emailError,
            password = password,
            onPasswordChange = {
                password = it
                if (passwordError != null) passwordError = null
            },
            passwordError = passwordError,
            isPasswordVisible = isPasswordVisible,
            onTogglePasswordVisibility = { isPasswordVisible = !isPasswordVisible },
            rememberMe = rememberMe,
            onRememberMeChange = { rememberMe = it },
            errorMessage = errorMessage,
            onForgotPasswordClick = { showResetDialog = true },
            isUnverifiedEmail = isUnverifiedEmail,
            resendCooldownSeconds = resendCooldown,
            onResendVerificationClick = { handleResendVerification() }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { validateAndSubmit() },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(text = "Sign In", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "  OR  ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Don't have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onNavigateToSignUp,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "Sign Up",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }

    SignInResetPasswordDialog(
        showDialog = showResetDialog,
        onDismiss = { showResetDialog = false }
    )
}
