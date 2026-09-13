package com.example.authapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase

class App : Application() {
    override fun onCreate() {
        super.onCreate()
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
