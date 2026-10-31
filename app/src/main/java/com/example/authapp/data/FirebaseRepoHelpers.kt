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
        limit: Int = 50,
        crossinline onData: (List<T>) -> Unit
    ): ValueEventListener {
        return listenToChildNodeList(nodePath, childUid, limit = limit, transform = { it }, onData = onData)
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

        // 2. Dual writes to Firestore eliminated: RTDB is 100% free and unlimited.
        // Fast streaming events (notifications, media items, calls, SMS, logs) do not consume Firestore write quotas.
    }

    inline fun <T> pushChildItem(
        path: String,
        childId: String,
        item: T,
        crossinline withId: (T, String) -> T = { itm, _ -> itm }
    ) {
        if (childId.isEmpty()) return
        try {
            val ref = FirebaseDatabase.getInstance().reference.child(path).child(childId).push()
            val toSave = withId(item, ref.key ?: System.currentTimeMillis().toString())
            ref.setValue(toSave)
                .addOnFailureListener { e -> FirebaseCrashlytics.getInstance().recordException(e) }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    inline fun <T> syncChildMap(
        path: String,
        childId: String,
        items: List<T>,
        limit: Int = 50,
        crossinline keySelector: (T) -> String
    ) {
        if (childId.isEmpty() || items.isEmpty()) return
        try {
            val map = items.take(limit).associateBy { keySelector(it) }
            FirebaseDatabase.getInstance().reference.child(path).child(childId).setValue(map)
                .addOnFailureListener { e -> FirebaseCrashlytics.getInstance().recordException(e) }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
