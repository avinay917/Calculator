import React from "react";
import { User as FirebaseUser } from "firebase/auth";
import { Shield, Download, LogOut, AlertTriangle, Plus, Battery, Wifi, Trash2 } from "lucide-react";
import { ChildUser, MainTabType } from "@/types/dashboard";

interface DashboardHeaderProps {
  user: FirebaseUser;
  canInstallPwa: boolean;
  onInstallPwa: () => void;
  onLogout: () => void;
  childrenList: ChildUser[];
  activeChildId: string;
  setActiveChildId: (id: string) => void;
  setActiveTab: (tab: MainTabType) => void;
  onGeneratePairingCode: () => void;
  onUnpairChild: (childUid: string) => void;
  isAdmin?: boolean;
  onSwitchToAdmin?: () => void;
}

export function DashboardHeader({
  user,
  canInstallPwa,
  onInstallPwa,
  onLogout,
  childrenList,
  activeChildId,
  setActiveChildId,
  setActiveTab,
  onGeneratePairingCode,
  onUnpairChild,
  isAdmin,
  onSwitchToAdmin,
}: DashboardHeaderProps) {
  const activeChild = childrenList.find((c) => c.uid === activeChildId);

  return (
    <>
      <header className="sticky top-0 z-30 flex items-center justify-between px-4 py-3 bg-[#1B1B22]/90 backdrop-blur-md border-b border-[#2D2D38]">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-blue-600/20 text-blue-400 rounded-lg">
            <Shield className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-sm font-bold text-white tracking-wide flex items-center gap-1.5">
              Parent Portal
              {isAdmin && (
                <span className="text-[10px] px-1.5 py-0.2 bg-amber-500/20 text-amber-300 border border-amber-500/40 rounded-full font-extrabold">
                  ADMIN
                </span>
              )}
            </h1>
            <p className="text-[10px] text-gray-400 truncate max-w-[150px] sm:max-w-xs">{user.email}</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {isAdmin && onSwitchToAdmin && (
            <button
              onClick={onSwitchToAdmin}
              className="flex items-center gap-1 px-2.5 py-1.5 bg-gradient-to-r from-amber-500/20 to-amber-600/20 hover:from-amber-500/30 hover:to-amber-600/30 text-amber-300 text-xs font-bold rounded-lg border border-amber-500/40 transition shadow-sm"
            >
              <span>👑 Admin Panel</span>
            </button>
          )}

          {canInstallPwa && (
            <button
              onClick={onInstallPwa}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600/20 hover:bg-blue-600/30 text-blue-400 text-xs font-semibold rounded-lg transition"
            >
              <Download className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Install</span>
            </button>
          )}

          <button
            onClick={onLogout}
            title="Sign Out"
            className="p-2 bg-[#25252E] hover:bg-[#2F2F3B] text-gray-300 rounded-lg transition"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* Child Device Selector Bar */}
      <div className="px-4 py-3 bg-[#17171E] border-b border-[#252530] flex items-center justify-between overflow-x-auto gap-3">
        <div className="flex items-center gap-2">
          {childrenList.length === 0 ? (
            <span className="text-xs text-yellow-400 flex items-center gap-1.5">
              <AlertTriangle className="w-4 h-4" /> No children linked yet
            </span>
          ) : (
            childrenList.map((child) => (
              <button
                key={child.uid}
                onClick={() => setActiveChildId(child.uid)}
                className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-medium transition ${
                  activeChildId === child.uid
                    ? "bg-blue-600 text-white shadow-md shadow-blue-600/20"
                    : "bg-[#22222B] text-gray-300 hover:bg-[#2A2A36]"
                }`}
              >
                <span
                  className={`w-2 h-2 rounded-full ${
                    child.isOnline ? "bg-green-400 animate-pulse" : "bg-gray-500"
                  }`}
                />
                <span className="truncate max-w-[110px]">{child.email || "Child Device"}</span>
              </button>
            ))
          )}
        </div>

        <button
          onClick={() => {
            setActiveTab("pairing");
            onGeneratePairingCode();
          }}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-[#252530] hover:bg-[#30303E] text-xs font-semibold text-blue-400 rounded-xl transition shrink-0"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>Add Child</span>
        </button>
      </div>

      {/* Active Device Status Ribbon */}
      {activeChild && (
        <div className="px-4 py-2 bg-[#14141A] border-b border-[#202028] flex items-center justify-between text-[11px] text-gray-400">
          <div className="flex items-center gap-4">
            <span className="flex items-center gap-1">
              <span className={`w-2 h-2 rounded-full ${activeChild.isOnline ? "bg-green-400" : "bg-gray-500"}`} />
              {activeChild.isOnline ? "Online" : "Offline"}
            </span>

            {activeChild.batteryLevel !== undefined && (
              <span className="flex items-center gap-1">
                <Battery className="w-3.5 h-3.5 text-gray-300" />
                {activeChild.batteryLevel}% {activeChild.isCharging ? "⚡" : ""}
              </span>
            )}

            {activeChild.networkType && (
              <span className="flex items-center gap-1">
                <Wifi className="w-3.5 h-3.5 text-gray-300" />
                {activeChild.networkType}
              </span>
            )}
          </div>

          <div className="flex items-center gap-2">
            <span className="truncate max-w-[140px] text-gray-500 hidden sm:inline">
              UID: {activeChild.uid.slice(0, 8)}...
            </span>
            <button
              onClick={() => onUnpairChild(activeChild.uid)}
              className="flex items-center gap-1 px-2 py-1 text-[11px] font-medium bg-red-950/40 hover:bg-red-900/60 text-red-400 border border-red-800/40 rounded-lg transition"
              title="Unpair Device"
            >
              <Trash2 className="w-3 h-3" />
              <span>Unpair</span>
            </button>
          </div>
        </div>
      )}
    </>
  );
}
