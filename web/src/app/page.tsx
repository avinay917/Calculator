"use client";

import React, { useState, useEffect } from "react";
import { onAuthStateChanged, signOut, User as FirebaseUser } from "firebase/auth";
import { auth } from "@/lib/firebase";
import { MainTabType } from "@/types/dashboard";
import ActivityLogList, { ActivityTabType } from "@/components/ActivityLogList";
import ControlsPanel from "@/components/ControlsPanel";
import ScheduleManager from "@/components/ScheduleManager";
import { AuthScreen } from "@/components/auth/AuthScreen";
import { DashboardHeader } from "@/components/dashboard/DashboardHeader";
import { LiveMonitorSection } from "@/components/dashboard/LiveMonitorSection";
import { PairingTabContent } from "@/components/dashboard/PairingTabContent";
import { SettingsTabContent } from "@/components/dashboard/SettingsTabContent";
import { BottomNavBar } from "@/components/dashboard/BottomNavBar";
import { SnapshotViewerModal } from "@/components/dashboard/SnapshotViewerModal";
import { useChildDataSync } from "@/hooks/useChildDataSync";
import { useWebRtcStream } from "@/hooks/useWebRtcStream";
import { useChildCommands } from "@/hooks/useChildCommands";
import { usePwaInstall } from "@/hooks/usePwaInstall";
import { ref, onValue } from "firebase/database";
import { rtdb } from "@/lib/firebase";
import { AdminDashboard } from "@/components/admin/AdminDashboard";

