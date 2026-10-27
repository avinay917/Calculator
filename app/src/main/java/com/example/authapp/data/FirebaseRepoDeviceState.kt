package com.example.authapp.data

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.authapp.utils.onValueChange

object FirebaseDeviceStateRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()

    fun updateChildLocation(childId: String, location: UserLocation) {
        database.reference.child("users").child(childId).child("location").setValue(location)
    }

    fun requestChildLocation(childId: String) {
        database.reference.child("users").child(childId).child("locationRequest").setValue(System.currentTimeMillis())
    }

    fun listenToChildLocation(childId: String, onLocationUpdated: (UserLocation?) -> Unit): ValueEventListener =
        database.reference.child("users").child(childId).child("location").onValueChange(
            onError = { FirebaseCrashlytics.getInstance().log("[Firebase childLocation cancelled] ${it.message}") }
        ) { snapshot ->
            onLocationUpdated(snapshot.getValue(UserLocation::class.java))
        }

    fun listenToLocationRequests(childId: String, onRequestReceived: () -> Unit): ValueEventListener {
        val ref = database.reference.child("users").child(childId).child("locationRequest")
        return ref.onValueChange(
            onError = { FirebaseCrashlytics.getInstance().log("[Firebase locationRequest cancelled] ${it.message}") }
        ) { snapshot ->
            val reqTime = snapshot.getValue(Long::class.java)
            if (reqTime != null && reqTime > 0L) {
                onRequestReceived()
                ref.removeValue()
            }
        }
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

    fun listenToLocationHistory(childId: String, onHistory: (List<UserLocation>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("location_history", childId, limit = 50, transform = { it.sortedByDescending { l -> l.timestamp }.take(50) }, onData = onHistory)

    fun saveGeofence(childId: String, zone: GeofenceZone) {
        if (childId.isEmpty() || zone.id.isEmpty()) return
        database.reference.child("geofences").child(childId).child(zone.id).setValue(zone)
    }

    fun listenToGeofences(childId: String, onZones: (List<GeofenceZone>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("geofences", childId, limit = 50, onData = onZones)

    fun saveSimCardInfo(childId: String, info: SimCardInfo) {
        if (childId.isEmpty()) return
        database.reference.child("sim_info").child(childId).setValue(info)
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToSimCardInfo(childId: String, onInfo: (SimCardInfo?) -> Unit): ValueEventListener =
        database.reference.child("sim_info").child(childId).onValueChange { snapshot ->
            onInfo(snapshot.getValue(SimCardInfo::class.java))
        }

    fun recordNetworkHistory(childId: String, item: NetworkHistoryItem) {
        if (childId.isEmpty()) return
        val ref = database.reference.child("network_history").child(childId).push()
        ref.setValue(item.copy(id = ref.key ?: ""))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToNetworkHistory(childId: String, onHistory: (List<NetworkHistoryItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("network_history", childId, limit = 50, transform = { it.sortedByDescending { n -> n.connectedAt }.take(50) }, onData = onHistory)

    fun saveNetworkHistoryToFirestore(childId: String, item: NetworkHistoryItem) {
        val docRef = firestore.collection("network_history").document(childId).collection("items").document()
        FirebaseRepository.saveChildDocumentDual("network_history", "network_history", childId, docRef.id, item.copy(id = docRef.id))
    }

    fun listenToChildNetworkHistoryFirestore(childId: String, limit: Long = 50, onUpdate: (List<NetworkHistoryItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("network_history", childId, "connectedAt", limit, onUpdate)

    fun recordWifiLog(childId: String, ssid: String, bssid: String = "", rssi: Int = 0, ipAddress: String = "") {
        if (childId.isEmpty() || ssid.isEmpty()) return
        val item = mapOf<String, Any>(
            "ssid" to ssid,
            "bssid" to bssid,
            "rssi" to rssi,
            "ipAddress" to ipAddress,
            "timestamp" to System.currentTimeMillis()
        )
        database.reference.child("wifi_logs").child(childId).push().setValue(item)
    }

    fun listenToDeviceHealth(childUid: String, onHealthUpdate: (DeviceHealth?) -> Unit): ValueEventListener {
        val ref = database.reference.child("device_health").child(childUid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val health = snapshot.getValue(DeviceHealth::class.java)
                    onHealthUpdate(health)
                } catch (e: Exception) {
                    FirebaseRepository.recordNonFatalError("Failed to parse DeviceHealth: ${e.localizedMessage}", e)
                    onHealthUpdate(null)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                FirebaseRepository.recordNonFatalError("Device health listener cancelled: ${error.message}", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToAppUpdate(onUpdateReceived: (AppUpdateInfo?) -> Unit): ValueEventListener =
        database.reference.child("app_update").onValueChange(
            onError = { FirebaseRepository.recordNonFatalError("App update listener cancelled: ${it.message}", it.toException()) }
        ) { snapshot ->
            onUpdateReceived(snapshot.getValue(AppUpdateInfo::class.java))
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
}
