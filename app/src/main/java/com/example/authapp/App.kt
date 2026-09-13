package com.example.authapp

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Install persistent fatal crash catcher
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTraceString = sw.toString()

                // Save to local SharedPreferences so it's readable immediately on relaunch
                val prefs = getSharedPreferences("crash_logs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("last_crash_time", System.currentTimeMillis())
                    .putString("last_crash_thread", thread.name)
                    .putString("last_crash_message", throwable.message ?: "Unknown fatal error")
                    .putString("last_crash_stacktrace", stackTraceString)
                    .commit()

                Log.e("FATAL_APP_CRASH", "FATAL CRASH on thread ${thread.name}: ${throwable.message}\n$stackTraceString")

                // Immediately send to Firebase Crashlytics
                val crashlytics = FirebaseCrashlytics.getInstance()
                crashlytics.setCustomKey("fatal_thread", thread.name)
                crashlytics.log("[FATAL CRASH] Thread: ${thread.name}, Msg: ${throwable.message}")
                crashlytics.recordException(throwable)
                crashlytics.sendUnsentReports()
            } catch (t: Throwable) {
                Log.e("FATAL_APP_CRASH", "Error in uncaught exception handler: ${t.localizedMessage}")
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        try {
            FirebaseApp.initializeApp(this)
            val rtdb = FirebaseDatabase.getInstance("https://apnasatthilko-default-rtdb.asia-southeast1.firebasedatabase.app")
            // Enable offline disk caching and synchronization
            rtdb.setPersistenceEnabled(true)
            rtdb.reference.child("app_update").keepSynced(true)
            FirebaseCrashlytics.getInstance().log("[App] Firebase Realtime Database disk persistence enabled")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[App] RTDB persistence init notice: ${e.localizedMessage}")
        }
    }
}

