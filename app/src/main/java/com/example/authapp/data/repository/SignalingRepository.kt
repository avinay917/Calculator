package com.example.authapp.data.repository

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object SignalingRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()

    fun sendSdpOffer(sessionId: String, sdp: String) {
        if (sessionId.isEmpty()) return
        database.reference.child("signaling").child(sessionId).child("sdpOffer").setValue(sdp)
    }

    fun sendSdpAnswer(sessionId: String, sdp: String) {
        if (sessionId.isEmpty()) return
        database.reference.child("signaling").child(sessionId).child("sdpAnswer").setValue(sdp)
    }

    fun sendIceCandidatesBatch(sessionId: String, candidates: List<Map<String, Any>>, isParent: Boolean) {
        if (sessionId.isEmpty() || candidates.isEmpty()) return
        val roleKey = if (isParent) "parent" else "child"
        val ref = database.reference.child("signaling").child(sessionId).child("candidates").child(roleKey)
        val batchMap = mutableMapOf<String, Any>()
        candidates.forEach { cand ->
            val key = ref.push().key
            if (!key.isNullOrEmpty()) {
                batchMap[key] = cand
            }
        }
        if (batchMap.isNotEmpty()) {
            ref.updateChildren(batchMap)
        }
    }

    fun cleanupSignalingData(sessionId: String, childId: String) {
        if (sessionId.isEmpty()) return
        try {
            database.reference.child("signaling").child(sessionId).removeValue()
        } catch (_: Exception) {}
    }
}
