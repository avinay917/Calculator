package com.example.authapp.data

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.example.authapp.utils.onValueChange
import com.example.authapp.utils.toListOf

object FirebaseRepoHelpers {
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
        limit: Long = 20,
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

    fun <T : Any> saveChildDocumentDual(
        collectionName: String,
        rtdbPath: String = collectionName,
        childId: String,
        itemKey: String,
        item: T
    ) {
        if (childId.isEmpty()) return
        // 1. RTDB is the primary real-time store consumed by Parent App & Web Portal
        FirebaseDatabase.getInstance().reference.child(rtdbPath).child(childId).child(itemKey).setValue(item)
            .addOnFailureListener { e -> FirebaseCrashlytics.getInstance().recordException(e) }

        // 2. Only mirror to Firestore if this collection is actually consumed by Firestore listeners (notifications, media_gallery)
        // This eliminates 70%+ redundant billable Firestore write operations for calls, SMS, web history, and network logs
        if (collectionName == "notifications" || collectionName == "media_gallery") {
            FirebaseFirestore.getInstance().collection(collectionName).document(childId)
                .collection("items").document(itemKey)
                .set(item, SetOptions.merge())
                .addOnFailureListener { e -> FirebaseCrashlytics.getInstance().recordException(e) }
        }
    }
}
