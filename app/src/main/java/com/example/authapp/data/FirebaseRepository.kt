package com.example.authapp.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object FirebaseRepository {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance("https://apnasatthilko-default-rtdb.asia-southeast1.firebasedatabase.app")
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()

    private var presenceConnectedListener: ValueEventListener? = null
    private var currentPresenceUid: String? = null

    val currentUser get() = auth.currentUser

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
        database.reference.child("users").child(uid).child("role")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val role = snapshot.getValue(String::class.java) ?: "child"
                    onRoleChanged(role)
                }

                override fun onCancelled(error: DatabaseError) {}
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

    fun listenToChildUsers(onUsersUpdated: (List<User>) -> Unit): ValueEventListener {
        val currentParentUid = currentUser?.uid ?: ""
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<User>()
                for (child in snapshot.children) {
                    val user = child.getValue(User::class.java)
                    if (user != null && user.role == "child") {
                        // If child is linked to a parent, only show to that parent.
                        // If not yet linked (legacy/unpaired), show so parent can claim/link.
                        val isBelongingToParent = user.parentId.isEmpty() || user.parentId == currentParentUid
                        if (isBelongingToParent) {
                            val isOnlineVal = child.child("isOnline").getValue(Boolean::class.java) ?: user.isOnline
                            val lastSeenVal = child.child("lastSeen").getValue(Long::class.java) ?: user.lastSeen
                            list.add(user.copy(isOnline = isOnlineVal, lastSeen = lastSeenVal))
                        }
                    }
                }
                onUsersUpdated(list)
            }

            override fun onCancelled(error: DatabaseError) {
                recordNonFatalError("listenToChildUsers cancelled: ${error.message}", error.toException())
            }
        }
        database.reference.child("users").addValueEventListener(listener)
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

    fun stopStream(childId: String, sessionId: String? = null) {
        val statusData = mapOf(
            "status" to "STOPPED",
            "timestamp" to System.currentTimeMillis()
        )
        database.reference.child("streams").child(childId).child("status").setValue(statusData)
        cleanupSignalingData(sessionId ?: "", childId)
        val parentId = currentUser?.uid
        if (parentId != null) {
            database.reference.child("signaling").child("session_${childId}_$parentId").removeValue()
        }
    }

    fun requestRemoteRecording(childId: String, isRecording: Boolean, streamType: String) {
        val data = mapOf(
            "isRecording" to isRecording,
            "streamType" to streamType,
            "timestamp" to System.currentTimeMillis()
        )
        database.reference.child("streams").child(childId).child("recordCommand").setValue(data)
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
                if (status == "REQUESTED" || status == "STREAMING") {
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

    fun saveRecordingSession(
        childId: String,
        streamType: String,
        durationSeconds: Long,
        localFilePath: String = "",
        parentId: String = "",
        onSaved: () -> Unit
    ) {
        val effectiveParentId = if (parentId.isNotEmpty()) parentId else (currentUser?.uid ?: "")
        val recId = "rec_${System.currentTimeMillis()}"
        val session = RecordingSession(
            id = recId,
            childId = childId,
            parentId = effectiveParentId,
            streamType = streamType,
            durationSeconds = durationSeconds,
            status = "SAVED",
            storageUrl = if (localFilePath.isNotEmpty()) "file://$localFilePath" else "gs://apnasatthilko.appspot.com/recordings/$childId/$recId.mp4",
            localFilePath = localFilePath
        )
        database.reference.child("recordings").child(childId).child(recId).setValue(session)
            .addOnSuccessListener { onSaved() }
            .addOnFailureListener { e ->
                FirebaseCrashlytics.getInstance().recordException(e)
            }
    }

    fun uploadRecordingFile(
        childId: String,
        fileUri: Uri,
        streamType: String,
        durationSeconds: Long,
        localFilePath: String = "",
        parentId: String = "",
        onSuccess: (RecordingSession) -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        val effectiveParentId = if (parentId.isNotEmpty()) parentId else (currentUser?.uid ?: "")
        val recId = "rec_${System.currentTimeMillis()}"
        val extension = if (streamType.equals("video", ignoreCase = true)) "mp4" else "m4a"
        val ref = storage.reference.child("recordings/$childId/$recId.$extension")

        ref.putFile(fileUri)
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

    fun listenToCameraFacing(sessionId: String, onFacingChanged: (isFront: Boolean) -> Unit): ValueEventListener {
        val ref = database.reference.child("signaling").child(sessionId).child("cameraFacing")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val facing = snapshot.getValue(String::class.java)
                if (facing != null) {
                    onFacingChanged(facing == "front")
                }
            }
            override fun onCancelled(error: DatabaseError) {
                FirebaseCrashlytics.getInstance().log("[Firebase cameraFacing cancelled] ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToRecordings(childId: String, onRecordingsUpdated: (List<RecordingSession>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<RecordingSession>()
                for (child in snapshot.children) {
                    val session = child.getValue(RecordingSession::class.java)
                    if (session != null) list.add(session)
                }
                onRecordingsUpdated(list.reversed())
            }
            override fun onCancelled(error: DatabaseError) {
                FirebaseCrashlytics.getInstance().log("[Firebase recordings cancelled] ${error.message}")
            }
        }
        database.reference.child("recordings").child(childId).limitToLast(50).addValueEventListener(listener)
        return listener
    }

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

    fun listenToSecurityAlerts(childId: String, onAlerts: (List<SecurityAlert>) -> Unit): ValueEventListener {
        val ref = database.reference.child("alerts").child(childId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<SecurityAlert>()
                for (child in snapshot.children) {
                    child.getValue(SecurityAlert::class.java)?.let { list.add(it) }
                }
                onAlerts(list.sortedByDescending { it.timestamp })
            }
            override fun onCancelled(error: DatabaseError) {
                FirebaseCrashlytics.getInstance().log("[Firebase alerts cancelled] ${error.message}")
            }
        }
        ref.limitToLast(50).addValueEventListener(listener)
        return listener
    }

    // --- Call Log History ---
    fun syncCallLogs(childId: String, callLogs: List<CallLogItem>) {
        if (callLogs.isEmpty()) return
        val ref = database.reference.child("call_logs").child(childId)
        val map = mutableMapOf<String, Any>()
        for (log in callLogs) {
            val key = if (log.id.isNotEmpty()) log.id else "${log.timestamp}_${log.number.takeLast(4)}"
            map[key] = log.copy(id = key)
        }
        ref.updateChildren(map)
    }

    fun listenToCallLogs(childId: String, onLogs: (List<CallLogItem>) -> Unit): ValueEventListener {
        val ref = database.reference.child("call_logs").child(childId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CallLogItem>()
                for (child in snapshot.children) {
                    child.getValue(CallLogItem::class.java)?.let { list.add(it) }
                }
                onLogs(list.sortedByDescending { it.timestamp })
            }
            override fun onCancelled(error: DatabaseError) {
                FirebaseCrashlytics.getInstance().log("[Firebase call_logs cancelled] ${error.message}")
            }
        }
        ref.limitToLast(50).addValueEventListener(listener)
        return listener
    }

    // --- Notification Mirroring ---
    fun pushNotification(childId: String, item: NotificationItem) {
        val ref = database.reference.child("notifications").child(childId).push()
        val toSave = item.copy(id = ref.key ?: "")
        ref.setValue(toSave)
    }

    fun listenToNotifications(childId: String, onNotifications: (List<NotificationItem>) -> Unit): ValueEventListener {
        val ref = database.reference.child("notifications").child(childId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<NotificationItem>()
                for (child in snapshot.children) {
                    child.getValue(NotificationItem::class.java)?.let { list.add(it) }
                }
                onNotifications(list.sortedByDescending { it.timestamp })
            }
            override fun onCancelled(error: DatabaseError) {
                FirebaseCrashlytics.getInstance().log("[Firebase notifications cancelled] ${error.message}")
            }
        }
        ref.limitToLast(50).addValueEventListener(listener)
        return listener
    }

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

    fun listenToRecordingSchedules(childId: String, onSchedules: (List<RecordingSchedule>) -> Unit): ValueEventListener {
        val ref = database.reference.child("schedules").child(childId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<RecordingSchedule>()
                for (child in snapshot.children) {
                    child.getValue(RecordingSchedule::class.java)?.let { list.add(it) }
                }
                onSchedules(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    // --- Remote Snapshots ---
    fun requestSnapshot(childId: String, cameraFacing: String = "back") {
        val data = mapOf(
            "requestedAt" to System.currentTimeMillis(),
            "cameraFacing" to cameraFacing,
            "status" to "REQUESTED"
        )
        database.reference.child("streams").child(childId).child("snapshotRequest").setValue(data)
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
        val storageRef = storage.reference.child("snapshots/$childId/$timestamp.jpg")
        storageRef.putFile(fileUri)
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
                        database.reference.child("streams").child(childId).child("snapshotRequest").removeValue()
                        onSuccess(info)
                    }.addOnFailureListener { e -> onFailure(e.localizedMessage ?: "DB Error") }
                }.addOnFailureListener { e -> onFailure(e.localizedMessage ?: "URL Error") }
            }
            .addOnFailureListener { e -> onFailure(e.localizedMessage ?: "Upload Error") }
    }

    fun listenToSnapshots(childId: String, onSnapshots: (List<SnapshotInfo>) -> Unit): ValueEventListener {
        val ref = database.reference.child("snapshots").child(childId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<SnapshotInfo>()
                for (child in snapshot.children) {
                    child.getValue(SnapshotInfo::class.java)?.let { list.add(it) }
                }
                onSnapshots(list.sortedByDescending { it.timestamp })
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

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

    fun listenToSnapshotsFlow(childId: String): Flow<List<SnapshotInfo>> = callbackFlow {
        val listener = listenToSnapshots(childId) { trySend(it) }
        awaitClose { removeValueListener("snapshots/$childId", listener) }
    }
}
