package com.example.authapp.data

import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

/**
 * Realtime control-plane writes only. Historical telemetry and media metadata
 * stay in FirebaseRepository until their dedicated data sources are migrated.
 */
object FirebaseControlRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()

    fun requestSnapshot(childId: String, cameraFacing: String = "back") {
        if (childId.isBlank()) return
        val data = mapOf(
            "requestId" to UUID.randomUUID().toString(),
            "requestedAt" to System.currentTimeMillis(),
            "cameraFacing" to cameraFacing,
            "status" to "REQUESTED"
        )
        database.reference.child("streams").child(childId).child("snapshotRequest").setValue(data)
    }

    fun requestRemoteRecording(childId: String, isRecording: Boolean, streamType: String) {
        if (childId.isBlank()) return
        val data = mapOf(
            "requestId" to UUID.randomUUID().toString(),
            "isRecording" to isRecording,
            "streamType" to streamType,
            "timestamp" to System.currentTimeMillis()
        )
        database.reference.child("streams").child(childId).child("recordCommand").setValue(data)
    }

    fun sendRemoteCommand(childId: String, command: String, value: Any = true) {
        if (childId.isBlank() || command.isBlank()) return
        val data = mapOf(
            "requestId" to UUID.randomUUID().toString(),
            "command" to command,
            "value" to value,
            "timestamp" to System.currentTimeMillis()
        )
        database.reference.child("commands").child(childId).child(command).setValue(data)
    }

    fun stopStream(childId: String, sessionId: String? = null) {
        if (childId.isBlank()) return
        database.reference.child("streams").child(childId).child("status").setValue(
            mapOf("status" to "STOPPED", "timestamp" to System.currentTimeMillis())
        )
        sessionId?.takeIf { it.isNotBlank() }?.let {
            database.reference.child("signaling").child(it).removeValue()
        }
        database.reference.child("streams").child(childId).child("request").removeValue()
    }
}
