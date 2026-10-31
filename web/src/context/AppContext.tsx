"use client";

import React, { createContext, useContext, useState, useEffect, ReactNode } from "react";
import { onAuthStateChanged, signOut, User as FirebaseUser } from "firebase/auth";
import { ref, onValue } from "firebase/database";
import { doc, setDoc } from "firebase/firestore";
import { auth, rtdb, firestore } from "@/lib/firebase";
import { MainTabType } from "@/types/dashboard";
import { ActivityTabType } from "@/components/ActivityLogList";
import { useChildDataSync } from "@/hooks/useChildDataSync";
import { useWebRtcStream } from "@/hooks/useWebRtcStream";
import { useChildCommands } from "@/hooks/useChildCommands";
import { usePwaInstall } from "@/hooks/usePwaInstall";

// -------------------------------------------------------------------
// Types
// -------------------------------------------------------------------
interface AppContextValue {
  // Auth
  user: FirebaseUser | null;
  loading: boolean;
  userRole: string;
  handleLogout: () => Promise<void>;

  // Tab Navigation
  activeTab: MainTabType;
  setActiveTab: (tab: MainTabType) => void;
  activityTab: ActivityTabType;
  setActivityTab: (tab: ActivityTabType) => void;

  // PWA
  canInstallPwa: boolean;
  handleInstallPwa: () => void;

  // All child data (from useChildDataSync)
  childData: ReturnType<typeof useChildDataSync>;

  // Stream (from useWebRtcStream)
  stream: ReturnType<typeof useWebRtcStream>;

  // Commands (from useChildCommands)
  commands: ReturnType<typeof useChildCommands>;

  // Misc UI state
  selectedDirectoryPath: string;
  setSelectedDirectoryPath: (p: string) => void;
  isRefreshingLocation: boolean;
  setIsRefreshingLocation: (v: boolean) => void;
  selectedSnapshotModal: any;
  setSelectedSnapshotModal: (s: any) => void;

  // Schedules modal state
  showAddScheduleModal: boolean;
  setShowAddScheduleModal: (v: boolean) => void;
  schedHour: number;
  setSchedHour: (v: number) => void;
  schedMinute: number;
  setSchedMinute: (v: number) => void;
  schedDuration: number;
  setSchedDuration: (v: number) => void;
  schedLabel: string;
  setSchedLabel: (v: string) => void;
}

// -------------------------------------------------------------------
// Context
// -------------------------------------------------------------------
const AppContext = createContext<AppContextValue | null>(null);

// -------------------------------------------------------------------
// Provider
// -------------------------------------------------------------------
export function AppProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<FirebaseUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [userRole, setUserRole] = useState<string>("parent");
  const [activeTab, setActiveTab] = useState<MainTabType>("live");
  const [activityTab, setActivityTab] = useState<ActivityTabType>("whatsapp");
  const [selectedDirectoryPath, setSelectedDirectoryPath] = useState("/storage/emulated/0");
  const [isRefreshingLocation, setIsRefreshingLocation] = useState(false);
  const [selectedSnapshotModal, setSelectedSnapshotModal] = useState<any>(null);
  const [showAddScheduleModal, setShowAddScheduleModal] = useState(false);
  const [schedHour, setSchedHour] = useState(14);
  const [schedMinute, setSchedMinute] = useState(0);
  const [schedDuration, setSchedDuration] = useState(5);
  const [schedLabel, setSchedLabel] = useState("Study Time Ambient");

  const { canInstallPwa, handleInstallPwa } = usePwaInstall();

  // Auth listener
  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (currentUser) => {
      if (currentUser && !currentUser.emailVerified) {
        auth.signOut();
        setUser(null);
        setLoading(false);
        return;
      }
      setUser(currentUser);
      setLoading(false);
    });
    return () => unsub();
  }, []);

  // Role listener
  useEffect(() => {
    if (!user) { setUserRole("parent"); return; }
    const roleRef = ref(rtdb, `users/${user.uid}/role`);
    const unsub = onValue(roleRef, (snap) => {
      setUserRole(snap.val() === "admin" ? "admin" : "parent");
    });

    // Auto-heal Parent user profile in Firestore to satisfy Firestore security rules
    const userDocRef = doc(firestore, "users", user.uid);
    setDoc(userDocRef, {
      uid: user.uid,
      email: user.email || "",
      name: user.displayName || user.email?.split("@")[0] || "Parent",
      role: "parent",
      lastSeen: Date.now()
    }, { merge: true }).catch((err) => console.warn("Firestore profile sync error:", err));

    return () => unsub();
  }, [user]);

  const childData = useChildDataSync(user, activeTab, activityTab);
  const stream = useWebRtcStream();
  const commands = useChildCommands(user, childData.activeChildId, childData.setActiveChildId, childData.snapshotsList, childData.recordings);

  const handleLogout = async () => {
    stream.stopStream();
    await signOut(auth);
  };

  return (
    <AppContext.Provider value={{
      user, loading, userRole, handleLogout,
      activeTab, setActiveTab,
      activityTab, setActivityTab,
      canInstallPwa, handleInstallPwa,
      childData, stream, commands,
      selectedDirectoryPath, setSelectedDirectoryPath,
      isRefreshingLocation, setIsRefreshingLocation,
      selectedSnapshotModal, setSelectedSnapshotModal,
      showAddScheduleModal, setShowAddScheduleModal,
      schedHour, setSchedHour,
      schedMinute, setSchedMinute,
      schedDuration, setSchedDuration,
      schedLabel, setSchedLabel,
    }}>
      {children}
    </AppContext.Provider>
  );
}

// -------------------------------------------------------------------
// Hook — use anywhere, no prop-drilling needed
// -------------------------------------------------------------------
export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used inside AppProvider");
  return ctx;
}

