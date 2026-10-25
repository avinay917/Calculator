"use client";

import React from "react";
import {
  MapPin,
  RefreshCw,
  ExternalLink,
  Volume2,
  Flashlight,
  Camera,
  Lock,
  Maximize2,
  Download,
  Trash2,
  Clock,
  CheckCircle,
  Home,
} from "lucide-react";

interface ControlsPanelProps {
  childLocation: any;
  isRefreshingLocation: boolean;
  setIsRefreshingLocation: (val: boolean) => void;
  sendCommand: (cmd: string, val: any) => void;
  isSirenActive: boolean;
  isTorchActive: boolean;
  capturingFacing: "front" | "back" | null;
  handleRequestSnapshot: (facing: "front" | "back") => void;
  snapshotsList: any[];
  latestSnapshot: any;
  setSelectedSnapshotModal: (snap: any) => void;
  storageRetentionDays: number;
  handleUpdateRetention: (days: number) => void;
  isCleaningStorage: boolean;
  cleanupStatusMessage: string | null;
  handleRunAutoClean: () => void;
}

export default function ControlsPanel({
  childLocation,
  isRefreshingLocation,
  setIsRefreshingLocation,
  sendCommand,
  isSirenActive,
  isTorchActive,
  capturingFacing,
  handleRequestSnapshot,
  snapshotsList,
  latestSnapshot,
  setSelectedSnapshotModal,
  storageRetentionDays,
  handleUpdateRetention,
  isCleaningStorage,
  cleanupStatusMessage,
  handleRunAutoClean,
}: ControlsPanelProps) {
  const rearSnap =
    snapshotsList.find((s) => s.cameraFacing !== "front") ||
    (latestSnapshot?.cameraFacing !== "front" ? latestSnapshot : null);
  const frontSnap =
    snapshotsList.find((s) => s.cameraFacing === "front") ||
    (latestSnapshot?.cameraFacing === "front" ? latestSnapshot : null);

  return (
    <div className="space-y-4">
      {/* Live GPS Location Card */}
      <div className="p-4 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <div className="p-2 bg-blue-600/20 text-blue-400 rounded-lg">
              <MapPin className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-xs font-bold text-white">Live GPS Location</h3>
              <p className="text-[10px] text-gray-400">Real-time coordinates of child device</p>
            </div>
          </div>
          <button
            onClick={() => {
              setIsRefreshingLocation(true);
              sendCommand("LOCATION", true);
              setTimeout(() => setIsRefreshingLocation(false), 2000);
            }}
            disabled={isRefreshingLocation}
            className="px-3 py-1.5 bg-[#252530] hover:bg-[#2e2e3d] text-gray-300 text-xs rounded-xl flex items-center gap-1.5 transition"
          >
            <RefreshCw className={`w-3 h-3 ${isRefreshingLocation ? "animate-spin" : ""}`} />
            <span>Refresh GPS</span>
          </button>
        </div>

        {childLocation && childLocation.latitude ? (
          <div className="p-3 bg-[#121217] rounded-xl border border-[#23232c] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs">
            <div>
              <p className="font-mono text-white font-semibold">
                Lat: {Number(childLocation.latitude).toFixed(6)}, Lng: {Number(childLocation.longitude).toFixed(6)}
              </p>
              <p className="text-[10px] text-gray-400 mt-0.5">
                Accuracy: ±{Math.round(childLocation.accuracy || 10)}m • Updated:{" "}
                {childLocation.timestamp ? new Date(childLocation.timestamp).toLocaleTimeString() : "Just now"}
              </p>
            </div>
            <button
              onClick={() =>
                window.open(
                  `https://www.google.com/maps?q=${childLocation.latitude},${childLocation.longitude}`,
                  "_blank"
                )
              }
              className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg flex items-center gap-1.5 font-medium shrink-0 transition"
            >
              <span>Google Maps</span>
              <ExternalLink className="w-3 h-3" />
            </button>
          </div>
        ) : (
          <div className="text-center py-4 text-xs text-gray-500">
            Location coordinates not available yet. Click "Refresh GPS" to request.
          </div>
        )}
      </div>

      {/* Quick Action Grid: Siren & Flashlight */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {/* Siren Toggle */}
        <button
          onClick={() => sendCommand("SIREN", !isSirenActive)}
          className={`p-4 bg-[#1B1B22] border rounded-2xl flex items-center gap-3 text-left transition ${
            isSirenActive
              ? "border-red-500 bg-red-950/20 shadow-lg shadow-red-900/20"
              : "border-[#2B2B36] hover:border-red-500/50"
          }`}
        >
          <div
            className={`p-3 rounded-xl transition ${
              isSirenActive ? "bg-red-600 text-white animate-pulse" : "bg-red-600/20 text-red-400"
            }`}
          >
            <Volume2 className="w-5 h-5" />
          </div>
          <div className="flex-1">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold text-white">Emergency Siren</h3>
              <span
                className={`text-[9px] px-2 py-0.5 rounded-full font-bold uppercase ${
                  isSirenActive ? "bg-red-600 text-white" : "bg-gray-800 text-gray-400"
                }`}
              >
                {isSirenActive ? "ALARM ACTIVE" : "OFF"}
              </span>
            </div>
            <p className="text-[10px] text-gray-400 mt-0.5">
              {isSirenActive ? "Click to stop emergency siren alarm" : "Click to sound loud alert on child phone"}
            </p>
          </div>
        </button>

        {/* Torch Toggle */}
        <button
          onClick={() => sendCommand("TORCH", !isTorchActive)}
          className={`p-4 bg-[#1B1B22] border rounded-2xl flex items-center gap-3 text-left transition ${
            isTorchActive
              ? "border-yellow-500 bg-yellow-950/20 shadow-lg shadow-yellow-900/20"
              : "border-[#2B2B36] hover:border-yellow-500/50"
          }`}
        >
          <div
            className={`p-3 rounded-xl transition ${
              isTorchActive ? "bg-yellow-500 text-black shadow-md shadow-yellow-500/50" : "bg-yellow-600/20 text-yellow-400"
            }`}
          >
            <Flashlight className="w-5 h-5" />
          </div>
          <div className="flex-1">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold text-white">Camera Torch / Flashlight</h3>
              <span
                className={`text-[9px] px-2 py-0.5 rounded-full font-bold uppercase ${
                  isTorchActive ? "bg-yellow-500 text-black" : "bg-gray-800 text-gray-400"
                }`}
              >
                {isTorchActive ? "ON" : "OFF"}
              </span>
            </div>
            <p className="text-[10px] text-gray-400 mt-0.5">
              {isTorchActive ? "Click to turn off camera torch" : "Click to remotely turn on camera flashlight"}
            </p>
          </div>
        </button>

        {/* Camera Snapshots Controls */}
        <button
          onClick={() => handleRequestSnapshot("back")}
          disabled={capturingFacing !== null}
          className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-blue-500/50 disabled:opacity-60 rounded-2xl flex items-center gap-3 text-left transition group cursor-pointer"
        >
          <div className="p-3 bg-blue-600/20 text-blue-400 rounded-xl group-hover:scale-105 transition">
            {capturingFacing === "back" ? (
              <RefreshCw className="w-5 h-5 animate-spin text-blue-400" />
            ) : (
              <Camera className="w-5 h-5" />
            )}
          </div>
          <div>
            <h3 className="text-xs font-bold text-white">Capture Rear Photo</h3>
            <p className="text-[10px] text-gray-400">
              {capturingFacing === "back" ? "Capturing photo from back camera..." : "Takes silent snapshot from back camera"}
            </p>
          </div>
        </button>

        <button
          onClick={() => handleRequestSnapshot("front")}
          disabled={capturingFacing !== null}
          className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-purple-500/50 disabled:opacity-60 rounded-2xl flex items-center gap-3 text-left transition group cursor-pointer"
        >
          <div className="p-3 bg-purple-600/20 text-purple-400 rounded-xl group-hover:scale-105 transition">
            {capturingFacing === "front" ? (
              <RefreshCw className="w-5 h-5 animate-spin text-purple-400" />
            ) : (
              <Camera className="w-5 h-5" />
            )}
          </div>
          <div>
            <h3 className="text-xs font-bold text-white">Capture Front Selfie</h3>
            <p className="text-[10px] text-gray-400">
              {capturingFacing === "front" ? "Capturing selfie from front camera..." : "Takes silent snapshot from front selfie camera"}
            </p>
          </div>
        </button>

        {/* Remote Screen Lock */}
        <button
          onClick={() => {
            if (confirm("Child device ki screen lock karein?")) {
              sendCommand("LOCK", true);
            }
          }}
          className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-red-500/50 rounded-2xl flex items-center gap-3 text-left transition group cursor-pointer"
        >
          <div className="p-3 bg-red-600/20 text-red-400 rounded-xl group-hover:scale-105 transition">
            <Lock className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-xs font-bold text-white">Remote Screen Lock</h3>
            <p className="text-[10px] text-gray-400">
              Immediately locks the child phone screen remotely
            </p>
          </div>
        </button>

        {/* Go Home / Close Active Apps */}
        <button
          onClick={() => {
            sendCommand("CLOSE_APP", "ALL");
          }}
          className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-amber-500/50 rounded-2xl flex items-center gap-3 text-left transition group cursor-pointer"
        >
          <div className="p-3 bg-amber-600/20 text-amber-400 rounded-xl group-hover:scale-105 transition">
            <Home className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-xs font-bold text-white">Go Home (Close Active App)</h3>
            <p className="text-[10px] text-gray-400">
              Closes current foreground apps and returns child to home screen
            </p>
          </div>
        </button>

        {/* Sync Media & Files */}
        <button
          onClick={() => {
            sendCommand("SYNC_MEDIA", true);
            alert("Sync request sent! Photos and files list update ho rahi hai.");
          }}
          className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-emerald-500/50 rounded-2xl flex items-center gap-3 text-left transition group cursor-pointer"
        >
          <div className="p-3 bg-emerald-600/20 text-emerald-400 rounded-xl group-hover:scale-105 transition">
            <RefreshCw className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-xs font-bold text-white">Sync Media & Files</h3>
            <p className="text-[10px] text-gray-400">
              Rescans gallery photos, videos & explorer files
            </p>
          </div>
        </button>
      </div>

      {/* Capturing Feedback Alert */}
      {capturingFacing && (
        <div className="p-3 bg-blue-950/40 border border-blue-800/40 rounded-xl flex items-center gap-2 text-xs text-blue-300">
          <RefreshCw className="w-4 h-4 animate-spin text-blue-400 shrink-0" />
          <span>
            {capturingFacing === "front"
              ? "Front Selfie camera capture request sent. Photo will appear in Front Selfie card below..."
              : "Rear Camera capture request sent. Photo will appear in Rear Photo card below..."}
          </span>
        </div>
      )}

      {/* Dedicated Cards: Rear Camera & Front Camera Photos Separated */}
      {(() => {
        if (!rearSnap && !frontSnap && !latestSnapshot) return null;

        return (
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
                      onClick={() => setSelectedSnapshotModal(rearSnap)}
                    />
                    <div className="p-2.5 bg-[#121217] flex items-center justify-between text-xs">
                      <button
                        type="button"
                        onClick={() => setSelectedSnapshotModal(rearSnap)}
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
                      onClick={() => setSelectedSnapshotModal(frontSnap)}
                    />
                    <div className="p-2.5 bg-[#121217] flex items-center justify-between text-xs">
                      <button
                        type="button"
                        onClick={() => setSelectedSnapshotModal(frontSnap)}
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
        );
      })()}

      {/* Snapshots Gallery (All Past Captured Photos) */}
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
                  onClick={() => setSelectedSnapshotModal(snap)}
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

      {/* Cloud Storage Auto-Clean & Retention Card */}
      <div className="p-5 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl shadow-xl">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-blue-600/20 text-blue-400 rounded-xl">
              <Clock className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <span>Cloud Storage Auto-Delete Retention</span>
                <span className="text-[10px] px-2 py-0.5 bg-emerald-950 text-emerald-400 border border-emerald-800/40 rounded-full font-bold">
                  Free Tier Saver
                </span>
              </h3>
              <p className="text-xs text-gray-400 mt-0.5">
                Automatically delete silent snapshots and call recordings older than your chosen period to keep Firebase Storage 100% Free.
              </p>
            </div>
          </div>

          <button
            onClick={handleRunAutoClean}
            disabled={isCleaningStorage || storageRetentionDays === 0}
            className="px-4 py-2 bg-[#252530] hover:bg-[#2e2e3d] disabled:opacity-50 text-xs font-semibold text-gray-200 rounded-xl flex items-center justify-center gap-2 border border-[#373747] transition cursor-pointer shrink-0"
          >
            {isCleaningStorage ? (
              <RefreshCw className="w-3.5 h-3.5 animate-spin text-blue-400" />
            ) : (
              <Trash2 className="w-3.5 h-3.5 text-red-400" />
            )}
            <span>{isCleaningStorage ? "Cleaning..." : "Clean Storage Now"}</span>
          </button>
        </div>

        {/* Retention Options Pills */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5 pt-1">
          {[
            { days: 1, label: "1 Day", desc: "Purge after 24 hours" },
            { days: 7, label: "7 Days", desc: "Recommended (Weekly)" },
            { days: 14, label: "14 Days", desc: "Bi-weekly retention" },
            { days: 28, label: "28 Days", desc: "Monthly retention" },
            { days: 0, label: "Never", desc: "Keep all media forever" },
          ].map((opt) => {
            const isSelected = storageRetentionDays === opt.days;
            return (
              <button
                key={opt.days}
                onClick={() => handleUpdateRetention(opt.days)}
                className={`p-3 rounded-xl border text-left transition cursor-pointer flex flex-col justify-between ${
                  isSelected
                    ? "bg-blue-600/20 border-blue-500 shadow-md shadow-blue-900/20"
                    : "bg-[#141419] border-[#272733] hover:border-gray-600 text-gray-400"
                }`}
              >
                <div className="flex items-center justify-between mb-1">
                  <span className={`text-xs font-bold ${isSelected ? "text-blue-400" : "text-gray-300"}`}>
                    {opt.label}
                  </span>
                  {isSelected && <CheckCircle className="w-3.5 h-3.5 text-blue-400" />}
                </div>
                <span className="text-[10px] text-gray-500">{opt.desc}</span>
              </button>
            );
          })}
        </div>

        {cleanupStatusMessage && (
          <div className="mt-3 p-2.5 bg-blue-950/40 border border-blue-800/40 rounded-xl text-xs text-blue-300 flex items-center gap-2">
            <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>{cleanupStatusMessage}</span>
          </div>
        )}
      </div>
    </div>
  );
}
