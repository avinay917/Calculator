import { useState, useEffect, useCallback, useRef } from "react";
import { User as FirebaseUser } from "firebase/auth";
import { ref, onValue, query, limitToLast, orderByChild, equalTo, DatabaseReference, Query, goOffline, goOnline } from "firebase/database";
import { collection, query as fsQuery, orderBy as fsOrderBy, limit as fsLimit, onSnapshot } from "firebase/firestore";
import { rtdb, firestore } from "@/lib/firebase";
import { ChildUser, ScheduleItem, ActivityFeedItem } from "@/types/dashboard";

// ---------------------------------------------------------------------------
// Generic Firebase RTDB listener factory — eliminates 200+ lines of copy-paste
// Usage: pass a config array, get auto-subscribed state for each path.
// Adding a new feature = adding 1 line to the config array below.
// ---------------------------------------------------------------------------
type Transform = (val: any) => any;

interface ListenerConfig {
  /** State key name (must match a key in ChildDataState) */
  key: string;
  /** RTDB path template — `{id}` is replaced with activeChildId */
  path: string;
  /** limitToLast count (0 = no limit, use raw ref) */
  limit: number;
  /** Transform raw snapshot value to state value */
  transform: Transform;
}

// Common transforms — reusable across all listener configs
const asReversedArray: Transform = (val) => (val ? Object.values(val).reverse() : []);
const asReversedLogsArray = (subKey: string): Transform => (val) => {
  if (!val) return [];
  const items = val[subKey] || Object.values(val);
  return (items as any[]).reverse();
};
const asArray: Transform = (val) => (val ? (Array.isArray(val) ? val : Object.values(val)) : []);
const asSingle: Transform = (val) => val || null;
const asBoolField = (field: string, matchValue: any = true): Transform =>
  (val) => val?.[field] === matchValue;

