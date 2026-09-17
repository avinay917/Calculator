package com.example.authapp.ui.viewmodel

import com.example.authapp.data.User

/**
 * Consolidated Sealed Interface for Active Dialog States in Parent UI (Point 6).
 * Replaces 10+ separate Boolean variables in ParentUiState.
 */
sealed interface ActiveDialogState {
    object None : ActiveDialogState
    data class Location(val child: User) : ActiveDialogState
    data class Snapshot(val child: User) : ActiveDialogState
    data class Activity(val child: User, val initialTab: Int = 0) : ActiveDialogState
    data class Alerts(val child: User) : ActiveDialogState
    data class Schedule(val child: User) : ActiveDialogState
}
