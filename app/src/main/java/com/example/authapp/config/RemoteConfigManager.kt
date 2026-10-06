package com.example.authapp.config

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

object RemoteConfigManager {

    private const val KEY_FORCE_UPDATE_VERSION_CODE = "force_update_version_code"
    private const val KEY_ENABLE_LIVE_VIDEO = "enable_live_video"
    private const val KEY_ENABLE_LIVE_SCREEN = "enable_live_screen"
    private const val KEY_LOCATION_SYNC_INTERVAL_SEC = "location_sync_interval_sec"
    private const val KEY_LOCATION_SYNC_DISTANCE_M = "location_sync_distance_m"
    private const val KEY_LOCATION_HISTORY_INTERVAL_SEC = "location_history_interval_sec"
    private const val KEY_LOCATION_HISTORY_DISTANCE_M = "location_history_distance_m"
    private const val KEY_RECORDING_RETENTION_DAYS = "recording_retention_days"

    fun init(context: Context, onConfigFetched: (() -> Unit)? = null) {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600L) // Fetch every hour or immediately on launch
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)

            val defaultMap = mapOf<String, Any>(
                KEY_FORCE_UPDATE_VERSION_CODE to 561L,
                KEY_ENABLE_LIVE_VIDEO to true,
                KEY_ENABLE_LIVE_SCREEN to true,
                KEY_LOCATION_SYNC_INTERVAL_SEC to 120L,
                KEY_LOCATION_SYNC_DISTANCE_M to 50L,
                KEY_LOCATION_HISTORY_INTERVAL_SEC to 300L,
                KEY_LOCATION_HISTORY_DISTANCE_M to 200L,
                KEY_RECORDING_RETENTION_DAYS to 14L
            )
            remoteConfig.setDefaultsAsync(defaultMap)

            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        FirebaseCrashlytics.getInstance().log("[RemoteConfigManager] Successfully fetched and activated remote config")
                    } else {
                        FirebaseCrashlytics.getInstance().log("[RemoteConfigManager] Remote config fetch failed: ${task.exception?.localizedMessage}")
                    }
                    onConfigFetched?.invoke()
                }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[RemoteConfigManager] Init error: ${e.localizedMessage}")
            onConfigFetched?.invoke()
        }
    }

    fun getForceUpdateVersionCode(): Long {
        return try {
            FirebaseRemoteConfig.getInstance().getLong(KEY_FORCE_UPDATE_VERSION_CODE)
        } catch (_: Exception) {
            561L
        }
    }

    fun isLiveVideoEnabled(): Boolean {
        return try {
            FirebaseRemoteConfig.getInstance().getBoolean(KEY_ENABLE_LIVE_VIDEO)
        } catch (_: Exception) {
            true
        }
    }

    fun isLiveScreenEnabled(): Boolean {
        return try {
            FirebaseRemoteConfig.getInstance().getBoolean(KEY_ENABLE_LIVE_SCREEN)
        } catch (_: Exception) {
            true
        }
    }

    fun getLocationSyncIntervalSec(): Long {
        return try {
            FirebaseRemoteConfig.getInstance().getLong(KEY_LOCATION_SYNC_INTERVAL_SEC).coerceIn(60L, 900L)
        } catch (_: Exception) {
            120L
        }
    }

    fun getLocationSyncDistanceMeters(): Float {
        return try {
            FirebaseRemoteConfig.getInstance().getLong(KEY_LOCATION_SYNC_DISTANCE_M)
                .toFloat().coerceIn(25f, 500f)
        } catch (_: Exception) {
            50f
        }
    }

    fun getLocationHistoryIntervalSec(): Long {
        return try {
            FirebaseRemoteConfig.getInstance().getLong(KEY_LOCATION_HISTORY_INTERVAL_SEC)
                .coerceIn(60L, 3600L)
        } catch (_: Exception) {
            300L
        }
    }

    fun getLocationHistoryDistanceMeters(): Float {
        return try {
            FirebaseRemoteConfig.getInstance().getLong(KEY_LOCATION_HISTORY_DISTANCE_M)
                .toFloat().coerceIn(50f, 1000f)
        } catch (_: Exception) {
            200f
        }
    }

    fun getRecordingRetentionDays(): Int {
        return try {
            FirebaseRemoteConfig.getInstance().getLong(KEY_RECORDING_RETENTION_DAYS)
                .toInt().coerceIn(1, 30)
        } catch (_: Exception) {
            14
        }
    }
}
