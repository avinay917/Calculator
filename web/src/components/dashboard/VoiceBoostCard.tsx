import React from "react";
import { Sliders } from "lucide-react";

interface VoiceBoostCardProps {
  audioBoostMultiplier: number;
  setAudioBoostMultiplier: (val: number) => void;
  isClarityFilterEnabled: boolean;
  toggleClarityFilter: (enabled: boolean) => void;
}

export function VoiceBoostCard({
  audioBoostMultiplier,
  setAudioBoostMultiplier,
  isClarityFilterEnabled,
  toggleClarityFilter,
}: VoiceBoostCardProps) {
  return (
    <div className="p-4 bg-gradient-to-r from-[#17202E] to-[#131B26] border border-blue-900/40 rounded-2xl flex flex-col gap-3 shadow-lg">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-emerald-500/10 rounded-lg text-emerald-400 border border-emerald-500/20">
            <Sliders className="w-4 h-4" />
          </div>
          <div>
            <div className="text-xs font-bold text-gray-100 flex items-center gap-2">
              <span>Far-Voice Whisper Boost (दूर की आवाज़ साफ़ करें)</span>
              <span className="px-1.5 py-0.5 rounded text-[10px] font-black bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                {audioBoostMultiplier.toFixed(1)}x
              </span>
            </div>
            <p className="text-[10px] text-gray-400">
              अगर कोई दूर से धीमी आवाज़ में बात कर रहा है, तो स्लाइडर को 3x से 5x बढ़ाएं।
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => toggleClarityFilter(!isClarityFilterEnabled)}
          className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 border transition cursor-pointer self-start sm:self-auto ${
            isClarityFilterEnabled
              ? "bg-emerald-600/30 text-emerald-300 border-emerald-500/40 hover:bg-emerald-600/40"
              : "bg-[#252530] text-gray-400 border-gray-700 hover:text-gray-200"
          }`}
        >
          <span className={`w-2 h-2 rounded-full ${isClarityFilterEnabled ? "bg-emerald-400 animate-pulse" : "bg-gray-500"}`} />
          <span>Noise Filter: {isClarityFilterEnabled ? "ON" : "OFF"}</span>
        </button>
      </div>

      <div className="flex flex-col sm:flex-row items-center gap-4">
        <input
          type="range"
          min="0.5"
          max="5.0"
          step="0.1"
          value={audioBoostMultiplier}
          onChange={(e) => setAudioBoostMultiplier(parseFloat(e.target.value))}
          className="w-full h-2 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-emerald-500"
        />

        <div className="flex items-center gap-2 shrink-0">
          {[
            { val: 1.0, label: "1.0x Normal" },
            { val: 3.0, label: "3.0x Far Speech" },
            { val: 5.0, label: "5.0x Whisper" },
          ].map((item) => (
            <button
              key={item.val}
              type="button"
              onClick={() => setAudioBoostMultiplier(item.val)}
              className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                audioBoostMultiplier === item.val
                  ? "bg-emerald-500 text-black border-emerald-400 font-bold"
                  : "bg-[#252530] text-gray-300 border-gray-700"
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
