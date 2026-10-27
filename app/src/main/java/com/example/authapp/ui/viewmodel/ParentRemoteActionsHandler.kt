package com.example.authapp.ui.viewmodel

import com.example.authapp.data.FirebaseControlRepository
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.GeofenceZone
import com.example.authapp.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ParentRemoteActionsHandler(
    private val uiStateFlow: MutableStateFlow<ParentUiState>
) {
    fun setSelectedChildForControls(child: User) {
        uiStateFlow.update { it.copy(selectedChildForControls = child) }
    }

    fun toggleAppBlock(childId: String, packageName: String, currentBlocked: Boolean) {
        try {
            FirebaseRepository.setAppBlocked(childId, packageName, !currentBlocked)
        } catch (_: Exception) {}
    }

    fun toggleStudyMode(childId: String, currentActive: Boolean, durationMinutes: Int = 0) {
        try {
            FirebaseRepository.setStudyMode(childId, !currentActive, durationMinutes)
        } catch (_: Exception) {}
    }

    fun toggleTorch(childId: String, currentActive: Boolean) {
        val nextState = !currentActive
        uiStateFlow.update { current ->
            val updated = current.isTorchActiveMap.toMutableMap()
            updated[childId] = nextState
            current.copy(isTorchActiveMap = updated)
        }
        try {
            FirebaseControlRepository.sendRemoteCommand(childId, "TORCH", nextState)
        } catch (_: Exception) {}
    }

    fun triggerSiren(childId: String, currentActive: Boolean) {
        val nextState = !currentActive
        uiStateFlow.update { current ->
            val updated = current.isSirenActiveMap.toMutableMap()
            updated[childId] = nextState
            current.copy(isSirenActiveMap = updated)
        }
        try {
            FirebaseControlRepository.sendRemoteCommand(childId, "SIREN", nextState)
        } catch (_: Exception) {}
    }

    fun enableRemoteInternet(childId: String) {
        if (childId.isEmpty()) return
        try {
            FirebaseControlRepository.sendRemoteCommand(childId, "INTERNET_ON", true)
        } catch (_: Exception) {}
    }

    fun setUninstallPin(childId: String, pin: String) {
        if (childId.isEmpty() || pin.length != 6) return
        FirebaseRepository.setUninstallPin(childId, pin)
    }

    fun launchRemoteApp(childId: String, packageName: String) {
        if (childId.isEmpty() || packageName.isEmpty()) return
        try {
            FirebaseControlRepository.sendRemoteCommand(childId, "OPEN_APP", packageName)
        } catch (_: Exception) {}
    }

    fun closeRemoteApp(childId: String, packageName: String = "") {
        if (childId.isEmpty()) return
        try {
            FirebaseControlRepository.sendRemoteCommand(childId, "CLOSE_APP", packageName.ifEmpty { "ALL" })
        } catch (_: Exception) {}
    }

    fun saveGeofence(childId: String, zone: GeofenceZone) {
        try {
            FirebaseRepository.saveGeofence(childId, zone)
        } catch (_: Exception) {}
    }

    fun unpairChild(childUid: String, onComplete: (Boolean, String?) -> Unit) {
        FirebaseRepository.unpairChild(childUid) { success, error ->
            if (success) {
                uiStateFlow.update { state ->
                    val remaining = state.childUsers.filter { it.uid != childUid }
                    val nextSelected = if (state.selectedChildForControls?.uid == childUid) remaining.firstOrNull() else state.selectedChildForControls
                    state.copy(
                        childUsers = remaining,
                        selectedChildForControls = nextSelected
                    )
                }
            }
            onComplete(success, error)
        }
    }

    fun cleanCloudStorage(childId: String, retentionDays: Int = 7) {
        if (childId.isEmpty()) return
        FirebaseRepository.cleanOldCloudStorage(childId, retentionDays) { deletedCount ->
            uiStateFlow.update {
                it.copy(userFeedbackMessage = "7-Day Cloud Auto-clean completed! $deletedCount old items deleted.")
            }
        }
    }
}
