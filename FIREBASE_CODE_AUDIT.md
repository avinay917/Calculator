# Calculator App — Code, Firebase Cost aur Maintainability Audit

**Audit date:** 2026-10-06  
**Repository:** `avinay917/Calculator`  
**Current main commit:** `892bd159b61052313de4fc1f1bc9c47a115438c7`

## Executive summary

Project ab build, unit tests, lint aur Firebase deployment ke level par healthy hai. Lekin codebase mein kuch architectural aur product-level risks hain:

1. **YouTube logs ka clear path mismatch hai:** Android writer `youtube_logs` mein write karta hai, lekin rules, reader aur cleanup `youtube_history` use karte hain. Isse YouTube activity save/display fail ho sakti hai.
2. **Firebase data architecture mixed hai:** kuch data Realtime Database mein, kuch Firestore mein, aur kuch features dono patterns ke aas-paas hain. Isse debugging, migration aur billing samajhna mushkil hota hai.
3. **Realtime Database listeners kai child nodes par continuously active hain.** Parent screen par har child ke liye multiple listeners lagte hain. Ye bandwidth, battery aur monthly download cost ka sabse bada risk hai.
4. **Location history 30-second / 15-meter updates par based hai.** Active monitoring mein ye bahut saare RTDB writes aur stored history points bana sakta hai.
5. **Cleanup duplicate aur incomplete hai:** client-side `pruneOldLogs()` aur daily Cloud Function dono cleanup karte hain, lekin retention list/path coverage consistent nahi hai.
6. **FirebaseRepository bahut bada monolith hai (~1,827 lines).** Isse changes, tests aur debugging difficult hote hain.
7. **Background monitoring, notification listener, accessibility, silent snapshot/microphone aur call recording OEM/Android restrictions ke karan fragile features hain.** Inhe device matrix par test karna zaroori hai.
8. **WebRTC mein public/free TURN credentials ka production use unreliable aur security risk hai.** Managed/rotating TURN credentials better honge.

## Jo cheezein sahi hain

- `main` branch par Android APK build, unit tests aur lint pass ho rahe hain.
- Firebase rules default-deny fallback rakhti hain.
- Parent-child authorization checks RTDB aur Firestore dono mein maujood hain.
- RTDB ke kai high-volume nodes par `.indexOn` diya gaya hai.
- Recording uploads ke liye offline queue aur WorkManager retry flow bana hua hai.
- FCM invalid tokens ko remove karne ka mechanism hai.
- WebRTC duplicate trigger/duplicate notification protection add ki gayi hai.
- Rules, Firestore indexes, Storage rules aur Functions source repository mein version-controlled hain.
- Release build mein minification aur resource shrinking enabled hai.
- CI mein debug build, unit tests, lint aur Firebase deploy automated hain.

## Confirmed functional issues

### 1. YouTube log path mismatch — high priority

`ChildNotificationListenerService.kt` ke through `ActivityLogRepository.pushYouTubeLog()` data ko:

```text
youtube_logs/{childId}/{pushId}
```

mein write karta hai.

Lekin:

- RTDB rules: `youtube_history`
- `FirebaseRepository.listenToYouTubeLogs()`: `youtube_history`
- `pruneOldLogs()`: `youtube_history`
- ParentViewModel listener cleanup: `youtube_history`

use karte hain.

**Likely result:** write permission deny ho sakti hai, ya data save hone ke baad parent UI mein kabhi nahi dikhega. Isko ek canonical path choose karke fix karna chahiye. Recommended path: `youtube_history`, kyunki reader/rules/cleanup already isi naam par hain.

### 2. Cleanup path consistency incomplete hai

Cloud Function cleanup list mein `whatsapp_logs`, `notifications`, `call_logs`, `sms_logs`, `web_history`, `network_history`, `keylogs`, `wifi_logs`, `social_media_usage`, `snapshots`, `location_history` hain. Ismein at least ye review chahiye:

- `youtube_history` missing hai.
- `alerts` missing hai.
- `recordings` metadata aur Storage files ka lifecycle alag handle hona chahiye.
- Firestore collections Cloud Function ke RTDB cleanup se clean nahi hoti.
- Client `pruneOldLogs()` aur Cloud Function ki retention policy ek single source of truth nahi hai.

### 3. RTDB aur Firestore ka mixed ownership

Historical data ke liye app dono systems use karti hai:

- RTDB: notifications, alerts, location history, recordings metadata, YouTube history, WhatsApp logs, etc.
- Firestore: call logs, SMS logs, web history, network history, security alerts, recordings, location history, etc. ke methods maujood hain.

