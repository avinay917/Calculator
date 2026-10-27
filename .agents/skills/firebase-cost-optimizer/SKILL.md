---
name: firebase-cost-optimizer
description: Enforces strict Firebase cost optimization, free-tier preservation, and zero-waste cloud architecture. Use whenever writing or modifying code involving Firebase RTDB, Firestore, Cloud Functions, Cloud Storage, or Realtime Web/Android listeners to ensure minimal cloud bills and deliver proactive cost-saving suggestions.
---

# Firebase Cost Optimizer Skill

This skill guarantees that every piece of code written for Firebase runs at **near-zero cost**, stays strictly within free-tier limits, and never causes unexpected billing surges.

---

## 1. Realtime Database (RTDB) Egress Defense ($1.00 / GB Egress)

1. **Mandatory Query Limiting**:
   - **RULE**: NEVER attach a listener (`onValue` in Web, `addValueEventListener` in Android) to an entire collection or array without a limit.
   - **IMPLEMENTATION**: Always enforce `.limitToLast(25)` or `.limitToLast(50)` on logs, chats, notifications, and history collections.
   - Prevents downloading megabytes of stale historical JSON every time a single item is added.

2. **Lifecycle Listener Detach**:
   - In Android: Always detach Firebase `ValueEventListener` and `ChildEventListener` in `ViewModel.onCleared()`, dialog dismissal, or screen navigation.
   - In React / Next.js: Always return cleanup functions in `useEffect()` (`off(ref)` or unsubscribe) and pause listeners when `document.visibilityState === 'hidden'`.

3. **Targeted Paths (Shallow Reads)**:
   - Never read `/users/{uid}` when you only need `/users/{uid}/isOnline`. Read the deepest specific path directly.

---

## 2. Cloud Functions Regional Collocation & Egress Elimination

1. **Collocate Regions**:
   - If RTDB is in `asia-southeast1` (Singapore), Cloud Functions MUST be defined with:
     ```javascript
     functions.region("asia-southeast1").database.instance(...)...
     ```
   - Running functions in `us-central1` while the database is in `asia-southeast1` incurs heavy Google Cloud **Cross-Region Network Egress** fees and adds latency.

2. **Trigger Guarding (`hasChanged`)**:
   - Always compare `change.before.val()` and `change.after.val()` for the specific fields that matter.
   - Exit immediately (`return null;`) if irrelevant fields changed to avoid wasted function execution billing.

3. **Resource Sizing**:
   - Set `runWith({ memory: "128MB", timeoutSeconds: 60, minInstances: 0 })` by default. Never run idle instances (`minInstances: 0`).

---

## 3. Cloud Storage Quota Management (Free-Tier Retention)

1. **Strict 7-Day / 14-Day Pruning**:
   - Automated scheduled functions must clean up binary files (`recordings/`, `snapshots/`) and their database metadata so total bucket storage stays under the 5GB free tier.
2. **Client-Side Compression**:
   - Snapshots must be compressed JPEG (quality 70-80%, max resolution 1280x720) before upload.
   - Audio recordings must use AMR-WB or AAC / Opus at optimal bitrates (16-32 kbps for voice), never raw uncompressed PCM.

---

## 4. Proactive Cost-Saving Suggestions Protocol

Whenever writing code or reviewing features with the user:
- Actively check: *"Kya is change se Firebase cost badhegi?"*
- Proactively suggest:
  1. Indexing optimizations (`.indexOn`) to avoid unindexed server scans.
  2. Data pagination and listener cleanup.
  3. Regional collocation adjustments.
  4. Compression and storage retention policies.
