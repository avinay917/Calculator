package com.example.authapp.utils

import android.content.Context
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.UserLocation
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object OfflineLocationCache {
    private const val CACHE_FILE_NAME = "offline_locations_cache.json"
    private const val MAX_CACHED_POINTS = 500
    private val lock = Any()

    fun cacheLocation(context: Context, location: UserLocation) {
        synchronized(lock) {
            try {
                val file = File(context.filesDir, CACHE_FILE_NAME)
                val jsonArray = if (file.exists() && file.length() > 0) {
                    try {
                        JSONArray(file.readText())
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else {
                    JSONArray()
                }

                if (jsonArray.length() >= MAX_CACHED_POINTS) {
                    jsonArray.remove(0)
                }

                val obj = JSONObject().apply {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("accuracy", location.accuracy)
                    put("timestamp", location.timestamp)
                    put("provider", location.provider)
                    put("isOfflineCached", true)
                }
                jsonArray.put(obj)
                file.writeText(jsonArray.toString())
            } catch (t: Throwable) {
                FirebaseCrashlytics.getInstance().log("[OfflineLocationCache] cacheLocation error: ${t.localizedMessage}")
            }
        }
    }

    fun flushCachedLocations(context: Context, childUid: String) {
        if (childUid.isEmpty()) return
        synchronized(lock) {
            try {
                val file = File(context.filesDir, CACHE_FILE_NAME)
                if (!file.exists() || file.length() == 0L) return

                val content = file.readText()
                if (content.isBlank()) return

                val jsonArray = JSONArray(content)
                val pointsCount = jsonArray.length()
                if (pointsCount == 0) return

                val batchList = mutableListOf<UserLocation>()
                for (i in 0 until pointsCount) {
                    val obj = jsonArray.getJSONObject(i)
                    val loc = UserLocation(
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        accuracy = obj.optDouble("accuracy", 0.0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        provider = obj.optString("provider", "offline_cache")
                    )
                    batchList.add(loc)
                }
                if (batchList.isNotEmpty()) {
                    FirebaseRepository.recordLocationHistoryBatch(childUid, batchList)
                }

                file.delete()
                FirebaseCrashlytics.getInstance().log("[OfflineLocationCache] Successfully flushed $pointsCount offline location points for child: $childUid")
            } catch (t: Throwable) {
                FirebaseCrashlytics.getInstance().log("[OfflineLocationCache] flushCachedLocations error: ${t.localizedMessage}")
            }
        }
    }

    fun getCachedCount(context: Context): Int {
        synchronized(lock) {
            return try {
                val file = File(context.filesDir, CACHE_FILE_NAME)
                if (file.exists() && file.length() > 0) {
                    JSONArray(file.readText()).length()
                } else 0
            } catch (_: Exception) {
                0
            }
        }
    }
}
