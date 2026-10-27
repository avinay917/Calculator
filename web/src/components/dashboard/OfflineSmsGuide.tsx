import React, { useState } from "react";
import { MessageSquare, Copy } from "lucide-react";

interface OfflineSmsGuideProps {
  simInfo: any;
}

export function OfflineSmsGuide({ simInfo }: OfflineSmsGuideProps) {
  const [copiedCmd, setCopiedCmd] = useState<string | null>(null);

  const copyToClipboard = (text: string) => {
    if (typeof navigator !== "undefined" && navigator.clipboard) {
      navigator.clipboard.writeText(text);
      setCopiedCmd(text);
      setTimeout(() => setCopiedCmd(null), 2500);
    }
  };

  return (
    <div className="p-4 bg-[#14141A] border border-[#262632] rounded-xl flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-emerald-600/20 text-emerald-400 rounded-lg">
            <MessageSquare className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-xs font-bold text-white">Offline SMS Commands (जब Internet बंद हो)</h3>
            <p className="text-[11px] text-gray-400">Child phone par SMS bhej kar Internet, GPS & Siren chalu karein</p>
          </div>
        </div>
        {copiedCmd && (
          <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
            Copied!
          </span>
        )}
      </div>

      <div className="space-y-2.5">
        <div className="p-3 bg-[#1B1B24] border border-[#2B2B38] rounded-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2.5">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-600/20 text-emerald-300 border border-emerald-500/30">
                INTERNET ON
              </span>
              <span className="text-xs font-mono font-bold text-white">SMS: "apple"</span>
            </div>
            <p className="text-[11px] text-gray-400 mt-1">SMS bhejne par phone mobile data & Wi-Fi on kar deta hai.</p>
          </div>
          <button
            type="button"
            onClick={() => copyToClipboard("apple")}
            className="px-2.5 py-1 bg-[#252530] hover:bg-[#303040] text-gray-300 rounded-lg text-[11px] font-medium flex items-center gap-1 transition"
          >
            <Copy className="w-3 h-3" /> <span>Copy</span>
          </button>
        </div>

        <div className="p-3 bg-[#1B1B24] border border-[#2B2B38] rounded-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2.5">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-600/20 text-blue-300 border border-blue-500/30">
                GPS LOCATION
              </span>
              <span className="text-xs font-mono font-bold text-white">SMS: "#CALC#LOC"</span>
            </div>
            <p className="text-[11px] text-gray-400 mt-1">Live GPS capture karke Maps link ke sath reply aayega.</p>
          </div>
          <button
            type="button"
            onClick={() => copyToClipboard("#CALC#LOC")}
            className="px-2.5 py-1 bg-[#252530] hover:bg-[#303040] text-gray-300 rounded-lg text-[11px] font-medium flex items-center gap-1 transition"
          >
            <Copy className="w-3 h-3" /> <span>Copy</span>
          </button>
        </div>

        <div className="p-3 bg-[#1B1B24] border border-[#2B2B38] rounded-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2.5">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-red-600/20 text-red-300 border border-red-500/30">
                EMERGENCY SIREN
              </span>
              <span className="text-xs font-mono font-bold text-white">SMS: "#CALC#SIREN"</span>
            </div>
            <p className="text-[11px] text-gray-400 mt-1">Silent mode me bhi 100% full volume siren bajega.</p>
          </div>
          <button
            type="button"
            onClick={() => copyToClipboard("#CALC#SIREN")}
            className="px-2.5 py-1 bg-[#252530] hover:bg-[#303040] text-gray-300 rounded-lg text-[11px] font-medium flex items-center gap-1 transition"
          >
            <Copy className="w-3 h-3" /> <span>Copy</span>
          </button>
        </div>
      </div>
    </div>
  );
}
