package com.example.authapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.example.authapp.utils.onValueChange
import org.webrtc.PeerConnection

object FirebaseSignalingRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

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

    fun requestStream(childId: String, streamType: String, onComplete: (String) -> Unit) {
        requestStream(childId, streamType, onComplete, null)
    }

    fun requestStream(
        childId: String,
        streamType: String,
        onComplete: (String) -> Unit,
        onError: ((Exception) -> Unit)?
    ) {
        val parentId = auth.currentUser?.uid
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
            "streamType" to streamType,
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
                if (!snapshot.exists() || !snapshot.hasChild("isRecording")) return
                val isRecording = snapshot.child("isRecording").getValue(Boolean::class.java) ?: return
                val streamType = snapshot.child("streamType").getValue(String::class.java) ?: "audio"
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
                    val sessionId = snapshot.child("sessionId").getValue(String::class.java) ?: "session_${childId}"
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
            .addOnFailureListener { e -> FirebaseCrashlytics.getInstance().recordException(e) }
    }

    fun sendSdpAnswer(sessionId: String, sdp: String) {
        database.reference.child("signaling").child(sessionId).child("sdpAnswer").setValue(sdp)
            .addOnFailureListener { e -> FirebaseCrashlytics.getInstance().recordException(e) }
    }

    fun sendIceCandidate(sessionId: String, candidate: Map<String, Any>, isParent: Boolean) {
        val targetNode = if (isParent) "parentCandidates" else "childCandidates"
        database.reference.child("signaling").child(sessionId).child(targetNode).push().setValue(candidate)
            .addOnFailureListener { e -> FirebaseCrashlytics.getInstance().recordException(e) }
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

    fun listenToSdpOffer(sessionId: String, onOfferReceived: (String) -> Unit): ValueEventListener =
        database.reference.child("signaling").child(sessionId).child("sdpOffer").onValueChange { snapshot ->
            snapshot.getValue(String::class.java)?.takeIf { it.isNotEmpty() }?.let(onOfferReceived)
        }

    fun listenToSdpAnswer(sessionId: String, onAnswerReceived: (String) -> Unit): ValueEventListener =
        database.reference.child("signaling").child(sessionId).child("sdpAnswer").onValueChange { snapshot ->
            snapshot.getValue(String::class.java)?.takeIf { it.isNotEmpty() }?.let(onAnswerReceived)
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

    fun listenToCameraFacingMode(sessionId: String, onFacingModeChanged: (facing: String) -> Unit): ValueEventListener =
        database.reference.child("signaling").child(sessionId).child("cameraFacing").onValueChange(
            onError = { FirebaseCrashlytics.getInstance().log("[Firebase cameraFacing cancelled] ${it.message}") }
        ) { snapshot ->
            snapshot.getValue(String::class.java)?.let(onFacingModeChanged)
        }

    fun fetchIceServers(onResult: (List<PeerConnection.IceServer>) -> Unit) {
        database.reference.child("ice_servers").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<PeerConnection.IceServer>()
                for (item in snapshot.children) {
                    val uri = item.child("uri").getValue(String::class.java)
                        ?: item.child("url").getValue(String::class.java)
                    val username = item.child("username").getValue(String::class.java)
                    val credential = item.child("credential").getValue(String::class.java)
                        ?: item.child("password").getValue(String::class.java)

                    if (!uri.isNullOrEmpty()) {
                        val builder = PeerConnection.IceServer.builder(uri)
                        if (!username.isNullOrEmpty()) builder.setUsername(username)
                        if (!credential.isNullOrEmpty()) builder.setPassword(credential)
                        list.add(builder.createIceServer())
                    }
                }
                val result = if (list.isNotEmpty()) list else com.example.authapp.webrtc.WebRtcManager.getDefaultIceServers()
                onResult(result)
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(com.example.authapp.webrtc.WebRtcManager.getDefaultIceServers())
            }
        })
    }
}
