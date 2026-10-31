"use client";

import React from "react";
import { AppProvider, useApp } from "@/context/AppContext";
import { AuthScreen } from "@/components/auth/AuthScreen";
import { AdminDashboard } from "@/components/admin/AdminDashboard";
import { ParentDashboard } from "@/components/dashboard/ParentDashboard";

function AppShell() {
  const { user, loading, userRole, handleLogout } = useApp();

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-[#121216]">
        <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (!user) return <AuthScreen canInstallPwa={false} onInstallPwa={() => {}} />;

  return <ParentDashboard />;
}

export default function RootPage() {
  return (
    <AppProvider>
      <AppShell />
    </AppProvider>
  );
}
