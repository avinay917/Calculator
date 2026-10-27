"use client";

import React, { useState } from "react";
import { Phone, Music, Download } from "lucide-react";

export function RecordingsTab({ recordings }: { recordings: any[] }) {
  const [recFilter, setRecFilter] = useState<"ALL" | "CALL" | "SCHEDULED" | "LIVE">("ALL");

  const callCount = recordings.filter((r) => r.type === "call" || r.streamType === "call").length;
  const schedCount = recordings.filter(
    (r) =>
      r.type === "scheduled" ||
      r.streamType === "scheduled" ||
      (r.localFilePath && r.localFilePath.includes("scheduled"))
  ).length;
  const liveCount = recordings.filter(
    (r) =>
      r.type !== "call" &&
      r.streamType !== "call" &&
      r.type !== "scheduled" &&
      r.streamType !== "scheduled" &&
      !(r.localFilePath && r.localFilePath.includes("scheduled")) &&
      !r.streamType?.includes("photo")
  ).length;

  const filtered = recordings.filter((rec) => {
    const isCall = rec.type === "call" || rec.streamType === "call";
    const isScheduled =
      rec.type === "scheduled" ||
      rec.streamType === "scheduled" ||
      (rec.localFilePath && rec.localFilePath.includes("scheduled"));
    if (recFilter === "CALL") return isCall;
    if (recFilter === "SCHEDULED") return isScheduled;
    if (recFilter === "LIVE") return !isCall && !isScheduled && !rec.streamType?.includes("photo");
    return true;
  });

  return (
    <div className="space-y-3 mt-2">
      {/* Filter Chips Bar */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1">
        <button
          type="button"
          onClick={() => setRecFilter("ALL")}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition shrink-0 ${
            recFilter === "ALL"
              ? "bg-blue-600 text-white shadow-md shadow-blue-600/30"
              : "bg-[#1E1E26] text-gray-400 hover:text-white"
          }`}
        >
          All ({recordings.length})
        </button>
        <button
          type="button"
          onClick={() => setRecFilter("CALL")}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition shrink-0 ${
            recFilter === "CALL"
              ? "bg-blue-600 text-white shadow-md shadow-blue-600/30"
              : "bg-[#1E1E26] text-gray-400 hover:text-white"
          }`}
        >
          <Phone className="w-3 h-3 text-sky-400" />
          <span>Calls ({callCount})</span>
        </button>
        <button
          type="button"
          onClick={() => setRecFilter("SCHEDULED")}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition shrink-0 ${
            recFilter === "SCHEDULED"
              ? "bg-purple-600 text-white shadow-md shadow-purple-600/30"
              : "bg-[#1E1E26] text-gray-400 hover:text-white"
          }`}
        >
          <Music className="w-3 h-3 text-purple-400" />
          <span>Scheduled ({schedCount})</span>
        </button>
        <button
          type="button"
          onClick={() => setRecFilter("LIVE")}
          className={`px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition shrink-0 ${
            recFilter === "LIVE"
              ? "bg-red-600 text-white shadow-md shadow-red-600/30"
              : "bg-[#1E1E26] text-gray-400 hover:text-white"
          }`}
        >
          <span className="w-2 h-2 rounded-full bg-red-400" />
          <span>Live Stream ({liveCount})</span>
        </button>
      </div>

      {/* List Content */}
      {filtered.length === 0 ? (
        <p className="text-center py-10 text-xs text-gray-500">
          {recFilter === "ALL"
            ? "No audio recordings saved yet."
            : `No ${recFilter.toLowerCase()} recordings found.`}
        </p>
      ) : (
        filtered.map((rec, idx) => {
          const isCall = rec.type === "call" || rec.streamType === "call";
          const isScheduled =
            rec.type === "scheduled" ||
            rec.streamType === "scheduled" ||
            (rec.localFilePath && rec.localFilePath.includes("scheduled"));
          const audioUrl = rec.downloadUrl || rec.storageUrl;

          return (
            <div
              key={rec.id || idx}
              className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-2 text-xs"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span
                    className={`text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wider ${
                      isCall
                        ? "bg-sky-950/70 text-sky-400 border border-sky-800/40"
                        : isScheduled
                        ? "bg-purple-950/70 text-purple-400 border border-purple-800/40"
                        : "bg-rose-950/70 text-rose-400 border border-rose-800/40"
                    }`}
                  >
                    {isCall ? "📞 Call Recording" : isScheduled ? "⏰ Scheduled Recording" : "🔴 Live Recording"}
                  </span>
                  {rec.label && <span className="text-[11px] text-gray-300 font-medium">{rec.label}</span>}
                </div>
                <span className="text-[10px] text-gray-500">
                  {rec.timestamp ? new Date(rec.timestamp).toLocaleString() : ""}
                </span>
              </div>

              {audioUrl ? (
                <div className="flex flex-col gap-1.5 mt-1">
                  <audio controls src={audioUrl} className="w-full h-8 rounded bg-[#121217]" preload="metadata" />
                  <div className="flex items-center justify-between text-[10px] text-gray-400">
                    <span>
                      {rec.durationSeconds || rec.duration ? `Duration: ${rec.durationSeconds || rec.duration}s` : ""}
                    </span>
                    <a
                      href={audioUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      download
                      className="text-blue-400 hover:underline flex items-center gap-1 font-medium"
                    >
                      <Download className="w-3 h-3" />
                      <span>Download to Laptop / PC</span>
                    </a>
                  </div>
                </div>
              ) : (
                <p className="text-[11px] text-gray-400">Audio link preparing or unavailable</p>
              )}
            </div>
          );
        })
      )}
    </div>
  );
}
