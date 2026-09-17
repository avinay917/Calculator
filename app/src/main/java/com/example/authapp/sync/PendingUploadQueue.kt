package com.example.authapp.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Offline-first pending upload queue.
 * Jab internet band ho aur recording complete ho, file path yahan save hota hai.
 * Jab internet wapas aaye, CloudSyncWorker sab upload karta hai.
 */
object PendingUploadQueue {

    private const val PREFS_NAME = "pending_upload_queue_v1"
    private const val KEY_QUEUE = "queue"
    private const val MAX_QUEUE_SIZE = 50

    data class PendingUpload(
        val id: String = UUID.randomUUID().toString(),
        val filePath: String,
        val childId: String,
        val streamType: String,
        val durationSeconds: Long,
        val recordedAtMillis: Long = System.currentTimeMillis()
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun addPending(context: Context, upload: PendingUpload) {
        try {
            val prefs = getPrefs(context)
            val existing = getAllInternal(prefs).toMutableList()
            if (existing.any { it.filePath == upload.filePath }) return
            if (existing.size >= MAX_QUEUE_SIZE) existing.removeAt(0)
            existing.add(upload)
            saveAll(prefs, existing)
            FirebaseCrashlytics.getInstance().log(
                "[PendingQueue] Added: ${upload.streamType} | size="
            )
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    @Synchronized
    fun getAll(context: Context): List<PendingUpload> {
        return try { getAllInternal(getPrefs(context)) }
        catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            emptyList()
        }
    }

    @Synchronized
    fun remove(context: Context, uploadId: String) {
        try {
            val prefs = getPrefs(context)
            val remaining = getAllInternal(prefs).filter { it.id != uploadId }
            saveAll(prefs, remaining)
            FirebaseCrashlytics.getInstance().log("[PendingQueue] Removed id=, remaining=")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    @Synchronized
    fun size(context: Context): Int = getAll(context).size

    @Synchronized
    fun isEmpty(context: Context): Boolean = getAll(context).isEmpty()

    private fun getAllInternal(prefs: SharedPreferences): List<PendingUpload> {
        val json = prefs.getString(KEY_QUEUE, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<PendingUpload>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                PendingUpload(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    filePath = obj.getString("filePath"),
                    childId = obj.getString("childId"),
                    streamType = obj.getString("streamType"),
                    durationSeconds = obj.getLong("durationSeconds"),
                    recordedAtMillis = obj.optLong("recordedAtMillis", System.currentTimeMillis())
                )
            )
        }
        return list
    }

    private fun saveAll(prefs: SharedPreferences, uploads: List<PendingUpload>) {
        val array = JSONArray()
        for (u in uploads) {
            array.put(JSONObject().apply {
                put("id", u.id)
                put("filePath", u.filePath)
                put("childId", u.childId)
                put("streamType", u.streamType)
                put("durationSeconds", u.durationSeconds)
                put("recordedAtMillis", u.recordedAtMillis)
            })
        }
        prefs.edit().putString(KEY_QUEUE, array.toString()).apply()
    }
}
