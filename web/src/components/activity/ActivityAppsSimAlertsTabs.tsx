"use client";

import React, { useState } from "react";
import {
  Smartphone,
  RefreshCw,
  Search,
  Lock,
  Unlock,
  Trash2,
  Play,
  Square,
  Home,
  AlertTriangle,
} from "lucide-react";

export function AppsTab({
  installedApps = [],
  packageEvents = [],
  blockedPackages = {},
  sendCommand,
  onToggleBlockApp,
  onUninstallApp,
}: {
  installedApps?: any[];
  packageEvents?: any[];
  blockedPackages?: Record<string, boolean>;
  sendCommand: (cmd: string, val: any) => void;
  onToggleBlockApp?: (pkg: string, currentBlocked: boolean) => void;
  onUninstallApp?: (pkg: string) => void;
}) {
  const [appSearch, setAppSearch] = useState("");

  const effectiveApps = installedApps.length > 0 ? installedApps : packageEvents;
  const filteredApps = effectiveApps.filter((app) => {
    const query = appSearch.toLowerCase().trim();
    if (!query) return true;
    return (
      (app.appName || "").toLowerCase().includes(query) ||
      (app.packageName || "").toLowerCase().includes(query)
    );
  });

  return (
    <div className="space-y-3 mt-2">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
        <div>
          <p className="text-xs text-gray-300 font-semibold">Installed Applications & Remote App Control</p>
          <p className="text-[11px] text-gray-400">
            Child ke phone ke apps remotely open karein (▶), close karein (⏹), block ya uninstall karein
          </p>
        </div>
        <div className="flex items-center gap-2 self-start sm:self-auto shrink-0">
          <button
            onClick={() => sendCommand("CLOSE_APP", "ALL")}
            className="px-2.5 py-1.5 bg-amber-900/30 hover:bg-amber-800/40 text-amber-300 border border-amber-700/50 text-[11px] font-semibold rounded-lg flex items-center gap-1.5 transition"
            title="Closes all currently open apps on child phone and returns to Home screen"
          >
            <Home className="w-3.5 h-3.5" />
            <span>Go Home (Close Active)</span>
          </button>
          <button
            onClick={() => sendCommand("SYNC_MEDIA", true)}
            className="px-2.5 py-1.5 bg-[#252530] hover:bg-[#2e2e3d] text-gray-300 text-[11px] rounded-lg flex items-center gap-1.5 transition shrink-0"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Rescan Apps</span>
          </button>
        </div>
      </div>

      <div className="relative">
        <Search className="w-4 h-4 text-gray-500 absolute left-3 top-2.5" />
        <input
          type="text"
          value={appSearch}
          onChange={(e) => setAppSearch(e.target.value)}
          placeholder="Search apps by name or package..."
          className="w-full pl-9 pr-3 py-2 bg-[#14141B] border border-[#272733] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 transition"
        />
      </div>

      {filteredApps.length === 0 ? (
        <div className="text-center py-12 text-xs text-gray-500 bg-[#16161D] rounded-xl border border-[#272733]">
          <Smartphone className="w-8 h-8 text-gray-600 mx-auto mb-2" />
          <p>No installed apps found matching your search.</p>
          <p className="text-[11px] text-gray-600 mt-1">Click "Rescan Apps" to index phone apps.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
          {filteredApps.map((app, idx) => {
            const sanitized = (app.packageName || "").replace(/\./g, "_");
            const isBlocked = blockedPackages[app.packageName] === true || blockedPackages[sanitized] === true;
            const initial = (app.appName || app.packageName || "A").charAt(0).toUpperCase();

            return (
              <div
                key={idx}
                className={`p-3 rounded-xl border flex items-center justify-between gap-3 text-xs transition ${
                  isBlocked
                    ? "bg-red-950/20 border-red-800/40"
                    : "bg-[#1B1B22] border-[#272733] hover:border-gray-700"
                }`}
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div
                    className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold text-sm shrink-0 shadow-md ${
                      isBlocked
                        ? "bg-red-900/40 text-red-300 border border-red-700/50"
                        : "bg-gradient-to-br from-indigo-600 to-blue-700 text-white border border-blue-500/30"
                    }`}
                  >
                    {isBlocked ? <Lock className="w-5 h-5 text-red-400" /> : initial}
                  </div>

                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5">
                      <p className="font-bold text-white truncate text-xs" title={app.appName || app.packageName}>
                        {app.appName || app.packageName}
                      </p>
                      {isBlocked && (
                        <span className="text-[9px] px-1.5 py-0.2 bg-red-950 text-red-300 border border-red-800/50 rounded-full font-bold">
                          BLOCKED
                        </span>
                      )}
                    </div>
                    <p className="text-[10px] text-gray-500 font-mono truncate" title={app.packageName}>
                      {app.packageName}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-1.5 shrink-0">
                  <button
                    onClick={() => sendCommand("OPEN_APP", app.packageName)}
                    className="px-2 py-1.5 bg-blue-600/20 hover:bg-blue-600/40 text-blue-300 border border-blue-500/40 rounded-lg text-xs font-semibold flex items-center gap-1 transition"
                    title="Remotely launch/open this app on child's phone"
                  >
                    <Play className="w-3.5 h-3.5 fill-blue-300" /><span className="hidden sm:inline">Open</span>
                  </button>
                  <button
                    onClick={() => sendCommand("CLOSE_APP", app.packageName)}
                    className="px-2 py-1.5 bg-amber-600/20 hover:bg-amber-600/40 text-amber-300 border border-amber-500/40 rounded-lg text-xs font-semibold flex items-center gap-1 transition"
                    title="Remotely kill/close this app"
                  >
                    <Square className="w-3.5 h-3.5 fill-amber-300" /><span className="hidden sm:inline">Close</span>
                  </button>
                  <button
                    onClick={() => onToggleBlockApp?.(app.packageName, isBlocked)}
                    className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1 transition ${
                      isBlocked ? "bg-emerald-600/20 text-emerald-400 border border-emerald-500/40 hover:bg-emerald-600/30" : "bg-red-600/20 text-red-400 border border-red-500/40 hover:bg-red-600/30"
                    }`}
                    title={isBlocked ? "Allow this app" : "Block child from using this app"}
                  >
                    {isBlocked ? (<><Unlock className="w-3.5 h-3.5" /><span className="hidden sm:inline">Unblock</span></>) : (<><Lock className="w-3.5 h-3.5" /><span className="hidden sm:inline">Block</span></>)}
                  </button>
                  <button
                    onClick={() => onUninstallApp?.(app.packageName)}
                    className="p-1.5 bg-[#252530] hover:bg-red-900/30 text-gray-400 hover:text-red-400 border border-transparent hover:border-red-800/50 rounded-lg transition"
                    title="Send Remote Uninstall Request to phone"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export function SimInfoTab({ simInfo }: { simInfo: any }) {
  return (
    <div className="p-4 bg-[#1B1B22] border border-[#272733] rounded-xl text-xs space-y-3">
      {simInfo && (simInfo.simState === "ABSENT" || simInfo.operatorName === "No SIM Card") && (
        <div className="p-3 bg-red-950/60 border border-red-600/80 rounded-xl flex items-center gap-3 text-red-200">
          <AlertTriangle className="w-6 h-6 text-red-400 shrink-0 animate-pulse" />
          <div>
            <p className="font-bold text-red-300 text-sm">CRITICAL ALERT: SIM Card Removed / Absent!</p>
            <p className="text-xs text-red-300/80">
              Child ke phone se SIM card nikal diya gaya hai ya koi SIM card detect nahi ho raha.
            </p>
          </div>
        </div>
      )}

      {!simInfo ? (
        <p className="text-center py-8 text-gray-500">No SIM details received yet.</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div className="p-3 bg-[#121217] rounded-lg border border-[#23232c]">
            <p className="text-[10px] text-gray-400">Network Operator</p>
            <p className="text-sm font-bold text-white mt-0.5">{simInfo.operatorName || "N/A"}</p>
          </div>
          <div className="p-3 bg-[#121217] rounded-lg border border-[#23232c]">
            <p className="text-[10px] text-gray-400">Country ISO</p>
            <p className="text-sm font-bold text-white uppercase mt-0.5">{simInfo.countryIso || "N/A"}</p>
          </div>
          <div className="p-3 bg-[#121217] rounded-lg border border-[#23232c]">
            <p className="text-[10px] text-gray-400">SIM State</p>
            <p className={`text-sm font-bold mt-0.5 ${simInfo.simState === "ABSENT" ? "text-red-400" : "text-green-400"}`}>{simInfo.simState || "Active"}</p>
          </div>
          <div className="p-3 bg-[#121217] rounded-lg border border-[#23232c]">
            <p className="text-[10px] text-gray-400">Last Synced</p>
            <p className="text-xs text-gray-300 mt-0.5">{simInfo.lastUpdated ? new Date(simInfo.lastUpdated).toLocaleString() : "Just now"}</p>
          </div>
        </div>
      )}
    </div>
  );
}

export function AlertsTab({ alerts = [] }: { alerts?: any[] }) {
  return (
    <div className="space-y-3 mt-2">
      <div className="flex items-center justify-between p-3 bg-[#16161E] rounded-xl border border-[#272733]">
        <div>
          <p className="text-xs font-semibold text-white">Live Security Alerts & Intruder Events</p>
          <p className="text-[11px] text-gray-400">
            SIM card change/removal, low battery, aur device security alerts yahan live aayenge
          </p>
        </div>
        <span className="text-[10px] px-2 py-0.5 bg-red-950/60 text-red-300 border border-red-800/40 rounded-full font-bold">
          {alerts.length} Alerts
        </span>
      </div>

      {alerts.length === 0 ? (
        <div className="text-center py-12 text-xs text-gray-500 bg-[#16161D] rounded-xl border border-[#272733]">
          <AlertTriangle className="w-8 h-8 text-gray-600 mx-auto mb-2" />
          <p>No security alerts recorded yet.</p>
          <p className="text-[11px] text-gray-600 mt-1">SIM change ya unusual security activity par yahan turant alert dikhega.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {alerts.map((alert: any, idx: number) => {
            const isHigh = alert.severity === "HIGH" || alert.type === "SIM_CHANGED" || alert.type === "SIM_REMOVED";
            return (
              <div
                key={idx}
                className={`p-3.5 rounded-xl border flex items-start justify-between gap-3 text-xs transition ${
                  isHigh ? "bg-red-950/30 border-red-800/50" : "bg-[#1B1B22] border-[#272733]"
                }`}
              >
                <div className="flex items-start gap-3">
                  <div
                    className={`p-2 rounded-xl shrink-0 mt-0.5 ${
                      isHigh
                        ? "bg-red-900/40 text-red-300 border border-red-700/50"
                        : "bg-amber-900/40 text-amber-300 border border-amber-700/50"
                    }`}
                  >
                    <AlertTriangle className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="font-bold text-white text-xs">{alert.title || alert.type}</p>
                      <span
                        className={`text-[9px] px-1.5 py-0.2 rounded font-bold uppercase ${
                          isHigh
                            ? "bg-red-900/60 text-red-200 border border-red-700/50"
                            : "bg-amber-900/60 text-amber-200 border border-amber-700/50"
                        }`}
                      >
                        {alert.type || "ALERT"}
                      </span>
                    </div>
                    <p className="text-gray-300 text-xs mt-1">{alert.message}</p>
                    {alert.extra && (
                      <div className="mt-1.5 p-1.5 bg-[#121217] rounded text-[10px] text-gray-400 font-mono">
                        {JSON.stringify(alert.extra)}
                      </div>
                    )}
                  </div>
                </div>
                <span className="text-[10px] text-gray-500 shrink-0 whitespace-nowrap">
                  {alert.timestamp ? new Date(alert.timestamp).toLocaleTimeString() : ""}
                </span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
