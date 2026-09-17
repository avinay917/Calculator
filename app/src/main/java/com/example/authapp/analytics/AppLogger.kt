package com.example.authapp.analytics

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Unified App Logger Helper (Point 18).
 * Replaces redundant 3-line diagnostic & Crashlytics log statements throughout codebase.
 */
object AppLogger {
    fun log(context: Context, category: String, status: String, message: String, exception: Throwable? = null) {
        AppHealthTelemetry.logDiagnostic(context, category, status, message, exception?.message)
        FirebaseCrashlytics.getInstance().log("[$category][$status] $message")
        if (exception != null) {
            FirebaseCrashlytics.getInstance().recordException(exception)
        }
    }
}
