"use client";

import React from "react";
import { Camera, Lock, RefreshCw, Home, Smartphone } from "lucide-react";
import { LucideIcon } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { CommandButton, AlertBanner } from "@/components/ui/SharedComponents";

interface ActionConfig {
  icon: LucideIcon;
  color: string;
  label: string;
  description: string;
  onClick: (ctx: ReturnType<typeof useApp>["commands"]) => void;
  disabled?: (ctx: ReturnType<typeof useApp>["commands"]) => boolean;
  isLoading?: (ctx: ReturnType<typeof useApp>["commands"]) => boolean;
}

const ACTIONS: ActionConfig[] = [
  { icon: Camera, color: "blue", label: "Capture Rear Photo",
    description: "Takes silent snapshot from back camera",
    onClick: (c) => c.handleRequestSnapshot("back"),
    disabled: (c) => c.capturingFacing !== null,
    isLoading: (c) => c.capturingFacing === "back" },
  { icon: Camera, color: "purple", label: "Capture Front Selfie",
    description: "Takes silent snapshot from front selfie camera",
    onClick: (c) => c.handleRequestSnapshot("front"),
    disabled: (c) => c.capturingFacing !== null,
    isLoading: (c) => c.capturingFacing === "front" },
  { icon: Lock, color: "red", label: "Remote Screen Lock",
    description: "Immediately locks the child phone screen remotely",
    onClick: (c) => { if (confirm("Child device ki screen lock karein?")) c.sendCommand("LOCK", true); } },
  { icon: Home, color: "amber", label: "Go Home (Close Active App)",
    description: "Closes current foreground apps and returns child to home screen",
    onClick: (c) => c.sendCommand("CLOSE_APP", "ALL") },
  { icon: RefreshCw, color: "emerald", label: "Sync Media & Files",
    description: "Rescans gallery photos, videos & explorer files",
    onClick: (c) => { c.sendCommand("SYNC_MEDIA", true); alert("Sync request sent! Photos and files list update ho rahi hai."); } },
  { icon: Smartphone, color: "cyan", label: "Vibrate Phone",
    description: "Silently vibrates child device for 3 seconds",
    onClick: (c) => { c.sendCommand("VIBRATE", 3); alert("Vibration signal sent to child phone!"); } },
];

export default function ControlsActionButtons() {
  const { commands } = useApp();

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {ACTIONS.map((action) => (
          <CommandButton
            key={action.label}
            icon={action.icon}
            color={action.color}
            label={action.label}
            description={action.description}
            onClick={() => action.onClick(commands)}
            disabled={action.disabled?.(commands) ?? false}
            isLoading={action.isLoading?.(commands) ?? false}
            variant="full"
          />
        ))}
      </div>
      <AlertBanner
        type="loading"
        message={commands.capturingFacing
          ? (commands.capturingFacing === "front"
            ? "Front Selfie camera capture request sent. Photo will appear in Front Selfie card below..."
            : "Rear Camera capture request sent. Photo will appear in Rear Photo card below...")
          : null}
      />
    </div>
  );
}
