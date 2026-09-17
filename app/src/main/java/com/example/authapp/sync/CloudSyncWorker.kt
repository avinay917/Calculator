package com.example.authapp.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.FirebaseRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * CloudSyncWorker — WorkManager CoroutineWorker
 *
 * Jab bhi network available ho aur pending uploads queue mein kuch ho,
 * ye worker automatically trigger hota hai aur sab kuch Firebase Storage pe upload karta hai.
 *
 * Constraints: NetworkType.CONNECTED (sirf internet hone par chalega)
 * Retry: Exponential backoff (WorkManager auto-handle karta hai)
 */
class CloudSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val childId = AppPreferences.getUserId(appContext)
        if (childId.isEmpty()) {
            FirebaseCrashlytics.getInstance().log("[CloudSync] No childId found, skipping sync")
            return Result.success()
        }

        val pending = PendingUploadQueue.getAll(appContext)
        if (pending.isEmpty()) {
            FirebaseCrashlytics.getInstance().log("[CloudSync] Queue empty, nothing to upload")
            return Result.success()
        }

        FirebaseCrashlytics.getInstance().log("[CloudSync] Starting sync:  pending uploads")

        var anyFailed = false

        for (upload in pending) {
            val file = File(upload.filePath)
            if (!file.exists() || file.length() == 0L) {
                // File gone — stale entry remove karo
                FirebaseCrashlytics.getInstance().log("[CloudSync] Stale entry removed: ")
                PendingUploadQueue.remove(appContext, upload.id)
                continue
            }

            val success = uploadSingle(upload, file)
            if (success) {
                PendingUploadQueue.remove(appContext, upload.id)
                try { file.delete() } catch (_: Exception) {}
                FirebaseCrashlytics.getInstance().log(
                    "[CloudSync] ✅ Uploaded & queued entry removed:  (s)"
                )
            } else {
                anyFailed = true
                FirebaseCrashlytics.getInstance().log(
                    "[CloudSync] ❌ Upload failed, will retry: "
                )
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }

    private suspend fun uploadSingle(
        upload: PendingUploadQueue.PendingUpload,
        file: File
    ): Boolean = suspendCancellableCoroutine { cont ->
        try {
            FirebaseRepository.saveRecordingSession(
                childId = upload.childId,
                streamType = upload.streamType,
                durationSeconds = upload.durationSeconds,
                localFilePath = upload.filePath
            ) {
                try {
                    FirebaseRepository.uploadRecordingFile(
                        childId = upload.childId,
                        fileUri = Uri.fromFile(file),
                        streamType = upload.streamType,
                        durationSeconds = upload.durationSeconds,
                        localFilePath = upload.filePath,
                        onSuccess = { cont.resume(true) },
                        onFailure = { cont.resume(false) }
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                    cont.resume(false)
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            cont.resume(false)
        }
    }
}
