# Project Context & AI Coding Guidelines (Antigravity)

## 1. Project Overview
- **App Name**: Calculator (Disguised Parental Monitoring App)
- **Package**: `com.example.authapp`
- **Tech Stack**: Kotlin 2.3.20, Android SDK 36 (minSdk 24), Jetpack Compose, WebRTC (`io.getstream:stream-webrtc-android:1.3.1`), Firebase (Auth, RTDB, FCM, Storage, Crashlytics, Perf).

---

## 2. Codebase Architecture Map (Direct File Pointers)
Whenever modifying features, target these files directly without scanning or searching:

| Feature / Area | Primary File | Responsibility |
|---|---|---|
| **WebRTC Streaming** | `app/src/main/java/com/example/authapp/webrtc/WebRtcManager.kt` | P2P streaming, audio/video capture, SDP offer/answer, ICE candidates, VP8 video restrictions. |
| **Child Background Service** | `app/src/main/java/com/example/authapp/service/ChildForegroundService.kt` | Background service lifecycle, wakelocks, 1x1 transparent overlay, monitoring RTDB commands. |
| **FCM Wake-Up Push** | `app/src/main/java/com/example/authapp/service/MyFirebaseMessagingService.kt` | Handles high-priority FCM pushes (`START_STREAM`, `STOP_STREAM`, `WAKEUP`). |
| **Cloud Functions Trigger** | `functions/index.js` | Database trigger on `/streams/{targetUid}/status`, sends FCM wake-up push. |
| **Firebase Data & Signaling** | `app/src/main/java/com/example/authapp/data/FirebaseRepository.kt` | RTDB reads/writes, child-parent linking, call logs, recording sessions, presence. |
| **Parent Live Stream UI** | `app/src/main/java/com/example/authapp/ui/screens/ParentScreen.kt` | Parent monitoring dashboard, Live stream dialog launcher. |
| **Live Stream Dialog** | `app/src/main/java/com/example/authapp/ui/components/LiveStreamDialog.kt` | WebRTC video view (`SafeSurfaceViewRenderer`), mic volume, audio controls. |
| **Parent ViewModel** | `app/src/main/java/com/example/authapp/ui/viewmodel/ParentViewModel.kt` | State management for parent dashboard and streams. |
| **Silent Snapshot Capture** | `app/src/main/java/com/example/authapp/camera/SilentSnapshotManager.kt` | Background camera photo capture and upload. |
| **Call Recording & Sync** | `app/src/main/java/com/example/authapp/recorder/CallRecorder.kt` & `receiver/CallReceiver.kt` | Automatic phone call recording and cloud sync. |
| **Notification Listener** | `app/src/main/java/com/example/authapp/service/ChildNotificationListenerService.kt` | Intercepts child notifications (WhatsApp, SMS) & syncs to Firebase. |
| **Calculator Disguise** | `app/src/main/java/com/example/authapp/ui/screens/CalculatorScreen.kt` | Working calculator interface disguising child app. |
| **Security Rules (RTDB)** | `database.rules.json` | Realtime Database security rules with parent-child ownership validation. |
| **Security Rules (Storage)** | `storage.rules` | Firebase Storage rules for recordings and snapshots. |

---

## 3. Realtime Database Structure
- `/users/{uid}`: User profile (`role`: `child` or `parent`, `parentId`, `fcmToken`, `isOnline`, `lastSeen`).
- `/streams/{childId}/status`: Streaming control (`status`: `REQUESTED`, `STOPPED`, `streamType`: `audio` / `video`, `sessionId`).
- `/signaling/{sessionId}`: WebRTC signaling (`sdpOffer`, `sdpAnswer`, `candidates/child`, `candidates/parent`, `cameraFacing`).
- `/recordings/{childId}/{recId}`: Recording metadata for calls and streams.
- `/snapshots/{childId}`: Remote camera snapshot trigger and metadata.
- `/call_logs/{childId}`: Captured call logs.
- `/notifications/{childId}`: Mirrored notifications.
- `/app_update`: Version info and latest APK download URL.

---

## 4. Key Rules for Fast AI Assistance
1. **Never scan the whole project**: Jump directly to the relevant file listed in the Architecture Map.
2. **WebRTC Video**: VP8 is mandatory for Samsung Exynos compatibility; codecs are dynamically discovered in `WebRtcManager.kt`.
3. **Child Permissions**: Background camera requires `SYSTEM_ALERT_WINDOW` overlay and `CAMERA` runtime permissions.
4. **Android 14 Compatibility**: Foreground services must declare appropriate `foregroundServiceType` (`microphone|camera|location`).
