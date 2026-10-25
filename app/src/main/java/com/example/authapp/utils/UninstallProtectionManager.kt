package com.example.authapp.utils

import android.content.Context
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.SecurityAlert
import com.google.firebase.crashlytics.FirebaseCrashlytics

object UninstallProtectionManager {
    private const val PREFS_NAME = "uninstall_protection_prefs"
    private const val KEY_PIN = "uninstall_pin"
    private const val DEFAULT_PIN = "123456"

    @Volatile
    private var temporaryUnlockExpiry = 0L

    fun setUninstallPin(context: Context, pin: String) {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_PIN, pin).apply()
            FirebaseCrashlytics.getInstance().log("[UninstallProtection] New 6-digit PIN saved")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[UninstallProtection] Save PIN error: ${e.localizedMessage}")
        }
    }

    fun getUninstallPin(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        } catch (_: Exception) {
            DEFAULT_PIN
        }
    }

    fun verifyPin(context: Context, inputPin: String): Boolean {
        val expected = getUninstallPin(context)
        val isMatch = inputPin.trim() == expected.trim()
        val childId = AppHealthTelemetry.getEffectiveUserId(context)

        if (isMatch) {
            allowTemporarily()
            if (childId.isNotEmpty()) {
                val alert = SecurityAlert(
                    type = "UNINSTALL_PIN_VERIFIED",
                    title = "App Protection Unlocked 🔓",
                    message = "Correct 6-digit Parent PIN was entered. Temporary uninstallation permission granted.",
                    timestamp = System.currentTimeMillis(),
                    severity = "HIGH"
                )
                FirebaseRepository.pushSecurityAlert(childId, alert)
            }
        } else {
            if (childId.isNotEmpty()) {
                val alert = SecurityAlert(
                    type = "UNINSTALL_PIN_FAILED",
                    title = "Failed Uninstall Attempt ⚠️",
                    message = "Wrong 6-digit PIN entered during uninstall attempt! Uninstallation blocked.",
                    timestamp = System.currentTimeMillis(),
                    severity = "CRITICAL"
                )
                FirebaseRepository.pushSecurityAlert(childId, alert)
            }
        }
        return isMatch
    }

    fun allowTemporarily(durationMs: Long = 120000L) {
        temporaryUnlockExpiry = System.currentTimeMillis() + durationMs
    }

    fun isTemporarilyUnlocked(): Boolean {
        return System.currentTimeMillis() < temporaryUnlockExpiry
    }

    fun resetLock() {
        temporaryUnlockExpiry = 0L
    }
}
