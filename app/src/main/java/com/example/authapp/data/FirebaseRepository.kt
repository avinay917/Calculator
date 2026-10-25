package com.example.authapp.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

import com.example.authapp.config.FirebasePaths
import com.example.authapp.utils.onValueChange
import com.example.authapp.utils.toListOf

object FirebaseRepository {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    // ✅ FIX: Hardcoded URL remove kiya - Firebase google-services.json se automatically correct DB use karta hai
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()

    inline fun <reified T> listenToChildNode(
        nodePath: String,
        childUid: String,
        crossinline onData: (List<T>) -> Unit
    ): ValueEventListener {
        return FirebaseDatabase.getInstance().reference.child(nodePath).child(childUid)
            .onValueChange { snapshot ->
                onData(snapshot.toListOf())
            }
    }

    inline fun <reified T> listenToChildNodeList(
        nodePath: String,
        childId: String,
        limit: Int = 50,
        crossinline transform: (List<T>) -> List<T> = { it },
        crossinline onData: (List<T>) -> Unit
    ): ValueEventListener {
        val ref = FirebaseDatabase.getInstance().reference.child(nodePath).child(childId)
        val query = if (limit > 0) ref.limitToLast(limit) else ref
        return query.onValueChange { snapshot ->
            onData(transform(snapshot.toListOf()))
        }
    }

