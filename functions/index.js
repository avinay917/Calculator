const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Triggered whenever a parent writes a stream request to /requests/{childId}
 * Sends a high-priority FCM data message to wake up the child device.
 */
exports.onStreamRequestCreated = functions.database
  .ref("/requests/{childId}")
  .onWrite(async (change, context) => {
    const childId = context.params.childId;
    const requestData = change.after.val();

    if (!requestData) {
      console.log(`Stream request removed for child: ${childId}`);
      return null;
    }

    const streamType = requestData.streamType || "audio";
    const sessionId = requestData.sessionId || "";
    const parentId = requestData.parentId || "";

    // 1. Fetch child FCM Token from /users/{childId}
    const userSnapshot = await admin.database().ref(`/users/${childId}`).once("value");
    const userData = userSnapshot.val();

    if (!userData || !userData.fcmToken) {
      console.error(`No FCM Token found for child user: ${childId}`);
      return null;
    }

    const fcmToken = userData.fcmToken;

    // 2. Construct High-Priority FCM Data Message Payload
    const message = {
      token: fcmToken,
      android: {
        priority: "high",
        ttl: 0 // Immediate delivery for background/lock-screen waking
      },
      data: {
        action: "START_STREAM",
        streamType: streamType,
        sessionId: sessionId,
        parentId: parentId,
        timestamp: String(Date.now())
      }
    };

    try {
      const response = await admin.messaging().send(message);
      console.log(`Successfully sent high-priority FCM push to ${childId}:`, response);
      return response;
    } catch (error) {
      console.error(`Error sending FCM push to child ${childId}:`, error);
      return null;
    }
  });

/**
 * Triggered whenever a user's role is updated in /users/{uid}/role
 */
exports.onUserRoleChanged = functions.database
  .ref("/users/{uid}/role")
  .onUpdate(async (change, context) => {
    const uid = context.params.uid;
    const oldRole = change.before.val();
    const newRole = change.after.val();

    console.log(`User ${uid} role changed from ${oldRole} to ${newRole}`);

    if (newRole === "parent") {
      const userSnapshot = await admin.database().ref(`/users/${uid}`).once("value");
      const userData = userSnapshot.val();

      if (userData && userData.fcmToken) {
        const message = {
          token: userData.fcmToken,
          notification: {
            title: "Role Promoted to Parent",
            body: "Your account has been upgraded to Parent role. You can now monitor child devices."
          },
          data: {
            action: "ROLE_CHANGED",
            newRole: "parent"
          }
        };
        await admin.messaging().send(message);
      }
    }
  });
