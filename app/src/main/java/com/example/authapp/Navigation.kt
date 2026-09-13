package com.example.authapp

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.ui.screens.ChildScreen
import com.example.authapp.ui.screens.ParentScreen
import com.example.authapp.ui.screens.SignInScreen
import com.example.authapp.ui.screens.SignUpScreen

@Composable
fun MainNavigation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser = FirebaseRepository.currentUser
    val cachedRole = remember(context) { com.example.authapp.data.AppPreferences.getUserRole(context) }
    val initialNavKey: Any = remember {
        if (currentUser != null && !currentUser.email.isNullOrEmpty()) {
            if (cachedRole == "parent") ParentNavKey(email = currentUser.email!!) else ChildNavKey(email = currentUser.email!!)
        } else {
            SignInNavKey
        }
    }
    val backStack = rememberNavBackStack(initialNavKey)

    fun navigateBasedOnRole(email: String) {
        val uid = FirebaseRepository.currentUser?.uid ?: return
        FirebaseRepository.getUserRoleOnce(uid) { role ->
            com.example.authapp.data.AppPreferences.saveUserSession(context, uid, email, role)
            com.example.authapp.analytics.AppHealthTelemetry.syncDeviceHealth(context)
            backStack.clear()
            if (role == "parent") {
                backStack.add(ParentNavKey(email = email))
            } else {
                backStack.add(ChildNavKey(email = email))
            }
        }
    }

    LaunchedEffect(Unit) {
        val user = FirebaseRepository.currentUser
        if (user != null && !user.email.isNullOrEmpty()) {
            FirebaseRepository.setupPresenceSystem(user.uid)
            navigateBasedOnRole(user.email!!)
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<SignInNavKey> {
                SignInScreen(
                    onSignInSuccess = { email ->
                        FirebaseRepository.currentUser?.let {
                            FirebaseRepository.setupPresenceSystem(it.uid)
                        }
                        navigateBasedOnRole(email)
                    },
                    onNavigateToSignUp = {
                        backStack.add(SignUpNavKey)
                    },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<SignUpNavKey> {
                SignUpScreen(
                    onSignUpSuccess = {
                        // Crucial: New sign-ups must log in to proceed to their panel
                        com.example.authapp.data.AppPreferences.clearSession(context)
                        FirebaseRepository.signOut()
                        backStack.clear()
                        backStack.add(SignInNavKey)
                    },
                    onNavigateToSignIn = {
                        backStack.removeLastOrNull()
                    },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<ChildNavKey> { key ->
                ChildScreen(
                    email = key.email,
                    onSignOut = {
                        com.example.authapp.analytics.AppHealthTelemetry.syncDeviceHealth(context, "STOPPED")
                        com.example.authapp.data.AppPreferences.clearSession(context)
                        FirebaseRepository.signOut()
                        backStack.clear()
                        backStack.add(SignInNavKey)
                    },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<ParentNavKey> { key ->
                ParentScreen(
                    email = key.email,
                    onSignOut = {
                        com.example.authapp.data.AppPreferences.clearSession(context)
                        FirebaseRepository.signOut()
                        backStack.clear()
                        backStack.add(SignInNavKey)
                    },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
        }
    )
}
