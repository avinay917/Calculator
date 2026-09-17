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
                KEY_LOCATION_SYNC_INTERVAL_SEC to 30L
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
            FirebaseRemoteConfig.getInstance().getLong(KEY_LOCATION_SYNC_INTERVAL_SEC)
        } catch (_: Exception) {
            30L
        }
    }
}
