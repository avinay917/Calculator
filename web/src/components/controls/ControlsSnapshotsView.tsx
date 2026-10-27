"use client";

import React from "react";
import { Camera, Maximize2, Download } from "lucide-react";

interface ControlsSnapshotsViewProps {
  rearSnap: any;
  frontSnap: any;
  snapshotsList: any[];
  onSelectSnapshot: (snap: any) => void;
}

export default function ControlsSnapshotsView({
  rearSnap,
  frontSnap,
  snapshotsList,
  onSelectSnapshot,
}: ControlsSnapshotsViewProps) {
  return (
    <div className="space-y-4">
      {(rearSnap || frontSnap) && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* REAR PHOTO CARD */}
          <div className="p-4 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl shadow-xl flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Camera className="w-4 h-4 text-blue-400" />
                  <h3 className="text-xs font-bold text-white">Rear Camera Photo (Back)</h3>
                  <span className="text-[9px] px-2 py-0.5 bg-blue-950 text-blue-300 border border-blue-800/40 rounded-full font-bold uppercase">
                    Back Cam
                  </span>
                </div>
                {rearSnap?.timestamp && (
                  <span className="text-[10px] text-gray-500">
                    {new Date(rearSnap.timestamp).toLocaleString()}
                  </span>
                )}
              </div>

              {rearSnap && (rearSnap.downloadUrl || rearSnap.storageUrl) ? (
                <div className="relative rounded-xl overflow-hidden border border-[#272733] bg-black group">
                  <img
                    src={rearSnap.downloadUrl || rearSnap.storageUrl}
                    alt="Rear Camera Snapshot"
                    className="w-full max-h-72 object-contain mx-auto cursor-pointer transition group-hover:scale-[1.01]"
                    onClick={() => onSelectSnapshot(rearSnap)}
                  />
                  <div className="p-2.5 bg-[#121217] flex items-center justify-between text-xs">
                    <button
                      type="button"
                      onClick={() => onSelectSnapshot(rearSnap)}
                      className="text-gray-300 hover:text-white flex items-center gap-1.5 font-medium cursor-pointer"
                    >
                      <Maximize2 className="w-3.5 h-3.5 text-blue-400" />
                      <span>View Zoom</span>
                    </button>
                    <a
                      href={rearSnap.downloadUrl || rearSnap.storageUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      download
                      className="text-blue-400 hover:underline flex items-center gap-1 font-medium"
                    >
                      <Download className="w-3.5 h-3.5" />
                      <span>Save Photo</span>
                    </a>
                  </div>
                </div>
              ) : (
                <div className="h-44 border border-dashed border-[#2B2B36] rounded-xl flex flex-col items-center justify-center p-4 text-center">
                  <Camera className="w-8 h-8 text-gray-600 mb-2" />
                  <p className="text-xs text-gray-400">No rear camera photo captured yet</p>
                  <p className="text-[10px] text-gray-600 mt-1">Click &apos;Capture Rear Photo&apos; above</p>
                </div>
              )}
            </div>
          </div>

          {/* FRONT SELFIE PHOTO CARD */}
          <div className="p-4 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl shadow-xl flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Camera className="w-4 h-4 text-purple-400" />
                  <h3 className="text-xs font-bold text-white">Front Selfie Photo</h3>
                  <span className="text-[9px] px-2 py-0.5 bg-purple-950 text-purple-300 border border-purple-800/40 rounded-full font-bold uppercase">
                    Front Cam
                  </span>
                </div>
                {frontSnap?.timestamp && (
                  <span className="text-[10px] text-gray-500">
                    {new Date(frontSnap.timestamp).toLocaleString()}
                  </span>
                )}
              </div>

              {frontSnap && (frontSnap.downloadUrl || frontSnap.storageUrl) ? (
                <div className="relative rounded-xl overflow-hidden border border-[#272733] bg-black group">
                  <img
                    src={frontSnap.downloadUrl || frontSnap.storageUrl}
                    alt="Front Selfie Snapshot"
                    className="w-full max-h-72 object-contain mx-auto cursor-pointer transition group-hover:scale-[1.01]"
                    onClick={() => onSelectSnapshot(frontSnap)}
                  />
                  <div className="p-2.5 bg-[#121217] flex items-center justify-between text-xs">
                    <button
                      type="button"
                      onClick={() => onSelectSnapshot(frontSnap)}
                      className="text-gray-300 hover:text-white flex items-center gap-1.5 font-medium cursor-pointer"
                    >
                      <Maximize2 className="w-3.5 h-3.5 text-purple-400" />
                      <span>View Zoom</span>
                    </button>
                    <a
                      href={frontSnap.downloadUrl || frontSnap.storageUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      download
                      className="text-purple-400 hover:underline flex items-center gap-1 font-medium"
                    >
                      <Download className="w-3.5 h-3.5" />
                      <span>Save Photo</span>
                    </a>
                  </div>
                </div>
              ) : (
                <div className="h-44 border border-dashed border-[#2B2B36] rounded-xl flex flex-col items-center justify-center p-4 text-center">
                  <Camera className="w-8 h-8 text-gray-600 mb-2" />
                  <p className="text-xs text-gray-400">No front camera selfie captured yet</p>
                  <p className="text-[10px] text-gray-600 mt-1">Click &apos;Capture Front Selfie&apos; above</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Snapshots Gallery Grid */}
      {snapshotsList.length > 1 && (
        <div className="p-4 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-xs font-bold text-white flex items-center gap-2">
              <Camera className="w-3.5 h-3.5 text-blue-400" />
              <span>Captured Photos Gallery ({snapshotsList.length})</span>
            </h3>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 gap-2.5">
            {snapshotsList.map((snap, idx) => {
              const snapUrl = snap.downloadUrl || snap.storageUrl;
              if (!snapUrl) return null;
              return (
                <div
                  key={snap.id || idx}
                  onClick={() => onSelectSnapshot(snap)}
                  className="group relative aspect-square rounded-xl overflow-hidden bg-black border border-[#272733] hover:border-blue-500/50 cursor-pointer transition"
                >
                  <img
                    src={snapUrl}
                    alt="Snapshot thumbnail"
                    className="w-full h-full object-cover group-hover:scale-105 transition"
                    loading="lazy"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition flex items-end p-1.5">
                    <span className="text-[9px] text-white font-bold capitalize">
                      {snap.cameraFacing || "Cam"}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
