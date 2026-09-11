package com.example.authapp

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object SignInNavKey : NavKey

@Serializable
data object SignUpNavKey : NavKey

@Serializable
data class ChildNavKey(val email: String) : NavKey

@Serializable
data class ParentNavKey(val email: String) : NavKey
