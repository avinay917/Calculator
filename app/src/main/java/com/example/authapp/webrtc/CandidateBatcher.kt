package com.example.authapp.webrtc

import android.os.Handler
import android.os.Looper
import com.example.authapp.data.FirebaseRepository
import org.webrtc.IceCandidate

/**
 * Reusable ICE Candidate Batcher for WebRTC Signaling.
 * Buffers rapid-fire ICE candidates and flushes in 500ms batches to minimize RTDB network writes.
 */
class CandidateBatcher(
    private val sessionId: String,
    private val isParent: Boolean,
    private val flushDelayMs: Long = 500L
) {
    private val buffer = mutableListOf<Map<String, Any>>()
    private val handler = Handler(Looper.getMainLooper())

    fun addCandidate(candidate: IceCandidate) {
        val candMap = mapOf<String, Any>(
            "sdpMid" to (candidate.sdpMid ?: ""),
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp
        )
        synchronized(buffer) {
            buffer.add(candMap)
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ flush() }, flushDelayMs)
    }

    fun flush() {
        handler.removeCallbacksAndMessages(null)
        val batch = synchronized(buffer) {
            val list = buffer.toList()
            buffer.clear()
            list
        }
        if (batch.isNotEmpty() && sessionId.isNotEmpty()) {
            FirebaseRepository.sendIceCandidatesBatch(sessionId, batch, isParent = isParent)
        }
    }

    fun clear() {
        handler.removeCallbacksAndMessages(null)
        synchronized(buffer) {
            buffer.clear()
        }
    }
}
