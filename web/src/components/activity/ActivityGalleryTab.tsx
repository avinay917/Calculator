"use client";

import React, { useState, useMemo } from "react";
import { Camera, Image as ImageIcon, RefreshCw, Eye, Download, Play, Layers } from "lucide-react";

export function GalleryTab({
  snapshotsList = [],
  mediaGallery = [],
  capturingFacing = null,
  handleRequestSnapshot,
  sendCommand,
  onSelectPhoto,
  onRequestFile,
  requestedFiles = {},
}: {
  snapshotsList?: any[];
  mediaGallery?: any[];
  capturingFacing?: "front" | "back" | null;
  handleRequestSnapshot?: (facing: "front" | "back") => void;
  sendCommand: (cmd: string, val: any) => void;
  onSelectPhoto: (item: any) => void;
  onRequestFile: (path: string) => void;
  requestedFiles?: Record<string, boolean>;
}) {
  const [filterType, setFilterType] = useState<"all" | "device" | "snapshots">("all");

  // Normalized unified media list
  const allMedia = useMemo(() => {
    const list: any[] = [];

    // Snapshots
    snapshotsList.forEach((snap, idx) => {
      const url = snap.downloadUrl || snap.storageUrl;
      if (url) {
        list.push({
          ...snap,
          id: snap.id || `snap_${idx}`,
          isSnapshot: true,
          displayUrl: url,
          timestamp: snap.timestamp || 0,
          title: snap.cameraFacing === "front" ? "Selfie Capture" : "Rear Camera",
          fileType: "SNAPSHOT",
        });
      }
    });

    // Device gallery
    mediaGallery.forEach((item, idx) => {
      let url = item.downloadUrl || item.storageUrl || item.mediaUrl || "";
      if (!url && item.thumbnailBase64) {
        url = item.thumbnailBase64.startsWith("data:")
          ? item.thumbnailBase64
          : `data:image/jpeg;base64,${item.thumbnailBase64}`;
      }
      const isVideo = item.fileType === "VIDEO" || (item.fileName && item.fileName.endsWith(".mp4"));
      list.push({
        ...item,
        id: item.id || `media_${idx}`,
        isSnapshot: false,
        displayUrl: url,
        timestamp: item.dateAdded || item.timestamp || 0,
        title: item.fileName || (isVideo ? "Video" : "Photo"),
        isVideo,
      });
    });

    // Sort newest first
    list.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
    return list;
  }, [snapshotsList, mediaGallery]);

  const displayedItems = useMemo(() => {
    if (filterType === "snapshots") return allMedia.filter((m) => m.isSnapshot);
    if (filterType === "device") return allMedia.filter((m) => !m.isSnapshot);
    return allMedia;
  }, [allMedia, filterType]);

  return (
    <div className="space-y-3 mt-1">
      {/* Sleek Action Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 p-3 bg-[#171720] rounded-2xl border border-[#272734]">
        <div className="flex items-center gap-2">
          <div className="p-2 bg-purple-500/15 text-purple-400 rounded-xl">
            <ImageIcon className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-xs font-bold text-white flex items-center gap-1.5">
              <span>Photo & Media Gallery</span>
              <span className="text-[10px] px-2 py-0.5 bg-[#252532] text-gray-400 rounded-full font-semibold">
                {allMedia.length}
              </span>
            </h3>
            <p className="text-[11px] text-gray-400">Phone photos, videos aur live camera snapshots</p>
          </div>
        </div>

        <div className="flex items-center gap-1.5 flex-wrap">
          {handleRequestSnapshot && (
            <>
              <button
                disabled={Boolean(capturingFacing)}
                onClick={() => handleRequestSnapshot("back")}
                className="px-2.5 py-1.5 bg-purple-600 hover:bg-purple-500 disabled:opacity-50 text-white text-[11px] font-bold rounded-xl flex items-center gap-1.5 transition shadow-sm shadow-purple-600/30"
                title="Capture instant rear photo"
              >
                <Camera className="w-3.5 h-3.5" />
                <span>{capturingFacing === "back" ? "Clicking..." : "📸 Rear Cam"}</span>
              </button>
              <button
                disabled={Boolean(capturingFacing)}
                onClick={() => handleRequestSnapshot("front")}
                className="px-2.5 py-1.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-[11px] font-bold rounded-xl flex items-center gap-1.5 transition shadow-sm shadow-blue-600/30"
                title="Capture instant front selfie"
              >
                <Camera className="w-3.5 h-3.5" />
                <span>{capturingFacing === "front" ? "Clicking..." : "🤳 Selfie"}</span>
              </button>
            </>
          )}
          <button
            onClick={() => sendCommand("SYNC_MEDIA", true)}
            className="px-2.5 py-1.5 bg-[#22222D] hover:bg-[#2C2C3A] text-gray-300 text-[11px] rounded-xl flex items-center gap-1.5 transition border border-[#2E2E3E]"
            title="Rescan child's phone gallery"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Sync</span>
          </button>
        </div>
      </div>

      {/* Filter Chips */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
        <button
          onClick={() => setFilterType("all")}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition flex items-center gap-1.5 ${
            filterType === "all"
              ? "bg-purple-600 text-white shadow-md shadow-purple-600/20"
              : "bg-[#1A1A23] text-gray-400 hover:text-white border border-[#262632]"
          }`}
        >
          <Layers className="w-3.5 h-3.5" />
          <span>All Media ({allMedia.length})</span>
        </button>
        <button
          onClick={() => setFilterType("device")}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition flex items-center gap-1.5 ${
            filterType === "device"
              ? "bg-purple-600 text-white shadow-md shadow-purple-600/20"
              : "bg-[#1A1A23] text-gray-400 hover:text-white border border-[#262632]"
          }`}
        >
          <ImageIcon className="w-3.5 h-3.5" />
          <span>Device Photos ({mediaGallery.length})</span>
        </button>
        <button
          onClick={() => setFilterType("snapshots")}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition flex items-center gap-1.5 ${
            filterType === "snapshots"
              ? "bg-purple-600 text-white shadow-md shadow-purple-600/20"
              : "bg-[#1A1A23] text-gray-400 hover:text-white border border-[#262632]"
          }`}
        >
          <Camera className="w-3.5 h-3.5" />
          <span>Snapshots ({snapshotsList.length})</span>
        </button>
      </div>

      {/* Modern Google Photos Style Square Tile Grid */}
      {displayedItems.length === 0 ? (
        <div className="text-center py-14 px-4 bg-[#16161E] rounded-2xl border border-[#252532] space-y-2.5">
          <ImageIcon className="w-10 h-10 text-gray-600 mx-auto" />
          <p className="text-sm font-semibold text-gray-300">Koi photo ya media nahi mila</p>
          <p className="text-xs text-gray-500 max-w-sm mx-auto">
            Phone se naye photos fetch karne ke liye upar diye gaye "Sync" button par tap karein ya "📸 Rear Cam" se photo click karein.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-2 sm:gap-2.5">
          {displayedItems.map((item, idx) => {
            const hasImg = Boolean(item.displayUrl);
            const isVideo = item.isVideo;
            const isSnapshot = item.isSnapshot;

            return (
              <div
                key={item.id || idx}
                onClick={() => onSelectPhoto(item)}
                className="group relative aspect-square rounded-xl overflow-hidden bg-[#181822] border border-[#282836] hover:border-purple-500 cursor-pointer transition-all duration-200 shadow-sm hover:shadow-lg hover:shadow-purple-900/20"
              >
                {hasImg ? (
                  <img
                    src={item.displayUrl}
                    alt={item.title || "Media"}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                    loading="lazy"
                  />
                ) : (
                  <div className="w-full h-full flex flex-col items-center justify-center p-2 bg-gradient-to-br from-[#1E1E2B] to-[#14141C] text-center">
                    <ImageIcon className="w-6 h-6 text-purple-400/60 mb-1" />
                    <p className="text-[10px] text-gray-300 font-medium truncate w-full px-1">{item.title}</p>
                    {item.filePath && !requestedFiles[item.filePath] && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          onRequestFile(item.filePath);
                        }}
                        className="mt-1 px-2 py-0.5 bg-purple-600/30 hover:bg-purple-600 text-purple-200 rounded text-[9px] font-semibold flex items-center gap-1 transition"
                      >
                        <Download className="w-2.5 h-2.5" />
                        <span>Request</span>
                      </button>
                    )}
                  </div>
                )}

                {/* Top Badge Overlay */}
                <div className="absolute top-1.5 right-1.5 flex items-center gap-1 z-10 pointer-events-none">
                  {isVideo && (
                    <span className="px-1.5 py-0.5 bg-red-600/90 backdrop-blur-md text-[9px] font-bold text-white rounded-md flex items-center gap-0.5 shadow">
                      <Play className="w-2.5 h-2.5 fill-current" /> Video
                    </span>
                  )}
                  {isSnapshot && (
                    <span
                      className={`px-1.5 py-0.5 backdrop-blur-md text-[9px] font-bold text-white rounded-md shadow ${
                        item.cameraFacing === "front" ? "bg-blue-600/90" : "bg-purple-600/90"
                      }`}
                    >
                      {item.cameraFacing === "front" ? "🤳 Selfie" : "📸 Rear"}
                    </span>
                  )}
                </div>

                {/* Bottom Gradient Hover Info */}
                <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-200 p-2 flex flex-col justify-end pointer-events-none">
                  <p className="text-white text-[10px] font-bold truncate leading-tight">{item.title}</p>
                  <div className="flex items-center justify-between text-[9px] text-gray-300 mt-0.5">
                    <span>{item.timestamp ? new Date(item.timestamp).toLocaleDateString() : ""}</span>
                    {item.sizeBytes > 0 && (
                      <span>{(item.sizeBytes / 1024 / 1024).toFixed(1)}MB</span>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