// ---------------------------------------------------------------------------
// MAIN LISTENER CONFIGS — to add a new data source, add 1 entry here
// ---------------------------------------------------------------------------
const ALWAYS_ON_LISTENERS: ListenerConfig[] = [
  { key: "whatsappLogs",   path: "whatsapp_logs/{id}",   limit: 25, transform: asReversedArray },
  { key: "callLogs",       path: "call_logs/{id}",       limit: 25, transform: asReversedLogsArray("logs") },
  { key: "smsLogs",        path: "sms_logs/{id}",        limit: 25, transform: asReversedLogsArray("logs") },
  { key: "clipboardLogs",  path: "clipboard_logs/{id}",  limit: 25, transform: asReversedArray },
  { key: "recordings",     path: "recordings/{id}",      limit: 25, transform: asReversedArray },
  { key: "activityFeed",   path: "activity_feed/{id}",   limit: 25, transform: (val) => (val ? (Object.values(val) as ActivityFeedItem[]).reverse() : []) },
  { key: "simInfo",        path: "sim_info/{id}",        limit: 0,  transform: asSingle },
  { key: "childLocation",  path: "location/{id}",        limit: 0,  transform: asSingle },
  { key: "schedulesList",  path: "schedules/{id}",       limit: 50, transform: (val) => (val ? Object.values(val) : []) },
  { key: "installedApps",  path: "installed_apps/{id}",  limit: 200, transform: asArray },
  { key: "isTorchActive",  path: "commands/{id}/TORCH",  limit: 0,  transform: asBoolField("value") },
  { key: "isSirenActive",  path: "commands/{id}/SIREN",  limit: 0,  transform: asBoolField("value") },
  { key: "fileExplorer",   path: "file_explorer/{id}",   limit: 50, transform: (val) => {
    if (!val) return [];
    const raw = Array.isArray(val) ? val : (val.files || Object.values(val));
    return Array.isArray(raw) ? [...raw] : [];
  }},
];

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------
export function useChildDataSync(
  user: FirebaseUser | null,
  activeTab: string,
  activityTab: string
) {
  const [children, setChildren] = useState<ChildUser[]>([]);
  const [activeChildId, setActiveChildId] = useState<string>("");

  // All child data lives in one state object — zero useState spam
  const [data, setData] = useState<Record<string, any>>({});
  const setField = useCallback((key: string, value: any) => {
    setData((prev) => (prev[key] === value ? prev : { ...prev, [key]: value }));
  }, []);

  // Snapshot-specific state (complex transform needs local logic)
  const [snapshotsList, setSnapshotsList] = useState<any[]>([]);
  const [latestSnapshot, setLatestSnapshot] = useState<any>(null);
  const [capturingFacing, setCapturingFacing] = useState<"front" | "back" | null>(null);
  const [blockedPackages, setBlockedPackages] = useState<{ [pkg: string]: boolean }>({});
  const [uninstallPin, setUninstallPin] = useState<string>("123456");
  const [isTabVisible, setIsTabVisible] = useState<boolean>(
    typeof document !== "undefined" ? document.visibilityState === "visible" : true
  );

  useEffect(() => {
    if (typeof document === "undefined") return;
    const onVisibilityChange = () => {
      setIsTabVisible(document.visibilityState === "visible");
    };
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => document.removeEventListener("visibilitychange", onVisibilityChange);
  }, []);

  // Cost optimization: cut RTDB WebSocket when tab hidden >2 min; restore on visible.
  // Prevents idle connection charges at scale (1M users x idle WebSocket = high cost).
  const offlineTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (isTabVisible) {
      if (offlineTimerRef.current) {
        clearTimeout(offlineTimerRef.current);
        offlineTimerRef.current = null;
      }
      goOnline(rtdb);
    } else {
      offlineTimerRef.current = setTimeout(() => {
        goOffline(rtdb);
      }, 2 * 60 * 1000); // 2 minutes hidden = disconnect WebSocket
    }
    return () => {
      if (offlineTimerRef.current) clearTimeout(offlineTimerRef.current);
    };
  }, [isTabVisible]);

  // 1. Children list listener
  useEffect(() => {
    if (!user || !isTabVisible) { return; }
    const usersQuery = query(ref(rtdb, "users"), orderByChild("parentId"), equalTo(user.uid));
    const unsub = onValue(usersQuery, (snapshot) => {
      const raw = snapshot.val();
      if (!raw) { setChildren([]); return; }
      const list: ChildUser[] = Object.keys(raw).map((uid) => ({ uid, ...raw[uid] }));
      setChildren(list);
      if (list.length > 0) {
        setActiveChildId((prev) => (prev && list.find((c) => c.uid === prev) ? prev : list[0].uid));
      } else {
        setActiveChildId("");
      }
    });
    return () => unsub();
  }, [user, isTabVisible]);

  // 2. All child data listeners — ONE useEffect drives all configs
  useEffect(() => {
    if (!activeChildId || !isTabVisible) return;
    const unsubs: (() => void)[] = [];

    // Generic config-driven listeners
    for (const cfg of ALWAYS_ON_LISTENERS) {
      const dbPath = cfg.path.replace("{id}", activeChildId);
      const dbRef: DatabaseReference | Query = cfg.limit > 0
        ? query(ref(rtdb, dbPath), limitToLast(cfg.limit))
        : ref(rtdb, dbPath);
      const unsub = onValue(dbRef, (snap) => setField(cfg.key, cfg.transform(snap.val())));
      unsubs.push(unsub);
    }

    // Firestore subcollections (Web History, Package Events, Security Alerts)
    const fsConfigs = [
      { key: "webHistory", coll: "web_history", orderField: "timestamp", limit: 30 },
      { key: "packageEvents", coll: "package_events", orderField: "timestamp", limit: 30 },
      { key: "securityAlerts", coll: "security_alerts", orderField: "timestamp", limit: 30 }
    ];

    for (const fsCfg of fsConfigs) {
      try {
        const q = fsQuery(
          collection(firestore, fsCfg.coll, activeChildId, "items"),
          fsOrderBy(fsCfg.orderField, "desc"),
          fsLimit(fsCfg.limit)
        );
        const unsub = onSnapshot(q, (snapshot) => {
          const items = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
          setField(fsCfg.key, items);
        }, (err) => {
          console.warn(`Firestore listener note on ${fsCfg.coll}:`, err.message);
        });
        unsubs.push(unsub);
      } catch (e) {
        console.warn(`Firestore attach note on ${fsCfg.coll}:`, e);
      }
    }

    // Snapshots (complex multi-field transform)
    const snapQ = query(ref(rtdb, `snapshots/${activeChildId}`), limitToLast(30));
    unsubs.push(onValue(snapQ, (snap) => {
      const val = snap.val();
      if (val && typeof val === "object") {
        let list: any[];
        if (val.downloadUrl || val.storageUrl) {
          list = [val];
        } else {
          list = Object.entries(val)
            .map(([k, v]: [string, any]) => ({ id: k, ...(typeof v === "object" ? v : {}) }))
            .filter((item: any) => item && (item.downloadUrl || item.storageUrl));
          list.sort((a: any, b: any) => (b.timestamp || 0) - (a.timestamp || 0));
        }
        setSnapshotsList(list);
        if (list.length > 0) { setLatestSnapshot(list[0]); setCapturingFacing(null); }
      } else {
        setSnapshotsList([]); setLatestSnapshot(null);
      }
    }));

    // Snapshot request status
    unsubs.push(onValue(ref(rtdb, `streams/${activeChildId}/snapshotRequest`), (snap) => {
      const val = snap.val();
      if (val && (val.status === "COMPLETED" || val.status === "FAILED")) setCapturingFacing(null);
    }));

    // Parent controls (blockedPackages + uninstallPin)
    unsubs.push(onValue(ref(rtdb, `parent_controls/${activeChildId}`), (snap) => {
      const val = snap.val();
      setBlockedPackages(val?.blockedPackages || {});
      const pin = val?.uninstallPin;
      setUninstallPin(pin && String(pin).length === 6 ? String(pin) : "123456");
    }));

    return () => unsubs.forEach((fn) => fn());
  }, [activeChildId, isTabVisible, setField]);

  // 3. Lazy listeners (only when specific tab is active & tab visible — Firebase cost optimization)
  useEffect(() => {
    if (!activeChildId || !isTabVisible || activeTab !== "activity" || activityTab !== "notifications") return;
    const q = query(ref(rtdb, `notifications/${activeChildId}`), limitToLast(50));
    const unsub = onValue(q, (snap) => {
      const val = snap.val();
      setField("notifications", val ? [...(Array.isArray(val) ? val : Object.values(val))].reverse() : []);
    });
    return () => unsub();
  }, [activeChildId, activeTab, activityTab, setField]);

  useEffect(() => {
    if (!activeChildId || !isTabVisible || activeTab !== "activity" || activityTab !== "gallery") return;
    const q = query(ref(rtdb, `media_gallery/${activeChildId}`), limitToLast(50));
    const unsub = onValue(q, (snap) => {
      const val = snap.val();
      setField("mediaGallery", val ? [...(Array.isArray(val) ? val : Object.values(val))] : []);
    });
    return () => unsub();
  }, [activeChildId, activeTab, activityTab, setField]);

  // Return same shape as before — backward compatible, zero breaking changes
  return {
    children,
    activeChildId,
    setActiveChildId,
    whatsappLogs: (data.whatsappLogs ?? []) as any[],
    callLogs: (data.callLogs ?? []) as any[],
    smsLogs: (data.smsLogs ?? []) as any[],
    notifications: (data.notifications ?? []) as any[],
    webHistory: (data.webHistory ?? []) as any[],
    packageEvents: (data.packageEvents ?? []) as any[],
    simInfo: data.simInfo ?? null,
    clipboardLogs: (data.clipboardLogs ?? []) as any[],
    recordings: (data.recordings ?? []) as any[],
    fileExplorer: (data.fileExplorer ?? []) as any[],
    mediaGallery: (data.mediaGallery ?? []) as any[],
    installedApps: (data.installedApps ?? []) as any[],
    blockedPackages,
    uninstallPin,
    setUninstallPin,
    securityAlerts: (data.securityAlerts ?? []) as any[],
    schedulesList: (data.schedulesList ?? []) as ScheduleItem[],
    childLocation: data.childLocation ?? (children.find((c: any) => c.uid === activeChildId) as any)?.location ?? null,
    snapshotsList,
    latestSnapshot,
    capturingFacing,
    setCapturingFacing,
    isTorchActive: (data.isTorchActive ?? false) as boolean,
    isSirenActive: (data.isSirenActive ?? false) as boolean,
    activityFeed: (data.activityFeed ?? []) as ActivityFeedItem[],
  };
}
