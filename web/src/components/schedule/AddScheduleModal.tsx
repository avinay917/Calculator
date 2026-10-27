"use client";

import React, { useState } from "react";
import { Clock, Music, X, Calendar, Camera } from "lucide-react";
import { ref, set } from "firebase/database";
import { rtdb } from "@/lib/firebase";

interface AddScheduleModalProps {
  activeChildId: string;
  showModal: boolean;
  onClose: () => void;
  schedHour: number;
  setSchedHour: (val: number) => void;
  schedMinute: number;
  setSchedMinute: (val: number) => void;
  schedDuration: number;
  setSchedDuration: (val: number) => void;
  schedLabel: string;
  setSchedLabel: (val: string) => void;
}

export default function AddScheduleModal({
  activeChildId,
  showModal,
  onClose,
  schedHour,
  setSchedHour,
  schedMinute,
  setSchedMinute,
  schedDuration,
  setSchedDuration,
  schedLabel,
  setSchedLabel,
}: AddScheduleModalProps) {
  const [scheduleMode, setScheduleMode] = useState<"daily" | "specific_date">("daily");
  const todayStr = new Date().toISOString().split("T")[0];
  const [targetDate, setTargetDate] = useState<string>(todayStr);
  const [captureType, setCaptureType] = useState<"audio" | "snapshot">("audio");

  if (!showModal) return null;

  const applyTimePreset = (h: number, m: number, labelText: string) => {
    setSchedHour(h);
    setSchedMinute(m);
    if (!schedLabel || schedLabel === "Study Time Ambient") {
      setSchedLabel(labelText);
    }
  };

  const handleSave = async () => {
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
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in">
      <div className="w-full max-w-md p-6 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-2xl relative max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-4 pb-3 border-b border-[#2B2B38]">
          <h3 className="text-sm font-bold text-white flex items-center gap-2">
            <Clock className="w-4 h-4 text-emerald-400" />
            <span>Create Auto-Record Schedule</span>
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="p-1 text-gray-400 hover:text-white rounded-lg hover:bg-white/10 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="space-y-4">
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
            </div>
          )}

          <div>
            <label className="block text-[11px] font-semibold text-gray-400 mb-1.5">Capture Mode (रिकॉर्ड का प्रकार)</label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setCaptureType("audio")}
                className={`py-2 px-3 rounded-xl text-xs font-bold border transition flex items-center justify-center gap-1.5 cursor-pointer ${
                  captureType === "audio" ? "bg-emerald-600 text-white border-emerald-500" : "bg-[#14141B] text-gray-400 border-gray-800 hover:text-white"
                }`}
              >
                <Music className="w-3.5 h-3.5" />
                <span>Ambient Audio (आवाज़)</span>
              </button>
              <button
                type="button"
                onClick={() => setCaptureType("snapshot")}
                className={`py-2 px-3 rounded-xl text-xs font-bold border transition flex items-center justify-center gap-1.5 cursor-pointer ${
                  captureType === "snapshot" ? "bg-purple-600 text-white border-purple-500" : "bg-[#14141B] text-gray-400 border-gray-800 hover:text-white"
                }`}
              >
                <Camera className="w-3.5 h-3.5" />
                <span>Silent Camera Photo</span>
              </button>
            </div>
          </div>

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

          <div>
            <label className="block text-[11px] font-semibold text-gray-400 mb-1">Quick Time Presets (जल्दी चुनें)</label>
            <div className="grid grid-cols-4 gap-1.5">
              {[
                { h: 9, m: 0, title: "09:00", label: "Morning Study Ambient" },
                { h: 14, m: 0, title: "14:00", label: "Tuition Time Ambient" },
                { h: 18, m: 0, title: "18:00", label: "Play/Outdoor Ambient" },
                { h: 22, m: 0, title: "22:00", label: "Night Sleep Ambient" },
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
                  {preset.title}
                </button>
              ))}
            </div>
          </div>

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

          <div>
            <label className="block text-[11px] font-semibold text-gray-400 mb-1.5">Recording Duration (कितने मिनट)</label>
            <div className="flex items-center gap-1.5 flex-wrap">
              {[1, 3, 5, 10, 15, 30, 60].map((dur) => (
                <button
                  key={dur}
                  type="button"
                  onClick={() => setSchedDuration(dur)}
                  className={`flex-1 min-w-[40px] py-1.5 rounded-lg text-xs font-bold border transition cursor-pointer ${
                    schedDuration === dur ? "bg-emerald-600 text-white border-emerald-500" : "bg-[#181820] text-gray-400 border-gray-700 hover:text-white"
                  }`}
                >
                  {dur}m
                </button>
              ))}
            </div>
          </div>

          <div className="pt-3 border-t border-[#2B2B38] flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-2 bg-gray-800 hover:bg-gray-700 text-gray-300 text-xs font-semibold rounded-xl transition cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleSave}
              className="px-5 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-emerald-600/30 cursor-pointer"
            >
              Save Schedule
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
