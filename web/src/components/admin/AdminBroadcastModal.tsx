import React, { useState } from "react";
import { X, Radio, Send, AlertTriangle } from "lucide-react";

interface BroadcastModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSend: (title: string, message: string, targetRole: "all" | "parent" | "child") => Promise<void>;
}

export function AdminBroadcastModal({ isOpen, onClose, onSend }: BroadcastModalProps) {
  const [title, setTitle] = useState("");
  const [message, setMessage] = useState("");
  const [target, setTarget] = useState<"all" | "parent" | "child">("all");
  const [sending, setSending] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !message.trim()) return;
    setSending(true);
    try {
      await onSend(title.trim(), message.trim(), target);
      setTitle("");
      setMessage("");
      onClose();
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in">
      <div className="bg-[#181822] border border-[#2d2d3d] w-full max-w-md rounded-2xl shadow-2xl overflow-hidden">
        <div className="p-4 border-b border-[#2d2d3d] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Radio className="w-5 h-5 text-amber-400" />
            <h3 className="font-bold text-white text-sm">Send Global System Announcement</h3>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-[#252535] rounded-lg text-gray-400">
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-4 space-y-3.5 text-xs">
          <div>
            <label className="block text-gray-300 font-semibold mb-1">Target Audience</label>
            <div className="grid grid-cols-3 gap-2">
              {(["all", "parent", "child"] as const).map((r) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => setTarget(r)}
                  className={`py-1.5 rounded-lg font-bold uppercase text-[10px] border transition-all ${
                    target === r
                      ? "bg-amber-500/20 border-amber-500/50 text-amber-300"
                      : "bg-[#131318] border-[#262633] text-gray-400 hover:border-gray-600"
                  }`}
                >
                  {r === "all" ? "All Users" : r === "parent" ? "Parents Only" : "Children Only"}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-gray-300 font-semibold mb-1">Announcement Title</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. System Maintenance Notice"
              className="w-full bg-[#121217] border border-[#272736] rounded-xl px-3 py-2 text-white placeholder-gray-500 outline-none focus:border-amber-500"
              required
            />
          </div>

          <div>
            <label className="block text-gray-300 font-semibold mb-1">Message Content</label>
            <textarea
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="Type your message to all users here..."
              rows={3}
              className="w-full bg-[#121217] border border-[#272736] rounded-xl p-3 text-white placeholder-gray-500 outline-none focus:border-amber-500 resize-none"
              required
            />
          </div>

          <div className="flex items-center gap-2 p-2.5 bg-amber-500/10 border border-amber-500/20 rounded-xl text-amber-300 text-[11px]">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>This announcement will be pushed live to the selected devices immediately.</span>
          </div>

          <div className="flex items-center justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-1.5 rounded-xl bg-[#22222e] hover:bg-[#2b2b3a] text-gray-300 font-semibold transition-all"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={sending}
              className="flex items-center gap-1.5 px-4 py-1.5 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-600 hover:to-amber-700 text-white font-bold rounded-xl shadow-md transition-all disabled:opacity-50"
            >
              <Send className="w-3.5 h-3.5" />
              {sending ? "Broadcasting..." : "Send Announcement"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
