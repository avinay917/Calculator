package com.example.authapp.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage

object FirebaseRepository {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance("https://apnasatthilko-default-rtdb.asia-southeast1.firebasedatabase.app")
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    val currentUser get() = auth.currentUser

    fun signUp(
        fullName: String,
        email: String,
        password: String,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = task.result?.user?.uid ?: ""
                    val user = User(
                        uid = uid,
                        name = fullName,
                        email = email,
                        role = "child", // Default role assigned to all newly created accounts
                        isOnline = true
                    )
                    database.reference.child("users").child(uid).setValue(user)
                        .addOnCompleteListener { dbTask ->
                            if (dbTask.isSuccessful) {
                                updateFcmToken()
                                onResult(true, null)
                            } else {
                                onResult(false, dbTask.exception?.localizedMessage)
                            }
                        }
                } else {
                    onResult(false, task.exception?.localizedMessage)
                }
            }
    }

    fun signIn(
        email: String,
        password: String,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateFcmToken()
                    setOnlineStatus(true)
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage)
                }
            }
    }

    fun signOut() {
        setOnlineStatus(false)
        auth.signOut()
    }

    fun setOnlineStatus(isOnline: Boolean) {
        val uid = currentUser?.uid ?: return
        database.reference.child("users").child(uid).child("isOnline").setValue(isOnline)
        database.reference.child("users").child(uid).child("lastSeen").setValue(System.currentTimeMillis())
    }

    fun updateFcmToken() {
        val uid = currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                database.reference.child("users").child(uid).child("fcmToken").setValue(token)
            }
        }
    }

    fun listenToUserRole(uid: String, onRoleChanged: (String) -> Unit) {
        database.reference.child("users").child(uid).child("role")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val role = snapshot.getValue(String::class.java) ?: "child"
                    onRoleChanged(role)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun listenToChildUsers(onUsersUpdated: (List<User>) -> Unit) {
        database.reference.child("users")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<User>()
                    for (child in snapshot.children) {
                        val user = child.getValue(User::class.java)
                        if (user != null && user.role == "child") {
                            list.add(user)
                        }
                    }
                    onUsersUpdated(list)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun requestStream(childId: String, streamType: String, onComplete: (String) -> Unit) {
        val parentId = currentUser?.uid ?: return
        val sessionId = "session_${childId}_$parentId"
        val requestData = mapOf(
            "childId" to childId,
            "parentId" to parentId,
            "streamType" to streamType, // "audio" or "video"
            "type" to streamType,
            "sessionId" to sessionId,
            "status" to "REQUESTED",
            "timestamp" to System.currentTimeMillis()
        )
        database.reference.child("streams").child(childId).child("status").setValue(requestData)
        database.reference.child("requests").child(childId).setValue(requestData)
            .addOnSuccessListener {
                onComplete(sessionId)
            }
    }

    fun sendSdpOffer(sessionId: String, sdp: String) {
        database.reference.child("signaling").child(sessionId).child("sdpOffer").setValue(sdp)
    }

    fun sendSdpAnswer(sessionId: String, sdp: String) {
        database.reference.child("signaling").child(sessionId).child("sdpAnswer").setValue(sdp)
    }

    fun sendIceCandidate(sessionId: String, candidate: Map<String, Any>, isParent: Boolean) {
        val targetNode = if (isParent) "parentCandidates" else "childCandidates"
        database.reference.child("signaling").child(sessionId).child(targetNode).push().setValue(candidate)
    }

    fun listenToSignaling(
        sessionId: String,
        onOfferReceived: (String) -> Unit,
        onAnswerReceived: (String) -> Unit,
        onIceCandidateReceived: (Map<String, Any>) -> Unit
    ) {
        val ref = database.reference.child("signaling").child(sessionId)
        ref.child("sdpOffer").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sdp = snapshot.getValue(String::class.java)
                if (!sdp.isNullOrEmpty()) onOfferReceived(sdp)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        ref.child("sdpAnswer").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sdp = snapshot.getValue(String::class.java)
                if (!sdp.isNullOrEmpty()) onAnswerReceived(sdp)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun saveRecordingSession(
        childId: String,
        streamType: String,
        durationSeconds: Long,
        onSaved: () -> Unit
    ) {
        val parentId = currentUser?.uid ?: return
        val recId = "rec_${System.currentTimeMillis()}"
        val session = RecordingSession(
            id = recId,
            childId = childId,
            parentId = parentId,
            streamType = streamType,
            durationSeconds = durationSeconds,
            status = "SAVED",
            storageUrl = "gs://apnasatthilko.appspot.com/recordings/$childId/$recId.mp4"
        )
        database.reference.child("recordings").child(childId).child(recId).setValue(session)
            .addOnSuccessListener { onSaved() }
    }

    fun uploadRecordingFile(
        childId: String,
        fileUri: Uri,
        streamType: String,
        durationSeconds: Long,
        onSuccess: (RecordingSession) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val parentId = currentUser?.uid ?: return
        val recId = "rec_${System.currentTimeMillis()}"
        val ref = storage.reference.child("recordings/$childId/$recId.mp4")

        ref.putFile(fileUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    val session = RecordingSession(
                        id = recId,
                        childId = childId,
                        parentId = parentId,
                        streamType = streamType,
                        startTime = System.currentTimeMillis(),
                        durationSeconds = durationSeconds,
                        status = "SAVED",
                        storageUrl = downloadUri.toString()
                    )
                    database.reference.child("recordings").child(childId).child(recId).setValue(session)
                        .addOnSuccessListener { onSuccess(session) }
                        .addOnFailureListener { e -> onFailure(e.localizedMessage ?: "Database error") }
                }.addOnFailureListener { e -> onFailure(e.localizedMessage ?: "Failed to get download URL") }
            }
            .addOnFailureListener { e -> onFailure(e.localizedMessage ?: "Storage upload failed") }
    }

    fun listenToRecordings(childId: String, onRecordingsUpdated: (List<RecordingSession>) -> Unit) {
        database.reference.child("recordings").child(childId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<RecordingSession>()
                    for (child in snapshot.children) {
                        val session = child.getValue(RecordingSession::class.java)
                        if (session != null) list.add(session)
                    }
                    onRecordingsUpdated(list.reversed())
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
