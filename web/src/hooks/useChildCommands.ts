import { useState, useEffect } from "react";
import { User as FirebaseUser } from "firebase/auth";
import { ref, set, get, update, onValue, off, query, orderByChild, equalTo } from "firebase/database";
import { rtdb } from "@/lib/firebase";

export function useChildCommands(
  user: FirebaseUser | null,
  activeChildId: string,
  setActiveChildId: (id: string) => void,
  snapshotsList: any[],
  recordings: any[]
) {
  const [capturingFacing, setCapturingFacing] = useState<"front" | "back" | null>(null);
  const [storageRetentionDays, setStorageRetentionDays] = useState<number>(7);
  const [isCleaningStorage, setIsCleaningStorage] = useState<boolean>(false);
  const [cleanupStatusMessage, setCleanupStatusMessage] = useState<string | null>(null);
  const [generatedPairingCode, setGeneratedPairingCode] = useState<string | null>(null);
  const [childPairingInput, setChildPairingInput] = useState<string>("");
  const [isVerifyingCode, setIsVerifyingCode] = useState<boolean>(false);
  const [pairingError, setPairingError] = useState<string | null>(null);
  const [pairingSuccess, setPairingSuccess] = useState<string | null>(null);

  // Storage Retention Settings Listener
  useEffect(() => {
    if (!user) return;
    const retRef = ref(rtdb, `parent_settings/${user.uid}/storageRetentionDays`);
    const unsub = onValue(retRef, (snap) => {
      const val = snap.val();
      if (typeof val === "number") setStorageRetentionDays(val);
    });
    return () => unsub();
  }, [user]);

  const sendCommand = async (command: string, value: any = true) => {
    if (!activeChildId) return;
    const requestId = "req_" + Date.now() + "_" + Math.random().toString(36).substring(2, 7);
    await set(ref(rtdb, `commands/${activeChildId}/${command}`), {
      requestId,
      command,
      value,
      timestamp: Date.now(),
    });
    alert(`Command '${command}' (${value ? "ON" : "OFF"}) sent to device.`);
  };

  const handleRequestLocation = async () => {
    if (!activeChildId) return;
    const reqRef = ref(rtdb, `users/${activeChildId}/locationRequest`);
    await set(reqRef, Date.now()).catch(() => {});
    await sendCommand("LOCATION", true).catch(() => {});
  };

  const handleVibratePhone = async (seconds: number = 3) => {
    if (!activeChildId) return;
    await sendCommand("VIBRATE", seconds);
  };

  const handleRequestSnapshot = async (facing: "front" | "back" = "back") => {
    if (!activeChildId) return;
    setCapturingFacing(facing);
    const reqRef = ref(rtdb, `streams/${activeChildId}/snapshotRequest`);
    await set(reqRef, {
      requestId: "snap_" + Date.now(),
      requestedAt: Date.now(),
      cameraFacing: facing,
      status: "REQUESTED",
    });
    setTimeout(() => setCapturingFacing(null), 15000);
  };

  const handleToggleBlockApp = (packageName: string, currentBlocked: boolean) => {
    if (!activeChildId || !packageName) return;
    const sanitized = packageName.replace(/\./g, "_");
    const nextBlocked = !currentBlocked;
    set(ref(rtdb, `parent_controls/${activeChildId}/blockedPackages/${sanitized}`), nextBlocked);
    set(ref(rtdb, `commands/${activeChildId}/${nextBlocked ? "BLOCK_APP" : "UNBLOCK_APP"}`), packageName);
  };

  const handleUninstallApp = (packageName: string) => {
    if (!activeChildId || !packageName) return;
    if (confirm(`Aap child phone se '${packageName}' uninstall karne ki request bhejna chahte hain?`)) {
      set(ref(rtdb, `commands/${activeChildId}/UNINSTALL_APP`), packageName);
      alert(`Uninstall request bheji gayi! Child phone par prompt aayega.`);
    }
  };

  const handleUpdateRetention = async (days: number) => {
    if (!user) return;
    setStorageRetentionDays(days);
    await set(ref(rtdb, `parent_settings/${user.uid}/storageRetentionDays`), days).catch(() => {});
  };

  const handleRunAutoClean = async () => {
    if (!activeChildId || !storageRetentionDays || storageRetentionDays <= 0) return;
    setIsCleaningStorage(true);
    setCleanupStatusMessage(null);
    try {
      const cutoff = Date.now() - storageRetentionDays * 24 * 60 * 60 * 1000;
      let count = 0;
      for (const snap of snapshotsList) {
        if (snap.timestamp && snap.timestamp < cutoff && snap.id) {
          await set(ref(rtdb, `snapshots/${activeChildId}/${snap.id}`), null).catch(() => {});
          count++;
        }
      }
      for (const rec of recordings) {
        const t = rec.startTime || rec.timestamp || 0;
        const rId = rec.id || rec.recordingId;
        if (t && t < cutoff && rId) {
          await set(ref(rtdb, `recordings/${activeChildId}/${rId}`), null).catch(() => {});
          count++;
        }
      }
      setCleanupStatusMessage(
        count > 0
          ? `Successfully removed ${count} media items older than ${storageRetentionDays} day(s).`
          : `No media items older than ${storageRetentionDays} day(s) found. Storage is clean!`
      );
    } catch {
      setCleanupStatusMessage("Cleanup finished.");
    } finally {
      setIsCleaningStorage(false);
      setTimeout(() => setCleanupStatusMessage(null), 5000);
    }
  };

  const handleSaveUninstallPin = async (newPin: string) => {
    if (!activeChildId) {
      alert("Pehle kisi child device ko select karein.");
      return;
    }
    const cleanPin = newPin.trim();
    if (!/^\d{6}$/.test(cleanPin)) {
      alert("PIN theek 6 digits ka hona chahiye (e.g. 123456).");
      return;
    }
    try {
      await set(ref(rtdb, `parent_controls/${activeChildId}/uninstallPin`), cleanPin);
      alert(`Uninstall Protection PIN safalta se badal kar '${cleanPin}' kar diya gaya hai.`);
    } catch (err: any) {
      alert("PIN save karne me error: " + err.message);
    }
  };

  const handleGeneratePairingCode = async () => {
    if (!user) return;
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    setGeneratedPairingCode(code);
    await set(ref(rtdb, `pairing_codes/${code}`), {
      parentUid: user.uid,
      parentEmail: user.email || "",
      createdAt: Date.now(),
      status: "active",
    });
  };

  const handleLinkChildWithCode = async (onSuccessNavigate?: () => void) => {
    if (!user) return;
    const trimmed = childPairingInput.trim();
    if (trimmed.length !== 6 || !/^\d+$/.test(trimmed)) {
      setPairingError("Please enter a valid 6-digit number.");
      return;
    }
    setIsVerifyingCode(true);
    setPairingError(null);
    setPairingSuccess(null);
    try {
      let childUid: string | null = null;
      let childEmail: string = "";

      let foundCodeType: "child" | "parent" | "user" | null = null;

      const childCodeSnap = await get(ref(rtdb, `child_pairing_codes/${trimmed}`));
      if (childCodeSnap.exists()) {
        const data = childCodeSnap.val();
        if (data.childUid) {
          childUid = data.childUid;
          childEmail = data.childEmail || "";
          foundCodeType = "child";
        }
      }

      if (!childUid) {
        const parentCodeSnap = await get(ref(rtdb, `pairing_codes/${trimmed}`));
        if (parentCodeSnap.exists()) {
          const pData = parentCodeSnap.val();
          if (pData.linkedChildUid) {
            childUid = pData.linkedChildUid;
            childEmail = pData.childEmail || "";
            foundCodeType = "parent";
          }
        }
      }

      if (!childUid) {
        const usersSnap = await get(query(ref(rtdb, "users"), orderByChild("childPairingCode"), equalTo(trimmed)));
        if (usersSnap.exists()) {
          const matched = usersSnap.val();
          const firstKey = Object.keys(matched)[0];
          if (firstKey) {
            childUid = firstKey;
            childEmail = matched[firstKey]?.email || "";
          }
        }
      }

      if (!childUid) {
        setPairingError("Invalid pairing code. Please enter the 6-digit code shown on child device.");
        setIsVerifyingCode(false);
        return;
      }

      // 1. Admin account cannot link to any child (Admin manages platform, not individual child)
      const userSnap = await get(ref(rtdb, `users/${user.uid}`));
      if (userSnap.exists() && userSnap.val().role === "admin") {
        setPairingError("Admin account ko kisi child se link hone ki zaroorat nahi hai. Kripya Parent account ka upyog karein.");
        setIsVerifyingCode(false);
        return;
      }

      // 2. Strict 1-Parent to 1-Child enforcement: Parent can only have 1 linked child
      const existingChildrenSnap = await get(
        query(ref(rtdb, "users"), orderByChild("parentId"), equalTo(user.uid))
      );
      if (existingChildrenSnap.exists()) {
        const linkedMap = existingChildrenSnap.val();
        const otherChildren = Object.keys(linkedMap).filter((k) => k !== childUid);
        if (otherChildren.length > 0) {
          setPairingError("Aapke account se pehle se 1 child device linked hai! Ek parent sirf 1 child ko monitor kar sakta hai. Naya child link karne ke liye pehle maujooda child ko Remove/Unpair karein.");
          setIsVerifyingCode(false);
          return;
        }
      }

      // 3. Strict 1-Child to 1-Parent enforcement: Child can only belong to 1 parent
      const targetChildSnap = await get(ref(rtdb, `users/${childUid}`));
      if (targetChildSnap.exists()) {
        const targetData = targetChildSnap.val();
        if (targetData.parentId && targetData.parentId !== user.uid) {
          setPairingError("Yeh child pehle se kisi doosre parent se linked hai! Naye parent se link karne ke liye pehle child ko purane parent se Remove/Unpair karna hoga.");
          setIsVerifyingCode(false);
          return;
        }
      }

      const updates: Record<string, any> = {};
      updates[`users/${childUid}/parentId`] = user.uid;
      if (foundCodeType === "child") {
        updates[`child_pairing_codes/${trimmed}/isUsed`] = true;
        updates[`child_pairing_codes/${trimmed}/linkedParentUid`] = user.uid;
        updates[`child_pairing_codes/${trimmed}/linkedParentEmail`] = user.email || "";
        updates[`child_pairing_codes/${trimmed}/linkedAt`] = Date.now();
      } else if (foundCodeType === "parent") {
        updates[`pairing_codes/${trimmed}/isUsed`] = true;
        updates[`pairing_codes/${trimmed}/linkedParentUid`] = user.uid;
        updates[`pairing_codes/${trimmed}/linkedAt`] = Date.now();
      }
      await update(ref(rtdb), updates);

      setPairingSuccess(`Successfully linked child device (${childEmail || childUid.slice(0, 8)})!`);
      setChildPairingInput("");
      setActiveChildId(childUid);
      if (onSuccessNavigate) {
        setTimeout(onSuccessNavigate, 1500);
      }
    } catch (err: any) {
      setPairingError("Failed to pair: " + (err.message || err));
    } finally {
      setIsVerifyingCode(false);
    }
  };

  const handleUnpairChild = async (childUid: string) => {
    if (
      !window.confirm(
        "Are you sure you want to unpair this child device? Real-time telemetry, remote controls, and streaming access will be disconnected."
      )
    ) {
      return;
    }
    try {
      const updates: Record<string, any> = {};
      updates[`users/${childUid}/parentId`] = null;
      updates[`users/${childUid}/childPairingCode`] = null;
      updates[`streams/${childUid}`] = null;
      await update(ref(rtdb), updates);

      if (activeChildId === childUid) setActiveChildId("");
      alert("Child device unpaired successfully. A new pairing code will be generated on child phone.");
    } catch (err: any) {
      alert("Failed to unpair child: " + (err.message || err));
    }
  };

  return {
    capturingFacing,
    sendCommand,
    handleRequestLocation,
    handleRequestSnapshot,
    handleToggleBlockApp,
    handleUninstallApp,
    handleVibratePhone,
    storageRetentionDays,
    isCleaningStorage,
    cleanupStatusMessage,
    handleUpdateRetention,
    handleRunAutoClean,
    handleSaveUninstallPin,
    generatedPairingCode,
    childPairingInput,
    setChildPairingInput,
    isVerifyingCode,
    pairingError,
    pairingSuccess,
    handleGeneratePairingCode,
    handleLinkChildWithCode,
    handleUnpairChild,
  };
}
