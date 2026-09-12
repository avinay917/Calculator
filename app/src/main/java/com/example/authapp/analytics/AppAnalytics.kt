package com.example.authapp.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase

object AppAnalytics {
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()
    private val analytics: FirebaseAnalytics?
        get() = try {
            Firebase.analytics
        } catch (e: Exception) {
            null
        }

    fun logButtonClick(buttonName: String, screenName: String, extraParams: Map<String, Any>? = null) {
        val bundle = Bundle().apply {
            putString("button_name", buttonName)
            putString("screen_name", screenName)
            putLong("timestamp", System.currentTimeMillis())
            extraParams?.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Boolean -> putBoolean(k, v)
                    is Double -> putDouble(k, v)
                }
            }
        }
        analytics?.logEvent("btn_click_$buttonName", bundle)
        crashlytics.log("[ButtonTap] Screen: $screenName -> Button: $buttonName")
    }

    fun logFeatureUsage(featureName: String, stage: String, details: Map<String, Any>? = null) {
        val bundle = Bundle().apply {
            putString("feature_name", featureName)
            putString("stage", stage)
            putLong("timestamp", System.currentTimeMillis())
            details?.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Boolean -> putBoolean(k, v)
                    is Double -> putDouble(k, v)
                }
            }
        }
        analytics?.logEvent("feature_$featureName", bundle)
        crashlytics.log("[FeatureUsage] $featureName - Stage: $stage")
    }

    fun logActionFailure(actionName: String, screenName: String, reason: String, throwable: Throwable? = null) {
        val bundle = Bundle().apply {
            putString("action_name", actionName)
            putString("screen_name", screenName)
            putString("error_reason", reason)
            putLong("timestamp", System.currentTimeMillis())
        }
        analytics?.logEvent("action_failure", bundle)

        crashlytics.setCustomKey("failed_action", actionName)
        crashlytics.setCustomKey("failed_screen", screenName)
        crashlytics.setCustomKey("failure_reason", reason)
        crashlytics.log("[ActionFailure] $screenName -> $actionName FAILED: $reason")

        val exceptionToRecord = throwable ?: Exception("ActionFailure: $actionName on $screenName - $reason")
        crashlytics.recordException(exceptionToRecord)
    }
}
