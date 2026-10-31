"use client";

import React, { useState, useMemo } from "react";
import { ActivityFeedItem } from "@/types/dashboard";
import {
  Phone,
  MessageSquare,
  Mail,
  Bell,
  ShieldAlert,
  Search,
  Info
} from "lucide-react";

interface Props {
  events: ActivityFeedItem[];
}

export function UniversalActivityFeed({ events }: Props) {
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState<string>("");

  const categories = [
    { code: "ALL", label: "All Activity" },
    { code: "CALL", label: "Calls" },
    { code: "WHATSAPP", label: "WhatsApp" },
    { code: "SMS", label: "SMS" },
    { code: "NOTIFICATION", label: "Notifications" },
    { code: "SECURITY", label: "Security" },
  ];

  const filteredEvents = useMemo(() => {
    return (events || []).filter((item) => {
      const matchCat =
        selectedCategory === "ALL" ||
        item.category?.toUpperCase() === selectedCategory;
      const q = searchQuery.toLowerCase();
      const matchQuery =
        !q ||
        item.title?.toLowerCase().includes(q) ||
        item.subtitle?.toLowerCase().includes(q) ||
        item.message?.toLowerCase().includes(q);
      return matchCat && matchQuery;
    });
  }, [events, selectedCategory, searchQuery]);

  const getCategoryStyles = (category: string) => {
    switch (category?.toUpperCase()) {
      case "CALL":
        return {
          icon: Phone,
          bg: "bg-blue-500/10 text-blue-400 border-blue-500/20",
          badge: "bg-blue-500/20 text-blue-300",
        };
      case "WHATSAPP":
        return {
          icon: MessageSquare,
          bg: "bg-emerald-500/10 text-emerald-400 border-emerald-500/20",
          badge: "bg-emerald-500/20 text-emerald-300",
        };
      case "SMS":
        return {
          icon: Mail,
          bg: "bg-purple-500/10 text-purple-400 border-purple-500/20",
          badge: "bg-purple-500/20 text-purple-300",
        };
      case "NOTIFICATION":
        return {
          icon: Bell,
          bg: "bg-amber-500/10 text-amber-400 border-amber-500/20",
          badge: "bg-amber-500/20 text-amber-300",
        };
      case "SECURITY":
        return {
          icon: ShieldAlert,
          bg: "bg-red-500/10 text-red-400 border-red-500/20",
          badge: "bg-red-500/20 text-red-300",
        };
      default:
        return {
          icon: Info,
          bg: "bg-slate-500/10 text-slate-400 border-slate-500/20",
          badge: "bg-slate-500/20 text-slate-300",
        };
    }
  };

  return (
    <div className="space-y-4">
      {/* Header controls: Search & Category Chips */}
      <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
        <div className="relative flex-1">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search feed by title, sender, or content..."
            className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-xl text-sm text-slate-200 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/30"
          />
        </div>

        {/* Filter Chips */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0 scrollbar-none">
          {categories.map((c) => {
            const isSelected = selectedCategory === c.code;
            return (
              <button
                key={c.code}
                onClick={() => setSelectedCategory(c.code)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
                  isSelected
                    ? "bg-emerald-500 text-white shadow-sm"
                    : "bg-slate-800/80 hover:bg-slate-800 text-slate-300 border border-slate-700/50"
                }`}
              >
                {c.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Events Feed List */}
      {filteredEvents.length === 0 ? (
        <div className="p-12 text-center border border-slate-800/80 rounded-2xl bg-slate-900/40">
          <Info className="w-10 h-10 mx-auto text-slate-600 mb-2" />
          <p className="text-sm text-slate-400 font-medium">
            {searchQuery
              ? "No activity matched your search filter."
              : "No activity logged in this category yet."}
          </p>
        </div>
      ) : (
        <div className="space-y-2.5">
          {filteredEvents.map((item, idx) => {
            const style = getCategoryStyles(item.category);
            const IconComp = style.icon;
            const dateStr = item.timestamp
              ? new Date(item.timestamp).toLocaleString("en-IN", {
                  hour: "2-digit",
                  minute: "2-digit",
                  day: "numeric",
                  month: "short",
                })
              : "";

            return (
              <div
                key={item.id || `${item.timestamp}_${idx}`}
                className="p-3.5 bg-slate-900/70 border border-slate-800/80 hover:border-slate-700/80 rounded-xl transition-all flex items-start gap-3.5 group"
              >
                <div
                  className={`p-2.5 rounded-xl border shrink-0 ${style.bg}`}
                >
                  <IconComp className="w-5 h-5" />
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <h4 className="text-sm font-semibold text-slate-100 truncate">
                      {item.title || "Activity Event"}
                    </h4>
                    <span className="text-[11px] text-slate-500 shrink-0">
                      {dateStr}
                    </span>
                  </div>

                  {item.subtitle && item.subtitle !== item.title && (
                    <p className="text-xs text-slate-400 mt-0.5 truncate">
                      {item.subtitle}
                    </p>
                  )}

                  {item.message && (
                    <p className="text-xs text-slate-300 mt-1 line-clamp-2 bg-slate-950/40 p-2 rounded-lg border border-slate-800/40">
                      {item.message}
                    </p>
                  )}

                  <div className="mt-2 flex items-center gap-2">
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${style.badge}`}>
                      {item.category?.toUpperCase()}
                    </span>
                    {item.badge && (
                      <span className="text-[10px] bg-slate-800 text-slate-400 px-1.5 py-0.5 rounded font-mono">
                        {item.badge}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
