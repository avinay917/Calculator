"use client";

import React, { useState } from "react";
import { Clock, Plus, CheckCircle, Play } from "lucide-react";
import { ref, set } from "firebase/database";
import { rtdb } from "@/lib/firebase";
import ScheduleListGrid from "@/components/schedule/ScheduleListGrid";
import AddScheduleModal from "@/components/schedule/AddScheduleModal";
import { useApp } from "@/context/AppContext";

export default function ScheduleManager() {
  const {
    childData,
    showAddScheduleModal, setShowAddScheduleModal,
    schedHour, setSchedHour,
    schedMinute, setSchedMinute,
    schedDuration, setSchedDuration,
    schedLabel, setSchedLabel,
  } = useApp();

  const { schedulesList, activeChildId } = childData;
  const [isTriggeringImmediate, setIsTriggeringImmediate] = useState<boolean>(false);
  const [immediateSuccessMsg, setImmediateSuccessMsg] = useState<string | null>(null);

  const handleImmediateTestRun = async () => {
    if (!activeChildId) return;
    setIsTriggeringImmediate(true);
    setImmediateSuccessMsg(null);
    try {
      const now = new Date();
      const testHour = now.getHours();
      const testMinute = (now.getMinutes() + 1) % 60;
      const schedId = "sched_test_" + Date.now();
      await set(ref(rtdb, `schedules/${activeChildId}/${schedId}`), {
        id: schedId, hour: testHour, minute: testMinute, durationMinutes: 1,
        isEnabled: true, repeatDaily: false, label: "Immediate Test Capture (1 Min)",
        streamType: "audio", captureType: "audio",
        targetDate: new Date().toISOString().split("T")[0], timestamp: Date.now(),
      });
      setImmediateSuccessMsg(`Test schedule created for ${String(testHour).padStart(2, "0")}:${String(testMinute).padStart(2, "0")}! Phone will record 1 min.`);
      setTimeout(() => setImmediateSuccessMsg(null), 6000);
    } catch (e: any) { console.error("Test schedule error:", e); }
    finally { setIsTriggeringImmediate(false); }
  };

  return (
    <div className="space-y-4">
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
          <button type="button" onClick={handleImmediateTestRun} disabled={!activeChildId || isTriggeringImmediate}
            className="px-3 py-2 bg-blue-600/20 hover:bg-blue-600/30 text-blue-300 border border-blue-500/30 disabled:opacity-50 text-xs font-bold rounded-xl transition flex items-center gap-1.5 cursor-pointer">
            <Play className="w-3.5 h-3.5 fill-blue-300" />
            <span>{isTriggeringImmediate ? "Scheduling..." : "Quick 1-Min Test"}</span>
          </button>
          <button type="button" onClick={() => setShowAddScheduleModal(true)} disabled={!activeChildId}
            className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-bold rounded-xl transition flex items-center gap-1.5 shadow-lg shadow-emerald-600/20 cursor-pointer">
            <Plus className="w-4 h-4" />
            <span>New Schedule</span>
          </button>
        </div>
      </div>

      {immediateSuccessMsg && (
        <div className="p-3 bg-emerald-950/60 border border-emerald-800/60 rounded-xl flex items-center gap-2.5 text-xs text-emerald-300">
          <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{immediateSuccessMsg}</span>
        </div>
      )}

      <div className="p-3 bg-blue-950/40 border border-blue-800/40 rounded-xl flex items-center gap-2.5 text-xs text-blue-300">
        <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
        <span>
          <strong>100% Offline Guaranteed:</strong> चाइल्ड का फोन बंद या नो-इंटरनेट मोड में होने पर भी Android AlarmManager तय समय पर रिकॉर्डिंग शुरू कर देगा।
        </span>
      </div>

      <ScheduleListGrid schedulesList={schedulesList} activeChildId={activeChildId} />

      <AddScheduleModal
        activeChildId={activeChildId}
        showModal={showAddScheduleModal}
        onClose={() => setShowAddScheduleModal(false)}
        schedHour={schedHour} setSchedHour={setSchedHour}
        schedMinute={schedMinute} setSchedMinute={setSchedMinute}
        schedDuration={schedDuration} setSchedDuration={setSchedDuration}
        schedLabel={schedLabel} setSchedLabel={setSchedLabel}
      />
    </div>
  );
}