export default function ParentApp() {
  const [user, setUser] = useState<FirebaseUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [userRole, setUserRole] = useState<string>("parent");
  const [isAdminView, setIsAdminView] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<MainTabType>("live");
  const [activityTab, setActivityTab] = useState<ActivityTabType>("whatsapp");
  const [selectedDirectoryPath, setSelectedDirectoryPath] = useState<string>("/storage/emulated/0");
  const [isRefreshingLocation, setIsRefreshingLocation] = useState(false);
  const [selectedSnapshotModal, setSelectedSnapshotModal] = useState<any>(null);

  // Schedules state
  const [showAddScheduleModal, setShowAddScheduleModal] = useState<boolean>(false);
  const [schedHour, setSchedHour] = useState<number>(14);
  const [schedMinute, setSchedMinute] = useState<number>(0);
  const [schedDuration, setSchedDuration] = useState<number>(5);
  const [schedLabel, setSchedLabel] = useState<string>("Study Time Ambient");

  const { canInstallPwa, handleInstallPwa } = usePwaInstall();

  // 1. Auth State Listener & Admin Role Detection
  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      setLoading(false);
    });
    return () => unsub();
  }, []);

  useEffect(() => {
    if (!user) {
      setUserRole("parent");
      setIsAdminView(false);
      return;
    }

    const roleRef = ref(rtdb, `users/${user.uid}/role`);
    const unsubRole = onValue(roleRef, (snapshot) => {
      const val = snapshot.val();
      if (val === "admin") {
        setUserRole("admin");
        setIsAdminView(true);
      } else {
        // Also check if admin exists in /admins/{uid}
        const adminCheckRef = ref(rtdb, `admins/${user.uid}`);
        onValue(adminCheckRef, (adminSnap) => {
          if (adminSnap.val() === true) {
            setUserRole("admin");
            setIsAdminView(true);
          } else {
            setUserRole("parent");
          }
        }, { onlyOnce: true });
      }
    });

    return () => unsubRole();
  }, [user]);

  // 2. Child Data Synchronization (RTDB + Firestore)
  const childData = useChildDataSync(user, activeTab, activityTab);
  const {
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
    securityAlerts,
    schedulesList,
    childLocation,
    snapshotsList,
    latestSnapshot,
    isTorchActive,
    isSirenActive,
  } = childData;

  // 3. WebRTC Stream Engine
  const stream = useWebRtcStream();

  // 4. Remote Commands, Retention, Snapshots & Pairing
  const commands = useChildCommands(
    user,
    activeChildId,
    setActiveChildId,
    snapshotsList,
    recordings
  );

  const handleLogout = async () => {
    stream.stopStream();
    await signOut(auth);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-[#121216]">
        <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (!user) {
    return (
      <AuthScreen
        canInstallPwa={canInstallPwa}
        onInstallPwa={handleInstallPwa}
      />
    );
  }

  if (isAdminView && userRole === "admin") {
    return (
      <div className="flex flex-col min-h-screen bg-[#121216] text-gray-100 pb-10">
        <header className="sticky top-0 z-30 flex items-center justify-between px-4 py-3 bg-[#181822]/95 backdrop-blur-md border-b border-[#2D2D38]">
          <div className="flex items-center gap-2">
            <span className="p-1.5 bg-amber-500/20 text-amber-400 rounded-lg font-bold text-xs">👑 Admin</span>
            <div>
              <h1 className="text-sm font-bold text-white">Super Admin Control Center</h1>
              <p className="text-[10px] text-gray-400">{user.email}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setIsAdminView(false)}
              className="px-3 py-1.5 bg-blue-600/20 hover:bg-blue-600/30 text-blue-300 rounded-xl text-xs font-semibold border border-blue-500/30 transition"
            >
              Monitor My Child Devices
            </button>
            <button
              onClick={handleLogout}
              className="px-3 py-1.5 bg-[#25252E] hover:bg-[#2F2F3B] text-gray-300 rounded-xl text-xs font-semibold transition"
            >
              Sign Out
            </button>
          </div>
        </header>

        <main className="flex-1 py-4">
          <AdminDashboard onBackToParentView={() => setIsAdminView(false)} />
        </main>
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-screen bg-[#121216] text-gray-100 max-w-5xl mx-auto pb-20 md:pb-6">
      {/* Top Header & Device Ribbon */}
      <DashboardHeader
        user={user}
        canInstallPwa={canInstallPwa}
        onInstallPwa={handleInstallPwa}
        onLogout={handleLogout}
        childrenList={children}
        activeChildId={activeChildId}
        setActiveChildId={setActiveChildId}
        setActiveTab={setActiveTab}
        onGeneratePairingCode={commands.handleGeneratePairingCode}
        onUnpairChild={commands.handleUnpairChild}
        isAdmin={userRole === "admin"}
        onSwitchToAdmin={() => setIsAdminView(true)}
      />

      {/* Main Content Body */}
      <main className="flex-1 p-4 pb-24">
        {/* Tab 1: Live Stream Player */}
        {activeTab === "live" && (
          <LiveMonitorSection
            isStreaming={stream.isStreaming}
            streamType={stream.streamType}
            setStreamType={stream.setStreamType}
            cameraFacing={stream.cameraFacing}
            streamDuration={stream.streamDuration}
            streamStatusText={stream.streamStatusText}
            isMuted={stream.isMuted}
            setIsMuted={stream.setIsMuted}
            audioBoostMultiplier={stream.audioBoostMultiplier}
            setAudioBoostMultiplier={stream.setAudioBoost}
            isClarityFilterEnabled={stream.isClarityFilterEnabled}
            toggleClarityFilter={stream.toggleClarity}
            videoRef={stream.videoRef}
            activeChildId={activeChildId}
            onStartStream={() => stream.startStream(activeChildId)}
            onStopStream={stream.stopStream}
            onToggleCamera={stream.toggleCamera}
            isRecordingStream={stream.isRecordingStream}
            recordingDuration={stream.recordingDuration}
            recordedDownloadUrl={stream.recordedDownloadUrl}
            onStartRecording={stream.startRecordingStream}
            onStopRecording={stream.stopRecordingStream}
          />
        )}

        {/* Tab 2: Activity Logs */}
        {activeTab === "activity" && (
          <ActivityLogList
            activityTab={activityTab}
            setActivityTab={setActivityTab}
            whatsappLogs={whatsappLogs}
            callLogs={callLogs}
            smsLogs={smsLogs}
            notifications={notifications}
            webHistory={webHistory}
            recordings={recordings}
            packageEvents={packageEvents}
            simInfo={simInfo}
            clipboardLogs={clipboardLogs}
            mediaGallery={mediaGallery}
            fileExplorer={fileExplorer}
            selectedDirectoryPath={selectedDirectoryPath}
            setSelectedDirectoryPath={setSelectedDirectoryPath}
            sendCommand={commands.sendCommand}
            installedApps={installedApps}
            blockedPackages={blockedPackages}
            onToggleBlockApp={commands.handleToggleBlockApp}
            onUninstallApp={commands.handleUninstallApp}
            snapshotsList={snapshotsList}
            handleRequestSnapshot={commands.handleRequestSnapshot}
            capturingFacing={commands.capturingFacing}
            securityAlerts={securityAlerts}
          />
        )}

        {/* Tab 3: Emergency Remote Controls */}
        {activeTab === "controls" && (
          <ControlsPanel
            childLocation={childLocation}
            isRefreshingLocation={isRefreshingLocation}
            setIsRefreshingLocation={setIsRefreshingLocation}
            sendCommand={commands.sendCommand}
            isSirenActive={isSirenActive}
            isTorchActive={isTorchActive}
            capturingFacing={commands.capturingFacing}
            handleRequestSnapshot={commands.handleRequestSnapshot}
            snapshotsList={snapshotsList}
            latestSnapshot={latestSnapshot}
            setSelectedSnapshotModal={setSelectedSnapshotModal}
            storageRetentionDays={commands.storageRetentionDays}
            handleUpdateRetention={commands.handleUpdateRetention}
            isCleaningStorage={commands.isCleaningStorage}
            cleanupStatusMessage={commands.cleanupStatusMessage}
            handleRunAutoClean={commands.handleRunAutoClean}
          />
        )}

        {/* Tab 4: Schedules & Auto-Record Routines */}
        {activeTab === "schedules" && (
          <ScheduleManager
            activeTab={activeTab}
            activeChildId={activeChildId}
            schedulesList={schedulesList}
            showAddScheduleModal={showAddScheduleModal}
            setShowAddScheduleModal={setShowAddScheduleModal}
            schedLabel={schedLabel}
            setSchedLabel={setSchedLabel}
            schedHour={schedHour}
            setSchedHour={setSchedHour}
            schedMinute={schedMinute}
            setSchedMinute={setSchedMinute}
            schedDuration={schedDuration}
            setSchedDuration={setSchedDuration}
          />
        )}

        {/* Tab 5: Pairing Code & Connect Child */}
        {activeTab === "pairing" && (
          <PairingTabContent
            childPairingInput={commands.childPairingInput}
            setChildPairingInput={commands.setChildPairingInput}
            pairingError={commands.pairingError}
            pairingSuccess={commands.pairingSuccess}
            isVerifyingCode={commands.isVerifyingCode}
            onLinkChildWithCode={() => commands.handleLinkChildWithCode(() => setActiveTab("live"))}
            generatedPairingCode={commands.generatedPairingCode}
            onGeneratePairingCode={commands.handleGeneratePairingCode}
          />
        )}

        {/* Tab 6: System & App Settings */}
        {activeTab === "settings" && (
          <SettingsTabContent
            user={user}
            activeChildId={activeChildId}
            simInfo={simInfo}
            uninstallPin={uninstallPin}
            onSaveUninstallPin={commands.handleSaveUninstallPin}
            storageRetentionDays={commands.storageRetentionDays}
            onUpdateRetention={commands.handleUpdateRetention}
            isCleaningStorage={commands.isCleaningStorage}
            cleanupStatusMessage={commands.cleanupStatusMessage}
            onRunAutoClean={commands.handleRunAutoClean}
            sendCommand={commands.sendCommand}
            onRequestSnapshot={commands.handleRequestSnapshot}
            capturingFacing={commands.capturingFacing}
            isTorchActive={isTorchActive}
            isSirenActive={isSirenActive}
            audioBoostMultiplier={stream.audioBoostMultiplier}
            setAudioBoostMultiplier={stream.setAudioBoost}
            isClarityFilterEnabled={stream.isClarityFilterEnabled}
            toggleClarityFilter={stream.toggleClarity}
            onLogout={handleLogout}
          />
        )}
      </main>

      {/* Bottom Navigation Bar */}
      <BottomNavBar activeTab={activeTab} setActiveTab={setActiveTab} />

      {/* Photo Viewer Modal */}
      <SnapshotViewerModal
        snapshot={selectedSnapshotModal}
        onClose={() => setSelectedSnapshotModal(null)}
      />
    </div>
  );
}
