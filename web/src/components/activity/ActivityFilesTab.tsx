"use client";

import React from "react";
import {
  Folder,
  RefreshCw,
  HardDrive,
  Check,
  Copy,
  ChevronRight,
  File,
  FileText,
  Music,
  Download,
  Info,
  Image as ImageIcon,
} from "lucide-react";

export function FilesTab({
  fileExplorer = [],
  mediaGallery = [],
  selectedDirectoryPath = "",
  setSelectedDirectoryPath,
  sendCommand,
  onSelectFile,
  onSelectPhoto,
  onCopyPath,
  copiedPath,
  onRequestFile,
  requestedFiles = {},
}: {
  fileExplorer?: any[];
  mediaGallery?: any[];
  selectedDirectoryPath?: string;
  setSelectedDirectoryPath: (path: string) => void;
  sendCommand: (cmd: string, val: any) => void;
  onSelectFile: (file: any) => void;
  onSelectPhoto: (photo: any) => void;
  onCopyPath: (path: string) => void;
  copiedPath: string | null;
  onRequestFile: (path: string) => void;
  requestedFiles?: Record<string, boolean>;
}) {
  const normSelected = (selectedDirectoryPath || "").trim().replace(/[\\/]+$/, "");

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
        return !fileExplorer.some((f) => f.path && f.path.trim().replace(/[\\/]+$/, "") === itemParent);
      });

  const sortedItems = [...displayItems].sort((a, b) => {
    const isDirA = Boolean(a.directory ?? a.isDirectory);
    const isDirB = Boolean(b.directory ?? b.isDirectory);
    if (isDirA && !isDirB) return -1;
    if (!isDirA && isDirB) return 1;
    return (a.name || "").localeCompare(b.name || "");
  });

  return (
    <div className="space-y-3 mt-2">
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

      <div className="flex items-center justify-between overflow-x-auto text-xs py-2 px-3 bg-[#14141B] rounded-xl border border-[#272733]">
        <div className="flex items-center gap-1.5 min-w-0">
          <button
            onClick={() => setSelectedDirectoryPath("")}
            className={`flex items-center gap-1 font-semibold px-2 py-0.5 rounded transition ${
              !selectedDirectoryPath ? "text-amber-400 bg-amber-500/10" : "text-blue-400 hover:text-white"
            }`}
          >
            <HardDrive className="w-3.5 h-3.5" />
            <span>Root Storage</span>
          </button>
          {selectedDirectoryPath && (
            <>
              <span className="text-gray-500">/</span>
              <span className="px-2 py-0.5 rounded text-amber-300 bg-amber-500/10 font-bold truncate max-w-[220px]">
                {selectedDirectoryPath.replace(/^\/storage\/emulated\/0\/?/, "") || selectedDirectoryPath}
              </span>
            </>
          )}
        </div>
        {selectedDirectoryPath && (
          <button
            onClick={() => setSelectedDirectoryPath("")}
            className="text-[10px] px-2.5 py-0.5 bg-[#252530] hover:bg-[#323242] text-gray-300 rounded-lg transition shrink-0 font-medium"
          >
            ← Back to Root
          </button>
        )}
      </div>

      {fileExplorer.length === 0 ? (
        <div className="text-center py-12 text-xs text-gray-500 bg-[#16161D] rounded-xl border border-[#272733]">
          <Folder className="w-8 h-8 text-gray-600 mx-auto mb-2" />
          <p>No files synced yet.</p>
          <p className="text-[11px] text-gray-600 mt-1">Click "Rescan Storage" to index files from child's device.</p>
        </div>
      ) : sortedItems.length === 0 && selectedDirectoryPath ? (
        <div className="p-8 text-center bg-[#16161D] rounded-xl border border-[#272733] space-y-3">
          <Folder className="w-8 h-8 text-amber-500/50 mx-auto" />
          <div>
            <p className="text-white font-medium text-xs">Folder is currently empty or contains no scanned sub-files.</p>
            <p className="text-[11px] text-gray-500 mt-1 font-mono break-all">{selectedDirectoryPath}</p>
          </div>
          <div className="flex items-center justify-center gap-2 pt-2">
            <button
              onClick={() => onCopyPath(selectedDirectoryPath)}
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
      ) : (
        <div className="space-y-1.5">
          {sortedItems.map((item, idx) => {
            const isDir = Boolean(item.directory ?? item.isDirectory);
            const isImg = !isDir && Boolean((item.name || "").match(/\.(jpg|jpeg|png|webp|heic)$/i));
            const matched = isImg
              ? mediaGallery.find(
                  (m) =>
                    (m.filePath && item.path && m.filePath === item.path) ||
                    (m.fileName && item.name && m.fileName === item.name)
                )
              : null;
            const thumbUrl = matched?.thumbnailBase64 || matched?.downloadUrl || matched?.storageUrl;

            return (
              <div
                key={idx}
                onClick={() => {
                  if (isDir) {
                    if (item.path) setSelectedDirectoryPath(item.path);
                  } else if (isImg && matched) {
                    onSelectPhoto(matched);
                  } else {
                    onSelectFile(item);
                  }
                }}
                className={`p-2.5 rounded-xl border flex items-center justify-between text-xs transition cursor-pointer select-none ${
                  isDir
                    ? "bg-[#1B1B22] border-[#272733] hover:bg-[#22222D] hover:border-amber-500/50"
                    : "bg-[#16161D] border-[#22222D] hover:bg-[#1C1C24] hover:border-gray-700"
                }`}
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div
                    className={`p-2 rounded-lg shrink-0 ${
                      isDir
                        ? "bg-amber-500/10 text-amber-400"
                        : isImg
                        ? "bg-purple-500/10 text-purple-400"
                        : "bg-blue-500/10 text-blue-400"
                    }`}
                  >
                    {isDir ? (
                      <Folder className="w-4 h-4 fill-amber-400/20" />
                    ) : isImg && thumbUrl ? (
                      <img src={thumbUrl} alt="" className="w-5 h-5 rounded object-cover" />
                    ) : (
                      <File className="w-4 h-4" />
                    )}
                  </div>
                  <div className="min-w-0">
                    <p className={`font-semibold truncate ${isDir ? "text-amber-100" : "text-white"}`}>{item.name}</p>
                    <div className="flex items-center gap-2 text-[10px] text-gray-500">
                      {isDir ? (
                        <span>Directory</span>
                      ) : (
                        <>
                          <span>
                            {item.sizeBytes > 0
                              ? item.sizeBytes > 1024 * 1024
                                ? `${(item.sizeBytes / 1024 / 1024).toFixed(2)} MB`
                                : `${(item.sizeBytes / 1024).toFixed(1)} KB`
                              : "0 KB"}
                          </span>
                          {item.lastModified > 0 && (
                            <span>• {new Date(item.lastModified).toLocaleDateString()}</span>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-1.5 shrink-0" onClick={(e) => e.stopPropagation()}>
                  {isDir ? (
                    <button
                      onClick={() => item.path && setSelectedDirectoryPath(item.path)}
                      className="p-1.5 text-gray-400 hover:text-white rounded-lg hover:bg-white/5"
                    >
                      <ChevronRight className="w-4 h-4" />
                    </button>
                  ) : (
                    <>
                      {item.path && (
                        <button
                          onClick={() => onRequestFile(item.path)}
                          className="px-2 py-1 bg-blue-600/20 hover:bg-blue-600/40 text-blue-300 border border-blue-500/30 rounded-lg text-[10px] font-semibold flex items-center gap-1 transition"
                          title="Request this file to upload from child's phone"
                        >
                          <Download className="w-3 h-3" />
                          <span className="hidden sm:inline">
                            {requestedFiles[item.path] ? "Requested" : "Request"}
                          </span>
                        </button>
                      )}
                      <button
                        onClick={() => onSelectFile(item)}
                        className="p-1.5 text-gray-400 hover:text-white rounded-lg hover:bg-white/5"
                        title="View details & path"
                      >
                        <Info className="w-3.5 h-3.5" />
                      </button>
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