Agar koi producer RTDB mein aur doosra Firestore mein likhta hai, to UI mein feature “kabhi data dikhata hai, kabhi nahi” jaisa behavior aa sakta hai. Har data type ke liye ek canonical backend choose karna chahiye.

## Firebase cost drivers

### 1. Realtime Database bandwidth

Current code mein parent side par per-child listeners ka pattern hai. `ParentViewModel` health, alerts, notifications, location, call logs, SMS, web history, network history, schedules, commands, YouTube history aur recordings ke listeners maintain karta hai.

RTDB mein billing ka main driver reads se aane wala **downloaded data + protocol/connection overhead** hai. Small but frequent connections bhi overhead create kar sakte hain.

### 2. Location history

`ChildForegroundService` GPS aur Network provider ko approximately **30 seconds / 15 meters** par request karta hai. Agar har update history mein push hota hai, to ek active device din mein hazaaron points bana sakta hai.

Recommended:

- Current location ko alag single document/node mein update karo.
- History sirf meaningful movement par save karo: e.g. 5–10 minutes, 100–250 meters, ya geofence transition.
- Offline batch queue use karo, already present mechanism ko centralize karo.
- History retention 7–14 days rakho unless user explicitly needs 30 days.
- Parent map ko live current location se power karo; history screen ko paginated/limited query se.

### 3. Large root reads

`limitToLast(50)` kuch listeners mein use hua hai, jo achha hai. Lekin root-level listeners aur per-child listeners ko consistently query/limit karna chahiye. Parent dashboard open hote hi har child ke liye multiple datasets load karna expensive ho sakta hai.

Recommended dashboard design:

- Initial screen: only child summary + online status + latest alert count.
- Detail screen open hone par logs ka listener attach karo.
- Screen close hone par listener immediately remove karo.
- Data ko `latest/{childId}` summary node mein denormalize karo.

### 4. Firestore listeners

Firestore mein query listener ke result mein har added/updated document read count hota hai. Offline listener reconnect hone par bhi fresh reads ho sakte hain. Large result sets par `limit()` aur cursor pagination mandatory rakho.

Recommended:

- Har history query par `limit(25/50)`.
- Infinite scroll ke liye cursor use karo, offset nahi.
- Dashboard par Firestore history listeners mat rakho.
- Security rule ke andar repeated `get()` calls ko minimize karo; dependent rule reads billable ho sakte hain.

### 5. Cloud Storage recordings

Audio/video recordings ka cost metadata se zyada important hai:

- Storage GB-month
- Downloads/streaming
- Upload/download operations
- Long retention

Recommended:

- Audio bitrate/duration limit.
- Video resolution/FPS limit.
- Upload se pehle compression.
- Per-child quota, e.g. 500 MB or 1 GB.
- Automatic delete policy: 7/14/30 days.
- UI mein “download” user action par hi file download karo; list screen par media bytes mat download karo.
- RTDB/Firestore mein only metadata + Storage path rakho.

### 6. Cloud Functions

Current triggers valid hain, lekin each RTDB write on `streams`, `snapshotRequest` aur `commands` function invocation trigger kar sakta hai. Duplicate protection hai, phir bhi client ko same state baar-baar rewrite nahi karna chahiye.

Recommended:

- Every request mein unique `requestId` mandatory.
- Same requestId dobara process na karo.
- FCM token lookup ko user profile ke small child node se karo.
- Function logs mein full identifiers/PII na likho.
- Daily cleanup ko one huge root scan banane ke bajay bounded pages/partitioned cleanup mein convert karo.

## Firebase cost-control plan

### Phase 0 — measurement first

Firebase Console mein ye metrics daily/weekly track karo:

- RTDB: downloads, storage, connections, load
- Firestore: reads, writes, deletes, stored data, listener activity
- Storage: stored GB, download GB, operations
- Functions: invocations, execution time, outbound network
- Crashlytics: crashes and non-fatal errors

Agar Blaze plan active hai to budget alert zaroor set karo. Pehle 7 din ka baseline lo, phir changes ke baad compare karo.

### Phase 1 — quick savings

1. `youtube_logs` → `youtube_history` path fix.
2. Location history frequency reduce.
3. Parent dashboard listeners lazy-load karo.
4. Har list query par `limitToLast(25/50)` ya Firestore `limit(25/50)` enforce karo.
5. 7–14 day retention for verbose telemetry.
6. Client-side cleanup ko remove/disable karke server-side single cleanup owner rakho.
7. Duplicate writes ko requestId/idempotency se stop karo.
8. Crashlytics logs mein sensitive payloads aur repeated high-frequency messages kam karo.

