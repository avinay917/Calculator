package com.example.authapp.data.repository

import com.example.authapp.data.SnapshotInfo
import com.google.firebase.database.FirebaseDatabase

object MediaRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()

    fun pushSnapshotInfo(childId: String, snapshotInfo: SnapshotInfo) {
        if (childId.isEmpty()) return
        database.reference.child("snapshots").child(childId).child(snapshotInfo.id).setValue(snapshotInfo)
    }

    fun reportSnapshotStatus(childId: String, status: String, error: String? = null) {
        if (childId.isEmpty()) return
        val data = mapOf(
            "status" to status,
            "error" to (error ?: ""),
            "timestamp" to System.currentTimeMillis()
        )
        database.reference.child("streams").child(childId).child("snapshotRequest").updateChildren(data)
    }
}
