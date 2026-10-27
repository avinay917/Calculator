import React from "react";
import { Camera, Download, X } from "lucide-react";

interface SnapshotViewerModalProps {
  snapshot: any;
  onClose: () => void;
}

export function SnapshotViewerModal({ snapshot, onClose }: SnapshotViewerModalProps) {
  if (!snapshot) return null;
  const imageUrl = snapshot.downloadUrl || snapshot.storageUrl;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/85 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in"
      onClick={onClose}
    >
      <div
        className="relative max-w-4xl w-full bg-[#1A1A22] border border-[#2B2B38] rounded-2xl overflow-hidden shadow-2xl flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-4 bg-[#14141A] border-b border-[#252530] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-purple-600/20 text-purple-400 rounded-lg">
              <Camera className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-xs font-bold text-white capitalize">
                {snapshot.cameraFacing || "Camera"} Photo Snapshot
              </h3>
              <p className="text-[10px] text-gray-400">
                {snapshot.timestamp ? new Date(snapshot.timestamp).toLocaleString() : ""}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <a
              href={imageUrl}
              download="snapshot_photo.jpg"
              target="_blank"
              rel="noopener noreferrer"
              className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-lg flex items-center gap-1.5 transition"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Download</span>
            </a>
            <button
              type="button"
              onClick={onClose}
              className="p-1.5 text-gray-400 hover:text-white hover:bg-white/10 rounded-lg transition"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-auto p-4 flex items-center justify-center bg-black/60 min-h-[300px]">
          <img
            src={imageUrl}
            alt="High Resolution Snapshot"
            className="max-h-[70vh] w-auto max-w-full object-contain rounded-lg shadow-2xl"
          />
        </div>
      </div>
    </div>
  );
}
