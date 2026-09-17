package com.example.authapp.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.authapp.data.FirebaseRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * SyncScheduler — Central entry point for offline-aware recording uploads.
 *
 * Usage:
 *   SyncScheduler.enqueueUpload(context, file, childId, "audio", durationSec)
 *
 * Agar net hai → direct Firebase upload
 * Agar net nahi → local queue mein save + WorkManager enqueue (net aane par auto-trigger)
 */
object SyncScheduler {

    private const val SYNC_WORK_NAME = "cloud_sync_pending_uploads"

    /**
     * Main entry point — recording complete hone par yahan call karo.
     * Network check karke decide karta hai: direct upload ya queue.
     */
    fun enqueueUpload(
        context: Context,
        file: File,
        childId: String,
        streamType: String,
        durationSeconds: Long
    ) {
        if (!file.exists() || file.length() == 0L) {
            FirebaseCrashlytics.getInstance().log("[SyncScheduler] Empty/missing file skipped: ")
            return
        }

        if (isNetworkAvailable(context)) {
            // ✅ Net hai → directly upload karo
            FirebaseCrashlytics.getInstance().log("[SyncScheduler] Network available — direct upload: ")
            directUpload(context, file, childId, streamType, durationSeconds)
        } else {
            // ❌ Net nahi → queue mein daalo + WorkManager schedule karo
            FirebaseCrashlytics.getInstance().log("[SyncScheduler] Offline — queuing for later:  | ")
            val pending = PendingUploadQueue.PendingUpload(
                filePath = file.absolutePath,
                childId = childId,
                streamType = streamType,
                durationSeconds = durationSeconds
            )
            PendingUploadQueue.addPending(context, pending)
            scheduleWorker(context)
        }
    }

    /**
     * WorkManager ko trigger karo — pending queue upload ke liye.
     * Network available hone par automatically chalega.
     */
    fun scheduleWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP, // Agar pehle se scheduled hai toh dobara na daalo
            workRequest
        )
        FirebaseCrashlytics.getInstance().log("[SyncScheduler] CloudSyncWorker enqueued (waiting for network)")
    }

    // --- Private helpers ---

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }

    private fun directUpload(
        context: Context,
        file: File,
        childId: String,
        streamType: String,
        durationSeconds: Long
    ) {
        try {
            FirebaseRepository.saveRecordingSession(
                childId = childId,
                streamType = streamType,
                durationSeconds = durationSeconds,
                localFilePath = file.absolutePath
            ) {
                try {
                    FirebaseRepository.uploadRecordingFile(
                        childId = childId,
                        fileUri = android.net.Uri.fromFile(file),
                        streamType = streamType,
                        durationSeconds = durationSeconds,
                        localFilePath = file.absolutePath,
                        onSuccess = {
                            FirebaseCrashlytics.getInstance().log("[SyncScheduler] ✅ Direct upload success: ")
                            try { file.delete() } catch (_: Exception) {}
                        },
                        onFailure = { err ->
                            // Direct upload failed → queue mein daalo for retry
                            FirebaseCrashlytics.getInstance().log("[SyncScheduler] Direct upload failed, queuing: ")
                            val pending = PendingUploadQueue.PendingUpload(
                                filePath = file.absolutePath,
                                childId = childId,
                                streamType = streamType,
                                durationSeconds = durationSeconds
                            )
                            PendingUploadQueue.addPending(context, pending)
                            scheduleWorker(context)
                        }
                    )
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                    // Exception bhi → queue
                    val pending = PendingUploadQueue.PendingUpload(
                        filePath = file.absolutePath,
                        childId = childId,
                        streamType = streamType,
                        durationSeconds = durationSeconds
                    )
                    PendingUploadQueue.addPending(context, pending)
                    scheduleWorker(context)
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
