package com.example.authapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
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
import com.example.authapp.utils.longToast

@Composable
fun SignUpScreen(
    onSignUpSuccess: (email: String) -> Unit,
    onNavigateToSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var agreeToTerms by remember { mutableStateOf(false) }

    var fullNameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var termsError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun validateAndSubmit() {
        if (isLoading) return
        var isValid = true
        fullNameError = null
        emailError = null
        passwordError = null
        confirmPasswordError = null
        termsError = null
        errorMessage = null

        if (fullName.isBlank()) {
            fullNameError = "Full Name is required"
            isValid = false
        }

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

        if (confirmPassword.isBlank()) {
            confirmPasswordError = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordError = "Passwords do not match"
            isValid = false
        }

        if (!agreeToTerms) {
            termsError = "You must agree to the Terms & Conditions"
            isValid = false
        }

        if (isValid) {
            isLoading = true
            AppAnalytics.logButtonClick("sign_up", "SignUpScreen")
            FirebaseRepository.signUp(fullName, email, password) { success, errorMsg ->
                isLoading = false
                if (success) {
                    context.longToast("Account created successfully! Welcome aboard.")
                    AppAnalytics.logFeatureUsage("sign_up", "success", mapOf("email" to email))
                    onSignUpSuccess(email)
                } else {
                    errorMessage = errorMsg ?: "Sign Up failed. Please try again."
                    AppAnalytics.logActionFailure("sign_up", "SignUpScreen", errorMsg ?: "Sign Up failed")
                }
            }
        } else {
            errorMessage = "Please fill all required fields correctly"
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
                imageVector = Icons.Default.PersonAdd,
                contentDescription = "Sign Up Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Fill in your details to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        SignUpFormInputs(
            fullName = fullName,
            onFullNameChange = {
                fullName = it
                if (fullNameError != null) fullNameError = null
            },
            fullNameError = fullNameError,
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
            confirmPassword = confirmPassword,
            onConfirmPasswordChange = {
                confirmPassword = it
                if (confirmPasswordError != null) confirmPasswordError = null
            },
            confirmPasswordError = confirmPasswordError,
            isPasswordVisible = isPasswordVisible,
            onTogglePasswordVisibility = { isPasswordVisible = !isPasswordVisible },
            isConfirmPasswordVisible = isConfirmPasswordVisible,
            onToggleConfirmPasswordVisibility = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
            agreeToTerms = agreeToTerms,
            onAgreeToTermsChange = {
                agreeToTerms = it
                if (termsError != null) termsError = null
            },
            termsError = termsError,
            errorMessage = errorMessage
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
                Text(text = "Sign Up", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Already have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onNavigateToSignIn,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "Sign In",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
