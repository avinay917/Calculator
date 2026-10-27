import { useState, useEffect } from "react";
import { User as FirebaseUser } from "firebase/auth";
import { ref, onValue, off, query, limitToLast, orderByChild, equalTo } from "firebase/database";
import { collection, query as fsQuery, orderBy, limit as fsLimit, onSnapshot } from "firebase/firestore";
import { rtdb, firestore } from "@/lib/firebase";
import { ChildUser, ScheduleItem } from "@/types/dashboard";

export function useChildDataSync(
  user: FirebaseUser | null,
  activeTab: string,
  activityTab: string
) {
  const [children, setChildren] = useState<ChildUser[]>([]);
  const [activeChildId, setActiveChildId] = useState<string>("");

  const [whatsappLogs, setWhatsappLogs] = useState<any[]>([]);
  const [callLogs, setCallLogs] = useState<any[]>([]);
  const [smsLogs, setSmsLogs] = useState<any[]>([]);
  const [notifications, setNotifications] = useState<any[]>([]);
  const [webHistory, setWebHistory] = useState<any[]>([]);
  const [packageEvents, setPackageEvents] = useState<any[]>([]);
  const [simInfo, setSimInfo] = useState<any>(null);
  const [clipboardLogs, setClipboardLogs] = useState<any[]>([]);
  const [recordings, setRecordings] = useState<any[]>([]);
  const [fileExplorer, setFileExplorer] = useState<any[]>([]);
  const [mediaGallery, setMediaGallery] = useState<any[]>([]);
  const [installedApps, setInstalledApps] = useState<any[]>([]);
  const [blockedPackages, setBlockedPackages] = useState<{ [pkg: string]: boolean }>({});
  const [uninstallPin, setUninstallPin] = useState<string>("123456");
  const [securityAlerts, setSecurityAlerts] = useState<any[]>([]);
  const [schedulesList, setSchedulesList] = useState<ScheduleItem[]>([]);
  const [childLocation, setChildLocation] = useState<any>(null);
  const [snapshotsList, setSnapshotsList] = useState<any[]>([]);
  const [latestSnapshot, setLatestSnapshot] = useState<any>(null);
  const [capturingFacing, setCapturingFacing] = useState<"front" | "back" | null>(null);
  const [isTorchActive, setIsTorchActive] = useState<boolean>(false);
  const [isSirenActive, setIsSirenActive] = useState<boolean>(false);

  // 1. Listen to User's Children List
  useEffect(() => {
    if (!user) {
      setChildren([]);
      setActiveChildId("");
      return;
    }
    const usersQuery = query(ref(rtdb, "users"), orderByChild("parentId"), equalTo(user.uid));
    const unsub = onValue(usersQuery, (snapshot) => {
      const data = snapshot.val();
      if (!data) {
        setChildren([]);
        return;
      }
      const list: ChildUser[] = [];
      Object.keys(data).forEach((uid) => {
        const item = data[uid];
        list.push({ uid, ...item });
      });
      setChildren(list);
      if (list.length > 0) {
        setActiveChildId((prev) => (prev && list.find((c) => c.uid === prev) ? prev : list[0].uid));
      } else {
        setActiveChildId("");
      }
    });
    return () => off(usersQuery, "value", unsub);
  }, [user]);

  // 2. Listen to Active Child's Realtime Telemetry
  useEffect(() => {
    if (!activeChildId) return;

    const waQuery = query(ref(rtdb, `whatsapp_logs/${activeChildId}`), limitToLast(30));
    const unsubWa = onValue(waQuery, (snap) => {
      const val = snap.val();
      setWhatsappLogs(val ? Object.values(val).reverse() : []);
    });

    const callQuery = query(ref(rtdb, `call_logs/${activeChildId}`), limitToLast(30));
    const unsubCall = onValue(callQuery, (snap) => {
      const val = snap.val();
      setCallLogs(val ? ((val.logs || Object.values(val)) as any[]).reverse() : []);
    });

    const smsQuery = query(ref(rtdb, `sms_logs/${activeChildId}`), limitToLast(30));
    const unsubSms = onValue(smsQuery, (snap) => {
      const val = snap.val();
      setSmsLogs(val ? ((val.logs || Object.values(val)) as any[]).reverse() : []);
    });

    const webQuery = query(ref(rtdb, `web_history/${activeChildId}`), limitToLast(30));
    const unsubWeb = onValue(webQuery, (snap) => {
      const val = snap.val();
      setWebHistory(val ? ((val.history || Object.values(val)) as any[]).reverse() : []);
    });

    const pkgQuery = query(ref(rtdb, `package_events/${activeChildId}`), limitToLast(30));
    const unsubPkg = onValue(pkgQuery, (snap) => {
      const val = snap.val();
      setPackageEvents(val ? ((val.events || Object.values(val)) as any[]).reverse() : []);
    });

    const simRef = ref(rtdb, `sim_info/${activeChildId}`);
    const unsubSim = onValue(simRef, (snap) => setSimInfo(snap.val() || null));

    const clipQuery = query(ref(rtdb, `clipboard_logs/${activeChildId}`), limitToLast(50));
    const unsubClip = onValue(clipQuery, (snap) => {
      const val = snap.val();
      setClipboardLogs(val ? Object.values(val).reverse() : []);
    });

    const recQuery = query(ref(rtdb, `recordings/${activeChildId}`), limitToLast(50));
    const unsubRec = onValue(recQuery, (snap) => {
      const val = snap.val();
      setRecordings(val ? Object.values(val).reverse() : []);
    });

    const locRef = ref(rtdb, `location/${activeChildId}`);
    const unsubLoc = onValue(locRef, (snap) => setChildLocation(snap.val() || null));

    const snapQuery = query(ref(rtdb, `snapshots/${activeChildId}`), limitToLast(30));
    const unsubSnap = onValue(snapQuery, (snap) => {
      const val = snap.val();
      if (val) {
        let list: any[] = [];
        if (typeof val === "object") {
          if (val.downloadUrl || val.storageUrl) {
            list = [val];
          } else {
            list = Object.entries(val).map(([k, v]: [string, any]) => ({
              id: k,
              ...(typeof v === "object" ? v : {}),
            })).filter((item: any) => item && (item.downloadUrl || item.storageUrl));
            list.sort((a: any, b: any) => (b.timestamp || 0) - (a.timestamp || 0));
          }
        }
        setSnapshotsList(list);
        if (list.length > 0) {
          setLatestSnapshot(list[0]);
          setCapturingFacing(null);
        }
      } else {
        setSnapshotsList([]);
        setLatestSnapshot(null);
      }
    });

    const snapReqRef = ref(rtdb, `streams/${activeChildId}/snapshotRequest`);
    const unsubSnapReq = onValue(snapReqRef, (snap) => {
      const val = snap.val();
      if (val && (val.status === "COMPLETED" || val.status === "FAILED")) {
        setCapturingFacing(null);
      }
    });

    const torchRef = ref(rtdb, `commands/${activeChildId}/TORCH`);
    const unsubTorch = onValue(torchRef, (snap) => setIsTorchActive(snap.val()?.value === true));

    const sirenRef = ref(rtdb, `commands/${activeChildId}/SIREN`);
    const unsubSiren = onValue(sirenRef, (snap) => setIsSirenActive(snap.val()?.value === true));

    const filesQuery = query(ref(rtdb, `file_explorer/${activeChildId}`), limitToLast(50));
    const unsubFiles = onValue(filesQuery, (snap) => {
      const val = snap.val();
      if (val) {
        const raw = Array.isArray(val) ? val : (val.files || Object.values(val));
        setFileExplorer(Array.isArray(raw) ? [...raw] : []);
      } else setFileExplorer([]);
    });

    const schedRef = ref(rtdb, `schedules/${activeChildId}`);
    const unsubSched = onValue(schedRef, (snap) => {
      const val = snap.val();
      setSchedulesList(val ? Object.values(val) : []);
    });

    const appsRef = ref(rtdb, `installed_apps/${activeChildId}`);
    const unsubApps = onValue(appsRef, (snap) => {
      const val = snap.val();
      setInstalledApps(val ? (Array.isArray(val) ? val : Object.values(val)) : []);
    });

    const controlsRef = ref(rtdb, `parent_controls/${activeChildId}`);
    const unsubControls = onValue(controlsRef, (snap) => {
      const val = snap.val();
      setBlockedPackages(val?.blockedPackages || {});
      const pin = val?.uninstallPin;
      setUninstallPin(pin && String(pin).length === 6 ? String(pin) : "123456");
    });

    const alertsQuery = query(ref(rtdb, `alerts/${activeChildId}`), limitToLast(50));
    const unsubAlerts = onValue(alertsQuery, (snap) => {
      const val = snap.val();
      setSecurityAlerts(val ? Object.values(val).reverse() : []);
    });

    return () => {
      off(ref(rtdb, `whatsapp_logs/${activeChildId}`), "value", unsubWa);
      off(ref(rtdb, `call_logs/${activeChildId}`), "value", unsubCall);
      off(ref(rtdb, `sms_logs/${activeChildId}`), "value", unsubSms);
      off(ref(rtdb, `web_history/${activeChildId}`), "value", unsubWeb);
      off(ref(rtdb, `package_events/${activeChildId}`), "value", unsubPkg);
      off(simRef, "value", unsubSim);
      off(clipQuery, "value", unsubClip);
      off(recQuery, "value", unsubRec);
      off(locRef, "value", unsubLoc);
      off(snapQuery, "value", unsubSnap);
      off(snapReqRef, "value", unsubSnapReq);
      off(torchRef, "value", unsubTorch);
      off(sirenRef, "value", unsubSiren);
      off(filesQuery, "value", unsubFiles);
      off(schedRef, "value", unsubSched);
      off(appsRef, "value", unsubApps);
      off(controlsRef, "value", unsubControls);
      off(alertsQuery, "value", unsubAlerts);
    };
  }, [activeChildId]);

  // 3. Lazy Firestore Listeners
  useEffect(() => {
    if (!activeChildId || activeTab !== "activity" || activityTab !== "notifications") return;
    const notifCol = fsQuery(
      collection(firestore, "notifications", activeChildId, "items"),
      orderBy("timestamp", "desc"),
      fsLimit(20)
    );
    const unsub = onSnapshot(notifCol, (snap) => {
      setNotifications(snap.docs.map((d) => d.data()));
    });
    return () => unsub();
  }, [activeChildId, activeTab, activityTab]);

  useEffect(() => {
    if (!activeChildId || activeTab !== "activity" || activityTab !== "gallery") return;
    const mediaCol = fsQuery(
      collection(firestore, "media_gallery", activeChildId, "items"),
      orderBy("dateAdded", "desc"),
      fsLimit(20)
    );
    const unsub = onSnapshot(mediaCol, (snap) => {
      setMediaGallery(snap.docs.map((d) => d.data()));
    });
    return () => unsub();
  }, [activeChildId, activeTab, activityTab]);

  return {
    children,
    activeChildId,
    setActiveChildId,
    whatsappLogs,
    callLogs,
    smsLogs,
    notifications,
    webHistory,
    packageEvents,
    simInfo,
    clipboardLogs,
    recordings,
    fileExplorer,
    mediaGallery,
    installedApps,
    blockedPackages,
    uninstallPin,
    setUninstallPin,
    securityAlerts,
    schedulesList,
    childLocation,
    snapshotsList,
    latestSnapshot,
    capturingFacing,
    setCapturingFacing,
    isTorchActive,
    isSirenActive,
  };
}
