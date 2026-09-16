# Project Context & AI Coding Guidelines (Antigravity)

## 1. Project Overview
- **App Name**: Calculator (Disguised Parental Monitoring App)
- **Package**: `com.example.authapp`
- **Tech Stack**: Kotlin 2.3.20, Android SDK 36 (minSdk 24), Jetpack Compose, WebRTC (`io.getstream:stream-webrtc-android:1.3.1`), Firebase (Auth, RTDB, FCM, Storage, Crashlytics, Perf).

---

## 2. STRICT FAST-EXECUTION RULES (ZERO-INSPECTION MODE)
1. **NO REPETITIVE READ/ANALYZE LOOPS**: Do NOT call `view_file`, `list_dir`, or `grep_search` before editing unless the exact target text is completely unknown. 
2. **SINGLE-TURN EXECUTION**: Combine editing and execution into a single prompt turn. Do not split into "Let me read -> let me think -> let me edit -> let me verify".
3. **TRUST THE SYMBOL MAP**: All classes, methods, RTDB paths, and behaviors are fully documented in this file. Refer directly to Section 3 & 4 instead of exploring the codebase.
4. **DIRECT EDITS**: When modifying existing files, apply changes directly in turn 1.

---

## 3. Core Architecture & Symbol Index

### A. WebRTC Engine
- **File**: `app/src/main/java/com/example/authapp/webrtc/WebRtcManager.kt`
- **Key Methods**:
  - `init(context: Context)`: Initializes `PeerConnectionFactory` and audio/video device modules.
  - `startLocalStream(context: Context, isVideo: Boolean, cameraFacing: String)`: Captures mic / camera.
  - `createOffer(onSdpCreated: (SessionDescription) -> Unit)`: Generates WebRTC SDP offer.
  - `createAnswer(onSdpCreated: (SessionDescription) -> Unit)`: Generates WebRTC SDP answer.
  - `setRemoteDescription(sdp: SessionDescription)`: Applies remote peer SDP.
  - `addIceCandidate(candidate: IceCandidate)`: Adds peer ICE candidate.
  - `restrictVideoToVP8Only(sdp: String): String`: Enforces VP8 codec regex for Exynos/Qualcomm Android compatibility.
  - `dispose()`: Safely releases camera, peer connection, audio tracks.

### B. Child Background Service & Overlay
- **File**: `app/src/main/java/com/example/authapp/service/ChildForegroundService.kt`
- **Key Methods**:
  - `onStartCommand(intent: Intent, flags: Int, startId: Int)`: Receives actions (`ACTION_START_SERVICE`, `ACTION_START_STREAM`, `ACTION_STOP_STREAM`).
  - `showOverlayWindow()`: Creates a 1x1 transparent `SYSTEM_ALERT_WINDOW` overlay to allow background camera capture on Android 10+.
  - `listenToStreamRequests(childUid: String)`: Observes RTDB `/streams/{childUid}/status`.
  - `acquireWakeLocks()`: Keeps CPU awake during active live stream / call recording.

### C. Firebase Signaling & Repository
- **File**: `app/src/main/java/com/example/authapp/data/FirebaseRepository.kt`
- **Key Methods**:
  - `updateStreamStatus(childId: String, status: String, streamType: String, sessionId: String)`
  - `sendSdp(sessionId: String, sdp: String, type: String, role: String)`: Writes to `/signaling/{sessionId}/sdpOffer` or `sdpAnswer`.
  - `sendIceCandidate(sessionId: String, candidate: Map<String, Any>, role: String)`: Pushes to `/signaling/{sessionId}/candidates/{role}`.
  - `listenToSignaling(sessionId: String, onOffer, onAnswer, onCandidate)`: Real-time listener on signaling node.
  - `updatePresence(uid: String, isOnline: Boolean)`: Sets user online status and `.info/connected`.

### D. Parent UI & State Management
- **Dashboard Screen**: `app/src/main/java/com/example/authapp/ui/screens/ParentScreen.kt`
  - Launches `LiveStreamActivity` via Intent when `activeSessionId` is non-null.
- **Dedicated Live Stream Activity Window**: `app/src/main/java/com/example/authapp/ui/activity/LiveStreamActivity.kt`
  - Separate Window Activity for WebRTC video/audio cast. Eliminates Samsung Exynos GPU SurfaceFlinger crashes.
- **Live Stream View/Dialog**: `app/src/main/java/com/example/authapp/ui/components/LiveStreamDialog.kt`
  - Uses `SafeSurfaceViewRenderer` for WebRTC video rendering with `setZOrderMediaOverlay(false)` and `onRendererReleased` callback.
