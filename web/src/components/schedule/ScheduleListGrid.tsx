"use client";

import React from "react";
import { Clock, Music, Trash2, Calendar, Camera } from "lucide-react";
import { ref, set, update } from "firebase/database";
import { rtdb } from "@/lib/firebase";

interface ScheduleListGridProps {
  schedulesList: any[];
  activeChildId: string;
}

export default function ScheduleListGrid({
  schedulesList,
  activeChildId,
}: ScheduleListGridProps) {
  if (schedulesList.length === 0) {
    return (
      <div className="p-10 bg-[#16161D] border border-[#262632] rounded-2xl flex flex-col items-center justify-center text-center">
        <Clock className="w-10 h-10 text-gray-600 mb-2" />
        <p className="text-xs font-bold text-gray-300">No Auto-Record Schedules</p>
        <p className="text-[11px] text-gray-500 mt-1 max-w-sm">
          'New Schedule' पर क्लिक करके किसी खास तारीख या रोजाना के समय (जैसे दोपहर 14:00 बजे 10 मिनट के लिए) साइलेंट एम्बिएंट रिकॉर्डिंग सेट करें।
        </p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
      {schedulesList.map((sched: any) => {
        const hourStr = String(sched.hour ?? 0).padStart(2, "0");
        const minStr = String(sched.minute ?? 0).padStart(2, "0");
        const rawHour = sched.hour ?? 0;
        const h12 = rawHour % 12 || 12;
        const amPm = rawHour >= 12 ? "PM" : "AM";
        const time12Str = `${h12}:${minStr} ${amPm}`;
        const isSpecificDate = !!sched.targetDate;
        const isSnapshot = sched.captureType === "snapshot";

        return (
          <div
            key={sched.id}
            className="p-4 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl flex items-center justify-between gap-3 shadow-md hover:border-gray-700 transition"
          >
            <div className="flex items-center gap-3">
              <div
                className={`p-2.5 rounded-xl ${
                  sched.isEnabled
                    ? isSnapshot
                      ? "bg-purple-500/20 text-purple-400 border border-purple-500/30"
                      : "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                    : "bg-gray-800 text-gray-500"
                }`}
              >
                {isSnapshot ? <Camera className="w-5 h-5" /> : <Music className="w-5 h-5" />}
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <span className="text-base font-bold text-white font-mono">
                    {time12Str}
                  </span>
                  <span className="px-2 py-0.5 bg-emerald-900/40 text-emerald-300 border border-emerald-700/40 rounded text-[10px] font-bold">
                    {sched.durationMinutes || 5} min
                  </span>
                  {isSnapshot && (
                    <span className="px-1.5 py-0.5 bg-purple-900/40 text-purple-300 border border-purple-700/40 rounded text-[9px] font-bold">
                      Camera Photo
                    </span>
                  )}
                </div>
                <p className="text-xs text-gray-300 mt-0.5 font-medium">
                  {sched.label || "Ambient Audio Capture"}
                </p>
                <p className="text-[10px] text-gray-400 flex items-center gap-1.5 mt-0.5">
                  <Calendar className="w-3 h-3 text-gray-500" />
                  {isSpecificDate ? (
                    <span className="text-blue-300 font-medium">Date: {sched.targetDate} (One-Time)</span>
                  ) : (
                    <span className="text-gray-400">Daily Repeat • 100% Offline Alarm</span>
                  )}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => {
                  if (!activeChildId || !sched.id) return;
                  update(ref(rtdb, `schedules/${activeChildId}/${sched.id}`), {
                    isEnabled: !sched.isEnabled,
                  });
                }}
                className={`px-2.5 py-1 rounded-lg text-[10px] font-bold border transition cursor-pointer ${
                  sched.isEnabled
                    ? "bg-emerald-600/30 text-emerald-300 border-emerald-500/40"
                    : "bg-gray-800 text-gray-500 border-gray-700"
                }`}
              >
                {sched.isEnabled ? "Active" : "Disabled"}
              </button>
              <button
                type="button"
                onClick={() => {
                  if (!activeChildId || !sched.id) return;
                  set(ref(rtdb, `schedules/${activeChildId}/${sched.id}`), null);
                }}
                className="p-1.5 text-gray-500 hover:text-red-400 rounded-lg hover:bg-white/5 transition cursor-pointer"
                title="Delete Schedule"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}
