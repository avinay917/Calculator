# 🗺️ Master Code-to-Feature Directory Map (Calculator App)

Yeh document AI Agent aur Developers ke liye **Single Source of Truth** hai. 
Aap jo bhi feature modify ya add karna chahein, AI Agent direct neeche di gayi files par bina kisi delay ya search ke kaam karega.

---

## 📱 1. Core Android Features & File Matrix

| Feature / Kaam | Exact File Path | Key Classes / Functions |
| :--- | :--- | :--- |
| **Silent Photo / Snapshot** | `app/src/main/java/com/example/authapp/camera/SilentSnapshotManager.kt` | `takeSilentSnapshot()`, CameraX background capture |
| **Live Audio/Video (WebRTC)** | `app/src/main/java/com/example/authapp/webrtc/WebRtcManager.kt` | `startLocalStream()`, `createOffer()`, `createAnswer()`, VP8 codec enforcement |
| **Live Stream Window (Parent)** | `app/src/main/java/com/example/authapp/ui/activity/LiveStreamActivity.kt` | Separate window activity (Exynos GPU crash prevention) |
| **Live Stream View & Whisper Boost** | `app/src/main/java/com/example/authapp/ui/components/LiveStreamDialog.kt` | Whisper boost (1x-5x), Clarity filter, SafeSurfaceViewRenderer |
| **Background Service & 1x1 Overlay** | `app/src/main/java/com/example/authapp/service/ChildForegroundService.kt` | `showOverlayWindow()`, background camera capture on Android 10+ |
| **WhatsApp Chat & Status Interceptor**| `app/src/main/java/com/example/authapp/service/ChildNotificationListenerService.kt` | Notification listener for `com.whatsapp`, status & media detection |
| **Keylogger & Screen/App Monitoring** | `app/src/main/java/com/example/authapp/service/ChildAccessibilityService.kt` | Accessibility events, keylogging, study mode blocking |
| **SMS Remote Commands (#CALC#...)** | `app/src/main/java/com/example/authapp/receiver/SmsCommandReceiver.kt` | `#CALC#LOC`, `#CALC#SIREN`, `#CALC#TORCH_ON/OFF` |
| **Scheduled Ambient Recording** | `app/src/main/java/com/example/authapp/ui/components/ChildSchedulesView.kt` | Offline schedule sync, auto-recording worker |
| **Schedule Worker / Offline Task** | `app/src/main/java/com/example/authapp/worker/RecordingSchedulerWorker.kt` | WorkManager periodic & exact alarm background task |
| **GPS Tracking & Offline Cache** | `app/src/main/java/com/example/authapp/utils/OfflineLocationCache.kt`<br>`app/src/main/java/com/example/authapp/utils/LocationUtils.kt` | GPS fetching, SQLite offline location queue |
| **Calls & SMS Log Sync** | `app/src/main/java/com/example/authapp/utils/CallLogUtils.kt`<br>`app/src/main/java/com/example/authapp/utils/SmsUtils.kt` | Contacts, call logs, SMS message extraction |
| **SIM Card & Wi-Fi History** | `app/src/main/java/com/example/authapp/utils/NetworkHistoryMonitor.kt` | SIM swap detection, Wi-Fi SSID tracking |
| **Gallery & File Explorer** | `app/src/main/java/com/example/authapp/utils/MediaGalleryUtils.kt`<br>`app/src/main/java/com/example/authapp/utils/FileManagerUtils.kt` | Device photo/video scan, directory browser |
| **Bluetooth Audio Routing** | `app/src/main/java/com/example/authapp/audio/AudioRouteManager.kt` | BT headset auto-routing for ultra-clear mic |
| **FCM Push & Wakeup Signals** | `app/src/main/java/com/example/authapp/service/MyFirebaseMessagingService.kt` | Wakeup, Start Stream, Take Snapshot remote push |
| **Firebase DB & Firestore Sync** | `app/src/main/java/com/example/authapp/data/FirebaseRepository.kt` | Generic RTDB listeners, Firestore cloud backup |
| **Database Paths & Constants** | `app/src/main/java/com/example/authapp/config/FirebasePaths.kt` | All node string constants (`/users`, `/streams`, etc.) |
| **Data Models** | `app/src/main/java/com/example/authapp/data/ChildActivityModels.kt`<br>`app/src/main/java/com/example/authapp/data/User.kt` | Data classes (`WhatsAppLogItem`, `CallLogItem`, `SmsItem`) |

---

## 💻 2. Web Parent Dashboard & WebRTC

| Web Feature | Exact File Path | Description |
| :--- | :--- | :--- |
| **Main Parent Dashboard** | `web/src/app/page.tsx` | Next.js Dashboard, Stream controls, Live Map, GPS, Pairing |
| **Activity Center (All 11 Subtabs)** | `web/src/components/ActivityLogList.tsx` | WhatsApp, Calls, SMS, Notifs, Web, Apps, SIM, Clipboard, Gallery, Files |
| **WebRTC Video/Audio Receiver** | `web/src/lib/webrtc.ts` | Coturn STUN/TURN, Whisper Audio Boost (Web Audio API GainNode + BiquadFilter) |
| **Silent Audio Receiver** | `web/src/lib/audioStreamer.ts` | Audio-only WebRTC silent receiver |
| **Web Firebase Config** | `web/src/lib/firebase.ts` | Firebase App, Auth, RTDB & Firestore initialization |

---

## ☁️ 3. Cloud Functions & Backend

| Backend Feature | Exact File Path | Description |
| :--- | :--- | :--- |
| **FCM Stream & Snapshot Triggers** | `functions/index.js` | Firebase Cloud Function trigger on `/streams/{childId}/status` |

---

## ⚡ 4. AI Agent Ke Liye Golden Rule
Jab bhi user kisi feature ka naam le:
1. Is **`CODE_MAP.md`** se direct file path uthao.
2. File search / inspect karne me samay mat gavao.
3. Seedha target file par jaakar code modify karo!
