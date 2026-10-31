import React from "react";
import { Smartphone, RefreshCw, AlertTriangle, CheckCircle, Shield } from "lucide-react";
import { useApp } from "@/context/AppContext";

export function PairingTabContent() {
  const { commands, setActiveTab } = useApp();
  const {
    childPairingInput, setChildPairingInput,
    pairingError, pairingSuccess, isVerifyingCode,
    handleLinkChildWithCode, generatedPairingCode, handleGeneratePairingCode,
  } = commands;

  return (
    <div className="p-6 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl flex flex-col items-center text-center max-w-md mx-auto">
      <div className="p-3 bg-blue-600/20 text-blue-400 rounded-full mb-3">
        <Smartphone className="w-8 h-8" />
      </div>
      <h2 className="text-base font-bold text-white">Pair Child Device</h2>
      <p className="text-xs text-gray-400 mt-1 mb-5">
        Enter the 6-digit Pairing Code displayed on the Child Phone Calculator dashboard.
      </p>

      <div className="w-full bg-[#14141A] border border-[#2B2B36] rounded-2xl p-4 mb-4">
        <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2 text-left">
          Child 6-Digit Pairing Code
        </label>
        <input
          type="text" maxLength={6} value={childPairingInput}
          onChange={(e) => setChildPairingInput(e.target.value.replace(/\D/g, "").slice(0, 6))}
          placeholder="e.g. 582914"
          className="w-full bg-[#1E1E26] border border-[#3A3A4D] rounded-xl py-3 px-4 text-center font-mono text-2xl font-bold tracking-widest text-white placeholder-gray-600 focus:outline-none focus:border-blue-500 transition"
        />
        {pairingError && (
          <p className="text-xs text-red-400 mt-2 font-medium flex items-center justify-center gap-1">
            <AlertTriangle className="w-3.5 h-3.5" />{pairingError}
          </p>
        )}
        {pairingSuccess && (
          <p className="text-xs text-green-400 mt-2 font-medium flex items-center justify-center gap-1">
            <CheckCircle className="w-3.5 h-3.5" />{pairingSuccess}
          </p>
        )}
        <button
          onClick={() => handleLinkChildWithCode(() => setActiveTab("live"))}
          disabled={isVerifyingCode || childPairingInput.length !== 6}
          className="w-full mt-4 py-3 bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-blue-600/20 flex items-center justify-center gap-2"
        >
          {isVerifyingCode ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Shield className="w-4 h-4" />}
          Verify & Link Device
        </button>
      </div>

      <div className="w-full text-left bg-[#14141A]/60 border border-[#262632] rounded-xl p-3 text-[11px] text-gray-400 space-y-1 mb-4">
        <p className="font-semibold text-gray-300">Quick Pairing Steps:</p>
        <p>1. Open Calculator app on Child phone & enter PIN (=).</p>
        <p>2. See the permanent 6-digit Pairing Code or QR Code.</p>
        <p>3. Enter code above to link permanently.</p>
        <p>4. Device cannot be unpaired by child; only parent can unpair.</p>
      </div>

      <div className="pt-3 border-t border-[#262632] w-full">
        <button onClick={handleGeneratePairingCode} className="text-[11px] text-blue-400 hover:text-blue-300 underline flex items-center justify-center gap-1 mx-auto">
          <RefreshCw className="w-3 h-3" />Or generate parent pairing code (legacy)
        </button>
        {generatedPairingCode && (
          <div className="mt-2 py-2 px-4 bg-[#14141A] border border-[#333342] rounded-xl inline-block">
            <span className="text-lg font-mono font-bold tracking-wider text-blue-400">{generatedPairingCode}</span>
          </div>
        )}
      </div>
    </div>
  );
}
