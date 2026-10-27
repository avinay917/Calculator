import React from "react";
import { Video, MessageSquare, Shield, Clock, Smartphone, Settings } from "lucide-react";
import { MainTabType } from "@/types/dashboard";

interface BottomNavBarProps {
  activeTab: MainTabType;
  setActiveTab: (tab: MainTabType) => void;
}

export function BottomNavBar({ activeTab, setActiveTab }: BottomNavBarProps) {
  const tabs = [
    { id: "live" as MainTabType, label: "Live Cast", icon: Video },
    { id: "activity" as MainTabType, label: "Activities", icon: MessageSquare },
    { id: "controls" as MainTabType, label: "Controls", icon: Shield },
    { id: "schedules" as MainTabType, label: "Schedules", icon: Clock },
    { id: "pairing" as MainTabType, label: "Pair Device", icon: Smartphone },
    { id: "settings" as MainTabType, label: "Settings", icon: Settings },
  ];

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-30 max-w-5xl mx-auto bg-[#17171E]/95 backdrop-blur-md border-t border-[#262632] flex items-center justify-around py-2 px-3">
      {tabs.map((tab) => {
        const IconComp = tab.icon;
        const isActive = activeTab === tab.id;
        return (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition cursor-pointer ${
              isActive ? "text-blue-400 font-bold" : "text-gray-400 hover:text-gray-200"
            }`}
          >
            <IconComp className="w-5 h-5" />
            <span className="text-[10px]">{tab.label}</span>
          </button>
        );
      })}
    </nav>
  );
}
