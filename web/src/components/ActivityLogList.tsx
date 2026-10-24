"use client";

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
  ChevronRight,
  ChevronLeft,
  RefreshCw,
  ExternalLink,
  Download,
  Copy,
  Check,
  X,
  File,
  HardDrive,
  Info,
  Lock,
  Unlock,
  Trash2,
  Search,
  Eye,
  ShieldAlert,
} from "lucide-react";

export type ActivityTabType =
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
  | "files";

interface ActivityLogListProps {
  activityTab: ActivityTabType;
  setActivityTab: (tab: ActivityTabType) => void;
  whatsappLogs: any[];
  callLogs: any[];
  smsLogs: any[];
  notifications: any[];
  webHistory: any[];
  recordings: any[];
  packageEvents: any[];
  simInfo: any;
  clipboardLogs: any[];
  mediaGallery: any[];
  fileExplorer: any[];
  selectedDirectoryPath: string;
  setSelectedDirectoryPath: (path: string) => void;
  sendCommand: (cmd: string, val: any) => void;
  installedApps?: any[];
  blockedPackages?: Record<string, boolean>;
  onToggleBlockApp?: (pkg: string, currentBlocked: boolean) => void;
  onUninstallApp?: (pkg: string) => void;
}

export default function ActivityLogList({
  activityTab,
  setActivityTab,
  whatsappLogs,
  callLogs,
  smsLogs,
  notifications,
  webHistory,
  recordings,
  packageEvents,
  simInfo,
  clipboardLogs,
  mediaGallery,
  fileExplorer,
  selectedDirectoryPath,
  setSelectedDirectoryPath,
  sendCommand,
  installedApps = [],
  blockedPackages = {},
  onToggleBlockApp,
  onUninstallApp,
}: ActivityLogListProps) {
  const [selectedMedia, setSelectedMedia] = useState<any | null>(null);
  const [selectedFile, setSelectedFile] = useState<any | null>(null);
  const [copiedPath, setCopiedPath] = useState<string | null>(null);
  const [appSearch, setAppSearch] = useState("");
  const [mediaFilter, setMediaFilter] = useState<"all" | "images" | "videos">("all");

  const handleCopyPath = (path: string) => {
    if (!path) return;
    navigator.clipboard.writeText(path);
    setCopiedPath(path);
    setTimeout(() => setCopiedPath(null), 2500);
  };

  const tabs = [
    { id: "whatsapp" as ActivityTabType, label: "WhatsApp", icon: MessageSquare, count: whatsappLogs.length },
    { id: "calls" as ActivityTabType, label: "Calls", icon: Phone, count: callLogs.length },
    { id: "sms" as ActivityTabType, label: "SMS", icon: MessageSquare, count: smsLogs.length },
    { id: "notifications" as ActivityTabType, label: "Notifs", icon: Bell, count: notifications.length },
    { id: "clipboard" as ActivityTabType, label: "Clipboard", icon: FileText, count: clipboardLogs.length },
    { id: "gallery" as ActivityTabType, label: "Gallery", icon: ImageIcon, count: mediaGallery.length },
    { id: "files" as ActivityTabType, label: "Files", icon: Folder, count: fileExplorer.length },
    { id: "web" as ActivityTabType, label: "Web", icon: Globe, count: webHistory.length },
    { id: "recordings" as ActivityTabType, label: "Recordings", icon: Music, count: recordings.length },
    { id: "apps" as ActivityTabType, label: "Apps", icon: Smartphone, count: installedApps.length > 0 ? installedApps.length : packageEvents.length },
    { id: "sim" as ActivityTabType, label: "SIM Info", icon: Radio, count: simInfo ? 1 : 0 },
  ];

  return (
    <div className="flex flex-col gap-3">
      {/* Activity Sub-Navigation */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1">
        {tabs.map((tab) => {
          const IconComp = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => setActivityTab(tab.id)}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-medium shrink-0 transition ${
                activityTab === tab.id
                  ? "bg-blue-600 text-white shadow-md shadow-blue-600/20"
                  : "bg-[#1E1E26] text-gray-400 hover:bg-[#252530]"
              }`}
            >
              <IconComp className="w-3.5 h-3.5" />
              <span>{tab.label}</span>
              <span className="text-[10px] px-1.5 py-0.5 bg-black/30 rounded-full font-bold">
                {tab.count}
              </span>
            </button>
          );
        })}
      </div>

      {/* WhatsApp Sub-Tab */}
      {activityTab === "whatsapp" && (
        <div className="space-y-2 mt-2">
          {whatsappLogs.length === 0 ? (
            <p className="text-center py-10 text-xs text-gray-500">No WhatsApp messages captured yet.</p>
          ) : (
            whatsappLogs.map((item, idx) => (
              <div
                key={idx}
                className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-1 text-xs"
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-green-400">{item.senderName || item.sender || "Unknown"}</span>
                  <span className="text-[10px] text-gray-500">
                    {item.timestamp ? new Date(item.timestamp).toLocaleTimeString() : ""}
                  </span>
                </div>
                <p className="text-gray-300 font-normal">{item.messageText || item.text || item.message}</p>
                {item.type && (
                  <span className="text-[9px] text-gray-500 uppercase tracking-wider font-semibold">
                    Type: {item.type}
                  </span>
                )}
              </div>
            ))
          )}
        </div>
      )}

      {/* Calls Sub-Tab */}
      {activityTab === "calls" && (
        <div className="space-y-2 mt-2">
          {callLogs.length === 0 ? (
            <p className="text-center py-10 text-xs text-gray-500">No call history available.</p>
          ) : (
            callLogs.map((call, idx) => (
              <div
                key={idx}
                className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex items-center justify-between text-xs"
              >
                <div>
                  <p className="font-bold text-white">{call.number || call.phoneNumber || "Unknown Number"}</p>
                  <p className="text-[10px] text-gray-400">
                    {call.callType || "Call"} • Duration: {call.duration || 0}s
                  </p>
                </div>
                <span className="text-[10px] text-gray-500">
                  {call.timestamp ? new Date(call.timestamp).toLocaleDateString() : ""}
                </span>
              </div>
            ))
          )}
        </div>
      )}

      {/* SMS Sub-Tab */}
      {activityTab === "sms" && (
        <div className="space-y-2 mt-2">
          {smsLogs.length === 0 ? (
            <p className="text-center py-10 text-xs text-gray-500">No SMS logs captured.</p>
          ) : (
            smsLogs.map((sms, idx) => (
              <div
                key={idx}
                className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-1 text-xs"
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-blue-400">{sms.address || sms.sender}</span>
                  <span className="text-[10px] text-gray-500">
                    {sms.date ? new Date(sms.date).toLocaleTimeString() : ""}
                  </span>
                </div>
                <p className="text-gray-300">{sms.body || sms.message}</p>
              </div>
            ))
          )}
        </div>
      )}

      {/* Notifications Sub-Tab */}
      {activityTab === "notifications" && (
        <div className="space-y-2 mt-2">
          {notifications.length === 0 ? (
            <p className="text-center py-10 text-xs text-gray-500">No app notifications captured.</p>
          ) : (
            notifications.map((notif, idx) => (
              <div
                key={idx}
                className="p-3 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-1 text-xs"
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-yellow-400">{notif.appName || notif.packageName}</span>
                  <span className="text-[10px] text-gray-500">
                    {notif.timestamp ? new Date(notif.timestamp).toLocaleTimeString() : ""}
                  </span>
                </div>
                <p className="text-white font-medium">{notif.title}</p>
                <p className="text-gray-400 text-[11px]">{notif.text}</p>
              </div>
            ))
          )}
        </div>
      )}

      {/* Web Browsing History */}
      {activityTab === "web" && (
        <div className="space-y-2 mt-2">
          {webHistory.length === 0 ? (
            <p className="text-center py-10 text-xs text-gray-500">No web browsing history captured.</p>
          ) : (
            webHistory.map((item, idx) => (
              <div
                key={idx}
                className="p-3 bg-[#1B1B22] border border-[#272733] rounded-xl flex items-center justify-between text-xs"
              >
                <div className="min-w-0 pr-3">
                  <p className="font-semibold text-white truncate">{item.title || "Visited Web Page"}</p>
                  <a
                    href={item.url?.startsWith("http") ? item.url : `https://${item.url}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-[11px] text-blue-400 hover:underline flex items-center gap-1 mt-0.5 truncate"
                  >
                    <span className="truncate">{item.url}</span>
                    <ExternalLink className="w-3 h-3 shrink-0" />
                  </a>
                </div>
                <span className="text-[10px] text-gray-500 shrink-0">
                  {item.timestamp ? new Date(item.timestamp).toLocaleTimeString() : ""}
                </span>
              </div>
            ))
          )}
        </div>
      )}

      {/* Recordings Sub-Tab */}
      {activityTab === "recordings" && (
        <div className="space-y-2 mt-2">
          {recordings.length === 0 ? (
            <p className="text-center py-10 text-xs text-gray-500">No audio recordings saved.</p>
          ) : (
            recordings.map((rec, idx) => (
              <div
                key={rec.id || idx}
                className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-2 text-xs"
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-white flex items-center gap-1.5">
                    <Music className="w-3.5 h-3.5 text-blue-400" />
                    {rec.type === "call" ? "Call Audio Recording" : "Mic Recording"}
                  </span>
                  <span className="text-[10px] text-gray-500">
                    {rec.timestamp ? new Date(rec.timestamp).toLocaleString() : ""}
                  </span>
                </div>
                {(() => {
                  const audioUrl = rec.downloadUrl || rec.storageUrl;
                  return audioUrl ? (
                    <div className="flex flex-col gap-1.5 mt-1">
                      <audio
                        controls
                        src={audioUrl}
                        className="w-full h-8 rounded bg-[#121217]"
                        preload="metadata"
                      />
                      <div className="flex items-center justify-between text-[10px] text-gray-400">
                        <span>
                          {rec.durationSeconds || rec.duration
                            ? `Duration: ${rec.durationSeconds || rec.duration}s`
                            : ""}
                        </span>
                        <a
                          href={audioUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          download
                          className="text-blue-400 hover:underline flex items-center gap-1 font-medium"
                        >
                          <Download className="w-3 h-3" />
                          <span>Download Recording</span>
                        </a>
                      </div>
                    </div>
                  ) : (
                    <p className="text-[11px] text-gray-400">Audio link preparing or unavailable</p>
                  );
                })()}
              </div>
            ))
          )}
        </div>
      )}

      {/* Installed Apps & Parental Control Sub-Tab */}
      {activityTab === "apps" && (
        <div className="space-y-3 mt-2">
          {/* Top Bar: Search & Rescan */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
            <div>
              <p className="text-xs text-gray-300 font-semibold">Installed Applications & App Blocker</p>
              <p className="text-[11px] text-gray-400">
                Child ke phone ke apps dekhein, block/unblock karein ya uninstall request bhejein
              </p>
            </div>
            <button
              onClick={() => sendCommand("SYNC_MEDIA", true)}
              className="px-2.5 py-1.5 bg-[#252530] hover:bg-[#2e2e3d] text-gray-300 text-[11px] rounded-lg flex items-center gap-1.5 transition self-start sm:self-auto shrink-0"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Rescan Installed Apps</span>
            </button>
          </div>

          {/* Search Bar */}
          <div className="relative">
            <Search className="w-4 h-4 text-gray-500 absolute left-3 top-2.5" />
            <input
              type="text"
              value={appSearch}
              onChange={(e) => setAppSearch(e.target.value)}
              placeholder="Search apps by name or package..."
              className="w-full pl-9 pr-3 py-2 bg-[#14141B] border border-[#272733] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 transition"
            />
          </div>

          {(() => {
            const effectiveApps = installedApps.length > 0 ? installedApps : packageEvents;
            const filteredApps = effectiveApps.filter((app) => {
              const query = appSearch.toLowerCase().trim();
              if (!query) return true;
              return (
                (app.appName || "").toLowerCase().includes(query) ||
                (app.packageName || "").toLowerCase().includes(query)
              );
            });

            if (filteredApps.length === 0) {
              return (
                <div className="text-center py-12 text-xs text-gray-500 bg-[#16161D] rounded-xl border border-[#272733]">
                  <Smartphone className="w-8 h-8 text-gray-600 mx-auto mb-2" />
                  <p>No installed apps found matching your search.</p>
                  <p className="text-[11px] text-gray-600 mt-1">Click "Rescan Installed Apps" to index phone apps.</p>
                </div>
              );
            }

            return (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
                {filteredApps.map((app, idx) => {
                  const sanitized = (app.packageName || "").replace(/\./g, "_");
                  const isBlocked = blockedPackages[app.packageName] === true || blockedPackages[sanitized] === true;
                  const initial = (app.appName || app.packageName || "A").charAt(0).toUpperCase();

                  return (
                    <div
                      key={idx}
                      className={`p-3 rounded-xl border flex items-center justify-between gap-3 text-xs transition ${
                        isBlocked
                          ? "bg-red-950/20 border-red-800/40"
                          : "bg-[#1B1B22] border-[#272733] hover:border-gray-700"
                      }`}
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        {/* App Icon / Logo Avatar */}
                        <div
                          className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold text-sm shrink-0 shadow-md ${
                            isBlocked
                              ? "bg-red-900/40 text-red-300 border border-red-700/50"
                              : "bg-gradient-to-br from-indigo-600 to-blue-700 text-white border border-blue-500/30"
                          }`}
                        >
                          {isBlocked ? <Lock className="w-5 h-5 text-red-400" /> : initial}
                        </div>

                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5">
                            <p className="font-bold text-white truncate text-xs" title={app.appName || app.packageName}>
                              {app.appName || app.packageName}
                            </p>
                            {isBlocked && (
                              <span className="text-[9px] px-1.5 py-0.2 bg-red-950 text-red-300 border border-red-800/50 rounded-full font-bold">
                                BLOCKED
                              </span>
                            )}
                          </div>
                          <p className="text-[10px] text-gray-500 font-mono truncate" title={app.packageName}>
                            {app.packageName}
                          </p>
                        </div>
                      </div>

                      {/* Action Buttons: Block/Unblock & Uninstall */}
                      <div className="flex items-center gap-1.5 shrink-0">
                        <button
                          onClick={() => onToggleBlockApp?.(app.packageName, isBlocked)}
                          className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1 transition ${
                            isBlocked
                              ? "bg-emerald-600/20 text-emerald-400 border border-emerald-500/40 hover:bg-emerald-600/30"
                              : "bg-red-600/20 text-red-400 border border-red-500/40 hover:bg-red-600/30"
                          }`}
                          title={isBlocked ? "Allow this app" : "Block child from using this app"}
                        >
                          {isBlocked ? (
                            <>
                              <Unlock className="w-3.5 h-3.5" />
                              <span className="hidden sm:inline">Unblock</span>
                            </>
                          ) : (
                            <>
                              <Lock className="w-3.5 h-3.5" />
                              <span className="hidden sm:inline">Block</span>
                            </>
                          )}
                        </button>

                        <button
                          onClick={() => onUninstallApp?.(app.packageName)}
                          className="p-1.5 bg-[#252530] hover:bg-red-900/30 text-gray-400 hover:text-red-400 border border-transparent hover:border-red-800/50 rounded-lg transition"
                          title="Send Remote Uninstall Request to phone"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            );
          })()}
        </div>
      )}

      {/* SIM & Network Sub-Tab */}
      {activityTab === "sim" && (
        <div className="p-4 bg-[#1B1B22] border border-[#272733] rounded-xl text-xs space-y-3">
          {!simInfo ? (
            <p className="text-center py-8 text-gray-500">No SIM details received yet.</p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="p-3 bg-[#121217] rounded-lg border border-[#23232c]">
                <p className="text-[10px] text-gray-400">Network Operator</p>
                <p className="text-sm font-bold text-white mt-0.5">{simInfo.operatorName || "N/A"}</p>
              </div>
              <div className="p-3 bg-[#121217] rounded-lg border border-[#23232c]">
                <p className="text-[10px] text-gray-400">Country ISO</p>
                <p className="text-sm font-bold text-white uppercase mt-0.5">{simInfo.countryIso || "N/A"}</p>
              </div>
              <div className="p-3 bg-[#121217] rounded-lg border border-[#23232c]">
                <p className="text-[10px] text-gray-400">SIM State</p>
                <p className="text-sm font-bold text-green-400 mt-0.5">{simInfo.simState || "Active"}</p>
              </div>
              <div className="p-3 bg-[#121217] rounded-lg border border-[#23232c]">
                <p className="text-[10px] text-gray-400">Last Synced</p>
                <p className="text-xs text-gray-300 mt-0.5">
                  {simInfo.lastUpdated ? new Date(simInfo.lastUpdated).toLocaleString() : "Just now"}
                </p>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Clipboard Sub-Tab */}
      {activityTab === "clipboard" && (
        <div className="space-y-2 mt-2">
          {clipboardLogs.length === 0 ? (
            <p className="text-center py-10 text-xs text-gray-500">
              No copied text or links captured yet.
            </p>
          ) : (
            clipboardLogs.map((item, idx) => (
              <div
                key={idx}
                className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-1.5 text-xs"
              >
                <div className="flex items-center justify-between">
                  <span className="text-[10px] px-2 py-0.5 bg-blue-950 text-blue-300 border border-blue-800/40 rounded-full font-bold">
                    Copied Text
                  </span>
                  <span className="text-[10px] text-gray-500">
                    {item.timestamp ? new Date(item.timestamp).toLocaleString() : ""}
                  </span>
                </div>
                <p className="text-gray-200 font-mono bg-[#121217] p-2.5 rounded-lg border border-[#22222b] break-words select-all">
                  {item.text || item.copiedText}
                </p>
              </div>
            ))
          )}
        </div>
      )}

      {/* Media Gallery Sub-Tab */}
      {activityTab === "gallery" && (
        <div className="space-y-3 mt-2">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-gray-300 font-semibold">Photos & Videos on device</p>
              <p className="text-[11px] text-gray-400">Click any photo or video to view path and copy</p>
            </div>
            <button
              onClick={() => sendCommand("SYNC_MEDIA", true)}
              className="px-2.5 py-1 bg-[#252530] hover:bg-[#2e2e3d] text-gray-300 text-[11px] rounded-lg flex items-center gap-1.5 transition"
            >
              <RefreshCw className="w-3 h-3" />
              <span>Sync Gallery</span>
            </button>
          </div>
          {mediaGallery.length === 0 ? (
            <div className="text-center py-12 text-xs text-gray-500 bg-[#16161D] rounded-xl border border-[#272733]">
              <ImageIcon className="w-8 h-8 text-gray-600 mx-auto mb-2" />
              <p>No gallery media synced yet.</p>
              <p className="text-[11px] text-gray-600 mt-1">Click "Sync Gallery" to scan child's photos and videos.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {mediaGallery.map((item, idx) => {
                const isVideo = item.fileType === "VIDEO" || (item.fileName && item.fileName.endsWith(".mp4"));
                const hasImage = Boolean(item.downloadUrl || item.storageUrl);

                return (
                  <div
                    key={idx}
                    onClick={() => setSelectedMedia(item)}
                    className="bg-[#1B1B22] hover:bg-[#22222D] border border-[#272733] hover:border-purple-500/60 rounded-2xl overflow-hidden transition cursor-pointer group shadow-md flex flex-col justify-between"
                  >
                    {/* Visual Media Banner */}
                    {hasImage ? (
                      <div className="relative h-36 w-full bg-black overflow-hidden">
                        <img
                          src={item.downloadUrl || item.storageUrl}
                          alt={item.fileName || "Media"}
                          className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
                        />
                        <span className="absolute top-2 right-2 px-2 py-0.5 bg-black/60 backdrop-blur-md text-[9px] font-bold text-white rounded-md">
                          {isVideo ? "🎬 Video" : "📸 Photo"}
                        </span>
                      </div>
                    ) : (
                      <div className="relative h-28 w-full bg-gradient-to-br from-purple-900/30 via-[#181822] to-[#121218] flex items-center justify-center border-b border-[#272733]/60 group-hover:from-purple-900/40 transition">
                        <div className="p-3 bg-purple-600/20 text-purple-400 rounded-2xl group-hover:scale-110 transition shadow-inner">
                          <ImageIcon className="w-7 h-7" />
                        </div>
                        <span className="absolute top-2 right-2 px-2 py-0.5 bg-purple-950/80 text-purple-300 border border-purple-800/40 text-[9px] font-bold rounded-md">
                          {isVideo ? "🎬 Video" : "📸 Photo"}
                        </span>
                      </div>
                    )}

                    {/* Card Content */}
                    <div className="p-3 space-y-2">
                      <div>
                        <p className="text-white font-bold truncate text-xs group-hover:text-purple-300 transition" title={item.fileName}>
                          {item.fileName || (isVideo ? "Video File" : "Camera Photo")}
                        </p>
                        <div className="flex items-center gap-2 mt-1 text-[10px] text-gray-400">
                          {item.sizeBytes > 0 && (
                            <span className="font-semibold text-gray-300">
                              {(item.sizeBytes / 1024 / 1024).toFixed(2)} MB
                            </span>
                          )}
                          {item.dateAdded > 0 && (
                            <span>• {new Date(item.dateAdded).toLocaleDateString()}</span>
                          )}
                        </div>
                      </div>

                      {/* Action Buttons */}
                      <div className="flex items-center gap-1.5 pt-1 border-t border-[#272733]/50">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedMedia(item);
                          }}
                          className="flex-1 py-1.5 bg-[#252530] hover:bg-purple-600/30 hover:text-purple-300 text-gray-300 rounded-lg text-[11px] font-semibold flex items-center justify-center gap-1 transition"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          <span>View Photo</span>
                        </button>
                        {!hasImage && item.filePath && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              sendCommand("FETCH_MEDIA", item.filePath);
                              alert("Phone se original photo mangwaya ja raha hai! Kuch der mein sync ho jayega.");
                            }}
                            className="px-2.5 py-1.5 bg-purple-600/20 hover:bg-purple-600/40 text-purple-300 border border-purple-500/30 rounded-lg text-[11px] font-semibold flex items-center gap-1 transition"
                            title="Request full file from child phone"
                          >
                            <Download className="w-3.5 h-3.5" />
                            <span>Request File</span>
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* File Explorer Sub-Tab */}
      {activityTab === "files" && (
        <div className="space-y-3 mt-2">
          {/* Header & Controls */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <p className="text-xs text-gray-300 font-semibold">Device Storage Explorer</p>
              <p className="text-[11px] text-gray-400">Click folder to open • Click file for path & details</p>
            </div>
            <button
              onClick={() => sendCommand("SYNC_MEDIA", true)}
              className="px-2.5 py-1 bg-[#252530] hover:bg-[#2e2e3d] text-gray-300 text-[11px] rounded-lg flex items-center gap-1.5 transition self-start sm:self-auto shrink-0"
            >
              <RefreshCw className="w-3 h-3" />
              <span>Rescan Storage</span>
            </button>
          </div>

          {/* Breadcrumb Navigation Bar */}
          <div className="flex items-center gap-1.5 overflow-x-auto text-xs py-2 px-3 bg-[#14141B] rounded-xl border border-[#272733]">
            <button
              onClick={() => setSelectedDirectoryPath("")}
              className={`flex items-center gap-1 font-semibold px-1.5 py-0.5 rounded transition ${
                !selectedDirectoryPath ? "text-amber-400 bg-amber-500/10" : "text-blue-400 hover:text-white"
              }`}
            >
              <HardDrive className="w-3.5 h-3.5" />
              <span>Root Storage</span>
            </button>
            {selectedDirectoryPath &&
              selectedDirectoryPath
                .split("/")
                .filter(Boolean)
                .map((part, i, arr) => {
                  const targetPath = "/" + arr.slice(0, i + 1).join("/");
                  const isCurrent = i === arr.length - 1;
                  return (
                    <React.Fragment key={targetPath}>
                      <span className="text-gray-600">/</span>
                      <button
                        onClick={() => setSelectedDirectoryPath(targetPath)}
                        className={`px-1.5 py-0.5 rounded transition truncate max-w-[150px] ${
                          isCurrent ? "text-amber-400 bg-amber-500/10 font-bold" : "text-gray-300 hover:text-white"
                        }`}
                        title={targetPath}
                      >
                        {part}
                      </button>
                    </React.Fragment>
                  );
                })}
          </div>

          {fileExplorer.length === 0 ? (
            <div className="text-center py-12 text-xs text-gray-500 bg-[#16161D] rounded-xl border border-[#272733]">
              <Folder className="w-8 h-8 text-gray-600 mx-auto mb-2" />
              <p>No files synced yet.</p>
              <p className="text-[11px] text-gray-600 mt-1">Click "Rescan Storage" to index files from child's device.</p>
            </div>
          ) : (
            <div className="space-y-1.5">
              {(() => {
                const normSelected = (selectedDirectoryPath || "").trim().replace(/[\\/]+$/, "");

                // Filter logic
                const displayItems = normSelected
                  ? fileExplorer.filter((item) => {
                      const itemParent = (item.parentPath || "").trim().replace(/[\\/]+$/, "");
                      const itemPath = (item.path || "").trim().replace(/[\\/]+$/, "");
                      if (itemParent && itemParent === normSelected) return true;
                      if (itemPath && itemPath.startsWith(normSelected + "/")) {
                        const rel = itemPath.slice(normSelected.length + 1);
                        return !rel.includes("/");
                      }
                      return false;
                    })
                  : fileExplorer.filter((item) => {
                      const itemParent = (item.parentPath || "").trim().replace(/[\\/]+$/, "");
                      if (!itemParent) return true;
                      // Show if its parent folder is not in the list (making it top-level)
                      return !fileExplorer.some((f) => f.path && f.path.trim().replace(/[\\/]+$/, "") === itemParent);
                    });

                const sortedItems = [...displayItems].sort((a, b) => {
                  if (a.isDirectory && !b.isDirectory) return -1;
                  if (!a.isDirectory && b.isDirectory) return 1;
                  return (a.name || "").localeCompare(b.name || "");
                });

                if (sortedItems.length === 0 && selectedDirectoryPath) {
                  return (
                    <div className="p-8 text-center bg-[#16161D] rounded-xl border border-[#272733] space-y-3">
                      <Folder className="w-8 h-8 text-amber-500/50 mx-auto" />
                      <div>
                        <p className="text-white font-medium text-xs">Folder is currently empty or contains no scanned sub-files.</p>
                        <p className="text-[11px] text-gray-500 mt-1 font-mono break-all">{selectedDirectoryPath}</p>
                      </div>
                      <div className="flex items-center justify-center gap-2 pt-2">
                        <button
                          onClick={() => handleCopyPath(selectedDirectoryPath)}
                          className="px-3 py-1.5 bg-[#252530] hover:bg-[#2e2e3d] text-gray-200 text-xs rounded-lg flex items-center gap-1.5 transition"
                        >
                          {copiedPath === selectedDirectoryPath ? (
                            <>
                              <Check className="w-3.5 h-3.5 text-emerald-400" />
                              <span className="text-emerald-400">Path Copied!</span>
                            </>
                          ) : (
                            <>
                              <Copy className="w-3.5 h-3.5" />
                              <span>Copy Folder Path</span>
                            </>
                          )}
                        </button>
                        <button
                          onClick={() => setSelectedDirectoryPath("")}
                          className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-xs rounded-lg transition"
                        >
                          Go to Root
                        </button>
                      </div>
                    </div>
                  );
                }

                return sortedItems.map((item, idx) => (
                  <div
                    key={idx}
                    onClick={() => {
                      if (item.isDirectory) {
                        setSelectedDirectoryPath(item.path);
                      } else {
                        setSelectedFile(item);
                      }
                    }}
                    className={`p-2.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex items-center justify-between gap-3 text-xs transition cursor-pointer ${
                      item.isDirectory
                        ? "hover:border-amber-500/60 hover:bg-[#23232C]"
                        : "hover:border-blue-500/60 hover:bg-[#21212B]"
                    }`}
                    title={item.isDirectory ? "Click to open folder" : "Click to view file details & copy path"}
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div
                        className={`p-2 rounded-lg shrink-0 ${
                          item.isDirectory ? "bg-amber-600/20 text-amber-400" : "bg-blue-600/20 text-blue-400"
                        }`}
                      >
                        {item.isDirectory ? <Folder className="w-4 h-4" /> : <File className="w-4 h-4" />}
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-1.5">
                          <p className="text-white font-medium truncate" title={item.name}>
                            {item.name || item.path}
                          </p>
                          {item.isDirectory && (
                            <span className="text-[9px] px-1.5 py-0.2 bg-amber-950 text-amber-300 border border-amber-800/40 rounded font-bold">
                              Folder
                            </span>
                          )}
                        </div>
                        <p className="text-[10px] text-gray-500 font-mono truncate mt-0.5" title={item.path}>
                          {item.path}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <div className="text-right">
                        <span className="text-[10px] text-gray-400 block font-mono">
                          {item.isDirectory ? "DIR" : `${(item.sizeBytes / 1024).toFixed(1)} KB`}
                        </span>
                        {item.lastModified > 0 && (
                          <span className="text-[9px] text-gray-500 block">
                            {new Date(item.lastModified).toLocaleDateString()}
                          </span>
                        )}
                      </div>
                      <ChevronRight className="w-4 h-4 text-gray-500" />
                    </div>
                  </div>
                ));
              })()}
            </div>
          )}
        </div>
      )}

      {/* Gallery Media Details Modal */}
      {selectedMedia && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="w-full max-w-md p-5 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-2xl relative space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="p-2 bg-purple-600/20 text-purple-400 rounded-lg">
                  <ImageIcon className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white">Media Details</h3>
                  <p className="text-[11px] text-purple-400 font-semibold">{selectedMedia.fileType || "IMAGE"}</p>
                </div>
              </div>
              <button
                onClick={() => setSelectedMedia(null)}
                className="p-1.5 text-gray-400 hover:text-white rounded-lg hover:bg-white/10"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-2.5 text-xs">
              <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
                <p className="text-[10px] text-gray-400 uppercase font-semibold mb-0.5">File Name</p>
                <p className="text-white font-medium break-all">{selectedMedia.fileName || "Unknown"}</p>
              </div>

              <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
                <div className="flex items-center justify-between mb-1">
                  <p className="text-[10px] text-gray-400 uppercase font-semibold">Device Storage Path</p>
                  <button
                    onClick={() => handleCopyPath(selectedMedia.filePath)}
                    className="text-[11px] text-purple-400 hover:text-purple-300 font-semibold flex items-center gap-1"
                  >
                    {copiedPath === selectedMedia.filePath ? (
                      <>
                        <Check className="w-3 h-3 text-emerald-400" />
                        <span className="text-emerald-400">Copied!</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3 h-3" />
                        <span>Copy</span>
                      </>
                    )}
                  </button>
                </div>
                <p className="text-gray-300 font-mono text-[11px] break-all select-all bg-[#0D0D12] p-2 rounded border border-[#1E1E28]">
                  {selectedMedia.filePath || "Internal Storage"}
                </p>
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
                  <p className="text-[10px] text-gray-400 uppercase font-semibold mb-0.5">File Size</p>
                  <p className="text-white font-mono font-semibold">
                    {selectedMedia.sizeBytes > 0
                      ? `${(selectedMedia.sizeBytes / 1024 / 1024).toFixed(2)} MB`
                      : "Unknown"}
                  </p>
                </div>
                <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
                  <p className="text-[10px] text-gray-400 uppercase font-semibold mb-0.5">Date Added</p>
                  <p className="text-white">
                    {selectedMedia.dateAdded > 0
                      ? new Date(selectedMedia.dateAdded).toLocaleDateString()
                      : "Recent"}
                  </p>
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2 pt-1">
              <button
                onClick={() => handleCopyPath(selectedMedia.filePath)}
                className="flex-1 py-2 bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold rounded-xl transition flex items-center justify-center gap-1.5 shadow-lg shadow-purple-600/30"
              >
                {copiedPath === selectedMedia.filePath ? (
                  <>
                    <Check className="w-4 h-4 text-emerald-300" />
                    <span>Path Copied to Clipboard</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-4 h-4" />
                    <span>Copy Full Path</span>
                  </>
                )}
              </button>
              <button
                onClick={() => setSelectedMedia(null)}
                className="px-4 py-2 bg-[#252530] hover:bg-[#30303E] text-gray-300 text-xs font-semibold rounded-xl transition"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* File Explorer Item Details Modal */}
      {selectedFile && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="w-full max-w-md p-5 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-2xl relative space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="p-2 bg-blue-600/20 text-blue-400 rounded-lg">
                  <File className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white">File Details</h3>
                  <p className="text-[11px] text-blue-400 font-semibold">Device Storage Item</p>
                </div>
              </div>
              <button
                onClick={() => setSelectedFile(null)}
                className="p-1.5 text-gray-400 hover:text-white rounded-lg hover:bg-white/10"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-2.5 text-xs">
              <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
                <p className="text-[10px] text-gray-400 uppercase font-semibold mb-0.5">File Name</p>
                <p className="text-white font-medium break-all">{selectedFile.name || "Unknown File"}</p>
              </div>

              <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
                <div className="flex items-center justify-between mb-1">
                  <p className="text-[10px] text-gray-400 uppercase font-semibold">Full Device Path</p>
                  <button
                    onClick={() => handleCopyPath(selectedFile.path)}
                    className="text-[11px] text-blue-400 hover:text-blue-300 font-semibold flex items-center gap-1"
                  >
                    {copiedPath === selectedFile.path ? (
                      <>
                        <Check className="w-3 h-3 text-emerald-400" />
                        <span className="text-emerald-400">Copied!</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3 h-3" />
                        <span>Copy</span>
                      </>
                    )}
                  </button>
                </div>
                <p className="text-gray-300 font-mono text-[11px] break-all select-all bg-[#0D0D12] p-2 rounded border border-[#1E1E28]">
                  {selectedFile.path}
                </p>
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
                  <p className="text-[10px] text-gray-400 uppercase font-semibold mb-0.5">Size</p>
                  <p className="text-white font-mono font-semibold">
                    {selectedFile.sizeBytes > 0
                      ? selectedFile.sizeBytes > 1024 * 1024
                        ? `${(selectedFile.sizeBytes / 1024 / 1024).toFixed(2)} MB`
                        : `${(selectedFile.sizeBytes / 1024).toFixed(1)} KB`
                      : "0 KB"}
                  </p>
                </div>
                <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
                  <p className="text-[10px] text-gray-400 uppercase font-semibold mb-0.5">Modified Date</p>
                  <p className="text-white">
                    {selectedFile.lastModified > 0
                      ? new Date(selectedFile.lastModified).toLocaleDateString()
                      : "Recent"}
                  </p>
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2 pt-1">
              <button
                onClick={() => handleCopyPath(selectedFile.path)}
                className="flex-1 py-2 bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold rounded-xl transition flex items-center justify-center gap-1.5 shadow-lg shadow-blue-600/30"
              >
                {copiedPath === selectedFile.path ? (
                  <>
                    <Check className="w-4 h-4 text-emerald-300" />
                    <span>Path Copied to Clipboard</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-4 h-4" />
                    <span>Copy Full Path</span>
                  </>
                )}
              </button>
              <button
                onClick={() => setSelectedFile(null)}
                className="px-4 py-2 bg-[#252530] hover:bg-[#30303E] text-gray-300 text-xs font-semibold rounded-xl transition"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
