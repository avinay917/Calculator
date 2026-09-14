const functions = require("firebase-functions");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
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

    // If stream was stopped by parent, notify child to STOP, never start audio!
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
          await admin.messaging().send(stopMsg);
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
      // 1. Fetch target FCM Token from /users/{targetUid}
      const userSnapshot = await admin.database().ref(`/users/${targetUid}`).once("value");
      const userData = userSnapshot.val();

      if (!userData || !userData.fcmToken) {
        console.error(`No FCM Token found for user: ${targetUid}`);
        return null;
      }

      // 2. Construct High-Priority FCM Data Payload
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

      const response = await admin.messaging().send(message);
      console.log(`Successfully sent high-priority FCM push (${streamType}) to ${targetUid}:`, response);
      return response;
    } catch (error) {
      console.error(`Error sending FCM push to ${targetUid}:`, error);
      return null;
    }
  });

