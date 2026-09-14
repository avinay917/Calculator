package com.example.authapp.utils

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Centralized logging utility that handles both Logcat and Crashlytics.
 * This replaces direct Log.* and Crashlytics.log() calls throughout the app.
 */
object Logger {
    private val crashlytics = FirebaseCrashlytics.getInstance()

    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, exception: Throwable? = null) {
        Log.e(tag, message, exception)
        crashlytics.log("[$tag] $message")
        exception?.let { crashlytics.recordException(it) }
    }

    fun logFatalCrash(thread: Thread, throwable: Throwable) {
        val stackTrace = getStackTrace(throwable)
        val message = "[FATAL CRASH] Thread: ${thread.name}, Msg: ${throwable.message}\n$stackTrace"

        Log.e(Constants.Logging.TAG_FATAL_CRASH, message)
        crashlytics.setCustomKey("fatal_thread", thread.name)
        crashlytics.log(message)
        crashlytics.recordException(throwable)
        crashlytics.sendUnsentReports()
    }

    fun logNonFatalError(message: String, exception: Throwable? = null) {
        Log.e(Constants.Logging.TAG_FIREBASE, message, exception)
        crashlytics.log("[FeatureHealth] $message")
        exception?.let { crashlytics.recordException(it) }
    }

    private fun getStackTrace(throwable: Throwable): String {
        val sw = java.io.StringWriter()
        throwable.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString()
    }

    fun logPreviousCrash(lastCrashTime: Long, thread: String, message: String, stackTrace: String) {
        val fullMessage = "[PREV FATAL CRASH] Time: $lastCrashTime, Thread: $thread, Msg: $message\nStack: $stackTrace"
        crashlytics.log(fullMessage)
        crashlytics.recordException(Exception("PREV_FATAL_CRASH: $message on $thread"))
        crashlytics.sendUnsentReports()
    }
}
