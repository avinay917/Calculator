package com.example.authapp.service

import android.content.Intent

/**
 * Sealed Interface for ChildForegroundService Intent Actions (Point 2).
 * Replaces verbose if-else intent action checks with type-safe pattern matching.
 */
sealed interface ServiceAction {
    object StartMonitoring : ServiceAction
    data class StartStream(val streamType: String, val sessionId: String) : ServiceAction
    object StopStream : ServiceAction
    object StopService : ServiceAction
    data class StartCallRecording(val phoneNumber: String) : ServiceAction
    object StopCallRecording : ServiceAction
    object ScheduledRecording : ServiceAction
    data class TakeSnapshot(val cameraFacing: String) : ServiceAction
    data class ExecuteCommand(val command: String, val value: String) : ServiceAction
    object Unknown : ServiceAction

    companion object {
        fun fromIntent(intent: Intent?): ServiceAction {
            if (intent == null || intent.action == null) return Unknown
            return when (intent.action) {
                ChildForegroundService.ACTION_START_MONITORING -> StartMonitoring
                ChildForegroundService.ACTION_START -> {
                    val streamType = intent.getStringExtra(ChildForegroundService.EXTRA_STREAM_TYPE) ?: "video"
                    val sessionId = intent.getStringExtra(ChildForegroundService.EXTRA_SESSION_ID) ?: ""
                    StartStream(streamType, sessionId)
                }
                ChildForegroundService.ACTION_STOP_STREAM -> StopStream
                ChildForegroundService.ACTION_STOP -> StopService
                ChildForegroundService.ACTION_START_CALL_RECORDING -> {
                    val phone = intent.getStringExtra(ChildForegroundService.EXTRA_PHONE_NUMBER) ?: ""
                    StartCallRecording(phone)
                }
                ChildForegroundService.ACTION_STOP_CALL_RECORDING -> StopCallRecording
                ChildForegroundService.ACTION_SCHEDULED_RECORDING -> ScheduledRecording
                ChildForegroundService.ACTION_TAKE_SNAPSHOT -> {
                    val camera = intent.getStringExtra(ChildForegroundService.EXTRA_CAMERA_FACING) ?: "back"
                    TakeSnapshot(camera)
                }
                ChildForegroundService.ACTION_EXECUTE_COMMAND -> {
                    val cmd = intent.getStringExtra(ChildForegroundService.EXTRA_COMMAND) ?: ""
                    val valStr = intent.getStringExtra(ChildForegroundService.EXTRA_COMMAND_VALUE) ?: ""
                    ExecuteCommand(cmd, valStr)
                }
                else -> Unknown
            }
        }
    }
}
