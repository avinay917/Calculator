"use client";

import React from "react";
import { X, ExternalLink, Download, File, Image as ImageIcon } from "lucide-react";

export function PhotoViewerModal({
  selectedPhotoViewer,
  setSelectedPhotoViewer,
  onCopyPath,
  copiedPath,
  onRequestFile,
  requestedFiles = {},
}: {
  selectedPhotoViewer: any;
  setSelectedPhotoViewer: (item: any) => void;
  onCopyPath: (path: string) => void;
  copiedPath: string | null;
  onRequestFile: (path: string) => void;
  requestedFiles?: Record<string, boolean>;
}) {
  if (!selectedPhotoViewer) return null;

  const isVideo =
    selectedPhotoViewer.fileType === "VIDEO" ||
    (selectedPhotoViewer.fileName && selectedPhotoViewer.fileName.endsWith(".mp4"));
  const isSnapshot = Boolean(selectedPhotoViewer.isSnapshot);
  const mediaUrl =
    selectedPhotoViewer.downloadUrl ||
    selectedPhotoViewer.storageUrl ||
    selectedPhotoViewer.mediaUrl ||
    "";
  const rawThumb = selectedPhotoViewer.thumbnailBase64 || selectedPhotoViewer.displayUrl || "";
  const thumbUrl = rawThumb
    ? (rawThumb.startsWith("data:") || rawThumb.startsWith("http")
        ? rawThumb
        : `data:image/jpeg;base64,${rawThumb}`)
    : "";
  const hasFullUrl = Boolean(mediaUrl);
  const hasThumb = Boolean(thumbUrl);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md">
      <div className="w-full max-w-lg p-5 bg-[#1B1B24] border border-[#2E2E3C] rounded-3xl shadow-2xl relative space-y-4 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span
              className={`p-1.5 rounded-lg text-white ${
                isSnapshot ? "bg-purple-600" : isVideo ? "bg-red-600" : "bg-blue-600"
              }`}
            >
              <ImageIcon className="w-4 h-4" />
            </span>
            <div>
              <h3 className="text-sm font-bold text-white truncate max-w-[300px]">
                {selectedPhotoViewer.fileName || (isSnapshot ? "Camera Snapshot" : "Device Media")}
              </h3>
              <p className="text-[11px] text-gray-400">
                {isSnapshot
                  ? selectedPhotoViewer.cameraFacing === "front"
                    ? "🤳 Front Selfie"
                    : "📸 Rear Camera Capture"
                  : isVideo
                  ? "Video Clip"
                  : "Photo"}
              </p>
            </div>
          </div>
          <button
            onClick={() => setSelectedPhotoViewer(null)}
            className="p-1.5 text-gray-400 hover:text-white rounded-xl hover:bg-white/10"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {hasFullUrl ? (
          <div className="relative rounded-2xl overflow-hidden bg-black flex items-center justify-center border border-[#272733] max-h-[50vh]">
            {isVideo ? (
              <video controls src={mediaUrl} className="w-full max-h-[50vh] object-contain rounded-2xl" autoPlay />
            ) : (
              <img src={mediaUrl} alt="" className="w-full max-h-[50vh] object-contain rounded-2xl" />
            )}
          </div>
        ) : hasThumb ? (
          <div className="relative rounded-2xl overflow-hidden bg-black flex items-center justify-center border border-[#272733] max-h-[50vh]">
            <img src={thumbUrl} alt="" className="w-full max-h-[50vh] object-contain rounded-2xl" />
          </div>
        ) : (
          <div className="p-12 text-center bg-[#13131A] rounded-2xl border border-[#272733] space-y-2">
            <ImageIcon className="w-12 h-12 text-gray-600 mx-auto" />
            <p className="text-xs text-gray-300 font-medium">Original file child ke phone storage par hai.</p>
            <p className="text-[11px] text-gray-500">Neeche diye gaye button se original photo request karein.</p>
          </div>
        )}

        <div className="space-y-2">
          {hasFullUrl && (
            <a
              href={mediaUrl}
              target="_blank"
              rel="noopener noreferrer"
              download
              className="w-full py-2.5 bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold rounded-xl transition flex items-center justify-center gap-1.5 shadow-lg shadow-purple-600/30"
            >
              <ExternalLink className="w-4 h-4" />
              <span>Open / Download Full Media</span>
            </a>
          )}

          {!hasFullUrl && selectedPhotoViewer.filePath && (
            <button
              onClick={() => onRequestFile(selectedPhotoViewer.filePath)}
              className="w-full py-2.5 bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold rounded-xl transition flex items-center justify-center gap-1.5 shadow-lg shadow-purple-600/30"
            >
              <Download className="w-4 h-4" />
              <span>
                {requestedFiles[selectedPhotoViewer.filePath]
                  ? "Photo Request Sent! Phone Upload Kar Raha Hai..."
                  : "📲 Request Photo From Child Phone"}
              </span>
            </button>
          )}
        </div>

        <div className="grid grid-cols-2 gap-2 text-xs">
          <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
            <p className="text-[10px] text-gray-400 uppercase font-semibold mb-0.5">Date / Time</p>
            <p className="text-white font-medium">
              {selectedPhotoViewer.timestamp
                ? new Date(selectedPhotoViewer.timestamp).toLocaleString()
                : selectedPhotoViewer.dateAdded
                ? new Date(selectedPhotoViewer.dateAdded).toLocaleDateString()
                : "Recent"}
            </p>
          </div>
          <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
            <p className="text-[10px] text-gray-400 uppercase font-semibold mb-0.5">Size / Type</p>
            <p className="text-white font-medium">
              {selectedPhotoViewer.sizeBytes > 0
                ? `${(selectedPhotoViewer.sizeBytes / 1024 / 1024).toFixed(2)} MB`
                : selectedPhotoViewer.cameraFacing
                ? "Camera Snapshot"
                : "Photo"}
            </p>
          </div>
        </div>

        {selectedPhotoViewer.filePath && (
          <div className="flex items-center justify-between p-2 bg-[#0E0E14] rounded-xl border border-[#222230] text-[10px]">
            <span className="text-gray-500 truncate max-w-[280px] font-mono">{selectedPhotoViewer.filePath}</span>
            <button
              onClick={() => onCopyPath(selectedPhotoViewer.filePath)}
              className="text-purple-400 hover:text-purple-300 font-semibold shrink-0 ml-2"
            >
              {copiedPath === selectedPhotoViewer.filePath ? "Copied!" : "Copy Path"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export function FileDetailsModal({
  selectedFile,
  setSelectedFile,
  onCopyPath,
  copiedPath,
  onRequestFile,
  requestedFiles = {},
}: {
  selectedFile: any;
  setSelectedFile: (file: any) => void;
  onCopyPath: (path: string) => void;
  copiedPath: string | null;
  onRequestFile: (path: string) => void;
  requestedFiles?: Record<string, boolean>;
}) {
  if (!selectedFile) return null;

  return (
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
                {selectedFile.lastModified > 0 ? new Date(selectedFile.lastModified).toLocaleDateString() : "Recent"}
              </p>
            </div>
          </div>

          {selectedFile.path && (
            <div className="p-2.5 bg-[#14141B] rounded-xl border border-[#272733]">
              <div className="flex items-center justify-between mb-1">
                <p className="text-[10px] text-gray-400 uppercase font-semibold">Device Storage Path</p>
                <button
                  onClick={() => onCopyPath(selectedFile.path)}
                  className="text-[11px] text-blue-400 hover:text-blue-300 font-semibold"
                >
                  {copiedPath === selectedFile.path ? "Copied!" : "Copy"}
                </button>
              </div>
              <p className="text-gray-400 font-mono text-[10px] break-all select-all">{selectedFile.path}</p>
            </div>
          )}
        </div>

        <div className="flex items-center gap-2 pt-1">
          {selectedFile.path && (
            <button
              onClick={() => onRequestFile(selectedFile.path)}
              className="flex-1 py-2 bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold rounded-xl transition flex items-center justify-center gap-1.5 shadow-lg shadow-blue-600/30"
            >
              <Download className="w-4 h-4" />
              <span>{requestedFiles[selectedFile.path] ? "File Requested!" : "Request File from Child Phone"}</span>
            </button>
          )}
          <button
            onClick={() => setSelectedFile(null)}
            className="px-4 py-2 bg-[#252530] hover:bg-[#30303E] text-gray-300 text-xs font-semibold rounded-xl transition"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
