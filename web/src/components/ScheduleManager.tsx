"use client";

import React, { useState } from "react";
import { Clock, Plus, CheckCircle, Music, Trash2, X, Calendar, Camera, Play, Sparkles, AlertCircle } from "lucide-react";
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
  activeTab?: string;
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
  // New Enhanced Schedule Form State
  const [scheduleMode, setScheduleMode] = useState<"daily" | "specific_date">("daily");
  const todayStr = new Date().toISOString().split("T")[0];
  const [targetDate, setTargetDate] = useState<string>(todayStr);
  const [captureType, setCaptureType] = useState<"audio" | "snapshot">("audio");
  const [isTriggeringImmediate, setIsTriggeringImmediate] = useState<boolean>(false);
  const [immediateSuccessMsg, setImmediateSuccessMsg] = useState<string | null>(null);

  // Quick Preset Helper
  const applyTimePreset = (h: number, m: number, labelText: string) => {
    setSchedHour(h);
    setSchedMinute(m);
    if (!schedLabel || schedLabel === "Study Time Ambient") {
      setSchedLabel(labelText);
    }
  };

  // Immediate Test Trigger
  const handleImmediateTestRun = async () => {
    if (!activeChildId) return;
    setIsTriggeringImmediate(true);
    setImmediateSuccessMsg(null);
    try {
      const now = new Date();
      // Schedule 20 seconds from now
      const testHour = now.getHours();
      const testMinute = (now.getMinutes() + 1) % 60;
      const schedId = "sched_test_" + Date.now();
      await set(ref(rtdb, `schedules/${activeChildId}/${schedId}`), {
        id: schedId,
        hour: testHour,
        minute: testMinute,
        durationMinutes: 1,
        isEnabled: true,
        repeatDaily: false,
        label: "Immediate Test Capture (1 Min)",
        streamType: "audio",
        captureType: "audio",
        targetDate: todayStr,
        timestamp: Date.now(),
      });
      setImmediateSuccessMsg(`Test schedule created for ${String(testHour).padStart(2, "0")}:${String(testMinute).padStart(2, "0")}! Phone will record 1 min.`);
      setTimeout(() => setImmediateSuccessMsg(null), 6000);
    } catch (e: any) {
      console.error("Test schedule error:", e);
    } finally {
      setIsTriggeringImmediate(false);
    }
  };

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
            तय तारीख और समय पर चाइल्ड के फोन पर साइलेंट एम्बिएंट रिकॉर्डिंग चलेगी (बिना इंटरनेट के भी)।
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* Immediate Test Trigger Button */}
          <button
            type="button"
            onClick={handleImmediateTestRun}
            disabled={!activeChildId || isTriggeringImmediate}
            className="px-3 py-2 bg-blue-600/20 hover:bg-blue-600/30 text-blue-300 border border-blue-500/30 disabled:opacity-50 text-xs font-bold rounded-xl transition flex items-center gap-1.5 cursor-pointer"
            title="चाइल्ड के फोन पर 1 मिनट की टेस्ट रिकॉर्डिंग शेड्यूल करें"
          >
            <Play className="w-3.5 h-3.5 fill-blue-300" />
            <span>{isTriggeringImmediate ? "Scheduling..." : "Quick 1-Min Test"}</span>
          </button>

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
      </div>

      {/* Immediate Success Toast */}
      {immediateSuccessMsg && (
        <div className="p-3 bg-emerald-950/60 border border-emerald-800/60 rounded-xl flex items-center gap-2.5 text-xs text-emerald-300 animate-in fade-in">
          <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{immediateSuccessMsg}</span>
        </div>
      )}

      {/* Offline Guarantee Info Banner */}
      <div className="p-3 bg-blue-950/40 border border-blue-800/40 rounded-xl flex items-center gap-2.5 text-xs text-blue-300">
        <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
        <span>
          <strong>100% Offline Guaranteed:</strong> चाइल्ड का फोन बंद या नो-इंटरनेट मोड में होने पर भी Android AlarmManager तय समय पर रिकॉर्डिंग शुरू कर देगा। रिकॉर्डिंग खत्म होने के बाद जैसे ही फोन इंटरनेट से जुड़ेगा, फाइल सीधे आपके पैरेंट पोर्टल पर ऑटो-अपलोड हो जाएगी।
        </span>
      </div>

      {/* Schedules List */}
      {schedulesList.length === 0 ? (
        <div className="p-10 bg-[#16161D] border border-[#262632] rounded-2xl flex flex-col items-center justify-center text-center">
          <Clock className="w-10 h-10 text-gray-600 mb-2" />
          <p className="text-xs font-bold text-gray-300">No Auto-Record Schedules</p>
          <p className="text-[11px] text-gray-500 mt-1 max-w-sm">
            'New Schedule' पर क्लिक करके किसी खास तारीख या रोजाना के समय (जैसे दोपहर 14:00 बजे 10 मिनट के लिए) साइलेंट एम्बिएंट रिकॉर्डिंग सेट करें।
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {schedulesList.map((sched: any) => {
            const hourStr = String(sched.hour ?? 0).padStart(2, "0");
            const minStr = String(sched.minute ?? 0).padStart(2, "0");
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
                        {hourStr}:{minStr}
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
      )}

      {/* Add Schedule Modal */}
      {showAddScheduleModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in">
          <div className="w-full max-w-md p-6 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-2xl relative max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between mb-4 pb-3 border-b border-[#2B2B38]">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <Clock className="w-4 h-4 text-emerald-400" />
                <span>Create Auto-Record Schedule</span>
              </h3>
              <button
                type="button"
                onClick={() => setShowAddScheduleModal(false)}
                className="p-1 text-gray-400 hover:text-white rounded-lg hover:bg-white/10 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-4">
              {/* Feature 1: Schedule Mode (Daily Repeat vs Specific Date) */}
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 mb-1.5">Schedule Type (शेड्यूल प्रकार)</label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setScheduleMode("daily")}
                    className={`py-2 px-3 rounded-xl text-xs font-bold border transition flex items-center justify-center gap-1.5 cursor-pointer ${
                      scheduleMode === "daily"
                        ? "bg-emerald-600 text-white border-emerald-500 shadow-md shadow-emerald-600/30"
                        : "bg-[#14141B] text-gray-400 border-gray-800 hover:text-white"
                    }`}
                  >
                    <Clock className="w-3.5 h-3.5" />
                    <span>Daily Repeat (रोजाना)</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setScheduleMode("specific_date")}
                    className={`py-2 px-3 rounded-xl text-xs font-bold border transition flex items-center justify-center gap-1.5 cursor-pointer ${
                      scheduleMode === "specific_date"
                        ? "bg-blue-600 text-white border-blue-500 shadow-md shadow-blue-600/30"
                        : "bg-[#14141B] text-gray-400 border-gray-800 hover:text-white"
                    }`}
                  >
                    <Calendar className="w-3.5 h-3.5" />
                    <span>Specific Date (तय तारीख)</span>
                  </button>
                </div>
              </div>

              {/* Feature 2: Specific Date Picker */}
              {scheduleMode === "specific_date" && (
                <div className="p-3 bg-[#14141C] border border-[#2B2B38] rounded-xl animate-in fade-in">
                  <label className="block text-[11px] font-semibold text-blue-300 mb-1 flex items-center gap-1">
                    <Calendar className="w-3.5 h-3.5 text-blue-400" />
                    <span>Select Target Date (तारीख चुनें)</span>
                  </label>
                  <input
                    type="date"
                    min={todayStr}
                    value={targetDate}
                    onChange={(e) => setTargetDate(e.target.value)}
                    className="w-full bg-[#1A1A24] border border-[#3A3A4C] rounded-xl px-3 py-2 text-xs text-white font-mono focus:outline-none focus:border-blue-500"
                  />
                  <p className="text-[10px] text-gray-400 mt-1">
                    यह शेड्यूल केवल चुनी गई तारीख को एक बार चलेगा और फिर बंद हो जाएगा।
                  </p>
                </div>
              )}

              {/* Feature 3: Capture Mode (Audio vs Camera Snapshot) */}
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 mb-1.5">Capture Mode (रिकॉर्ड का प्रकार)</label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setCaptureType("audio")}
                    className={`py-2 px-3 rounded-xl text-xs font-bold border transition flex items-center justify-center gap-1.5 cursor-pointer ${
                      captureType === "audio"
                        ? "bg-emerald-600 text-white border-emerald-500"
                        : "bg-[#14141B] text-gray-400 border-gray-800 hover:text-white"
                    }`}
                  >
                    <Music className="w-3.5 h-3.5" />
                    <span>Ambient Audio (आवाज़)</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setCaptureType("snapshot")}
                    className={`py-2 px-3 rounded-xl text-xs font-bold border transition flex items-center justify-center gap-1.5 cursor-pointer ${
                      captureType === "snapshot"
                        ? "bg-purple-600 text-white border-purple-500"
                        : "bg-[#14141B] text-gray-400 border-gray-800 hover:text-white"
                    }`}
                  >
                    <Camera className="w-3.5 h-3.5" />
                    <span>Silent Camera Photo</span>
                  </button>
                </div>
              </div>

              {/* Feature 4: Schedule Label / Note */}
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 mb-1">Schedule Label / Note</label>
                <input
                  type="text"
                  value={schedLabel}
                  onChange={(e) => setSchedLabel(e.target.value)}
                  placeholder="e.g. Tuition Class / Night Ambient"
                  className="w-full bg-[#13131A] border border-[#2E2E3C] rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-emerald-500"
                />
              </div>

              {/* Feature 5: Quick Time Presets */}
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 mb-1">Quick Time Presets (जल्दी चुनें)</label>
                <div className="grid grid-cols-4 gap-1.5">
                  {[
                    { h: 9, m: 0, title: "Morning (09:00)", label: "Morning Study Ambient" },
                    { h: 14, m: 0, title: "Noon (14:00)", label: "Tuition Time Ambient" },
                    { h: 18, m: 0, title: "Evening (18:00)", label: "Play/Outdoor Ambient" },
                    { h: 22, m: 0, title: "Night (22:00)", label: "Night Sleep Ambient" },
                  ].map((preset) => (
                    <button
                      key={preset.title}
                      type="button"
                      onClick={() => applyTimePreset(preset.h, preset.m, preset.label)}
                      className={`py-1.5 rounded-lg text-[10px] font-bold border transition cursor-pointer ${
                        schedHour === preset.h && schedMinute === preset.m
                          ? "bg-emerald-600 text-white border-emerald-500"
                          : "bg-[#14141B] text-gray-400 border-gray-800 hover:border-gray-600"
                      }`}
                    >
                      {String(preset.h).padStart(2, "0")}:{String(preset.m).padStart(2, "0")}
                    </button>
                  ))}
                </div>
              </div>

              {/* Feature 6: Time Inputs (Hour & Minute) */}
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 mb-1">Hour (0-23 घंटा)</label>
                  <input
                    type="number"
                    min="0"
                    max="23"
                    value={schedHour}
                    onChange={(e) => setSchedHour(Math.max(0, Math.min(23, parseInt(e.target.value) || 0)))}
                    className="w-full bg-[#13131A] border border-[#2E2E3C] rounded-xl px-3 py-2 text-sm font-mono font-bold text-white focus:outline-none focus:border-emerald-500 text-center"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 mb-1">Minute (0-59 मिनट)</label>
                  <input
                    type="number"
                    min="0"
                    max="59"
                    value={schedMinute}
                    onChange={(e) => setSchedMinute(Math.max(0, Math.min(59, parseInt(e.target.value) || 0)))}
                    className="w-full bg-[#13131A] border border-[#2E2E3C] rounded-xl px-3 py-2 text-sm font-mono font-bold text-white focus:outline-none focus:border-emerald-500 text-center"
                  />
                </div>
              </div>

              {/* Feature 7: Duration Selector (Kitna time tak) */}
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 mb-1.5">
                  Recording Duration (कितने मिनट तक चले)
                </label>
                <div className="flex items-center gap-1.5 flex-wrap">
                  {[1, 3, 5, 10, 15, 30, 60].map((dur) => (
                    <button
                      key={dur}
                      type="button"
                      onClick={() => setSchedDuration(dur)}
                      className={`flex-1 min-w-[40px] py-1.5 rounded-lg text-xs font-bold border transition cursor-pointer ${
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

              {/* Actions Footer */}
              <div className="pt-3 border-t border-[#2B2B38] flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowAddScheduleModal(false)}
                  className="px-3.5 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 text-xs font-semibold rounded-xl transition cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={async () => {
                    if (!activeChildId) return;
                    const schedId = "sched_" + Date.now();
                    const isDaily = scheduleMode === "daily";
                    await set(ref(rtdb, `schedules/${activeChildId}/${schedId}`), {
                      id: schedId,
                      hour: schedHour,
                      minute: schedMinute,
                      durationMinutes: schedDuration,
                      isEnabled: true,
                      repeatDaily: isDaily,
                      targetDate: isDaily ? "" : targetDate,
                      captureType: captureType,
                      streamType: captureType,
                      label: schedLabel || (captureType === "snapshot" ? "Scheduled Camera Photo" : "Ambient Audio"),
                      timestamp: Date.now(),
                    });
                    setShowAddScheduleModal(false);
                  }}
                  className="px-5 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-emerald-600/30 cursor-pointer"
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
