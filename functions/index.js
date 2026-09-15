const functions = require("firebase-functions");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
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

/**
 * Realtime Database Trigger: onStreamRequested
 * Triggers on /streams/{targetUid}/status
 * Sends High-Priority FCM Data Push to wake up Child phone lock-screen.
 */
exports.onStreamRequested = functions.database
  .ref("/streams/{targetUid}/status")
  .onWrite(async (change, context) => {
    const targetUid = context.params.targetUid;
    const statusData = change.after.val();

    if (!statusData) {
      console.log(`Stream request removed for target: ${targetUid}`);
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
 * After sending FCM, deletes the request node to prevent re-triggers and save RTDB cost.
 */
exports.onSnapshotRequested = functions.database
  .ref("/streams/{targetUid}/snapshotRequest")
  .onWrite(async (change, context) => {
    const targetUid = context.params.targetUid;
    const data = change.after.val();
    if (!data || data.status !== "REQUESTED") return null;

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

        // ✅ Clean up request node after sending to prevent duplicate triggers & save RTDB cost
        await admin.database().ref(`/streams/${targetUid}/snapshotRequest`).remove();
      }
    } catch (err) {
      console.error(`Error sending snapshot FCM to ${targetUid}:`, err);
    }
    return null;
  });

/**
 * Realtime Database Trigger: onCommandSent
 * Triggers on /commands/{targetUid}/{command}
 * After sending FCM, deletes the command node to prevent re-triggers and save RTDB cost.
 */
exports.onCommandSent = functions.database
  .ref("/commands/{targetUid}/{command}")
  .onWrite(async (change, context) => {
    const targetUid = context.params.targetUid;
    const command = context.params.command;
    const data = change.after.val();
    // Only trigger on new writes, not deletes
    if (!data) return null;
    // Avoid re-triggering on our own delete
    if (!change.before.val() && !data) return null;

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

        // ✅ Clean up command node after sending to prevent duplicate triggers & save RTDB cost
        await admin.database()
          .ref(`/commands/${targetUid}/${command}`)
          .remove();
      }
    } catch (err) {
      console.error(`Error sending command FCM to ${targetUid}:`, err);
    }
    return null;
  });