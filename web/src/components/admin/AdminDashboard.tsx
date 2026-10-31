import React, { useState, useEffect } from "react";
import { rtdb } from "@/lib/firebase";
import { ref, onValue, set, update, push } from "firebase/database";
import { SystemUser } from "@/types/dashboard";
import { AdminOverviewCards } from "./AdminOverviewCards";
import { AdminUsersTable } from "./AdminUsersTable";
import { AdminBroadcastModal } from "./AdminBroadcastModal";
import { ArrowLeft, RefreshCw } from "lucide-react";

interface AdminDashboardProps {
  onBackToParentView?: () => void;
}

export function AdminDashboard({ onBackToParentView }: AdminDashboardProps) {
  const [users, setUsers] = useState<SystemUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [showBroadcastModal, setShowBroadcastModal] = useState(false);

  useEffect(() => {
    const usersRef = ref(rtdb, "users");
    const unsub = onValue(usersRef, (snapshot) => {
      const data = snapshot.val();
      if (data) {
        const list: SystemUser[] = Object.entries(data).map(([uid, val]: [string, any]) => ({
          uid,
          email: val.email || "",
          name: val.name || val.displayName || "",
          role: (val.role || "child") as any,
          parentId: val.parentId || "",
          isOnline: !!(val.isOnline || val.online),
          batteryLevel: typeof val.batteryLevel === "number" ? val.batteryLevel : undefined,
          lastSeen: val.lastSeen || 0,
          createdAt: val.createdAt || 0,
          isBlocked: !!val.isBlocked,
        }));
        setUsers(list);
      } else {
        setUsers([]);
      }
      setLoading(false);
    });

    return () => unsub();
  }, []);

  const handleToggleBlock = async (uid: string, currentStatus: boolean) => {
    try {
      await update(ref(rtdb, `users/${uid}`), { isBlocked: !currentStatus });
    } catch (e) {
      console.error("Failed to update user block status:", e);
    }
  };

  const handleUpdateRole = async (uid: string, newRole: "parent" | "child" | "admin") => {
    try {
      await update(ref(rtdb, `users/${uid}`), { role: newRole });
      if (newRole === "admin") {
        await set(ref(rtdb, `admins/${uid}`), true);
      } else {
        await set(ref(rtdb, `admins/${uid}`), null);
      }
    } catch (e) {
      console.error("Failed to update user role:", e);
    }
  };

  const handleSendBroadcast = async (title: string, message: string, targetRole: "all" | "parent" | "child") => {
    const broadcastRef = push(ref(rtdb, "admin_broadcasts"));
    await set(broadcastRef, {
      title,
      message,
      targetRole,
      timestamp: Date.now(),
      sentBy: "Super Admin",
    });
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-gray-400 gap-2">
        <RefreshCw className="w-6 h-6 animate-spin text-amber-500" />
        <p className="text-xs">Loading Admin Control Directory...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-7xl mx-auto px-4 py-4 animate-fade-in">
      <div className="flex items-center justify-between bg-amber-500/10 border border-amber-500/20 p-3 rounded-2xl">
        <div className="flex items-center gap-2">
          <span className="px-2 py-0.5 bg-amber-500 text-black font-extrabold text-[10px] rounded-full uppercase tracking-wider">
            Super Admin Active
          </span>
          <span className="text-xs text-amber-200">You are in System Administration Master Control Mode.</span>
        </div>
      </div>

      <AdminOverviewCards users={users} onOpenBroadcast={() => setShowBroadcastModal(true)} />

      <AdminUsersTable
        users={users}
        onToggleBlock={handleToggleBlock}
        onUpdateRole={handleUpdateRole}
      />

      <AdminBroadcastModal
        isOpen={showBroadcastModal}
        onClose={() => setShowBroadcastModal(false)}
        onSend={handleSendBroadcast}
      />
    </div>
  );
}
