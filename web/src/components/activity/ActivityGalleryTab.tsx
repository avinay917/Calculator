"use client";

import React, { useState } from "react";
import { Camera, Image as ImageIcon, RefreshCw, Eye, Download } from "lucide-react";

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
  const [gallerySection, setGallerySection] = useState<"snapshots" | "device">("device");

  return (
    <div className="space-y-3 mt-2">
      {/* Top Bar: Controls & 1-Click Camera Capture */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 p-3 bg-[#16161E] rounded-2xl border border-[#272733]">
        <div>
          <p className="text-xs text-white font-bold flex items-center gap-2">
            <Camera className="w-4 h-4 text-purple-400" />
            <span>Photos & Camera Captures</span>
          </p>
          <p className="text-[11px] text-gray-400 mt-0.5">
            Real camera snapshots dekhein ya phone se live photo click karein
          </p>
        </div>

        <div className="flex items-center flex-wrap gap-2">
          {handleRequestSnapshot && (
            <>
              <button
                disabled={Boolean(capturingFacing)}
                onClick={() => handleRequestSnapshot("back")}
                className="px-3 py-1.5 bg-purple-600 hover:bg-purple-500 disabled:opacity-50 text-white text-[11px] font-bold rounded-xl flex items-center gap-1.5 transition shadow-md shadow-purple-600/20"
              >
                <Camera className="w-3.5 h-3.5" />
                <span>{capturingFacing === "back" ? "Capturing..." : "📸 Take Rear Photo"}</span>
              </button>
              <button
                disabled={Boolean(capturingFacing)}
                onClick={() => handleRequestSnapshot("front")}
                className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-[11px] font-bold rounded-xl flex items-center gap-1.5 transition shadow-md shadow-blue-600/20"
              >
                <Camera className="w-3.5 h-3.5" />
                <span>{capturingFacing === "front" ? "Capturing..." : "🤳 Take Front Selfie"}</span>
              </button>
            </>
          )}
          <button
            onClick={() => sendCommand("SYNC_MEDIA", true)}
            className="px-2.5 py-1.5 bg-[#252530] hover:bg-[#2e2e3d] text-gray-300 text-[11px] rounded-xl flex items-center gap-1.5 transition border border-[#2E2E3E]"
            title="Rescan child's phone gallery"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Sync Device</span>
          </button>
        </div>
      </div>

      {/* Sub-Section Filter Pills */}
      <div className="flex items-center gap-2">
        <button
          onClick={() => setGallerySection("snapshots")}
          className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition flex items-center gap-2 ${
            gallerySection === "snapshots"
              ? "bg-purple-600 text-white shadow-md shadow-purple-600/20"
              : "bg-[#1B1B22] text-gray-400 hover:text-white border border-[#272733]"
          }`}
        >
          <Camera className="w-3.5 h-3.5" />
          <span>Camera Snapshots ({snapshotsList.length})</span>
        </button>
        <button
          onClick={() => setGallerySection("device")}
          className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition flex items-center gap-2 ${
            gallerySection === "device"
              ? "bg-purple-600 text-white shadow-md shadow-purple-600/20"
              : "bg-[#1B1B22] text-gray-400 hover:text-white border border-[#272733]"
          }`}
        >
          <ImageIcon className="w-3.5 h-3.5" />
          <span>Device & WhatsApp Media ({mediaGallery.length})</span>
        </button>
      </div>

      {/* Section 1: Real Camera Snapshots Grid */}
      {gallerySection === "snapshots" && (
        <div>
          {snapshotsList.length === 0 ? (
            <div className="text-center py-12 text-xs text-gray-500 bg-[#16161D] rounded-2xl border border-[#272733] space-y-2">
              <Camera className="w-8 h-8 text-purple-400/60 mx-auto" />
              <p className="text-white font-medium">Abhi tak koi camera snapshot nahi liya gaya hai.</p>
              <p className="text-[11px] text-gray-400">
                Upar diye gaye "Take Rear Photo" ya "Take Front Selfie" button par click karke turant live photo khinchein!
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
              {snapshotsList.map((snap, idx) => {
                const snapUrl = snap.downloadUrl || snap.storageUrl;
                if (!snapUrl) return null;
                const isFront = snap.cameraFacing === "front";

                return (
                  <div
                    key={snap.id || idx}
                    onClick={() => onSelectPhoto({ ...snap, isSnapshot: true })}
                    className="group relative aspect-square rounded-2xl overflow-hidden bg-black border border-[#272733] hover:border-purple-500 cursor-pointer transition duration-200 shadow-md flex flex-col justify-end"
                  >
                    <img
                      src={snapUrl}
                      alt="Snapshot"
                      className="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition duration-300"
                      loading="lazy"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-80 group-hover:opacity-100 transition p-2.5 flex flex-col justify-between">
                      <span className={`self-end px-2 py-0.5 backdrop-blur-md text-[9px] font-bold text-white rounded-md ${
                        isFront ? "bg-blue-600/80" : "bg-purple-600/80"
                      }`}>
                        {isFront ? "🤳 Selfie" : "📸 Rear Cam"}
                      </span>
                      <div className="flex items-center justify-between text-white text-[10px] font-semibold">
                        <span>{snap.timestamp ? new Date(snap.timestamp).toLocaleTimeString() : ""}</span>
                        <span className="p-1 bg-white/20 rounded-lg group-hover:bg-purple-500 transition">
                          <Eye className="w-3 h-3 text-white" />
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Section 2: Device Media Files Grid */}
      {gallerySection === "device" && (
        <div>
          {mediaGallery.length === 0 ? (
            <div className="text-center py-12 text-xs text-gray-500 bg-[#16161D] rounded-2xl border border-[#272733]">
              <ImageIcon className="w-8 h-8 text-gray-600 mx-auto mb-2" />
              <p>No gallery media synced yet.</p>
              <p className="text-[11px] text-gray-600 mt-1">Click "Sync Device" to scan child's photos and videos.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {mediaGallery.map((item, idx) => {
                const isVideo = item.fileType === "VIDEO" || (item.fileName && item.fileName.endsWith(".mp4"));
                const imgSrc = item.thumbnailBase64 || item.downloadUrl || item.storageUrl || item.mediaUrl || "";
                const hasImage = Boolean(imgSrc);

                return (
                  <div
                    key={idx}
                    onClick={() => onSelectPhoto(item)}
                    className="bg-[#1B1B22] hover:bg-[#22222D] border border-[#272733] hover:border-purple-500/60 rounded-2xl overflow-hidden transition cursor-pointer group shadow-md flex flex-col justify-between"
                  >
                    {hasImage ? (
                      <div className="relative h-36 w-full bg-black overflow-hidden">
                        <img
                          src={imgSrc}
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
                          {item.dateAdded > 0 && <span>• {new Date(item.dateAdded).toLocaleDateString()}</span>}
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 pt-1 border-t border-[#272733]/50">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            onSelectPhoto(item);
                          }}
                          className="flex-1 py-1.5 bg-[#252530] hover:bg-purple-600/30 hover:text-purple-300 text-gray-300 rounded-lg text-[11px] font-semibold flex items-center justify-center gap-1 transition"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          <span>{hasImage ? "View Photo" : "Photo Details"}</span>
                        </button>
                        {!hasImage && item.filePath && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              onRequestFile(item.filePath);
                            }}
                            className="px-2.5 py-1.5 bg-purple-600/20 hover:bg-purple-600/40 text-purple-300 border border-purple-500/30 rounded-lg text-[11px] font-semibold flex items-center gap-1 transition"
                            title="Request original photo from child phone"
                          >
                            <Download className="w-3.5 h-3.5" />
                            <span>{requestedFiles[item.filePath] ? "Requested" : "Request Photo"}</span>
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
    </div>
  );
}
