"use client";

import React from "react";
import { MapPin, RefreshCw, ExternalLink } from "lucide-react";
import ControlsActionButtons from "@/components/controls/ControlsActionButtons";
import ControlsSnapshotsView from "@/components/controls/ControlsSnapshotsView";
import { useApp } from "@/context/AppContext";

export default function ControlsPanel() {
  const { childData, commands, isRefreshingLocation, setIsRefreshingLocation, setSelectedSnapshotModal } = useApp();
  const { childLocation, snapshotsList, latestSnapshot, isTorchActive, isSirenActive } = childData;

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
              commands.sendCommand("LOCATION", true);
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

      <ControlsActionButtons />
      <ControlsSnapshotsView
        rearSnap={rearSnap}
        frontSnap={frontSnap}
        snapshotsList={snapshotsList}
        onSelectSnapshot={setSelectedSnapshotModal}
      />
    </div>
  );
}
