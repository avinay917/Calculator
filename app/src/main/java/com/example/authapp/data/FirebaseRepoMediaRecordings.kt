package com.example.authapp.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

object FirebaseMediaRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    fun saveRecordingSession(
        childId: String,
        streamType: String,
        durationSeconds: Long,
        localFilePath: String = "",
        parentId: String = "",
        existingRecId: String = "",
        onSaved: () -> Unit = {}
    ): String {
        val effectiveParentId = if (parentId.isNotEmpty()) parentId else (auth.currentUser?.uid ?: "")
        val recId = if (existingRecId.isNotEmpty()) existingRecId else "rec_${System.currentTimeMillis()}"
        val session = RecordingSession(
            id = recId,
            childId = childId,
            parentId = effectiveParentId,
            streamType = streamType,
            durationSeconds = durationSeconds,
            status = "SAVED",
            storageUrl = if (localFilePath.isNotEmpty()) "file://$localFilePath" else "",
            localFilePath = localFilePath
        )
        database.reference.child("recordings").child(childId).child(recId).setValue(session)
            .addOnSuccessListener { onSaved() }
            .addOnFailureListener { e -> FirebaseCrashlytics.getInstance().recordException(e) }
        return recId
    }

    fun uploadRecordingFile(
        childId: String,
        fileUri: Uri,
        streamType: String,
        durationSeconds: Long,
        localFilePath: String = "",
        parentId: String = "",
        existingRecId: String = "",
        onSuccess: (RecordingSession) -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        val effectiveParentId = if (parentId.isNotEmpty()) parentId else (auth.currentUser?.uid ?: "")
        val recId = if (existingRecId.isNotEmpty()) existingRecId else "rec_${System.currentTimeMillis()}"
        val extension = if (streamType.equals("video", ignoreCase = true)) "mp4" else "m4a"
        val ref = storage.reference.child("recordings/$childId/$recId.$extension")

        val mimeType = if (streamType.equals("video", ignoreCase = true)) "video/mp4" else "audio/mp4"
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType(mimeType)
            .setCacheControl("public, max-age=604800")
            .build()

        ref.putFile(fileUri, metadata)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    val session = RecordingSession(
                        id = recId,
                        childId = childId,
                        parentId = effectiveParentId,
                        streamType = streamType,
                        startTime = System.currentTimeMillis(),
                        durationSeconds = durationSeconds,
                        status = "SAVED",
                        storageUrl = downloadUri.toString(),
                        downloadUrl = downloadUri.toString(),
                        localFilePath = localFilePath
                    )
                    database.reference.child("recordings").child(childId).child(recId).setValue(session)
                        .addOnSuccessListener { onSuccess(session) }
                        .addOnFailureListener { e -> onFailure(e.localizedMessage ?: "Database error") }
                }.addOnFailureListener { e -> onFailure(e.localizedMessage ?: "Failed to get download URL") }
            }
            .addOnFailureListener { e -> onFailure(e.localizedMessage ?: "Storage upload failed") }
    }

    fun listenToRecordings(childId: String, onRecordingsUpdated: (List<RecordingSession>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("recordings", childId, limit = 50, transform = { it.reversed() }, onData = onRecordingsUpdated)

    fun listenToAllRecordings(onRecordingsUpdated: (List<RecordingSession>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<RecordingSession>()
                for (childFolder in snapshot.children) {
                    for (recSnapshot in childFolder.children) {
                        val session = recSnapshot.getValue(RecordingSession::class.java)
                        if (session != null) {
                            list.add(session)
                        }
                    }
                }
                onRecordingsUpdated(list.sortedByDescending { it.startTime })
            }
            override fun onCancelled(error: DatabaseError) {
                FirebaseCrashlytics.getInstance().log("[Firebase allRecordings cancelled] ${error.message}")
                onRecordingsUpdated(emptyList())
            }
        }
        database.reference.child("recordings").limitToLast(50).addValueEventListener(listener)
        return listener
    }

    fun saveRecordingSchedule(childId: String, schedule: RecordingSchedule) {
        val ref = if (schedule.id.isNotEmpty()) {
            database.reference.child("schedules").child(childId).child(schedule.id)
        } else {
            database.reference.child("schedules").child(childId).push()
        }
        val toSave = schedule.copy(id = ref.key ?: schedule.id)
        ref.setValue(toSave)
    }

    fun deleteRecordingSchedule(childId: String, scheduleId: String) {
        database.reference.child("schedules").child(childId).child(scheduleId).removeValue()
    }

    fun listenToRecordingSchedules(childId: String, onSchedules: (List<RecordingSchedule>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("schedules", childId, limit = 25, onData = onSchedules)

    fun requestSnapshot(childId: String, cameraFacing: String = "back") {
        FirebaseControlRepository.requestSnapshot(childId, cameraFacing)
    }

    fun listenToSnapshotRequest(childId: String, onRequested: (cameraFacing: String) -> Unit): ValueEventListener {
        val ref = database.reference.child("streams").child(childId).child("snapshotRequest")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                if (status == "REQUESTED") {
                    val facing = snapshot.child("cameraFacing").getValue(String::class.java) ?: "back"
                    onRequested(facing)
                    ref.child("status").setValue("PROCESSING")
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun uploadSnapshot(
        childId: String,
        fileUri: Uri,
        cameraFacing: String,
        onSuccess: (SnapshotInfo) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val timestamp = System.currentTimeMillis()
        val ext = fileUri.path?.substringAfterLast('.')?.takeIf { it.length in 2..5 } ?: "jpg"
        val storageRef = storage.reference.child("snapshots/$childId/$timestamp.$ext")
        val mimeType = if (ext.equals("webp", ignoreCase = true)) "image/webp" else "image/jpeg"
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType(mimeType)
            .setCacheControl("public, max-age=604800")
            .build()

        storageRef.putFile(fileUri, metadata)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val snapshotRef = database.reference.child("snapshots").child(childId).push()
                    val info = SnapshotInfo(
                        id = snapshotRef.key ?: "",
                        downloadUrl = downloadUrl.toString(),
                        timestamp = timestamp,
                        cameraFacing = cameraFacing
                    )
                    snapshotRef.setValue(info).addOnSuccessListener {
                        database.reference.child("streams").child(childId).child("snapshotRequest").setValue(
                            mapOf(
                                "status" to "COMPLETED",
                                "downloadUrl" to downloadUrl.toString(),
                                "timestamp" to timestamp,
                                "cameraFacing" to cameraFacing
                            )
                        )
                        onSuccess(info)
                    }.addOnFailureListener { e -> onFailure(e.localizedMessage ?: "DB Error") }
                }.addOnFailureListener { e -> onFailure(e.localizedMessage ?: "URL Error") }
            }
            .addOnFailureListener { e -> onFailure(e.localizedMessage ?: "Upload Error") }
    }

    fun reportSnapshotError(childId: String, error: String) {
        val data = mapOf<String, Any>(
            "status" to "FAILED",
            "error" to error,
            "failedAt" to System.currentTimeMillis()
        )
        database.reference.child("streams").child(childId).child("snapshotRequest").updateChildren(data)
    }

    fun listenToSnapshotRequestStatus(childId: String, onStatusChanged: (status: String, error: String?) -> Unit): ValueEventListener {
        val ref = database.reference.child("streams").child(childId).child("snapshotRequest")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java) ?: ""
                val error = snapshot.child("error").getValue(String::class.java)
                if (status.isNotEmpty()) {
                    onStatusChanged(status, error)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToSnapshots(childId: String, onSnapshots: (List<SnapshotInfo>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("snapshots", childId, limit = 50, transform = { it.sortedByDescending { s -> s.timestamp } }, onData = onSnapshots)

    fun saveRecordingToFirestore(childId: String, session: RecordingSession) {
        val key = if (session.id.isNotEmpty()) session.id else "${session.startTime}_${session.streamType}"
        FirebaseRepository.saveChildDocumentDual("recordings", "recordings", childId, key, session.copy(id = key))
    }

    fun listenToRecordingsFirestore(childId: String, limit: Long = 50, onUpdate: (List<RecordingSession>) -> Unit) =
        FirebaseRepository.listenFirestoreCollection<RecordingSession>("recordings", childId, "startTime", limit, onUpdate)
}
