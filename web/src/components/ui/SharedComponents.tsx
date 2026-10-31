/**
 * Reusable UI Building Blocks — eliminates 200+ lines of copy-paste across
 * SettingsTabContent, ControlsActionButtons, ControlsPanel, etc.
 *
 * USAGE:
 *   <SettingsCard icon={Lock} color="amber" title="..." subtitle="...">
 *     {children}
 *   </SettingsCard>
 *
 *   <CommandButton icon={Camera} color="blue" label="Photo" onClick={fn} />
 *
 *   <AlertBanner type="success" message="Done!" />
 */
import React, { ReactNode } from "react";
import { LucideIcon, CheckCircle, AlertTriangle, RefreshCw } from "lucide-react";

// ---------------------------------------------------------------------------
// 1. SettingsCard — repeated 8+ times across Settings/Controls
// ---------------------------------------------------------------------------
interface SettingsCardProps {
  icon: LucideIcon;
  color: string; // tailwind color name: "amber" | "blue" | "purple" | "red" | "emerald" | "cyan"
  title: string;
  subtitle?: string;
  badge?: string;
  badgeColor?: string;
  headerRight?: ReactNode;
  children: ReactNode;
}

const colorMap: Record<string, { bg: string; text: string; border: string }> = {
  amber:   { bg: "bg-amber-500/20",   text: "text-amber-400",   border: "border-amber-500/30" },
  blue:    { bg: "bg-blue-600/20",    text: "text-blue-400",    border: "border-blue-500/30" },
  purple:  { bg: "bg-purple-600/20",  text: "text-purple-400",  border: "border-purple-500/30" },
  red:     { bg: "bg-red-600/20",     text: "text-red-400",     border: "border-red-500/30" },
  emerald: { bg: "bg-emerald-600/20", text: "text-emerald-400", border: "border-emerald-500/30" },
  cyan:    { bg: "bg-cyan-600/20",    text: "text-cyan-400",    border: "border-cyan-500/30" },
};

export function SettingsCard({ icon: Icon, color, title, subtitle, badge, badgeColor, headerRight, children }: SettingsCardProps) {
  const c = colorMap[color] || colorMap.blue;
  return (
    <div className="p-4 bg-[#14141A] border border-[#262632] rounded-xl flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className={`p-1.5 ${c.bg} ${c.text} rounded-lg`}>
            <Icon className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-xs font-bold text-white">{title}</h3>
            {subtitle && <p className="text-[11px] text-gray-400">{subtitle}</p>}
          </div>
        </div>
        {badge && (
          <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${colorMap[badgeColor || color]?.bg || c.bg} ${colorMap[badgeColor || color]?.text || c.text} ${colorMap[badgeColor || color]?.border || c.border} border`}>
            {badge}
          </span>
        )}
        {headerRight}
      </div>
      {children}
    </div>
  );
}

// ---------------------------------------------------------------------------
// 2. CommandButton — repeated 12+ times across Settings & Controls
// ---------------------------------------------------------------------------
interface CommandButtonProps {
  icon: LucideIcon;
  color: string;
  label: string;
  description?: string;
  onClick: () => void;
  disabled?: boolean;
  isActive?: boolean;
  isLoading?: boolean;
  /** "compact" = small icon+label (Settings grid), "full" = icon+title+desc (Controls grid) */
  variant?: "compact" | "full";
}

export function CommandButton({
  icon: Icon, color, label, description, onClick,
  disabled = false, isActive = false, isLoading = false,
  variant = "compact",
}: CommandButtonProps) {
  const c = colorMap[color] || colorMap.blue;

  if (variant === "full") {
    return (
      <button
        onClick={onClick}
        disabled={disabled}
        className={`p-4 bg-[#1B1B22] border hover:border-${color}-500/50 disabled:opacity-60 rounded-2xl flex items-center gap-3 text-left transition group cursor-pointer ${
          isActive ? `${c.bg} ${c.border} border` : "border-[#2B2B36]"
        }`}
      >
        <div className={`p-3 ${c.bg} ${c.text} rounded-xl group-hover:scale-105 transition`}>
          {isLoading ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Icon className="w-5 h-5" />}
        </div>
        <div>
          <h3 className="text-xs font-bold text-white">{label}</h3>
          {description && <p className="text-[10px] text-gray-400">{description}</p>}
        </div>
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`p-3 rounded-xl border text-left transition flex flex-col justify-between cursor-pointer ${
        isActive ? `${c.bg} border-${color}-500 ${c.text}` : "bg-[#181820] border-[#292938] text-gray-300 hover:border-gray-600"
      } ${disabled ? "opacity-50" : ""}`}
    >
      <Icon className={`w-4 h-4 mb-1.5 ${isActive ? c.text : "text-gray-400"}`} />
      <span className="text-xs font-bold text-white">{label}</span>
    </button>
  );
}

// ---------------------------------------------------------------------------
// 3. AlertBanner — repeated 6+ times across Auth, Pairing, Settings
// ---------------------------------------------------------------------------
interface AlertBannerProps {
  type: "error" | "success" | "info" | "loading";
  message: string | null | undefined;
}

export function AlertBanner({ type, message }: AlertBannerProps) {
  if (!message) return null;

  const styles = {
    error:   { bg: "bg-red-950/40", border: "border-red-800/60", text: "text-red-300", Icon: AlertTriangle, iconColor: "text-red-400" },
    success: { bg: "bg-emerald-950/40", border: "border-emerald-800/60", text: "text-emerald-300", Icon: CheckCircle, iconColor: "text-emerald-400" },
    info:    { bg: "bg-blue-950/40", border: "border-blue-800/40", text: "text-blue-300", Icon: CheckCircle, iconColor: "text-blue-400" },
    loading: { bg: "bg-blue-950/40", border: "border-blue-800/40", text: "text-blue-300", Icon: RefreshCw, iconColor: "text-blue-400" },
  };

  const s = styles[type];
  return (
    <div className={`p-3 ${s.bg} border ${s.border} rounded-xl flex items-center gap-2 text-xs ${s.text}`}>
      <s.Icon className={`w-4 h-4 shrink-0 ${s.iconColor} ${type === "loading" ? "animate-spin" : ""}`} />
      <span>{message}</span>
    </div>
  );
}