### Phase 2 — architecture savings

1. RTDB ko sirf realtime/control data ke liye rakho:
   - online status
   - current location
   - active stream state
   - commands
   - signaling
   - latest summary
2. Historical/event data ko Firestore ya BigQuery-compatible export model mein rakho.
3. High-volume events ko daily/hourly summary mein aggregate karo.
4. Parent dashboard ke liye `child_summaries/{childId}` node/collection banao.
5. Recordings ke liye metadata aur binary Storage lifecycle separate rakho.
6. Cleanup ko per-user/per-day partitions mein implement karo.

### Phase 3 — governance

- Budget alerts: 25%, 50%, 75%, 90%.
- Per-feature kill switch Remote Config se.
- Maximum location points/day.
- Maximum recording minutes/day.
- Maximum storage per child.
- Retention policy visible in parent UI.
- Manual “delete my data” flow.
- Monthly cost report with top 5 nodes/collections.

## Kaunse features sabse zyada fragile hain

### 1. Background monitoring / foreground service

Android OEM battery savers, Android version, notification permission, exact alarm policy aur auto-start restrictions ki wajah se service stop ho sakti hai. `KeepAliveWorker` service ko revive karne ki koshish karta hai, lekin Android background-start restrictions ko guarantee nahi kar sakta.

Test matrix:

- Samsung, Xiaomi, Oppo/Realme, OnePlus, Pixel
- Android 10, 12, 13, 14, 15+
- Battery saver ON/OFF
- App force-stop ke baad
- Reboot ke baad
- Notification permission denied

### 2. Notification listener / Accessibility

Notification access revoke hone, OEM restrictions, app notification format changes ya Android privacy policy ki wajah se WhatsApp/YouTube/app activity capture incomplete ho sakti hai. Ye features UI changes ke saath frequently break hote hain.

### 3. Silent camera/microphone/scheduled recording

Camera/microphone background restrictions, privacy indicators, foreground service type, permission state aur OEM behavior ke karan reliability guarantee nahi hai. Call recording bhi device/OEM/region-dependent hai.

### 4. WebRTC

- TURN public/free credentials production mein reliable nahi hain.
- Network switching, NAT, hotspot aur strict firewall par call fail ho sakti hai.
- SDP codec filtering hardware-specific risk create kar sakta hai.
- WebRTC native crash Kotlin `try/catch` se catch nahi hota.

Recommended: managed TURN with short-lived credentials, health metrics for ICE failure rate, and a tested VP8/H264 device matrix.

### 5. FCM wake-up

High-priority FCM delivery best-effort hai; it is not a guaranteed background execution mechanism. Device idle mode, revoked token, OEM restrictions aur user-disabled notifications ke cases mein stream/snapshot/command delay ya fail ho sakta hai.

## Code ko chhota aur maintainable banane ka plan

### Current maintainability problems

- `FirebaseRepository.kt` approximately 1,827 lines ka monolith hai.
- Firebase singletons multiple repositories mein repeat ho rahe hain.
- `Constants.kt` do locations mein hai: `utils` aur `config`.
- String paths har jagah manually use ho rahe hain.
- Callback APIs aur coroutine/Flow APIs mixed hain.
- RTDB + Firestore + Storage responsibilities ek hi repository mein mixed hain.
- Background service bahut large responsibility handle karta hai.
- Tests mostly unit/static logic level par hain; real Firebase rules + device behavior coverage limited hai.

### Recommended target modules

```text
core/
  auth/
  firebase/
  logging/
  result/

data/
  model/
  local/
  remote/
    FirebaseAuthDataSource.kt
    RealtimeControlDataSource.kt
    RealtimeTelemetryDataSource.kt
    FirestoreHistoryDataSource.kt
    StorageRecordingDataSource.kt
  repository/
    AuthRepository.kt
    ChildRepository.kt
    MonitoringRepository.kt
    HistoryRepository.kt
    RecordingRepository.kt

feature/
  auth/
  parent_dashboard/
  child_monitoring/
  live_stream/
  recordings/
  location/

service/
  ChildForegroundService.kt
  MonitoringCoordinator.kt
  RecordingCoordinator.kt
  LocationCoordinator.kt
```

### Refactor order

