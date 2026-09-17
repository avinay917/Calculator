package com.example.authapp

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.example.authapp.utils.Logger
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Install persistent fatal crash catcher
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTraceString = sw.toString()

                // Save to local SharedPreferences
                val prefs = getSharedPreferences("crash_logs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("last_crash_time", System.currentTimeMillis())
                    .putString("last_crash_thread", thread.name)
                    .putString("last_crash_message", throwable.message ?: "Unknown fatal error")
                    .putString("last_crash_stacktrace", stackTraceString)
                    .commit()

                Logger.logFatalCrash(thread, throwable)
            } catch (t: Throwable) {
                Logger.e("FATAL_CRASH_HANDLER", "Error in uncaught exception handler", t)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        try {
            FirebaseApp.initializeApp(this)
            val rtdb = FirebaseDatabase.getInstance()
            rtdb.setPersistenceEnabled(true)
            rtdb.reference.child("app_update").keepSynced(true)
            com.example.authapp.config.RemoteConfigManager.init(this)
            Logger.i("App", "Firebase initialized with persistence enabled and RemoteConfig initialized")
        } catch (e: Exception) {
            Logger.e("App", "Firebase initialization error", e)
        }
    }
}
