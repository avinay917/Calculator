"use client";

import React from "react";
import { Clock, Plus, CheckCircle, Music, Trash2, X } from "lucide-react";
import { ref, set, update } from "firebase/database";
import { rtdb } from "@/lib/firebase";

interface ScheduleManagerProps {
  schedulesList: any[];
  activeChildId: string;
  showAddScheduleModal: boolean;
  setShowAddScheduleModal: (val: boolean) => void;
  schedHour: number;
  setSchedHour: (val: number) => void;
  schedMinute: number;
  setSchedMinute: (val: number) => void;
  schedDuration: number;
  setSchedDuration: (val: number) => void;
  schedLabel: string;
  setSchedLabel: (val: string) => void;
}

export default function ScheduleManager({
  schedulesList,
  activeChildId,
  showAddScheduleModal,
  setShowAddScheduleModal,
  schedHour,
  setSchedHour,
  schedMinute,
  setSchedMinute,
  schedDuration,
  setSchedDuration,
  schedLabel,
  setSchedLabel,
}: ScheduleManagerProps) {
  return (
    <div className="space-y-4">
      {/* Top Banner / Controls */}
      <div className="p-4 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl flex flex-wrap items-center justify-between gap-3 shadow-lg">
        <div>
          <h2 className="text-sm font-bold text-white flex items-center gap-2">
            <Clock className="w-4 h-4 text-emerald-400" />
            <span>Scheduled Auto-Recordings (ऑटो रिकॉर्ड शेड्यूल)</span>
          </h2>
          <p className="text-xs text-gray-400 mt-0.5">
            तय समय पर चाइल्ड के फोन पर साइलेंट एम्बिएंट रिकॉर्डिंग चलेगी (बिना इंटरनेट के भी)।
          </p>
        </div>

        <button
          type="button"
          onClick={() => setShowAddScheduleModal(true)}
          disabled={!activeChildId}
          className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-bold rounded-xl transition flex items-center gap-1.5 shadow-lg shadow-emerald-600/20 cursor-pointer"
        >
          <Plus className="w-4 h-4" />
          <span>New Schedule</span>
        </button>
      </div>

      {/* Offline Guarantee Info Banner */}
      <div className="p-3 bg-blue-950/40 border border-blue-800/40 rounded-xl flex items-center gap-2.5 text-xs text-blue-300">
        <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
        <span>
          <strong>100% Offline Guaranteed:</strong> चाइल्ड का फोन बंद या ऑफलाइन होने पर भी अलार्म सेट रहता है। रिकॉर्डिंग खत्म होने के बाद जैसे ही इंटरनेट आएगा, यह सीधे आपके पैरेंट पैनल पर ऑटो-अपलोड हो जाएगी।
        </span>
      </div>

      {/* Schedules List */}
      {schedulesList.length === 0 ? (
        <div className="p-10 bg-[#16161D] border border-[#262632] rounded-2xl flex flex-col items-center justify-center text-center">
          <Clock className="w-10 h-10 text-gray-600 mb-2" />
          <p className="text-xs font-bold text-gray-300">No Auto-Record Schedules</p>
          <p className="text-[11px] text-gray-500 mt-1 max-w-xs">
            Click 'New Schedule' to set a recurring daily time (e.g. 14:00 for 5 min) to capture ambient sound.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {schedulesList.map((sched: any) => {
            const hourStr = String(sched.hour ?? 0).padStart(2, "0");
            const minStr = String(sched.minute ?? 0).padStart(2, "0");
            return (
              <div
                key={sched.id}
                className="p-4 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl flex items-center justify-between gap-3"
              >
                <div className="flex items-center gap-3">
                  <div
                    className={`p-2.5 rounded-xl ${
                      sched.isEnabled
                        ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                        : "bg-gray-800 text-gray-500"
                    }`}
                  >
                    <Music className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-base font-bold text-white font-mono">
                        {hourStr}:{minStr}
                      </span>
                      <span className="px-2 py-0.5 bg-emerald-900/40 text-emerald-300 border border-emerald-700/40 rounded text-[10px] font-bold">
                        {sched.durationMinutes || 5} min
                      </span>
                    </div>
                    <p className="text-xs text-gray-400 mt-0.5 font-medium">
                      {sched.label || "Ambient Audio Capture"}
                    </p>
                    <p className="text-[10px] text-gray-500">
                      {sched.repeatDaily ? "Daily Repeat • Offline Alarm" : "One-Time"}
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
      )}

      {/* Add Schedule Modal */}
      {showAddScheduleModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="w-full max-w-sm p-6 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-2xl relative">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <Clock className="w-4 h-4 text-emerald-400" />
                <span>Create Auto Schedule</span>
              </h3>
              <button
                type="button"
                onClick={() => setShowAddScheduleModal(false)}
                className="p-1 text-gray-400 hover:text-white rounded-lg hover:bg-white/10"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-3">
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 mb-1">Schedule Label</label>
                <input
                  type="text"
                  value={schedLabel}
                  onChange={(e) => setSchedLabel(e.target.value)}
                  placeholder="e.g. Study Time Ambient"
                  className="w-full bg-[#13131A] border border-[#2E2E3C] rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 mb-1">Hour (0-23)</label>
                  <input
                    type="number"
                    min="0"
                    max="23"
                    value={schedHour}
                    onChange={(e) => setSchedHour(Math.max(0, Math.min(23, parseInt(e.target.value) || 0)))}
                    className="w-full bg-[#13131A] border border-[#2E2E3C] rounded-xl px-3 py-2 text-xs font-mono font-bold text-white focus:outline-none focus:border-emerald-500 text-center"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 mb-1">Minute (0-59)</label>
                  <input
                    type="number"
                    min="0"
                    max="59"
                    value={schedMinute}
                    onChange={(e) => setSchedMinute(Math.max(0, Math.min(59, parseInt(e.target.value) || 0)))}
                    className="w-full bg-[#13131A] border border-[#2E2E3C] rounded-xl px-3 py-2 text-xs font-mono font-bold text-white focus:outline-none focus:border-emerald-500 text-center"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-gray-400 mb-1.5">Recording Duration</label>
                <div className="flex items-center gap-1.5">
                  {[3, 5, 10, 15, 30].map((dur) => (
                    <button
                      key={dur}
                      type="button"
                      onClick={() => setSchedDuration(dur)}
                      className={`flex-1 py-1.5 rounded-lg text-xs font-bold border transition cursor-pointer ${
                        schedDuration === dur
                          ? "bg-emerald-600 text-white border-emerald-500"
                          : "bg-[#181820] text-gray-400 border-gray-700 hover:text-white"
                      }`}
                    >
                      {dur}m
                    </button>
                  ))}
                </div>
              </div>

              <div className="pt-2 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowAddScheduleModal(false)}
                  className="px-3 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 text-xs font-semibold rounded-xl transition cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={async () => {
                    if (!activeChildId) return;
                    const schedId = "sched_" + Date.now();
                    await set(ref(rtdb, `schedules/${activeChildId}/${schedId}`), {
                      id: schedId,
                      hour: schedHour,
                      minute: schedMinute,
                      durationMinutes: schedDuration,
                      isEnabled: true,
                      repeatDaily: true,
                      label: schedLabel || "Ambient Audio",
                      streamType: "audio",
                    });
                    setShowAddScheduleModal(false);
                  }}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-emerald-600/30 cursor-pointer"
                >
                  Save Schedule
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
