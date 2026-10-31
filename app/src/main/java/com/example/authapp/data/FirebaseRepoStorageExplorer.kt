package com.example.authapp.data

import com.example.authapp.config.FirebasePaths
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

object FirebaseStorageExplorerRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()

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
            database.reference.child("media_items").child(childId).setValue(items)
        }
    }

    fun listenToMediaItemsFirestore(childId: String, limit: Long = 50, onMedia: (List<MediaItemInfo>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("media_items", childId, "timestamp", limit, onMedia)

    fun listenToMediaItems(childId: String, onMedia: (List<MediaItemInfo>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("media_items", childId, limit = 50, transform = { it.sortedByDescending { m -> m.timestamp } }, onData = onMedia)

    fun syncMediaGallery(childUid: String, items: List<MediaGalleryItem>) {
        if (childUid.isEmpty()) return
        try {
            // Write to RTDB (UNLIMITED, $0.00 cost, zero billable Firestore writes)
            val topItems = items.take(30)
            database.reference.child(FirebasePaths.MEDIA_GALLERY).child(childUid).setValue(topItems)
                .addOnFailureListener { e -> crashlytics.recordException(e) }
        } catch (e: Exception) {
            crashlytics.recordException(e)
        }
    }

    fun pushMediaGalleryItem(childUid: String, item: MediaGalleryItem) {
        if (childUid.isEmpty()) return
        try {
            val key = if (item.id.isNotEmpty()) item.id else System.currentTimeMillis().toString()
            database.reference.child(FirebasePaths.MEDIA_GALLERY).child(childUid).child(key).setValue(item)
                .addOnFailureListener { e -> crashlytics.recordException(e) }
        } catch (e: Exception) {
            crashlytics.recordException(e)
        }
    }

    fun listenToMediaGalleryFirestore(childId: String, limit: Long = 20, onItems: (List<MediaGalleryItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("media_gallery", childId, "dateAdded", limit, onItems)

    fun listenToMediaGallery(childId: String, onItems: (List<MediaGalleryItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.MEDIA_GALLERY, childId, limit = 50, onData = onItems)

    fun syncFileExplorer(childUid: String, items: List<FileExplorerItem>) {
        if (childUid.isEmpty()) return
        try {
            // Write to RTDB (UNLIMITED, $0.00 cost, zero billable Firestore writes)
            val topFiles = items.take(35)
            database.reference.child(FirebasePaths.FILE_EXPLORER).child(childUid).setValue(topFiles)
                .addOnFailureListener { e -> crashlytics.recordException(e) }
        } catch (e: Exception) {
            crashlytics.recordException(e)
        }
    }

    fun listenToFileExplorerFirestore(childId: String, limit: Long = 20, onFiles: (List<FileExplorerItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("file_explorer", childId, "name", limit, onFiles)

    fun listenToFileExplorer(childId: String, onFiles: (List<FileExplorerItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("file_explorer", childId, limit = 50, onData = onFiles)

    fun pushYouTubeLog(childUid: String, item: YouTubeLogItem) {
        if (childUid.isEmpty()) return
        val ref = database.reference.child(FirebasePaths.YOUTUBE_HISTORY).child(childUid).push()
        val itemWithId = item.copy(id = ref.key ?: "")
        ref.setValue(itemWithId)
    }

    fun listenToYouTubeLogs(childId: String, onLogs: (List<YouTubeLogItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.YOUTUBE_HISTORY, childId, limit = 50, transform = { it.reversed() }, onData = onLogs)
}
