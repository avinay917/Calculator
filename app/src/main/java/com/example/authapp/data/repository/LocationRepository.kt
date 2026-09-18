package com.example.authapp.data.repository

import com.example.authapp.data.UserLocation
import com.google.firebase.database.FirebaseDatabase

object LocationRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()

    fun updateChildLocation(childId: String, location: UserLocation) {
        if (childId.isEmpty()) return
        database.reference.child("users").child(childId).child("location").setValue(location)
    }

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
}
