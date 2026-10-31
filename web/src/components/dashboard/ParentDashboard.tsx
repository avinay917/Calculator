"use client";

import React from "react";
import { useApp } from "@/context/AppContext";
import { DashboardHeader } from "@/components/dashboard/DashboardHeader";
import { LiveMonitorSection } from "@/components/dashboard/LiveMonitorSection";
import { PairingTabContent } from "@/components/dashboard/PairingTabContent";
import { SettingsTabContent } from "@/components/dashboard/SettingsTabContent";
import { BottomNavBar } from "@/components/dashboard/BottomNavBar";
import { SnapshotViewerModal } from "@/components/dashboard/SnapshotViewerModal";
import ActivityLogList from "@/components/ActivityLogList";
import ControlsPanel from "@/components/ControlsPanel";
import ScheduleManager from "@/components/ScheduleManager";

export function ParentDashboard() {
  const { activeTab, setActiveTab, selectedSnapshotModal, setSelectedSnapshotModal } = useApp();

  return (
    <div className="flex flex-col min-h-screen bg-[#121216] text-gray-100 max-w-5xl mx-auto pb-20 md:pb-6">
      <DashboardHeader />

      <main className="flex-1 p-4 pb-24">
        {activeTab === "live"      && <LiveMonitorSection />}
        {activeTab === "activity"  && <ActivityLogList />}
        {activeTab === "controls"  && <ControlsPanel />}
        {activeTab === "schedules" && <ScheduleManager />}
        {activeTab === "pairing"   && <PairingTabContent />}
        {activeTab === "settings"  && <SettingsTabContent />}
      </main>

      <BottomNavBar activeTab={activeTab} setActiveTab={setActiveTab} />
      <SnapshotViewerModal snapshot={selectedSnapshotModal} onClose={() => setSelectedSnapshotModal(null)} />
    </div>
  );
}
