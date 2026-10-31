package com.example.authapp.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.authapp.data.RecordingSession
import com.example.authapp.data.User
import com.example.authapp.ui.components.ChildControlsView
import com.example.authapp.ui.components.ChildSchedulesView
import com.example.authapp.ui.components.CloudRecordingsView
import com.example.authapp.ui.components.ParentSettingsView
import com.example.authapp.ui.viewmodel.ParentUiState
import com.example.authapp.ui.viewmodel.ParentViewModel
import com.example.authapp.utils.Constants
import com.example.authapp.utils.toast

@Composable
fun ParentTabsContent(
    email: String,
    uiState: ParentUiState,
    viewModel: ParentViewModel,
    context: Context,
    onOpenDrawer: () -> Unit,
    onPairClick: () -> Unit,
    onSignOut: () -> Unit,
    onPlayRecording: (RecordingSession) -> Unit,
    onStopPlayback: () -> Unit,
    onUnpairClick: ((User) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    when (uiState.selectedTab) {
        0 -> {
            ParentLiveTab(
                email = email,
                uiState = uiState,
                onOpenDrawer = onOpenDrawer,
                onPairClick = onPairClick,
                onSignOut = onSignOut,
                onAudioClick = { child ->
                    viewModel.startStream(child, Constants.StreamTypes.AUDIO_ONLY)
                    context.toast("Requesting Audio Stream from ${child.name}...")
                },
                onVideoClick = { child ->
                    viewModel.startStream(child, Constants.StreamTypes.CAMERA_VIDEO)
                    context.toast("Requesting Video Stream from ${child.name}...")
                },
                onScreenClick = { child ->
                    viewModel.startStream(child, Constants.StreamTypes.SCREEN_MIRROR)
                    context.toast("Requesting Live Screen Mirror from ${child.name}...")
                },
                onLocationClick = { child -> viewModel.openLocationDialog(child) },
                onSnapshotClick = { child -> viewModel.openSnapshotDialog(child) },
                onActivityClick = { child, tab -> viewModel.openActivityDialog(child, initialTab = tab) },
                onAlertsClick = { child -> viewModel.openAlertsDialog(child) },
                onScheduleClick = { child -> viewModel.openScheduleDialog(child) },
                onUnlinkClick = onUnpairClick,
                modifier = modifier
            )
        }
        1 -> {
            val selectedChild = uiState.selectedChildForControls ?: uiState.childUsers.firstOrNull()
            val targetChildId = selectedChild?.uid ?: ""
            ChildControlsView(
                childUsers = uiState.childUsers,
                selectedChild = selectedChild,
                onSelectChild = viewModel::setSelectedChildForControls,
                appUsageList = uiState.appUsageMap[targetChildId] ?: emptyList(),
                parentControls = uiState.parentControlsMap[targetChildId],
                isTorchActive = uiState.isTorchActiveMap[targetChildId] == true,
                isSirenActive = uiState.isSirenActiveMap[targetChildId] == true,
                onToggleTorch = {
                    if (targetChildId.isNotEmpty()) {
                        val cur = uiState.isTorchActiveMap[targetChildId] == true
                        viewModel.toggleTorch(targetChildId, cur)
                        context.toast(if (!cur) "Turning Torch ON..." else "Turning Torch OFF...")
                    }
                },
                onToggleSiren = {
                    if (targetChildId.isNotEmpty()) {
                        val cur = uiState.isSirenActiveMap[targetChildId] == true
                        viewModel.triggerSiren(targetChildId, cur)
                        context.toast(if (!cur) "Triggering Emergency Siren..." else "Stopping Siren...")
                    }
                },
                onToggleAppBlock = { pkg, isBlocked ->
                    if (targetChildId.isNotEmpty()) {
                        viewModel.toggleAppBlock(targetChildId, pkg, isBlocked)
                        context.toast("App ${if (isBlocked) "Unlocked" else "Locked"} successfully")
                    }
                },
                onToggleStudyMode = { isActive, durationMins ->
                    if (targetChildId.isNotEmpty()) {
                        viewModel.toggleStudyMode(targetChildId, isActive, durationMins)
                        context.toast("Study Mode ${if (isActive) "Deactivated" else "Activated"}")
                    }
                },
                onLaunchApp = { pkg ->
                    if (targetChildId.isNotEmpty()) {
                        viewModel.launchRemoteApp(targetChildId, pkg)
                        context.toast("Opening app on child device...")
                    }
                },
                onCloseApp = { pkg ->
                    if (targetChildId.isNotEmpty()) {
                        viewModel.closeRemoteApp(targetChildId, pkg)
                        context.toast(if (pkg.isEmpty()) "Returning child device to Home..." else "Closing app on child device...")
                    }
                },
                modifier = modifier.fillMaxSize()
            )
        }
        2 -> {
            CloudRecordingsView(
                recordings = uiState.allRecordings,
                childUsers = uiState.childUsers,
                isLoading = uiState.isLoadingRecordings,
                currentFilter = uiState.recordingFilter,
                onFilterChange = { filter -> viewModel.setRecordingFilter(filter) },
                currentlyPlayingRecId = uiState.currentlyPlayingRecId,
                onPlayRecording = onPlayRecording,
                onStopPlayback = onStopPlayback,
                modifier = modifier.fillMaxSize()
            )
        }
        3 -> {
            ChildSchedulesView(
                childUsers = uiState.childUsers,
                schedulesMap = uiState.schedulesMap,
                onSaveSchedule = { childId, schedule ->
                    viewModel.saveRecordingSchedule(childId, schedule)
                    context.toast("Schedule saved! Offline active.")
                },
                onDeleteSchedule = { childId, scheduleId ->
                    viewModel.deleteRecordingSchedule(childId, scheduleId)
                    context.toast("Schedule deleted")
                },
                modifier = modifier.fillMaxSize()
            )
        }
        else -> {
            val selectedChild = uiState.selectedChildForControls ?: uiState.childUsers.firstOrNull()
            val targetChildId = selectedChild?.uid ?: ""
            ParentSettingsView(
                parentEmail = email,
                selectedChild = selectedChild,
                parentControls = uiState.parentControlsMap[targetChildId],
                simInfo = uiState.simInfoMap[targetChildId],
                isTorchActive = uiState.isTorchActiveMap[targetChildId] == true,
                isSirenActive = uiState.isSirenActiveMap[targetChildId] == true,
                onSetUninstallPin = { newPin ->
                    if (targetChildId.isNotEmpty()) {
                        viewModel.setUninstallPin(targetChildId, newPin)
                        context.toast("Uninstall PIN updated to $newPin")
                    }
                },
                onToggleTorch = {
                    if (targetChildId.isNotEmpty()) {
                        val cur = uiState.isTorchActiveMap[targetChildId] == true
                        viewModel.toggleTorch(targetChildId, cur)
                        context.toast(if (!cur) "Turning Torch ON..." else "Turning Torch OFF...")
                    }
                },
                onToggleSiren = {
                    if (targetChildId.isNotEmpty()) {
                        val cur = uiState.isSirenActiveMap[targetChildId] == true
                        viewModel.triggerSiren(targetChildId, cur)
                        context.toast(if (!cur) "Triggering Emergency Siren..." else "Stopping Siren...")
                    }
                },
                onEnableInternet = {
                    if (targetChildId.isNotEmpty()) {
                        viewModel.enableRemoteInternet(targetChildId)
                        context.toast("Triggering Internet/Wi-Fi ON on Child Device...")
                    }
                },
                onRefreshLocation = {
                    if (targetChildId.isNotEmpty()) {
                        viewModel.requestLocationRefresh(targetChildId)
                        context.toast("Requesting GPS Location from Child Device...")
                    } else {
                        context.toast("Please select a child device first")
                    }
                },
                onCleanCloudStorage = { days ->
                    if (targetChildId.isNotEmpty()) {
                        context.toast("Starting $days-Day Cloud Storage Cleanup...")
                        viewModel.cleanCloudStorage(targetChildId, days)
                    } else {
                        context.toast("Please select a child device first")
                    }
                },
                onSignOut = onSignOut,
                modifier = modifier.fillMaxSize()
            )
        }
    }
}
