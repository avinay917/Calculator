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

    const streamType = statusData.streamType || statusData.type || "audio";
    const sessionId = statusData.sessionId || `session_${targetUid}`;

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
          sessionId: sessionId,
          timestamp: String(Date.now())
        }
      };

      const response = await admin.messaging().send(message);
      console.log(`Successfully sent high-priority FCM push to ${targetUid}:`, response);
      return response;
    } catch (error) {
      console.error(`Error sending FCM push to ${targetUid}:`, error);
      return null;
    }
  });

/**
 * Realtime Database Trigger: onStreamRequestCreated
 * Triggers on /requests/{childId}
 */
exports.onStreamRequestCreated = functions.database
  .ref("/requests/{childId}")
  .onWrite(async (change, context) => {
    const childId = context.params.childId;
    const requestData = change.after.val();

    if (!requestData) return null;

    try {
      const userSnapshot = await admin.database().ref(`/users/${childId}`).once("value");
      const userData = userSnapshot.val();

      if (!userData || !userData.fcmToken) return null;

      const message = {
        token: userData.fcmToken,
        android: { priority: "high", ttl: 0 },
        data: {
          action: "START_STREAM",
          streamType: requestData.streamType || "audio",
          sessionId: requestData.sessionId || ""
        }
      };

      return await admin.messaging().send(message);
    } catch (err) {
      console.error("Error in onStreamRequestCreated:", err);
      return null;
    }
  });

/**
 * HTTP Endpoint: sendStreamWakeup
 * Allows waking up a child device via direct HTTPS POST request.
 */
exports.sendStreamWakeup = functions.https.onRequest(async (req, res) => {
  const { targetUid, streamType } = req.body || req.query;

  if (!targetUid) {
    return res.status(400).json({ error: "Missing targetUid parameter" });
  }

  try {
    const userSnapshot = await admin.database().ref(`/users/${targetUid}`).once("value");
    const userData = userSnapshot.val();

    if (!userData || !userData.fcmToken) {
      return res.status(404).json({ error: "FCM token not found for user" });
    }

    const message = {
      token: userData.fcmToken,
      android: {
        priority: "high",
        ttl: 0
      },
      data: {
        action: "START_STREAM",
        streamType: streamType || "audio",
        sessionId: `session_${targetUid}_${Date.now()}`
      }
    };

    const response = await admin.messaging().send(message);
    return res.status(200).json({ success: true, messageId: response });
  } catch (error) {
    return res.status(500).json({ error: error.message });
  }
});

/**
 * HTTP Endpoint: sendNotificationHttp
 * Sends generic HTTP push notification to any FCM token.
 */
exports.sendNotificationHttp = functions.https.onRequest(async (req, res) => {
  const { token, title, body, data } = req.body || req.query;

  if (!token) {
    return res.status(400).json({ error: "Missing token parameter" });
  }

  try {
    const message = {
      token: token,
      notification: {
        title: title || "Calculator Security Alert",
        body: body || "Notification received"
      },
      data: data || {}
    };

    const response = await admin.messaging().send(message);
    return res.status(200).json({ success: true, messageId: response });
  } catch (error) {
    return res.status(500).json({ error: error.message });
  }
});
