import React from "react";
import { SystemUser } from "@/types/dashboard";
import { Shield, Smartphone, UserCheck, Battery, Circle, Ban, CheckCircle2 } from "lucide-react";

interface AdminUsersTableProps {
  users: SystemUser[];
  onToggleBlock: (uid: string, currentStatus: boolean) => void;
  onUpdateRole: (uid: string, newRole: "parent" | "child" | "admin") => void;
}

export function AdminUsersTable({ users, onToggleBlock, onUpdateRole }: AdminUsersTableProps) {
  return (
    <div className="bg-[#181820] border border-[#262633] rounded-xl overflow-hidden">
      <div className="p-3.5 border-b border-[#262633] flex items-center justify-between">
        <h3 className="text-sm font-bold text-white flex items-center gap-2">
          <UserCheck className="w-4 h-4 text-blue-400" />
          Registered Users & Devices ({users.length})
        </h3>
        <span className="text-[11px] text-gray-400">Real-time Directory</span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead className="bg-[#131318] text-gray-400 border-b border-[#23232f] uppercase text-[10px]">
            <tr>
              <th className="px-4 py-3 font-semibold">User / Device</th>
              <th className="px-3 py-3 font-semibold">Role</th>
              <th className="px-3 py-3 font-semibold">Status</th>
              <th className="px-3 py-3 font-semibold">Battery</th>
              <th className="px-3 py-3 font-semibold">Last Seen</th>
              <th className="px-3 py-3 font-semibold text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#22222d]">
            {users.map((u) => {
              const isOnline = !!u.isOnline;
              const isBlocked = !!u.isBlocked;
              const lastSeenText = u.lastSeen ? new Date(u.lastSeen).toLocaleTimeString() : "Never";

              return (
                <tr key={u.uid} className="hover:bg-[#1f1f2a]/50 transition-colors">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2.5">
                      <div className="w-7 h-7 rounded-full bg-[#252533] flex items-center justify-center text-gray-300 font-bold text-xs">
                        {u.name ? u.name[0].toUpperCase() : u.email ? u.email[0].toUpperCase() : "U"}
                      </div>
                      <div>
                        <p className="font-semibold text-gray-100">{u.name || u.email || "Device " + u.uid.slice(0, 6)}</p>
                        <p className="text-[10px] text-gray-500 font-mono">{u.email || u.uid}</p>
                      </div>
                    </div>
                  </td>

                  <td className="px-3 py-3">
                    <select
                      value={u.role || "child"}
                      onChange={(e) => onUpdateRole(u.uid, e.target.value as any)}
                      className={`text-[10px] font-bold px-2 py-0.5 rounded border outline-none bg-transparent ${
                        u.role === "admin"
                          ? "border-amber-500/40 text-amber-300 bg-amber-500/10"
                          : u.role === "parent"
                          ? "border-purple-500/40 text-purple-300 bg-purple-500/10"
                          : "border-cyan-500/40 text-cyan-300 bg-cyan-500/10"
                      }`}
                    >
                      <option value="parent" className="bg-[#181820] text-gray-200">PARENT</option>
                      <option value="child" className="bg-[#181820] text-gray-200">CHILD</option>
                      <option value="admin" className="bg-[#181820] text-amber-400">ADMIN</option>
                    </select>
                  </td>

                  <td className="px-3 py-3">
                    <span
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium ${
                        isOnline
                          ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                          : "bg-gray-800 text-gray-400 border border-gray-700"
                      }`}
                    >
                      <Circle className={`w-1.5 h-1.5 fill-current ${isOnline ? "animate-pulse" : ""}`} />
                      {isOnline ? "Online" : "Offline"}
                    </span>
                  </td>

                  <td className="px-3 py-3 text-gray-300">
                    {typeof u.batteryLevel === "number" ? (
                      <span className="flex items-center gap-1 font-mono text-[11px]">
                        <Battery className="w-3.5 h-3.5 text-green-400" />
                        {u.batteryLevel}%
                      </span>
                    ) : (
                      <span className="text-gray-500">—</span>
                    )}
                  </td>

                  <td className="px-3 py-3 text-gray-400 text-[11px] font-mono">{lastSeenText}</td>

                  <td className="px-3 py-3 text-right">
                    <button
                      onClick={() => onToggleBlock(u.uid, isBlocked)}
                      className={`px-2.5 py-1 rounded text-[11px] font-semibold transition-all inline-flex items-center gap-1 ${
                        isBlocked
                          ? "bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border border-emerald-500/30"
                          : "bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 border border-rose-500/30"
                      }`}
                    >
                      {isBlocked ? (
                        <>
                          <CheckCircle2 className="w-3 h-3" />
                          Unblock
                        </>
                      ) : (
                        <>
                          <Ban className="w-3 h-3" />
                          Block
                        </>
                      )}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
