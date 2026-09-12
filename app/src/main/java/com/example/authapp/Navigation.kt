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
    val backStack = rememberNavBackStack(SignInNavKey)

    fun navigateBasedOnRole(email: String) {
        val uid = FirebaseRepository.currentUser?.uid ?: return
        FirebaseRepository.getUserRoleOnce(uid) { role ->
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