- **Child Activity Dialog**: `app/src/main/java/com/example/authapp/ui/components/ChildActivityDialog.kt`
  - Activity Center for Calls, SMS, WhatsApp (Chats & Status), Web History, Apps, SIM & Network, Notifications.
- **Parent ViewModel**: `app/src/main/java/com/example/authapp/ui/viewmodel/ParentViewModel.kt`
  - `startStream(child, streamType)`, `clearActiveSessionId()`, `stopStream()`
  - Real-time Firebase listeners for `callLogsMap`, `whatsAppLogsMap`, `smsLogsMap`, `notificationsMap`, `webHistoryMap`, `networkHistoryMap`, `simInfoMap`, `packageEventsMap`.

### E. WhatsApp Monitoring & Notification Interceptor
- **Service**: `app/src/main/java/com/example/authapp/service/ChildNotificationListenerService.kt`
  - Captures notifications from `com.whatsapp`, `com.whatsapp.w4b`.
  - Categorizes into `CHAT`, `STATUS`, `AUDIO`, `PHOTO`, `VIDEO` and pushes to `/whatsapp_logs/{childId}` via `FirebaseRepository.pushWhatsAppLog()`.

### F. Bluetooth Audio & Device Controls
- **Audio Routing**: `app/src/main/java/com/example/authapp/audio/AudioRouteManager.kt` (BT headset auto-routing & permissions).
- **SMS Commands**: `app/src/main/java/com/example/authapp/receiver/SmsCommandReceiver.kt` (`#CALC#LOC`, `#CALC#SIREN`, `#CALC#TORCH_ON/OFF`).
- **Offline Location Cache**: `app/src/main/java/com/example/authapp/utils/OfflineLocationCache.kt`.
- **FCM Push & Wakeup**: `app/src/main/java/com/example/authapp/service/MyFirebaseMessagingService.kt` (`START_STREAM`, `STOP_STREAM`, `WAKEUP`, `TAKE_SNAPSHOT`).
- **Cloud Functions**: `functions/index.js` (FCM trigger on `/streams/{targetUid}/status`).

---

## 4. Realtime Database Structure
- `/users/{uid}`: `{ role: "child"|"parent", parentId: string, fcmToken: string, isOnline: boolean, lastSeen: number }`
- `/streams/{childId}/status`: `{ status: "REQUESTED"|"STREAMING"|"STOPPED", streamType: "audio"|"video", sessionId: string }`
- `/signaling/{sessionId}`: `{ sdpOffer: string, sdpAnswer: string, cameraFacing: "front"|"back", candidates: { child: {...}, parent: {...} } }`
- `/recordings/{childId}/{recId}`: `{ downloadUrl: string, timestamp: number, duration: number, type: "call"|"stream" }`
- `/snapshots/{childId}`: `{ status: "REQUESTED"|"COMPLETED", downloadUrl: string, timestamp: number }`
- `/call_logs/{childId}`: `{ logs: [...] }`
- `/whatsapp_logs/{childId}`: `{ logs: [{ senderName, messageText, timestamp, type, isIncoming }] }`
- `/sms_logs/{childId}`: `{ logs: [...] }`
- `/notifications/{childId}`: `{ notifications: [...] }`
- `/web_history/{childId}`: `{ history: [...] }`
- `/network_history/{childId}`: `{ history: [...] }`
- `/sim_info/{childId}`: `{ operatorName, countryIso, simState, lastUpdated }`
- `/app_update`: `{ latestVersion: string, downloadUrl: string }`

---

## 5. Critical Technical Constraints
1. **VP8 Video Only**: Never use H264 or VP9 without VP8 fallback; Samsung Exynos chips fail WebRTC decode unless VP8 is first.
2. **Dedicated Activity for Live Video**: Always launch `LiveStreamActivity` for WebRTC live video/audio cast to avoid SurfaceFlinger GPU crashes.
3. **Overlay Permission**: `SYSTEM_ALERT_WINDOW` is mandatory for background camera on Android 10+.
4. **No Local Gradle Builds**: Test and build strictly via GitHub Actions CI/CD (`gh run list`).

---

## 6. Communication & Explanation Rules
1. **PURI BAATCHEET HINDI MEIN**: Saari explanations, updates aur summary Hindi/Hinglish mein deni hai ("English mein kuch bhi nahi").
2. **STEP-BY-STEP UPDATES**: Har ek action aur code change ke waqt user ko pehle Hindi mein batao:
   - "Ab main yeh kaam kar raha hoon..."
   - "Is change se yeh problem fix ho rahi hai..."
   - Har step clear Hindi mein explain karte hue aage badho.
3. **ZERO TOKEN-WASTE DIRECT EDITS**: Always read this `GEMINI.md` symbol index first. Never read/scan unrelated codebase files repeatedly before editing. Apply target edits directly in Turn 1.

