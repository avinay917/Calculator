package com.example.authapp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Backfills a small recent window; receiver events handle real-time updates. */
object CallLogSyncManager {
    private const val PREFS = "call_log_sync"
    private const val LAST_SYNC = "last_sync_at"
    private const val SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val MAX_ITEMS = 50
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncIfDue(context: Context, childId: String) {
        if (childId.isBlank() || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(LAST_SYNC, 0L) < SYNC_INTERVAL_MS) return
        prefs.edit().putLong(LAST_SYNC, now).apply()

        scope.launch {
            try {
                val items = mutableListOf<CallLogItem>()
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls._ID,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION
                    ),
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext() && items.size < MAX_ITEMS) {
                        val typeValue = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                        val type = when (typeValue) {
                            CallLog.Calls.MISSED_TYPE -> "MISSED"
                            CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                            CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                            else -> "INCOMING"
                        }
                        items += CallLogItem(
                            id = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls._ID)).orEmpty(),
                            number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)).orEmpty(),
                            name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)).orEmpty(),
                            type = type,
                            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)),
                            durationSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                        )
                    }
                }
                items.forEach { FirebaseRepository.logSingleCallLog(childId, it) }
            } catch (error: Exception) {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(error)
            }
        }
    }
}