    inline fun <reified T> listenFirestoreCollection(
        collection: String,
        childId: String,
        orderField: String = "timestamp",
        limit: Long = 50,
        crossinline onUpdate: (List<T>) -> Unit
    ): ListenerRegistration {
        return FirebaseFirestore.getInstance().collection(collection).document(childId)
            .collection("items")
            .orderBy(orderField, Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirebaseCrashlytics.getInstance().recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    onUpdate(snapshot.documents.mapNotNull { it.toObject(T::class.java) })
                }
            }
    }
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()

    private var presenceConnectedListener: ValueEventListener? = null
    private var currentPresenceUid: String? = null

    val currentUser get() = auth.currentUser

    fun updateStreamStatus(childId: String, status: String, streamType: String, sessionId: String) {
        val streamingData = mapOf(
            "status" to status,
            "streamType" to streamType,
            "sessionId" to sessionId,
            "timestamp" to ServerValue.TIMESTAMP
        )
        database.reference.child("streams").child(childId).child("status")
            .updateChildren(streamingData)
            .addOnFailureListener { e ->
                crashlytics.log("[FirebaseRepository] updateStreamStatus failed: ${e.localizedMessage}")
            }
    }

    fun recordNonFatalError(message: String, exception: Throwable? = null) {
        crashlytics.log("[FeatureHealth] $message")
        exception?.let { crashlytics.recordException(it) }
    }

    fun setupPresenceSystem(uid: String) {
        if (currentPresenceUid == uid && presenceConnectedListener != null) {
            return
        }
        presenceConnectedListener?.let {
            database.reference.child(".info/connected").removeEventListener(it)
            presenceConnectedListener = null
        }
        currentPresenceUid = uid

        val userRef = database.reference.child("users").child(uid)
        val connectedRef = database.reference.child(".info/connected")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    val disconnectMap = mapOf<String, Any>(
                        "isOnline" to false,
                        "online" to false,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    userRef.onDisconnect().updateChildren(disconnectMap)

                    val onlineMap = mapOf<String, Any>(
                        "isOnline" to true,
                        "online" to true,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    userRef.updateChildren(onlineMap)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                recordNonFatalError("Presence listener cancelled: ${error.message}", error.toException())
            }
        }

        connectedRef.addValueEventListener(listener)
        presenceConnectedListener = listener
    }

    fun signUp(
        fullName: String,
        email: String,
        password: String,
        role: String = "child",
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
                        role = if (role.lowercase() == "parent") "parent" else "child", // Explicit role assignment
                        isOnline = false
                    )
                    database.reference.child("users").child(uid).setValue(user)
                        .addOnCompleteListener { dbTask ->
                            if (dbTask.isSuccessful) {
                                // Crucial: sign out immediately so new user must log in with email and password first
                                auth.signOut()
                                onResult(true, null)
                            } else {
                                auth.currentUser?.delete()
                                onResult(false, dbTask.exception?.localizedMessage ?: "Failed to save user profile")
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
                    val uid = task.result?.user?.uid ?: auth.currentUser?.uid
                    if (uid != null) {
                        setupPresenceSystem(uid)
                    }
                    updateFcmToken()
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage)
                }
            }
    }

    fun sendPasswordResetEmail(
        email: String,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            onResult(false, "Please enter your email address")
            return
        }
        auth.sendPasswordResetEmail(trimmedEmail)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage ?: "Failed to send reset email")
                }
            }
    }

    fun signOut() {
        val uid = currentUser?.uid
        if (uid != null) {
            val userRef = database.reference.child("users").child(uid)
            val offlineMap = mapOf<String, Any>(
                "isOnline" to false,
                "online" to false,
                "lastSeen" to ServerValue.TIMESTAMP
            )
            userRef.updateChildren(offlineMap)
            userRef.onDisconnect().cancel()
        }
        presenceConnectedListener?.let {
            database.reference.child(".info/connected").removeEventListener(it)
            presenceConnectedListener = null
        }
        currentPresenceUid = null
        auth.signOut()
    }

    fun setOnlineStatus(isOnline: Boolean) {
        val uid = currentUser?.uid ?: return
        val userRef = database.reference.child("users").child(uid)
        val statusMap = mapOf<String, Any>(
            "isOnline" to isOnline,
            "online" to isOnline,
            "lastSeen" to ServerValue.TIMESTAMP
        )
        userRef.updateChildren(statusMap)
        if (isOnline) {
            setupPresenceSystem(uid)
        }
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
        // FIXED: Persistent ValueEventListener se Memory Leak hota tha — SingleValueEvent use karo
        database.reference.child("users").child(uid).child("role")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val role = snapshot.getValue(String::class.java) ?: "child"
                    onRoleChanged(role)
                }

                override fun onCancelled(error: DatabaseError) {
                    onRoleChanged("child") // Default safe fallback
                }
            })
    }

    // One-shot role fetch — no persistent listener, prevents memory leak
    fun getUserRoleOnce(uid: String, onRoleFetched: (String) -> Unit) {
        database.reference.child("users").child(uid).child("role")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val role = snapshot.getValue(String::class.java) ?: "child"
                    onRoleFetched(role)
                }

                override fun onCancelled(error: DatabaseError) {
                    onRoleFetched("child") // Default fallback
                }
            })
    }

    init {
        try {
            database.setPersistenceEnabled(true)
        } catch (_: Exception) {}
    }

    fun saveCachedChildUsers(context: android.content.Context, list: List<User>) {
        try {
            val json = com.google.gson.Gson().toJson(list)
            val prefs = context.getSharedPreferences("parent_cache_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("cached_child_users", json).apply()
        } catch (_: Exception) {}
    }

    fun getCachedChildUsers(context: android.content.Context): List<User> {
        return try {
            val prefs = context.getSharedPreferences("parent_cache_prefs", android.content.Context.MODE_PRIVATE)
            val json = prefs.getString("cached_child_users", null) ?: return emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<User>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun listenToChildUsers(onUsersUpdated: (List<User>) -> Unit): ValueEventListener {
        val parentUid = currentUser?.uid
        if (parentUid.isNullOrEmpty()) {
            return object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) = onUsersUpdated(emptyList())
                override fun onCancelled(error: DatabaseError) {
                    recordNonFatalError("listenToChildUsers: not authenticated", error.toException())
                }
            }
        }
        val ref = database.reference.child("users")
            .orderByChild("parentId")
            .equalTo(parentUid)
        try { ref.keepSynced(true) } catch (_: Exception) {}
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentParentUid = currentUser?.uid ?: ""
                val list = mutableListOf<User>()
                for (child in snapshot.children) {
                    try {
                        val user = child.getValue(User::class.java)
                        if (user != null && user.role == "child" && user.parentId == currentParentUid) {
                            val isOnlineVal = child.child("isOnline").getValue(Boolean::class.java) ?: user.isOnline
                            val lastSeenVal = child.child("lastSeen").getValue(Long::class.java) ?: user.lastSeen
                            list.add(user.copy(isOnline = isOnlineVal, lastSeen = lastSeenVal))
                        }
                    } catch (e: Exception) {
                        // Corrupt/invalid RTDB record — skip this entry silently
                        recordNonFatalError("listenToChildUsers: Skipping corrupt child node [${child.key}]: ${e.message}", e)
                    }
                }
                onUsersUpdated(list)
            }

            override fun onCancelled(error: DatabaseError) {
                recordNonFatalError("listenToChildUsers cancelled: ${error.message}", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun linkChildToParent(childId: String, parentId: String, onComplete: (Boolean, String?) -> Unit) {
        val updates = mapOf<String, Any>(
            "parentId" to parentId
        )
        database.reference.child("users").child(childId).updateChildren(updates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, task.exception?.localizedMessage)
                }
            }
    }

    fun requestStream(childId: String, streamType: String, onComplete: (String) -> Unit) {
        requestStream(childId, streamType, onComplete, null)
    }

    fun requestStream(
        childId: String,
        streamType: String,
        onComplete: (String) -> Unit,
        onError: ((Exception) -> Unit)?
    ) {
        val parentId = currentUser?.uid
        if (parentId == null) {
            val err = IllegalStateException("Parent not authenticated")
            onError?.invoke(err)
            return
        }
        val timestamp = System.currentTimeMillis()
        val sessionId = "session_${childId}_${parentId}_$timestamp"
        val requestData = mapOf(
            "requestId" to sessionId,
            "childId" to childId,
            "parentId" to parentId,
            "streamType" to streamType, // "audio" or "video"
            "sessionId" to sessionId,
            "status" to "REQUESTED",
            "timestamp" to timestamp
        )
        try {
            database.reference.child("signaling").child("session_${childId}_$parentId").removeValue()
        } catch (_: Exception) {}
        val signalingMetadata = mapOf<String, Any>(
            "childId" to childId,
            "parentId" to parentId,
            "sessionId" to sessionId,
            "createdAt" to ServerValue.TIMESTAMP
        )
        database.reference.child("signaling").child(sessionId).setValue(signalingMetadata)
            .addOnFailureListener { e ->
                FirebaseCrashlytics.getInstance().recordException(e)
                onError?.invoke(e)
            }
            .addOnSuccessListener {
                database.reference.child("streams").child(childId).child("status").setValue(requestData)
            .addOnSuccessListener {
                try {
                    database.reference.child("users").child(childId).child("streamWakeup").setValue(timestamp)
                } catch (_: Exception) {}
                onComplete(sessionId)
            }
            .addOnFailureListener { e ->
                FirebaseCrashlytics.getInstance().recordException(e)
                onError?.invoke(e)
            }
            }
            .addOnFailureListener { e ->
                FirebaseCrashlytics.getInstance().recordException(e)
                onError?.invoke(e)
            }
    }

    fun stopStream(childId: String, sessionId: String? = null) {
        FirebaseControlRepository.stopStream(childId, sessionId)
    }

    fun requestRemoteRecording(childId: String, isRecording: Boolean, streamType: String) {
        FirebaseControlRepository.requestRemoteRecording(childId, isRecording, streamType)
    }

    fun listenToRemoteRecording(
        childId: String,
        onCommand: (isRecording: Boolean, streamType: String) -> Unit
    ): ValueEventListener {
        val ref = database.reference.child("streams").child(childId).child("recordCommand")
        var lastProcessedTimestamp = 0L
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Guard: skip if node does not exist or has no real data
                if (!snapshot.exists() || !snapshot.hasChild("isRecording")) return
                val isRecording = snapshot.child("isRecording").getValue(Boolean::class.java) ?: return
                val streamType = snapshot.child("streamType").getValue(String::class.java) ?: "audio"
                // Deduplication: ignore duplicate events with same timestamp
                val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                if (ts > 0L && ts == lastProcessedTimestamp) return
                if (ts > 0L) lastProcessedTimestamp = ts
                onCommand(isRecording, streamType)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToStreamRequests(
        childId: String,
        onRequested: (streamType: String, sessionId: String) -> Unit,
        onStopped: () -> Unit
    ): ValueEventListener {
        val ref = database.reference.child("streams").child(childId).child("status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                if (status == "REQUESTED") {
                    val streamType = snapshot.child("streamType").getValue(String::class.java) ?: "audio"
                    val sessionId = snapshot.child("sessionId").getValue(String::class.java)
                        ?: "session_${childId}"
                    onRequested(streamType, sessionId)
                } else if (status == "STOPPED" || status == "DISCONNECTED") {
                    onStopped()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeStreamRequestListener(childId: String, listener: ValueEventListener) {
        database.reference.child("streams").child(childId).child("status").removeEventListener(listener)
    }

    fun sendSdpOffer(sessionId: String, sdp: String) {
        database.reference.child("signaling").child(sessionId).child("sdpOffer").setValue(sdp)
            .addOnFailureListener { e ->
                FirebaseCrashlytics.getInstance().recordException(e)
            }
    }

    fun sendSdpAnswer(sessionId: String, sdp: String) {
        database.reference.child("signaling").child(sessionId).child("sdpAnswer").setValue(sdp)
            .addOnFailureListener { e ->
                FirebaseCrashlytics.getInstance().recordException(e)
            }
    }

    fun sendIceCandidate(sessionId: String, candidate: Map<String, Any>, isParent: Boolean) {
        val targetNode = if (isParent) "parentCandidates" else "childCandidates"
        database.reference.child("signaling").child(sessionId).child(targetNode).push().setValue(candidate)
            .addOnFailureListener { e ->
                FirebaseCrashlytics.getInstance().recordException(e)
            }
    }

    fun sendIceCandidatesBatch(sessionId: String, candidates: List<Map<String, Any>>, isParent: Boolean) {
        if (candidates.isEmpty() || sessionId.isEmpty()) return
        val targetNode = if (isParent) "parentCandidates" else "childCandidates"
        val ref = database.reference.child("signaling").child(sessionId).child(targetNode)
        val batchMap = mutableMapOf<String, Any>()
        for (candidate in candidates) {
            val key = ref.push().key ?: continue
            batchMap[key] = candidate
        }
        if (batchMap.isNotEmpty()) {
            ref.updateChildren(batchMap).addOnFailureListener { e ->
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    fun cleanupSignalingData(sessionId: String, childId: String? = null) {
        if (sessionId.isNotEmpty()) {
            database.reference.child("signaling").child(sessionId).removeValue()
        }
        if (!childId.isNullOrEmpty()) {
            database.reference.child("streams").child(childId).child("request").removeValue()
        }
    }

    fun listenToSdpOffer(sessionId: String, onOfferReceived: (String) -> Unit): ValueEventListener {
        val ref = database.reference.child("signaling").child(sessionId).child("sdpOffer")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sdp = snapshot.getValue(String::class.java)
                if (!sdp.isNullOrEmpty()) onOfferReceived(sdp)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToSdpAnswer(sessionId: String, onAnswerReceived: (String) -> Unit): ValueEventListener {
        val ref = database.reference.child("signaling").child(sessionId).child("sdpAnswer")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sdp = snapshot.getValue(String::class.java)
                if (!sdp.isNullOrEmpty()) onAnswerReceived(sdp)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToCandidates(
        sessionId: String,
        listenToParentCandidates: Boolean,
        onCandidateReceived: (sdpMid: String, sdpMLineIndex: Int, sdp: String) -> Unit
    ): ChildEventListener {
        val targetNode = if (listenToParentCandidates) "parentCandidates" else "childCandidates"
        val ref = database.reference.child("signaling").child(sessionId).child(targetNode)
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val sdpMid = snapshot.child("sdpMid").getValue(String::class.java) ?: ""
                val sdpMLineIndex = (snapshot.child("sdpMLineIndex").getValue(Long::class.java) ?: 0L).toInt()
                val sdp = snapshot.child("sdp").getValue(String::class.java) ?: ""
                if (sdp.isNotEmpty()) {
                    onCandidateReceived(sdpMid, sdpMLineIndex, sdp)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addChildEventListener(listener)
        return listener
    }

    fun removeCandidateListener(sessionId: String, listenToParentCandidates: Boolean, listener: ChildEventListener) {
        val targetNode = if (listenToParentCandidates) "parentCandidates" else "childCandidates"
        database.reference.child("signaling").child(sessionId).child(targetNode).removeEventListener(listener)
    }

    fun removeSdpOfferListener(sessionId: String, listener: ValueEventListener) {
        database.reference.child("signaling").child(sessionId).child("sdpOffer").removeEventListener(listener)
    }

    fun removeSdpAnswerListener(sessionId: String, listener: ValueEventListener) {
        database.reference.child("signaling").child(sessionId).child("sdpAnswer").removeEventListener(listener)
    }

    fun removeCameraFacingListener(sessionId: String, listener: ValueEventListener) {
        database.reference.child("signaling").child(sessionId).child("cameraFacing").removeEventListener(listener)
    }

    fun removeValueListener(path: String, listener: ValueEventListener) {
        database.reference.child(path).removeEventListener(listener)
    }

    fun deleteSignalingSession(sessionId: String) {
        if (sessionId.isBlank()) return
        database.reference.child("signaling").child(sessionId).removeValue()
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun saveRecordingSession(
        childId: String,
        streamType: String,
        durationSeconds: Long,
        localFilePath: String = "",
        parentId: String = "",
        existingRecId: String = "",
        onSaved: () -> Unit = {}
    ): String {
        val effectiveParentId = if (parentId.isNotEmpty()) parentId else (currentUser?.uid ?: "")
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
            .addOnFailureListener { e ->
                FirebaseCrashlytics.getInstance().recordException(e)
            }
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
        val effectiveParentId = if (parentId.isNotEmpty()) parentId else (currentUser?.uid ?: "")
        val recId = if (existingRecId.isNotEmpty()) existingRecId else "rec_${System.currentTimeMillis()}"
        val extension = if (streamType.equals("video", ignoreCase = true)) "mp4" else "m4a"
        val ref = storage.reference.child("recordings/$childId/$recId.$extension")

        val mimeType = if (streamType.equals("video", ignoreCase = true)) "video/mp4" else "audio/mp4"
        val metadata = com.google.firebase.storage.StorageMetadata.Builder().setContentType(mimeType).build()

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

    fun toggleCameraFacing(sessionId: String, isFront: Boolean) {
        database.reference.child("signaling").child(sessionId).child("cameraFacing").setValue(if (isFront) "front" else "back")
    }

    fun setCameraFacingMode(sessionId: String, facing: String) {
        database.reference.child("signaling").child(sessionId).child("cameraFacing").setValue(facing)
    }

    fun listenToCameraFacing(sessionId: String, onFacingChanged: (isFront: Boolean) -> Unit): ValueEventListener {
        return listenToCameraFacingMode(sessionId) { mode ->
            onFacingChanged(mode.equals("front", ignoreCase = true))
        }
    }

    fun listenToCameraFacingMode(sessionId: String, onFacingModeChanged: (facing: String) -> Unit): ValueEventListener {
        val ref = database.reference.child("signaling").child(sessionId).child("cameraFacing")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val facing = snapshot.getValue(String::class.java)
                if (facing != null) {
                    onFacingModeChanged(facing)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                FirebaseCrashlytics.getInstance().log("[Firebase cameraFacing cancelled] ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToRecordings(childId: String, onRecordingsUpdated: (List<RecordingSession>) -> Unit): ValueEventListener =
        listenToChildNodeList("recordings", childId, limit = 50, transform = { it.reversed() }, onData = onRecordingsUpdated)

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

    fun updateChildLocation(childId: String, location: UserLocation) {
        database.reference.child("users").child(childId).child("location").setValue(location)
    }

    fun requestChildLocation(childId: String) {
        database.reference.child("users").child(childId).child("locationRequest").setValue(System.currentTimeMillis())
    }

    fun listenToChildLocation(childId: String, onLocationUpdated: (UserLocation?) -> Unit): ValueEventListener {
        val ref = database.reference.child("users").child(childId).child("location")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loc = snapshot.getValue(UserLocation::class.java)
                onLocationUpdated(loc)
            }
            override fun onCancelled(error: DatabaseError) {
                FirebaseCrashlytics.getInstance().log("[Firebase childLocation cancelled] ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToLocationRequests(childId: String, onRequestReceived: () -> Unit): ValueEventListener {
        val ref = database.reference.child("users").child(childId).child("locationRequest")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reqTime = snapshot.getValue(Long::class.java)
                if (reqTime != null && reqTime > 0L) {
                    onRequestReceived()
                    ref.removeValue()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                FirebaseCrashlytics.getInstance().log("[Firebase locationRequest cancelled] ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToAppUpdate(onUpdateReceived: (AppUpdateInfo?) -> Unit): ValueEventListener {
        val ref = database.reference.child("app_update")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val updateInfo = snapshot.getValue(AppUpdateInfo::class.java)
                onUpdateReceived(updateInfo)
            }
            override fun onCancelled(error: DatabaseError) {
                recordNonFatalError("App update listener cancelled: ${error.message}", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun publishAppUpdate(info: AppUpdateInfo, onResult: (Boolean, String?) -> Unit) {
        database.reference.child("app_update").setValue(info)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage)
                }
            }
    }

    fun listenToDeviceHealth(childUid: String, onHealthUpdate: (DeviceHealth?) -> Unit): ValueEventListener {
        val ref = database.reference.child("device_health").child(childUid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val health = snapshot.getValue(DeviceHealth::class.java)
                    onHealthUpdate(health)
                } catch (e: Exception) {
                    recordNonFatalError("Failed to parse DeviceHealth: ${e.localizedMessage}", e)
                    onHealthUpdate(null)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                recordNonFatalError("Device health listener cancelled: ${error.message}", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun fetchIceServers(onResult: (List<org.webrtc.PeerConnection.IceServer>) -> Unit) {
        database.reference.child("ice_servers").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<org.webrtc.PeerConnection.IceServer>()
                for (item in snapshot.children) {
                    val uri = item.child("uri").getValue(String::class.java)
                        ?: item.child("url").getValue(String::class.java)
                    val username = item.child("username").getValue(String::class.java)
                    val credential = item.child("credential").getValue(String::class.java)
                        ?: item.child("password").getValue(String::class.java)

                    if (!uri.isNullOrEmpty()) {
                        val builder = org.webrtc.PeerConnection.IceServer.builder(uri)
                        if (!username.isNullOrEmpty()) builder.setUsername(username)
                        if (!credential.isNullOrEmpty()) builder.setPassword(credential)
                        list.add(builder.createIceServer())
                    }
                }
                if (list.isNotEmpty()) {
                    onResult(list)
                } else {
                    onResult(com.example.authapp.webrtc.WebRtcManager.getDefaultIceServers())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(com.example.authapp.webrtc.WebRtcManager.getDefaultIceServers())
            }
        })
    }

    // --- Security Alerts (Battery, SIM, Keywords) ---
    fun pushSecurityAlert(childId: String, alert: SecurityAlert) {
        val ref = database.reference.child("alerts").child(childId).push()
        val toSave = alert.copy(id = ref.key ?: "")
        ref.setValue(toSave)
    }

    fun listenToSecurityAlerts(childId: String, onAlerts: (List<SecurityAlert>) -> Unit): ValueEventListener =
        listenToChildNodeList("alerts", childId, limit = 50, transform = { it.sortedByDescending { a -> a.timestamp } }, onAlerts)

    // --- Call Log History (Cloud Firestore - eliminates RTDB bloat) ---
    fun syncCallLogs(childId: String, callLogs: List<CallLogItem>) {
        if (childId.isEmpty() || callLogs.isEmpty()) return
        
        // Primary write to Cloud Firestore
        try {
            val batch = firestore.batch()
            val collection = firestore.collection("call_logs").document(childId).collection("items")
            for (log in callLogs) {
                val key = if (log.id.isNotEmpty()) log.id else "${log.timestamp}_${log.number.takeLast(4)}"
                val docRef = collection.document(key)
                batch.set(docRef, log.copy(id = key), SetOptions.merge())
            }
            batch.commit().addOnFailureListener { e -> crashlytics.recordException(e) }
        } catch (e: Exception) {
            crashlytics.recordException(e)
        }
    }

    fun listenToCallLogs(childId: String, onLogs: (List<CallLogItem>) -> Unit): ValueEventListener =
        listenToChildNodeList("call_logs", childId, limit = 50, transform = { it.sortedByDescending { c -> c.timestamp } }, onLogs)

    // --- Notification Mirroring (Cloud Firestore - saves RTDB Quota) ---
    fun pushNotification(childId: String, item: NotificationItem) {
        if (childId.isEmpty()) return
        val docRef = firestore.collection("notifications").document(childId).collection("items").document()
        val toSave = item.copy(id = docRef.id)
        docRef.set(toSave, SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToNotificationsFirestore(childId: String, limit: Long = 50, onNotifications: (List<NotificationItem>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("notifications", childId, "timestamp", limit, onNotifications)

    fun listenToNotifications(childId: String, onNotifications: (List<NotificationItem>) -> Unit): ValueEventListener =
        listenToChildNodeList("notifications", childId, limit = 50, transform = { it.sortedByDescending { n -> n.timestamp } }, onNotifications)

    // --- Scheduled Recordings ---
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
        listenToChildNodeList("schedules", childId, limit = 25, onData = onSchedules)

    // --- Remote Snapshots ---
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
        // BUG FIX: Hardcoded ".jpg" tha — ab actual file extension fileUri se dynamically extract karo
        val ext = fileUri.path?.substringAfterLast('.')?.takeIf { it.length in 2..5 } ?: "jpg"
        val storageRef = storage.reference.child("snapshots/$childId/$timestamp.$ext")
        val mimeType = if (ext.equals("webp", ignoreCase = true)) "image/webp" else "image/jpeg"
        val metadata = com.google.firebase.storage.StorageMetadata.Builder().setContentType(mimeType).build()

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
        listenToChildNodeList("snapshots", childId, limit = 50, transform = { it.sortedByDescending { s -> s.timestamp } }, onData = onSnapshots)

    // --- Reactive Clean Architecture Flow Extensions (Best Practice) ---
    fun listenToChildUsersFlow(): Flow<List<User>> = callbackFlow {
        val listener = listenToChildUsers { trySend(it) }
        awaitClose { removeValueListener("users", listener) }
    }

    fun listenToDeviceHealthFlow(childId: String): Flow<DeviceHealth?> = callbackFlow {
        val listener = listenToDeviceHealth(childId) { trySend(it) }
        awaitClose { removeValueListener("device_health/$childId", listener) }
    }

    fun listenToSecurityAlertsFlow(childId: String): Flow<List<SecurityAlert>> = callbackFlow {
        val listener = listenToSecurityAlerts(childId) { trySend(it) }
        awaitClose { removeValueListener("alerts/$childId", listener) }
    }

    fun listenToCallLogsFlow(childId: String): Flow<List<CallLogItem>> = callbackFlow {
        val listener = listenToCallLogs(childId) { trySend(it) }
        awaitClose { removeValueListener("call_logs/$childId", listener) }
    }

    fun listenToNotificationsFlow(childId: String): Flow<List<NotificationItem>> = callbackFlow {
        val listener = listenToNotifications(childId) { trySend(it) }
        awaitClose { removeValueListener("notifications/$childId", listener) }
    }

    fun listenToRecordingSchedulesFlow(childId: String): Flow<List<RecordingSchedule>> = callbackFlow {
        val listener = listenToRecordingSchedules(childId) { trySend(it) }
        awaitClose { removeValueListener("schedules/$childId", listener) }
    }

    // --- Parental Controls & App Usage Tracking (Cloud Firestore - eliminates RTDB bloat) ---
    fun syncAppUsage(childId: String, appUsageList: List<AppUsageInfo>) {
        if (childId.isEmpty()) return
        try {
            val collection = firestore.collection("app_usage").document(childId).collection("items")
            val batch = firestore.batch()
            for (app in appUsageList.take(50)) {
                val key = app.packageName.replace(".", "_")
                val docRef = collection.document(key)
                batch.set(docRef, app, SetOptions.merge())
            }
            batch.commit().addOnFailureListener { e -> crashlytics.recordException(e) }
        } catch (e: Exception) {
            crashlytics.recordException(e)
        }
    }

    fun listenToAppUsageFirestore(childId: String, limit: Long = 50, onUsageUpdated: (List<AppUsageInfo>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("app_usage", childId, "totalTimeInForegroundMinutes", limit, onUsageUpdated)

    fun listenToAppUsage(childId: String, onUsageUpdated: (List<AppUsageInfo>) -> Unit): ValueEventListener =
        listenToChildNodeList("app_usage", childId, limit = 50, transform = { it.sortedByDescending { a -> a.totalTimeInForegroundMinutes } }, onData = onUsageUpdated)

    fun setAppBlocked(childId: String, packageName: String, isBlocked: Boolean) {
        if (childId.isEmpty() || packageName.isEmpty()) return
        val sanitizedKey = packageName.replace(".", "_")
        database.reference.child("parent_controls").child(childId).child("blockedPackages").child(sanitizedKey).setValue(isBlocked)
    }

    fun setStudyMode(childId: String, isActive: Boolean, durationMinutes: Int = 0) {
        if (childId.isEmpty()) return
        val untilTimestamp = if (isActive && durationMinutes > 0) System.currentTimeMillis() + (durationMinutes * 60 * 1000L) else 0L
        val data = mapOf(
            "isStudyModeActive" to isActive,
            "studyModeUntilTimestamp" to untilTimestamp,
            "lastUpdated" to System.currentTimeMillis()
        )
        database.reference.child("parent_controls").child(childId).updateChildren(data)
    }

    fun listenToParentControls(childId: String, onControlsUpdated: (ParentControlSettings) -> Unit): ValueEventListener {
        val ref = database.reference.child("parent_controls").child(childId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isStudyModeActive = snapshot.child("isStudyModeActive").getValue(Boolean::class.java) ?: false
                val studyModeMessage = snapshot.child("studyModeMessage").getValue(String::class.java) ?: "Study Mode is active. Focus on your studies!"
                val studyModeUntilTimestamp = snapshot.child("studyModeUntilTimestamp").getValue(Long::class.java) ?: 0L
                val lastUpdated = snapshot.child("lastUpdated").getValue(Long::class.java) ?: 0L
                val dailyLimit = (snapshot.child("dailyScreenTimeLimitMinutes").getValue(Long::class.java) ?: 0L).toInt()

                val blockedMap = mutableMapOf<String, Boolean>()
                for (child in snapshot.child("blockedPackages").children) {
                    val rawPkg = child.key ?: continue
                    val isBlocked = child.getValue(Boolean::class.java) ?: false
                    // BUG FIX: Sanitized key "com_google_android_youtube" → original "com.google.android.youtube"
                    // setAppBlocked() mein "." ko "_" se replace kiya tha — yahan undo karo
                    val decodedPkg = rawPkg.replace("_", ".")
                    // Both forms store karo taaki purane aur naye data formats dono match hon
                    blockedMap[rawPkg] = isBlocked
                    if (decodedPkg != rawPkg) blockedMap[decodedPkg] = isBlocked
                }

                val settings = ParentControlSettings(
                    isStudyModeActive = isStudyModeActive,
                    studyModeMessage = studyModeMessage,
                    studyModeUntilTimestamp = studyModeUntilTimestamp,
                    blockedPackages = blockedMap,
                    dailyScreenTimeLimitMinutes = dailyLimit,
                    lastUpdated = lastUpdated
                )
                onControlsUpdated(settings)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    // --- SMS Logs Sync (Cloud Firestore - eliminates RTDB bloat) ---
    fun syncSmsLogs(childId: String, smsList: List<SmsItem>) {
        if (childId.isEmpty() || smsList.isEmpty()) return

        // Primary write to Cloud Firestore
        try {
            val batch = firestore.batch()
            val collection = firestore.collection("sms_logs").document(childId).collection("items")
            for (sms in smsList) {
                val key = if (sms.id.isNotEmpty()) sms.id else "${sms.timestamp}_${sms.address.takeLast(4)}"
                val docRef = collection.document(key)
                batch.set(docRef, sms.copy(id = key), SetOptions.merge())
            }
            batch.commit().addOnFailureListener { e -> crashlytics.recordException(e) }
        } catch (e: Exception) {
            crashlytics.recordException(e)
        }
    }

    fun listenToSmsLogs(childId: String, onSms: (List<SmsItem>) -> Unit): ValueEventListener =
        listenToChildNodeList("sms_logs", childId, limit = 50, transform = { it.sortedByDescending { s -> s.timestamp } }, onSms)

    // --- Remote Hardware Commands (Torch, Siren - Phase 4) ---
    fun sendRemoteCommand(childId: String, command: String, value: Any = true) {
        FirebaseControlRepository.sendRemoteCommand(childId, command, value)
    }

    fun listenToRemoteCommands(childId: String, onCommand: (String, Any?) -> Unit): ValueEventListener {
        val ref = database.reference.child("commands").child(childId)
        // BUG FIX: Per-command timestamp track karo — same command baar baar trigger hota tha
        val lastProcessedTsMap = mutableMapOf<String, Long>()
        val lastProcessedRequestMap = mutableMapOf<String, String>()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val cmd = child.key ?: continue
                    val value = child.child("value").value
                    val requestId = child.child("requestId").getValue(String::class.java).orEmpty()
                    val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val now = System.currentTimeMillis()
                    // Only process recent commands (within last 2 minutes) AND not already processed
                    val lastTs = lastProcessedTsMap[cmd] ?: 0L
                    val duplicateRequest = requestId.isNotEmpty() && lastProcessedRequestMap[cmd] == requestId
                    if (!duplicateRequest && now - ts < 2 * 60 * 1000L && ts > lastTs) {
                        lastProcessedTsMap[cmd] = ts
                        if (requestId.isNotEmpty()) lastProcessedRequestMap[cmd] = requestId
                        onCommand(cmd, value)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    // --- Location History & Breadcrumbs (Phase 4) ---
    fun recordLocationHistoryPoint(childId: String, loc: UserLocation) {
        if (childId.isEmpty() || loc.latitude == 0.0) return
        val timestamp = if (loc.timestamp > 0L) loc.timestamp else System.currentTimeMillis()
        database.reference.child("location_history").child(childId).push().setValue(loc.copy(timestamp = timestamp))
    }

    fun recordLocationHistoryBatch(childId: String, locations: List<UserLocation>) {
        if (childId.isEmpty() || locations.isEmpty()) return
        val ref = database.reference.child("location_history").child(childId)
        val batchMap = mutableMapOf<String, Any>()
        locations.forEach { loc ->
            if (loc.latitude != 0.0) {
                val newKey = ref.push().key
                if (!newKey.isNullOrEmpty()) {
                    val timestamp = if (loc.timestamp > 0L) loc.timestamp else System.currentTimeMillis()
                    batchMap[newKey] = loc.copy(timestamp = timestamp)
                }
            }
        }
        if (batchMap.isNotEmpty()) {
            ref.updateChildren(batchMap)
        }
    }

    fun listenToLocationHistory(childId: String, onHistory: (List<UserLocation>) -> Unit): ValueEventListener =
        listenToChildNodeList("location_history", childId, limit = 50, transform = { it.sortedByDescending { l -> l.timestamp }.take(50) }, onHistory)

    // --- Geofence Zones (Phase 4) ---
    fun saveGeofence(childId: String, zone: GeofenceZone) {
        if (childId.isEmpty() || zone.id.isEmpty()) return
        database.reference.child("geofences").child(childId).child(zone.id).setValue(zone)
    }

    fun listenToGeofences(childId: String, onZones: (List<GeofenceZone>) -> Unit): ValueEventListener =
        listenToChildNodeList("geofences", childId, limit = 50, onData = onZones)

    // --- Media & Gallery Items (Cloud Firestore - eliminates RTDB bloat) ---
    fun syncMediaItems(childId: String, items: List<MediaItemInfo>) {
        if (childId.isEmpty()) return
        val collectionRef = firestore.collection("media_items").document(childId).collection("items")
        val batch = firestore.batch()
        items.take(50).forEach { item ->
            val key = if (item.id.isNotEmpty()) item.id else "${item.timestamp}_${item.displayName.hashCode()}"
            val docRef = collectionRef.document(key)
            batch.set(docRef, item.copy(id = key), SetOptions.merge())
        }
        batch.commit().addOnFailureListener { e ->
            crashlytics.recordException(e)
            // Fallback write to RTDB
            database.reference.child("media_items").child(childId).setValue(items)
        }
    }

    fun listenToMediaItemsFirestore(childId: String, limit: Long = 50, onMedia: (List<MediaItemInfo>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("media_items", childId, "timestamp", limit, onMedia)

    fun listenToMediaItems(childId: String, onMedia: (List<MediaItemInfo>) -> Unit): ValueEventListener =
        listenToChildNodeList("media_items", childId, limit = 50, transform = { it.sortedByDescending { m -> m.timestamp } }, onMedia)

    // --- Web History & Safety (Feature 5) ---
    fun logWebHistoryItem(childId: String, item: WebHistoryItem) {
        if (childId.isEmpty() || item.url.isEmpty()) return
        val ref = database.reference.child("web_history").child(childId).push()
        ref.setValue(item.copy(id = ref.key ?: ""))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToWebHistory(childId: String, onHistory: (List<WebHistoryItem>) -> Unit): ValueEventListener =
        listenToChildNodeList("web_history", childId, limit = 100, transform = { it.sortedByDescending { w -> w.timestamp }.take(100) }, onHistory)

    // --- Network & Wi-Fi Connection History (Feature 6) ---
    fun recordNetworkHistory(childId: String, item: NetworkHistoryItem) {
        if (childId.isEmpty()) return
        val ref = database.reference.child("network_history").child(childId).push()
        ref.setValue(item.copy(id = ref.key ?: ""))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToNetworkHistory(childId: String, onHistory: (List<NetworkHistoryItem>) -> Unit): ValueEventListener =
        listenToChildNodeList("network_history", childId, limit = 50, transform = { it.sortedByDescending { n -> n.connectedAt }.take(50) }, onHistory)

    // --- Package Install/Uninstall Events (Feature 8) ---
    fun recordPackageEvent(childId: String, event: AppInstallEvent) {
        if (childId.isEmpty()) return
        val ref = database.reference.child("package_events").child(childId).push()
        ref.setValue(event.copy(id = ref.key ?: ""))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToPackageEvents(childId: String, onEvents: (List<AppInstallEvent>) -> Unit): ValueEventListener =
        listenToChildNodeList("package_events", childId, limit = 50, transform = { it.sortedByDescending { e -> e.timestamp }.take(50) }, onData = onEvents)

    // --- SIM Card Info & Swap Alerts (Feature 7) ---
    fun saveSimCardInfo(childId: String, info: SimCardInfo) {
        if (childId.isEmpty()) return
        database.reference.child("sim_info").child(childId).setValue(info)
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToSimCardInfo(childId: String, onInfo: (SimCardInfo?) -> Unit): ValueEventListener {
        val ref = database.reference.child("sim_info").child(childId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val info = snapshot.getValue(SimCardInfo::class.java)
                onInfo(info)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    // --- App Install / Uninstall Events (Feature 1) ---
    fun logAppInstallEvent(childId: String, event: AppInstallEvent) {
        if (childId.isEmpty()) return
        val ref = database.reference.child("package_events").child(childId).push()
        ref.setValue(event.copy(id = ref.key ?: ""))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToAppInstallEvents(childId: String, onEvents: (List<AppInstallEvent>) -> Unit): ValueEventListener =
        listenToChildNodeList("package_events", childId, limit = 50, transform = { it.sortedByDescending { e -> e.timestamp }.take(50) }, onEvents)

    fun logSingleCallLog(childId: String, callLog: CallLogItem) {
        if (childId.isEmpty()) return
        val key = if (callLog.id.isNotEmpty()) callLog.id else "${callLog.timestamp}_${callLog.number.takeLast(4)}"
        
        // 1. Write to Firestore (Primary Scalable Document Store)
        firestore.collection("call_logs").document(childId)
            .collection("items").document(key)
            .set(callLog.copy(id = key), SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        // 2. Backward compatibility fallback in RTDB
        database.reference.child("call_logs").child(childId).child(key).setValue(callLog.copy(id = key))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun attachRecordingUrlToLatestCallLog(childId: String, recordingUrl: String) {
        if (childId.isEmpty() || recordingUrl.isEmpty()) return
        firestore.collection("call_logs").document(childId)
            .collection("items")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnap ->
                val doc = querySnap.documents.firstOrNull() ?: return@addOnSuccessListener
                doc.reference.update("audioRecordingUrl", recordingUrl)
                database.reference.child("call_logs").child(childId).child(doc.id)
                    .child("audioRecordingUrl").setValue(recordingUrl)
            }
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    // ==========================================
    // CLOUD FIRESTORE INTEGRATION & COST SAVING
    // ==========================================

    fun saveCallLogToFirestore(childId: String, callLog: CallLogItem) {
        logSingleCallLog(childId, callLog)
    }

    fun listenToChildCallLogsFirestore(childId: String, limit: Long = 50, onUpdate: (List<CallLogItem>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("call_logs", childId, "timestamp", limit, onUpdate)

    fun saveSmsLogToFirestore(childId: String, sms: SmsItem) {
        if (childId.isEmpty()) return
        val key = if (sms.id.isNotEmpty()) sms.id else "${sms.timestamp}_${sms.address.takeLast(4)}"
        firestore.collection("sms_logs").document(childId)
            .collection("items").document(key)
            .set(sms.copy(id = key), SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        database.reference.child("sms_logs").child(childId).child(key).setValue(sms.copy(id = key))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToChildSmsLogsFirestore(childId: String, limit: Long = 50, onUpdate: (List<SmsItem>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("sms_logs", childId, "timestamp", limit, onUpdate)

    fun saveWebHistoryToFirestore(childId: String, item: WebHistoryItem) {
        if (childId.isEmpty() || item.url.isEmpty()) return
        val docRef = firestore.collection("web_history").document(childId).collection("items").document()
        val finalItem = item.copy(id = docRef.id)
        docRef.set(finalItem, SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        val rtdbRef = database.reference.child("web_history").child(childId).push()
        rtdbRef.setValue(item.copy(id = rtdbRef.key ?: ""))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToChildWebHistoryFirestore(childId: String, limit: Long = 50, onUpdate: (List<WebHistoryItem>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("web_history", childId, "timestamp", limit, onUpdate)

    fun saveNetworkHistoryToFirestore(childId: String, item: NetworkHistoryItem) {
        if (childId.isEmpty()) return
        val docRef = firestore.collection("network_history").document(childId).collection("items").document()
        val finalItem = item.copy(id = docRef.id)
        docRef.set(finalItem, SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        val rtdbRef = database.reference.child("network_history").child(childId).push()
        rtdbRef.setValue(item.copy(id = rtdbRef.key ?: ""))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToChildNetworkHistoryFirestore(childId: String, limit: Long = 50, onUpdate: (List<NetworkHistoryItem>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("network_history", childId, "connectedAt", limit, onUpdate)

    fun savePackageEventToFirestore(childId: String, event: AppInstallEvent) {
        if (childId.isEmpty()) return
        val docRef = firestore.collection("package_events").document(childId).collection("items").document()
        val finalItem = event.copy(id = docRef.id)
        docRef.set(finalItem, SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        val rtdbRef = database.reference.child("package_events").child(childId).push()
        rtdbRef.setValue(event.copy(id = rtdbRef.key ?: ""))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToChildPackageEventsFirestore(childId: String, limit: Long = 50, onUpdate: (List<AppInstallEvent>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("package_events", childId, "timestamp", limit, onUpdate)

    fun saveSecurityAlertToFirestore(childId: String, alert: SecurityAlert) {
        if (childId.isEmpty()) return
        val key = if (alert.id.isNotEmpty()) alert.id else "${alert.timestamp}_${alert.type}"
        firestore.collection("security_alerts").document(childId)
            .collection("items").document(key)
            .set(alert.copy(id = key), SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        database.reference.child("alerts").child(childId).child(key).setValue(alert.copy(id = key))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToChildSecurityAlertsFirestore(childId: String, limit: Long = 50, onUpdate: (List<SecurityAlert>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("security_alerts", childId, "timestamp", limit, onUpdate)

    fun saveRecordingToFirestore(childId: String, session: RecordingSession) {
        if (childId.isEmpty()) return
        val key = if (session.id.isNotEmpty()) session.id else "${session.startTime}_${session.streamType}"
        firestore.collection("recordings").document(childId)
            .collection("items").document(key)
            .set(session.copy(id = key), SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        database.reference.child("recordings").child(childId).child(key).setValue(session.copy(id = key))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToRecordingsFirestore(childId: String, limit: Long = 50, onUpdate: (List<RecordingSession>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("recordings", childId, "startTime", limit, onUpdate)

    // --- 6-Digit In-App Pairing Code System ---

    fun getOrGenerateParentPairingCode(parentUid: String, parentEmail: String, onComplete: (code: String?, error: String?) -> Unit) {
        if (parentUid.isEmpty()) {
            onComplete(null, "Parent UID is empty")
            return
        }
        val userRef = database.reference.child("users").child(parentUid)
        userRef.child("pairingCode").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existingCode = snapshot.getValue(String::class.java)
                if (!existingCode.isNullOrBlank()) {
                    database.reference.child("pairing_codes").child(existingCode).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(codeSnap: DataSnapshot) {
                            val isUsed = codeSnap.child("isUsed").getValue(Boolean::class.java) ?: false
                            if (codeSnap.exists() && !isUsed) {
                                onComplete(existingCode, null)
                            } else {
                                createNewPairingCode(parentUid, parentEmail, onComplete)
                            }
                        }
                        override fun onCancelled(err: DatabaseError) {
                            createNewPairingCode(parentUid, parentEmail, onComplete)
                        }
                    })
                } else {
                    createNewPairingCode(parentUid, parentEmail, onComplete)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                createNewPairingCode(parentUid, parentEmail, onComplete)
            }
        })
    }

    fun generateNewParentPairingCode(parentUid: String, parentEmail: String, onComplete: (code: String?, error: String?) -> Unit) {
        createNewPairingCode(parentUid, parentEmail, onComplete)
    }

    private fun createNewPairingCode(parentUid: String, parentEmail: String, onComplete: (code: String?, error: String?) -> Unit) {
        val random6Digit = (100000..999999).random().toString()
        val codeData = mapOf<String, Any>(
            "parentUid" to parentUid,
            "parentEmail" to parentEmail,
            "createdAt" to ServerValue.TIMESTAMP
        )
        database.reference.child("pairing_codes").child(random6Digit).setValue(codeData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    database.reference.child("users").child(parentUid).child("pairingCode").setValue(random6Digit)
                    onComplete(random6Digit, null)
                } else {
                    onComplete(null, task.exception?.localizedMessage ?: "Failed to generate pairing code")
                }
            }
    }

    fun pairChildWithCode(childUid: String, code: String, onResult: (success: Boolean, parentEmail: String?, errorMsg: String?) -> Unit) {
        val trimmedCode = code.trim()
        if (childUid.isBlank()) {
            onResult(false, null, "Child UID is missing")
            return
        }
        if (trimmedCode.length != 6 || !trimmedCode.all(Char::isDigit)) {
            onResult(false, null, "Please enter a valid 6-digit code")
            return
        }

        val codeRef = database.reference.child("pairing_codes").child(trimmedCode)
        codeRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                if (currentData.value == null) return Transaction.abort()

                val isUsed = currentData.child("isUsed").getValue(Boolean::class.java) ?: false
                val createdAt = currentData.child("createdAt").getValue(Long::class.java) ?: 0L
                val parentUid = currentData.child("parentUid").getValue(String::class.java)

                if (isUsed || createdAt <= 0L || System.currentTimeMillis() - createdAt > 10 * 60 * 1000L || parentUid.isNullOrBlank()) {
                    return Transaction.abort()
                }

                currentData.child("linkedChildUid").value = childUid
                currentData.child("isUsed").value = true
                currentData.child("linkedAt").value = ServerValue.TIMESTAMP
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    onResult(false, null, error.message)
                    return
                }
                if (!committed || currentData == null) {
                    onResult(false, null, "Pairing code is invalid, expired, or already used. Please generate a new code.")
                    return
                }

                val parentUid = currentData.child("parentUid").getValue(String::class.java)
                val parentEmail = currentData.child("parentEmail").getValue(String::class.java) ?: "Parent"
                if (parentUid.isNullOrBlank()) {
                    onResult(false, null, "Pairing code data corrupted")
                    return
                }

                database.reference.child("users").child(childUid).child("parentId").setValue(parentUid)
                    .addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            firestore.collection("users").document(childUid)
                                .set(mapOf("parentId" to parentUid), SetOptions.merge())
                                .addOnFailureListener { e -> crashlytics.recordException(e) }
                            onResult(true, parentEmail, null)
                        } else {
                            // Best-effort rollback if linking the child profile failed.
                            codeRef.runTransaction(object : Transaction.Handler {
                                override fun doTransaction(data: com.google.firebase.database.MutableData): Transaction.Result {
                                    if (data.child("linkedChildUid").getValue(String::class.java) == childUid &&
                                        data.child("isUsed").getValue(Boolean::class.java) == true) {
                                        data.child("linkedChildUid").value = null
                                        data.child("isUsed").value = false
                                        data.child("linkedAt").value = null
                                    }
                                    return Transaction.success(data)
                                }
                                override fun onComplete(
                                    error: DatabaseError?,
                                    committed: Boolean,
                                    snapshot: DataSnapshot?
                                ) = Unit
                            })
                            onResult(false, null, updateTask.exception?.localizedMessage ?: "Failed to link to parent")
                        }
                    }
            }
        })
    }

    fun listenToPairingCodeStatus(code: String, onLinked: (childUid: String) -> Unit): ValueEventListener {
        val ref = database.reference.child("pairing_codes").child(code).child("linkedChildUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val childUid = snapshot.getValue(String::class.java)
                if (!childUid.isNullOrEmpty()) {
                    onLinked(childUid)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToChildParentLink(childUid: String, onParentIdUpdated: (String) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val parentId = snapshot.getValue(String::class.java) ?: ""
                onParentIdUpdated(parentId)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.reference.child("users").child(childUid).child("parentId").addValueEventListener(listener)
        return listener
    }

    fun getOrGenerateChildPairingCode(childUid: String, childEmail: String, onComplete: (code: String?, error: String?) -> Unit) {
        if (childUid.isBlank()) {
            onComplete(null, "Child UID is empty")
            return
        }
        val userRef = database.reference.child("users").child(childUid)
        userRef.child("childPairingCode").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existingCode = snapshot.getValue(String::class.java)
                if (!existingCode.isNullOrBlank()) {
                    database.reference.child("child_pairing_codes").child(existingCode).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(codeSnap: DataSnapshot) {
                            val isUsed = codeSnap.child("isUsed").getValue(Boolean::class.java) ?: false
                            if (codeSnap.exists() && !isUsed) {
                                onComplete(existingCode, null)
                            } else {
                                createNewChildPairingCode(childUid, childEmail, onComplete)
                            }
                        }
                        override fun onCancelled(err: DatabaseError) {
                            createNewChildPairingCode(childUid, childEmail, onComplete)
                        }
                    })
                } else {
                    createNewChildPairingCode(childUid, childEmail, onComplete)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                createNewChildPairingCode(childUid, childEmail, onComplete)
            }
        })
    }

    fun generateNewChildPairingCode(childUid: String, childEmail: String, onComplete: (code: String?, error: String?) -> Unit) {
        createNewChildPairingCode(childUid, childEmail, onComplete)
    }

    private fun createNewChildPairingCode(childUid: String, childEmail: String, onComplete: (code: String?, error: String?) -> Unit) {
        val random6Digit = (100000..999999).random().toString()
        val codeData = mapOf<String, Any>(
            "childUid" to childUid,
            "childEmail" to childEmail,
            "createdAt" to ServerValue.TIMESTAMP,
            "isUsed" to false
        )
        database.reference.child("child_pairing_codes").child(random6Digit).setValue(codeData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    database.reference.child("users").child(childUid).child("childPairingCode").setValue(random6Digit)
                    onComplete(random6Digit, null)
                } else {
                    onComplete(null, task.exception?.localizedMessage ?: "Failed to generate child pairing code")
                }
            }
    }

    fun pairParentWithChildCode(
        parentUid: String,
        parentEmail: String,
        code: String,
        onResult: (success: Boolean, childUid: String?, childEmail: String?, errorMsg: String?) -> Unit
    ) {
        val trimmedCode = code.trim()
        if (parentUid.isBlank()) {
            onResult(false, null, null, "Parent UID is missing")
            return
        }
        if (trimmedCode.length != 6 || !trimmedCode.all(Char::isDigit)) {
            onResult(false, null, null, "Please enter a valid 6-digit code")
            return
        }

        val codeRef = database.reference.child("child_pairing_codes").child(trimmedCode)
        codeRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                if (currentData.value == null) return Transaction.abort()

                val isUsed = currentData.child("isUsed").getValue(Boolean::class.java) ?: false
                val childUid = currentData.child("childUid").getValue(String::class.java)
                if (isUsed || childUid.isNullOrBlank()) {
                    return Transaction.abort()
                }

                currentData.child("linkedParentUid").value = parentUid
                currentData.child("linkedParentEmail").value = parentEmail
                currentData.child("isUsed").value = true
                currentData.child("linkedAt").value = ServerValue.TIMESTAMP
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    onResult(false, null, null, error.message)
                    return
                }
                if (!committed || currentData == null) {
                    onResult(false, null, null, "Pairing code is invalid, already used, or expired.")
                    return
                }

                val childUid = currentData.child("childUid").getValue(String::class.java)
                val childEmail = currentData.child("childEmail").getValue(String::class.java) ?: "Child Device"
                if (childUid.isNullOrBlank()) {
                    onResult(false, null, null, "Child pairing data corrupted")
                    return
                }

                // Link child to parent
                database.reference.child("users").child(childUid).child("parentId").setValue(parentUid)
                    .addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            try {
                                firestore.collection("users").document(childUid)
                                    .set(mapOf("parentId" to parentUid), SetOptions.merge())
                            } catch (_: Exception) {}
                            onResult(true, childUid, childEmail, null)
                        } else {
                            onResult(false, null, null, updateTask.exception?.localizedMessage ?: "Failed to link device")
                        }
                    }
            }
        })
    }

    fun unpairChild(childUid: String, onComplete: (Boolean, String?) -> Unit) {
        val updates = mapOf<String, Any?>(
            "users/$childUid/parentId" to null,
            "users/$childUid/childPairingCode" to null,
            "streams/$childUid" to null
        )
        database.reference.updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                try {
                    firestore.collection("users").document(childUid)
                        .set(mapOf("parentId" to ""), SetOptions.merge())
                } catch (_: Exception) {}
                onComplete(true, null)
            } else {
                onComplete(false, task.exception?.localizedMessage ?: "Failed to unpair child")
            }
        }
    }

    // --- WhatsApp Chat & Status Monitoring (Cloud Firestore - saves RTDB Bandwidth) ---
    fun pushWhatsAppLog(childId: String, item: WhatsAppLogItem) {
        if (childId.isEmpty()) return
        val docRef = firestore.collection("whatsapp_logs").document(childId).collection("items").document()
        val toSave = item.copy(id = docRef.id)

        // 1. Primary write to Cloud Firestore
        docRef.set(toSave, SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        // 2. Backward compatibility fallback in RTDB
        try {
            val ref = database.reference.child(FirebasePaths.WHATSAPP_LOGS).child(childId).child(toSave.id)
            ref.setValue(toSave)
        } catch (_: Exception) {}
    }

    fun listenToWhatsAppLogsFirestore(childId: String, limit: Long = 50, onLogs: (List<WhatsAppLogItem>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("whatsapp_logs", childId, "timestamp", limit, onLogs)

    fun listenToWhatsAppLogs(childId: String, onLogs: (List<WhatsAppLogItem>) -> Unit): ValueEventListener =
        listenToChildNodeList(FirebasePaths.WHATSAPP_LOGS, childId, limit = 100, transform = { it.sortedByDescending { w -> w.timestamp } }, onLogs)

    // --- Clipboard Copy-Paste Monitoring (Cloud Firestore - saves RTDB Bandwidth) ---
    fun pushClipboardLog(childId: String, text: String) {
        val trimmed = text.trim()
        if (childId.isEmpty() || trimmed.isEmpty()) return
        val docRef = firestore.collection("clipboard_logs").document(childId).collection("items").document()
        val item = ClipboardLogItem(
            id = docRef.id,
            text = trimmed,
            timestamp = System.currentTimeMillis()
        )

        // 1. Primary write to Cloud Firestore
        docRef.set(item, SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        // 2. Backward compatibility fallback in RTDB
        try {
            database.reference.child(FirebasePaths.CLIPBOARD_LOGS).child(childId).child(item.id).setValue(item)
        } catch (_: Exception) {}
    }

    fun listenToClipboardLogsFirestore(childId: String, limit: Long = 50, onLogs: (List<ClipboardLogItem>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("clipboard_logs", childId, "timestamp", limit, onLogs)

    fun listenToClipboardLogs(childId: String, onLogs: (List<ClipboardLogItem>) -> Unit): ValueEventListener =
        listenToChildNodeList(FirebasePaths.CLIPBOARD_LOGS, childId, limit = 100, transform = { it.sortedByDescending { c -> c.timestamp } }, onLogs)

    // --- Firebase Remote Config Feature Flags ---
    fun fetchRemoteConfig(onConfigFetched: (Map<String, Boolean>) -> Unit = {}) {
        try {
            val remoteConfig = com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance()
            val configSettings = com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.setDefaultsAsync(
                mapOf(
                    "enable_whatsapp_monitoring" to true,
                    "enable_sms_commands" to true,
                    "enable_live_video" to true,
                    "enable_offline_gps_cache" to true
                )
            )
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                val config = mapOf(
                    "enable_whatsapp_monitoring" to remoteConfig.getBoolean("enable_whatsapp_monitoring"),
                    "enable_sms_commands" to remoteConfig.getBoolean("enable_sms_commands"),
                    "enable_live_video" to remoteConfig.getBoolean("enable_live_video"),
                    "enable_offline_gps_cache" to remoteConfig.getBoolean("enable_offline_gps_cache")
                )
                onConfigFetched(config)
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            onConfigFetched(emptyMap())
        }
    }

    // --- OTA App Update Management ---
    fun publishAppUpdate(
        versionCode: Long,
        versionName: String,
        apkUrl: String,
        releaseNotes: String,
        isForceUpdate: Boolean = false,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val data = mapOf<String, Any>(
            "versionCode" to versionCode,
            "versionName" to versionName,
            "apkUrl" to apkUrl,
            "releaseNotes" to releaseNotes,
            "isForceUpdate" to isForceUpdate,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        database.reference.child("app_update").setValue(data)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                crashlytics.recordException(e)
                onComplete(false)
            }
    }

    fun pushYouTubeLog(childUid: String, item: YouTubeLogItem) {
        if (childUid.isEmpty()) return
        val ref = database.reference.child(FirebasePaths.YOUTUBE_HISTORY).child(childUid).push()
        val itemWithId = item.copy(id = ref.key ?: "")
        ref.setValue(itemWithId)
    }

    // --- Media Gallery (Cloud Firestore - eliminates RTDB quota bloat) ---
    fun syncMediaGallery(childUid: String, items: List<MediaGalleryItem>) {
        if (childUid.isEmpty()) return
        val collectionRef = firestore.collection("media_gallery").document(childUid).collection("items")
        val batch = firestore.batch()
        items.take(100).forEach { item ->
            val key = if (item.id.isNotEmpty()) item.id else item.filePath.hashCode().toString()
            val docRef = collectionRef.document(key)
            batch.set(docRef, item.copy(id = key), SetOptions.merge())
        }
        batch.commit().addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun pushMediaGalleryItem(childUid: String, item: MediaGalleryItem) {
        if (childUid.isEmpty()) return
        val docRef = firestore.collection("media_gallery").document(childUid).collection("items").document()
        docRef.set(item.copy(id = docRef.id), SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToMediaGalleryFirestore(childId: String, limit: Long = 50, onItems: (List<MediaGalleryItem>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("media_gallery", childId, "dateAdded", limit, onItems)

    // --- File Explorer (Cloud Firestore - eliminates 1MB RTDB bloat) ---
    fun syncFileExplorer(childUid: String, items: List<FileExplorerItem>) {
        if (childUid.isEmpty()) return
        try {
            val collection = firestore.collection("file_explorer").document(childUid).collection("items")
            val batch = firestore.batch()
            for (file in items.take(100)) {
                val key = file.path.hashCode().toString()
                val docRef = collection.document(key)
                batch.set(docRef, file, SetOptions.merge())
            }
            batch.commit().addOnFailureListener { e -> crashlytics.recordException(e) }
        } catch (e: Exception) {
            crashlytics.recordException(e)
        }
    }

    fun listenToFileExplorerFirestore(childId: String, limit: Long = 100, onFiles: (List<FileExplorerItem>) -> Unit): ListenerRegistration =
        listenFirestoreCollection("file_explorer", childId, "name", limit, onFiles)

    fun listenToYouTubeLogs(childId: String, onLogs: (List<YouTubeLogItem>) -> Unit): ValueEventListener {
        return listenToChildNodeList(FirebasePaths.YOUTUBE_HISTORY, childId, limit = 50, transform = { it.reversed() }, onData = onLogs)
    }

    fun listenToMediaGallery(childId: String, onItems: (List<MediaGalleryItem>) -> Unit): ValueEventListener {
        return listenToChildNodeList("media_gallery", childId, limit = 50, onData = onItems)
    }

    fun listenToFileExplorer(childId: String, onFiles: (List<FileExplorerItem>) -> Unit): ValueEventListener {
        return listenToChildNodeList("file_explorer", childId, limit = 100, onData = onFiles)
    }

    /**
     * Client-side Auto-Pruning: Purges logs older than retentionDays (default: 30 days)
     * Keeps Realtime Database fast, under quotas, and reduces storage bloat.
     */
    fun pruneOldLogs(childId: String, retentionDays: Int = 30) {
        if (childId.isBlank()) return
        val cutoffTimestamp = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val nodesToPrune = listOf(
            "whatsapp_logs",
            "youtube_history",
            "notifications",
            "alerts",
            "call_logs",
            "sms_logs",
            "web_history",
            "network_history",
            "keylogs",
            "wifi_logs",
            "social_media_usage",
            "snapshots",
            "location_history"
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
}
