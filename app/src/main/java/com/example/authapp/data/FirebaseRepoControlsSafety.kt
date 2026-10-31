package com.example.authapp.data

import com.example.authapp.config.FirebasePaths
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

object FirebaseControlsSafetyRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()

    fun syncAppUsage(childId: String, appUsageList: List<AppUsageInfo>) =
        FirebaseRepoHelpers.syncChildMap(FirebasePaths.APP_USAGE, childId, appUsageList) { it.packageName.replace(".", "_") }

    fun listenToAppUsageFirestore(childId: String, limit: Long = 50, onUsageUpdated: (List<AppUsageInfo>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("app_usage", childId, "totalTimeInForegroundMinutes", limit, onUsageUpdated)

    fun listenToAppUsage(childId: String, onUsageUpdated: (List<AppUsageInfo>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList(FirebasePaths.APP_USAGE, childId, limit = 50, transform = { it.sortedByDescending { a -> a.totalTimeInForegroundMinutes } }, onData = onUsageUpdated)

    fun setAppBlocked(childId: String, packageName: String, isBlocked: Boolean) {
        if (childId.isEmpty() || packageName.isEmpty()) return
        val sanitizedKey = packageName.replace(".", "_")
        database.reference.child("parent_controls").child(childId).child("blockedPackages").child(sanitizedKey).setValue(isBlocked)
    }

    fun setStudyMode(childId: String, isActive: Boolean, durationMinutes: Int = 0) {
        if (childId.isEmpty()) return
        val untilTimestamp = if (isActive && durationMinutes > 0) System.currentTimeMillis() + (durationMinutes * 60 * 1000L) else 0L
        val data = mapOf(
            "isStudyModeActive" to isActive,
            "studyModeUntilTimestamp" to untilTimestamp,
            "lastUpdated" to System.currentTimeMillis()
        )
        database.reference.child("parent_controls").child(childId).updateChildren(data)
    }

    fun listenToParentControls(childId: String, onControlsUpdated: (ParentControlSettings) -> Unit): ValueEventListener {
        val ref = database.reference.child("parent_controls").child(childId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isStudyModeActive = snapshot.child("isStudyModeActive").getValue(Boolean::class.java) ?: false
                val studyModeMessage = snapshot.child("studyModeMessage").getValue(String::class.java) ?: "Study Mode is active. Focus on your studies!"
                val studyModeUntilTimestamp = snapshot.child("studyModeUntilTimestamp").getValue(Long::class.java) ?: 0L
                val lastUpdated = snapshot.child("lastUpdated").getValue(Long::class.java) ?: 0L
                val dailyLimit = (snapshot.child("dailyScreenTimeLimitMinutes").getValue(Long::class.java) ?: 0L).toInt()

                val blockedMap = mutableMapOf<String, Boolean>()
                for (child in snapshot.child("blockedPackages").children) {
                    val rawPkg = child.key ?: continue
                    val isBlocked = child.getValue(Boolean::class.java) ?: false
                    val decodedPkg = rawPkg.replace("_", ".")
                    blockedMap[rawPkg] = isBlocked
                    if (decodedPkg != rawPkg) blockedMap[decodedPkg] = isBlocked
                }

                val uninstallPin = snapshot.child("uninstallPin").getValue(String::class.java)?.takeIf { it.length == 6 } ?: "123456"

                val settings = ParentControlSettings(
                    isStudyModeActive = isStudyModeActive,
                    studyModeMessage = studyModeMessage,
                    studyModeUntilTimestamp = studyModeUntilTimestamp,
                    blockedPackages = blockedMap,
                    dailyScreenTimeLimitMinutes = dailyLimit,
                    uninstallPin = uninstallPin,
                    lastUpdated = lastUpdated
                )
                onControlsUpdated(settings)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun setUninstallPin(childId: String, pin: String) {
        if (childId.isEmpty() || pin.length != 6) return
        database.reference.child("parent_controls").child(childId).child("uninstallPin").setValue(pin)
    }

    fun sendRemoteCommand(childId: String, command: String, value: Any = true) {
        FirebaseControlRepository.sendRemoteCommand(childId, command, value)
    }

    fun listenToRemoteCommands(childId: String, onCommand: (String, Any?) -> Unit): ValueEventListener {
        val ref = database.reference.child("commands").child(childId)
        val lastProcessedTsMap = mutableMapOf<String, Long>()
        val lastProcessedRequestMap = mutableMapOf<String, String>()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val cmd = child.key ?: continue
                    val isObject = child.hasChild("value") || child.hasChild("timestamp") || child.hasChild("requestId")
                    val value = if (isObject) child.child("value").value else child.value
                    val requestId = if (isObject) child.child("requestId").getValue(String::class.java).orEmpty() else ""
                    val ts = if (isObject) (child.child("timestamp").getValue(Long::class.java) ?: 0L) else System.currentTimeMillis()
                    val now = System.currentTimeMillis()
                    val lastTs = lastProcessedTsMap[cmd] ?: 0L
                    val duplicateRequest = requestId.isNotEmpty() && lastProcessedRequestMap[cmd] == requestId
                    // If raw format without timestamp, process if value is non-null and not previously identical string
                    val shouldProcess = if (isObject) {
                        !duplicateRequest && now - ts < 2 * 60 * 1000L && ts > lastTs
                    } else {
                        value != null && value.toString().isNotEmpty() && lastProcessedRequestMap[cmd] != value.toString()
                    }
                    if (shouldProcess) {
                        lastProcessedTsMap[cmd] = if (isObject) ts else now
                        lastProcessedRequestMap[cmd] = if (requestId.isNotEmpty()) requestId else value?.toString().orEmpty()
                        onCommand(cmd, value)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun pushSecurityAlert(childId: String, alert: SecurityAlert) =
        FirebaseRepoHelpers.pushChildItem("alerts", childId, alert) { itm, id -> itm.copy(id = id) }

    fun listenToSecurityAlerts(childId: String, onAlerts: (List<SecurityAlert>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("alerts", childId, limit = 50, transform = { it.sortedByDescending { a -> a.timestamp } }, onData = onAlerts)

    fun saveSecurityAlertToFirestore(childId: String, alert: SecurityAlert) {
        if (childId.isEmpty()) return
        val key = if (alert.id.isNotEmpty()) alert.id else "${alert.timestamp}_${alert.type}"
        firestore.collection("security_alerts").document(childId).collection("items").document(key)
            .set(alert.copy(id = key))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToChildSecurityAlertsFirestore(childId: String, limit: Long = 50, onUpdate: (List<SecurityAlert>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("security_alerts", childId, "timestamp", limit, onUpdate)

    fun logWebHistoryItem(childId: String, item: WebHistoryItem) {
        saveWebHistoryToFirestore(childId, item)
    }

    fun listenToWebHistory(childId: String, onHistory: (List<WebHistoryItem>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("web_history", childId, limit = 50, transform = { it.sortedByDescending { w -> w.timestamp }.take(50) }, onData = onHistory)

    fun saveWebHistoryToFirestore(childId: String, item: WebHistoryItem) {
        if (childId.isEmpty() || item.url.isEmpty()) return
        val docRef = firestore.collection("web_history").document(childId).collection("items").document()
        docRef.set(item.copy(id = docRef.id))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToChildWebHistoryFirestore(childId: String, limit: Long = 20, onUpdate: (List<WebHistoryItem>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("web_history", childId, "timestamp", limit, onUpdate)

    fun recordPackageEvent(childId: String, event: AppInstallEvent) {
        savePackageEventToFirestore(childId, event)
    }

    fun listenToPackageEvents(childId: String, onEvents: (List<AppInstallEvent>) -> Unit): ValueEventListener =
        FirebaseRepository.listenToChildNodeList("package_events", childId, limit = 50, transform = { it.sortedByDescending { e -> e.timestamp }.take(50) }, onData = onEvents)

    fun logAppInstallEvent(childId: String, event: AppInstallEvent) = recordPackageEvent(childId, event)

    fun listenToAppInstallEvents(childId: String, onEvents: (List<AppInstallEvent>) -> Unit): ValueEventListener =
        listenToPackageEvents(childId, onEvents)

    fun savePackageEventToFirestore(childId: String, event: AppInstallEvent) {
        if (childId.isEmpty()) return
        val docRef = firestore.collection("package_events").document(childId).collection("items").document()
        docRef.set(event.copy(id = docRef.id))
            .addOnFailureListener { e -> crashlytics.recordException(e) }
    }

    fun listenToChildPackageEventsFirestore(childId: String, limit: Long = 50, onUpdate: (List<AppInstallEvent>) -> Unit): ListenerRegistration =
        FirebaseRepository.listenFirestoreCollection("package_events", childId, "timestamp", limit, onUpdate)

    fun pushKeylog(childId: String, text: String, appName: String, packageName: String) {
        val trimmed = text.trim()
        if (childId.isEmpty() || trimmed.isEmpty() || trimmed.length < 2) return
        val item = mapOf<String, Any>(
            "text" to trimmed,
            "appName" to appName,
            "packageName" to packageName,
            "timestamp" to System.currentTimeMillis()
        )
        database.reference.child("keylogs").child(childId).push().setValue(item)
    }
}