1. **No behavior change refactor:** all Firebase paths and constants centralize karo.
2. `FirebaseRepository` ko 5 data sources mein split karo.
3. Callback methods ko `suspend` / `Flow` wrappers mein standardize karo.
4. UI ko repositories ke through access karao; UI se direct Firebase calls remove karo.
5. `Result<T>` based error model banao.
6. Firebase path names ko enum/sealed constants se type-safe banao.
7. Service ke andar location, recording, command aur health coordinators alag karo.
8. Common listener lifecycle helper banao.
9. Fake repositories ke saath unit tests add karo.
10. Har feature ka emulator integration test add karo.

### Example: canonical path constants

```kotlin
object DbPath {
    const val USERS = "users"
    const val YOUTUBE_HISTORY = "youtube_history"
    const val LOCATION_HISTORY = "location_history"
    const val NOTIFICATIONS = "notifications"
    const val RECORDINGS = "recordings"
}
```

Isse `youtube_logs` jaisi typo/mismatch future mein compile-time ya test-time par pakdi ja sakti hai.

## Testing roadmap

### Unit tests

- Path mapping tests
- Retention cutoff tests
- Location sampling/throttling tests
- RequestId/idempotency tests
- FCM payload tests
- Repository success/failure mapping tests
- Permission-state tests

### Firebase Emulator tests

- Parent can read linked child
- Parent cannot read unrelated child
- Child can write own telemetry only
- Parent cannot write child telemetry
- Commands/streams only linked parent can write
- Pairing code expires after 10 minutes
- Used pairing code cannot be reused
- Storage unauthorized read/write denied
- Firestore rule-dependent reads behave as intended

### Device tests

- Login/sign-up/pairing
- FCM wake-up
- Audio/video live stream
- Snapshot
- Scheduled recording
- Offline upload/retry
- Location update and history
- Notification listener
- App force-stop/reboot behavior
- Battery saver/OEM restrictions

## Recommended product decisions

### Keep and improve

- Parent-child pairing
- Current location + geofence alerts
- Live stream with explicit user consent
- Offline recording upload
- Device health dashboard
- Crashlytics + diagnostics
- FCM command wake-up with idempotency

### Simplify or make optional

- Full notification history: store only selected apps/categories.
- Continuous location history: default to low-frequency mode.
- Full keylogging: high privacy/security risk; disable by default or remove.
- Silent snapshots/audio: explicit consent, visible status, and strict retention.
- Multiple duplicate historical data paths: choose one backend per data type.
- Always-on parent listeners: replace with on-demand detail screens.

## Official Firebase cost references

- [Firebase Pricing](https://firebase.google.com/pricing)
- [Realtime Database billing and optimization](https://firebase.google.com/docs/database/usage/billing)
- [Cloud Firestore pricing](https://firebase.google.com/docs/firestore/pricing)

Current official no-cost quotas shown in the pricing documentation include approximately:

- RTDB: 1 GB stored data and 10 GB/month downloads; Blaze usage beyond that is billed.
- Firestore: 1 GiB storage, 50,000 document reads/day, 20,000 writes/day, 20,000 deletes/day, and 10 GiB/month outbound transfer.
- Cloud Functions: up to 2M invocations/month plus free compute quotas on Blaze.
- Default `*.firebasestorage.app` bucket: no-cost storage/download/operation quotas, followed by Google Cloud Storage pricing.

Exact billing depends on the project plan, region, traffic, retention and Google Cloud pricing at the time of usage. Console Usage/Billing data is the source of truth.

## Final priority list

### P0 — fix first

1. Fix `youtube_logs` / `youtube_history` mismatch.
2. Add Firebase Emulator authorization tests.
3. Add budget alerts and usage dashboard.
4. Remove public TURN credentials from production path.
5. Make cleanup ownership single and complete.

### P1 — next release

1. Reduce location history frequency.
2. Lazy-load parent listeners.
3. Add pagination/limits everywhere.
4. Add requestId/idempotency to all commands and snapshot/stream requests.
5. Add recording/storage quotas and retention.

### P2 — maintainability sprint

1. Split `FirebaseRepository.kt`.
2. Remove duplicate constants and raw path strings.
3. Standardize coroutines/Flow and error handling.
4. Split `ChildForegroundService` into coordinators.
5. Add emulator + device regression suite.

**Bottom line:** Project ka foundation workable hai aur CI/deployment healthy hai, lekin ab sabse bada improvement “more features add karna” nahi, balki data ownership, listener lifecycle, retention, cost controls aur service reliability ko simplify karna hai.
