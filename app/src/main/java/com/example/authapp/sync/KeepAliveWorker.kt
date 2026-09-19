package com.example.authapp.sync

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.service.ChildForegroundService
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.TimeUnit

class KeepAliveWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val uid = AppHealthTelemetry.getEffectiveUserId(context)
        if (uid.isEmpty()) {
            return Result.success()
        }

        try {
            FirebaseCrashlytics.getInstance().log("[KeepAliveWorker] Periodic watchdog check executing for: $uid")

            // Re-sync presence and device telemetry
            FirebaseRepository.setupPresenceSystem(uid)
            AppHealthTelemetry.syncDeviceHealth(context)

            // Revive background monitoring service if stopped
            val serviceIntent = Intent(context, ChildForegroundService::class.java).apply {
                action = ChildForegroundService.ACTION_START_MONITORING
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[KeepAliveWorker] Start service notice: ${e.localizedMessage}")
            }

            return Result.success()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            return Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "child_service_keep_alive_periodic"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
            FirebaseCrashlytics.getInstance().log("[KeepAliveWorker] Periodic 15-minute watchdog scheduled")
        }
    }
}
