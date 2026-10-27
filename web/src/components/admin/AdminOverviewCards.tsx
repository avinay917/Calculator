import React from "react";
import { Users, Smartphone, ShieldCheck, Activity, Server, Radio } from "lucide-react";
import { SystemUser } from "@/types/dashboard";

interface AdminOverviewProps {
  users: SystemUser[];
  onOpenBroadcast: () => void;
}

export function AdminOverviewCards({ users, onOpenBroadcast }: AdminOverviewProps) {
  const totalUsers = users.length;
  const parentsCount = users.filter((u) => u.role === "parent" || u.role === "admin").length;
  const childrenCount = users.filter((u) => u.role === "child").length;
  const onlineCount = users.filter((u) => u.isOnline).length;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-amber-400" />
            System Administration
          </h2>
          <p className="text-xs text-gray-400">Manage all registered accounts, server health & broadcasts</p>
        </div>
        <button
          onClick={onOpenBroadcast}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-600 hover:to-amber-700 text-white rounded-lg text-xs font-semibold shadow-md transition-all"
        >
          <Radio className="w-4 h-4 animate-pulse" />
          Send Global Alert
        </button>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-[#181820] border border-[#262633] rounded-xl p-3.5 flex items-center justify-between">
          <div>
            <p className="text-[11px] font-medium text-gray-400">Total Accounts</p>
            <p className="text-xl font-bold text-white mt-0.5">{totalUsers}</p>
          </div>
          <div className="w-9 h-9 rounded-lg bg-blue-500/10 border border-blue-500/20 flex items-center justify-center">
            <Users className="w-4 h-4 text-blue-400" />
          </div>
        </div>

        <div className="bg-[#181820] border border-[#262633] rounded-xl p-3.5 flex items-center justify-between">
          <div>
            <p className="text-[11px] font-medium text-gray-400">Parents Registered</p>
            <p className="text-xl font-bold text-purple-400 mt-0.5">{parentsCount}</p>
          </div>
          <div className="w-9 h-9 rounded-lg bg-purple-500/10 border border-purple-500/20 flex items-center justify-center">
            <Users className="w-4 h-4 text-purple-400" />
          </div>
        </div>

        <div className="bg-[#181820] border border-[#262633] rounded-xl p-3.5 flex items-center justify-between">
          <div>
            <p className="text-[11px] font-medium text-gray-400">Child Devices</p>
            <p className="text-xl font-bold text-cyan-400 mt-0.5">{childrenCount}</p>
          </div>
          <div className="w-9 h-9 rounded-lg bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center">
            <Smartphone className="w-4 h-4 text-cyan-400" />
          </div>
        </div>

        <div className="bg-[#181820] border border-[#262633] rounded-xl p-3.5 flex items-center justify-between">
          <div>
            <p className="text-[11px] font-medium text-gray-400">Active Online</p>
            <p className="text-xl font-bold text-emerald-400 mt-0.5">{onlineCount}</p>
          </div>
          <div className="w-9 h-9 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center">
            <Activity className="w-4 h-4 text-emerald-400" />
          </div>
        </div>
      </div>

      <div className="bg-[#141419] border border-[#22222b] rounded-xl p-3 flex items-center justify-between text-xs">
        <div className="flex items-center gap-2 text-gray-300">
          <Server className="w-4 h-4 text-blue-400" />
          <span>Coturn TURN/STUN Server:</span>
          <span className="font-mono text-gray-100 bg-[#1e1e28] px-2 py-0.5 rounded border border-[#2b2b3a]">
            34.46.137.244:3478
          </span>
        </div>
        <span className="flex items-center gap-1.5 text-emerald-400 font-semibold text-[11px]">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
          Online & Ready
        </span>
      </div>
    </div>
  );
}
