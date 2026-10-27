package com.example.authapp.ui.screens

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.authapp.data.User
import com.example.authapp.ui.components.*
import com.example.authapp.ui.viewmodel.ParentUiState
import com.example.authapp.ui.viewmodel.ParentViewModel
import com.example.authapp.utils.longToast
import com.example.authapp.utils.toast

@Composable
fun ParentDialogsHost(
    email: String,
    uiState: ParentUiState,
    viewModel: ParentViewModel,
    context: Context,
    showPairingDialog: Boolean,
    onDismissPairingDialog: () -> Unit,
    childToUnpair: User?,
    isUnpairing: Boolean,
    onDismissUnpairDialog: () -> Unit,
    onConfirmUnpair: (User) -> Unit
) {
    // Live Location Map/Dialog
    uiState.locationDialogChild?.let { childUser ->
        ChildLocationDialog(
            childUser = childUser,
            childLocation = uiState.childLocation,
            isRefreshingLocation = uiState.isRefreshingLocation,
            onDismiss = { viewModel.closeLocationDialog() },
            onRefreshLocation = { viewModel.requestLocationRefresh(childUser.uid) }
        )
    }

    // Remote Photo Snapshot Dialog
    uiState.activeSnapshotDialogChild?.let { childUser ->
        val snapshots = uiState.snapshotsMap[childUser.uid] ?: emptyList()
        ChildSnapshotDialog(
            childUser = childUser,
            snapshots = snapshots,
            statusMessage = uiState.snapshotStatusMessage,
            isCapturing = uiState.isSnapshotCapturing,
            onRequestSnapshot = { facing ->
                viewModel.requestSnapshot(childUser.uid, facing)
            },
            onDismiss = { viewModel.closeSnapshotDialog() }
        )
    }

    // Call Logs, SMS, WhatsApp, Web, Wi-Fi, Apps & SIM Activity Dialog
    uiState.activeActivityDialogChild?.let { childUser ->
        ChildActivityDialog(
            childUser = childUser,
            callLogs = uiState.callLogsMap[childUser.uid] ?: emptyList(),
            notifications = uiState.notificationsMap[childUser.uid] ?: emptyList(),
            smsLogs = uiState.smsLogsMap[childUser.uid] ?: emptyList(),
            webHistory = uiState.webHistoryMap[childUser.uid] ?: emptyList(),
            networkHistory = uiState.networkHistoryMap[childUser.uid] ?: emptyList(),
            simInfo = uiState.simInfoMap[childUser.uid],
            packageEvents = uiState.packageEventsMap[childUser.uid] ?: emptyList(),
            whatsAppLogs = uiState.whatsAppLogsMap[childUser.uid] ?: emptyList(),
            youtubeLogs = uiState.youtubeLogsMap[childUser.uid] ?: emptyList(),
            mediaGallery = uiState.mediaGalleryMap[childUser.uid] ?: emptyList(),
            fileExplorer = uiState.fileExplorerMap[childUser.uid] ?: emptyList(),
            clipboardLogs = uiState.clipboardLogsMap[childUser.uid] ?: emptyList(),
            initialTab = uiState.initialActivityTab,
            onDismiss = { viewModel.closeActivityDialog() }
        )
    }

    // Security Alerts Dialog
    uiState.activeAlertsDialogChild?.let { childUser ->
        ChildAlertsDialog(
            childUser = childUser,
            alerts = uiState.securityAlertsMap[childUser.uid] ?: emptyList(),
            onDismiss = { viewModel.closeAlertsDialog() }
        )
    }

    // Auto-Recording Schedules Dialog
    uiState.activeScheduleDialogChild?.let { childUser ->
        ChildScheduleDialog(
            childUser = childUser,
            schedules = uiState.schedulesMap[childUser.uid] ?: emptyList(),
            onSaveSchedule = { schedule ->
                viewModel.saveRecordingSchedule(childUser.uid, schedule)
                context.toast("Recording schedule saved")
            },
            onDeleteSchedule = { scheduleId ->
                viewModel.deleteRecordingSchedule(childUser.uid, scheduleId)
                context.toast("Recording schedule removed")
            },
            onDismiss = { viewModel.closeScheduleDialog() }
        )
    }

    // Pairing Code Dialog
    if (showPairingDialog) {
        PairingCodeDialog(
            parentEmail = email,
            onDismiss = onDismissPairingDialog
        )
    }

    // Unpair Child Confirmation Dialog
    childToUnpair?.let { targetChild ->
        AlertDialog(
            onDismissRequest = { if (!isUnpairing) onDismissUnpairDialog() },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Unpair Child Device?") },
            text = {
                Text("Are you sure you want to unpair \"${targetChild.name.ifEmpty { "Child Device" }}\"? This will disconnect monitoring, remote controls, and real-time streaming.")
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmUnpair(targetChild) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isUnpairing
                ) {
                    Text(if (isUnpairing) "Unpairing..." else "Unpair")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissUnpairDialog,
                    enabled = !isUnpairing
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
