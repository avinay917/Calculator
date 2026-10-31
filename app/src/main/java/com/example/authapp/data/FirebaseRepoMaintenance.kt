package com.example.authapp.data

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

object FirebaseMaintenanceRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()

    fun pruneOldLogs(childId: String, retentionDays: Int = 30) {
        if (childId.isBlank()) return
        val cutoffTimestamp = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val nodesToPrune = listOf(
            "whatsapp_logs", "youtube_history", "notifications", "alerts",
            "call_logs", "sms_logs", "web_history", "network_history",
            "keylogs", "wifi_logs", "social_media_usage", "snapshots", "location_history"
        )

        for (node in nodesToPrune) {
            try {
                database.reference.child(node).child(childId)
                    .orderByChild("timestamp")
                    .endAt(cutoffTimestamp.toDouble())
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (!snapshot.exists()) return
                            val updates = mutableMapOf<String, Any?>()
                            for (child in snapshot.children) {
                                child.key?.let { updates[it] = null }
                            }
                            if (updates.isNotEmpty()) {
                                database.reference.child(node).child(childId).updateChildren(updates)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            crashlytics.log("[FirebaseRepository] pruneOldLogs cancelled for $node: ${error.message}")
                        }
                    })
            } catch (e: Exception) {
                crashlytics.recordException(e)
            }
        }
    }

    fun cleanOldCloudStorage(childId: String, retentionDays: Int = 7, onComplete: (Int) -> Unit = {}) {
        if (childId.isBlank()) {
            onComplete(0)
            return
        }
        val cutoffTimestamp = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        var totalDeleted = 0

        database.reference.child("recordings").child(childId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val dbUpdates = mutableMapOf<String, Any?>()
                    for (rec in snapshot.children) {
                        val startTime = rec.child("startTime").getValue(Long::class.java)
                            ?: rec.child("timestamp").getValue(Long::class.java) ?: 0L
                        if (startTime > 0L && startTime < cutoffTimestamp) {
                            val storageUrl = rec.child("storageUrl").getValue(String::class.java)
                                ?: rec.child("downloadUrl").getValue(String::class.java)
                            if (!storageUrl.isNullOrEmpty() && storageUrl.startsWith("http")) {
                                try {
                                    storage.getReferenceFromUrl(storageUrl).delete()
                                } catch (_: Exception) {}
                            }
                            rec.key?.let { dbUpdates[it] = null }
                            totalDeleted++
                        }
                    }
                    if (dbUpdates.isNotEmpty()) {
                        database.reference.child("recordings").child(childId).updateChildren(dbUpdates)
                    }

                    database.reference.child("snapshots").child(childId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapSnapshot: DataSnapshot) {
                                val snapUpdates = mutableMapOf<String, Any?>()
                                val sortedSnaps = snapSnapshot.children.sortedByDescending { it.child("timestamp").getValue(Long::class.java) ?: 0L }
                                // Preserve at least the most recent 1 snapshot so UI never displays an empty/broken state
                                val snapsToPrune = if (sortedSnaps.size > 1) sortedSnaps.drop(1) else emptyList()
                                for (snap in snapsToPrune) {
                                    val ts = snap.child("timestamp").getValue(Long::class.java) ?: 0L
                                    if (ts > 0L && ts < cutoffTimestamp) {
                                        val downloadUrl = snap.child("downloadUrl").getValue(String::class.java)
                                        if (!downloadUrl.isNullOrEmpty() && downloadUrl.startsWith("http")) {
                                            try {
                                                storage.getReferenceFromUrl(downloadUrl).delete()
                                            } catch (_: Exception) {}
                                        }
                                        snap.key?.let { snapUpdates[it] = null }
                                        totalDeleted++
                                    }
                                }
                                if (snapUpdates.isNotEmpty()) {
                                    database.reference.child("snapshots").child(childId).updateChildren(snapUpdates)
                                }
                                pruneOldLogs(childId, retentionDays)
                                onComplete(totalDeleted)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                onComplete(totalDeleted)
                            }
                        })
                }

                override fun onCancelled(error: DatabaseError) {
                    onComplete(0)
                }
            })
    }
}
