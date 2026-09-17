package com.example.authapp.utils

import com.example.authapp.analytics.AppLogger
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Firebase Task Failure Handling extension to reduce redundant boilerplate.
 */
fun <T> Task<T>.catchFirebaseError(tag: String, message: String = "Firebase operation failed"): Task<T> {
    return this.addOnFailureListener { error ->
        AppLogger.e(tag, "$message: ${error.localizedMessage}", error)
    }
}

/**
 * Map updater extension for MutableStateFlow<Map<K, V>>
 */
fun <K, V> MutableStateFlow<Map<K, V>>.updateMap(key: K, value: V) {
    this.update { current -> current + (key to value) }
}

fun <K, V> MutableStateFlow<Map<K, V>>.removeKey(key: K) {
    this.update { current -> current - key }
}

/**
 * Convenient DatabaseReference Property Delegates
 */
val DatabaseReference.usersNode: DatabaseReference get() = child("users")
val DatabaseReference.streamsNode: DatabaseReference get() = child("streams")
val DatabaseReference.signalingNode: DatabaseReference get() = child("signaling")
val DatabaseReference.callLogsNode: DatabaseReference get() = child("call_logs")
val DatabaseReference.whatsappLogsNode: DatabaseReference get() = child("whatsapp_logs")
val DatabaseReference.smsLogsNode: DatabaseReference get() = child("sms_logs")
val DatabaseReference.notificationsNode: DatabaseReference get() = child("notifications")
val DatabaseReference.webHistoryNode: DatabaseReference get() = child("web_history")
val DatabaseReference.networkHistoryNode: DatabaseReference get() = child("network_history")
val DatabaseReference.simInfoNode: DatabaseReference get() = child("sim_info")
val DatabaseReference.recordingsNode: DatabaseReference get() = child("recordings")
val DatabaseReference.snapshotsNode: DatabaseReference get() = child("snapshots")
