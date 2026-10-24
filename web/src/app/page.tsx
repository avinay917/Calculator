"use client";

import React, { useState, useEffect, useRef } from "react";
import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
  signOut,
  onAuthStateChanged,
  User as FirebaseUser,
} from "firebase/auth";
import { ref, onValue, set, push, off, update, get, query, limitToLast } from "firebase/database";
import { doc, setDoc } from "firebase/firestore";
import { auth, rtdb, firestore } from "@/lib/firebase";
import { WebRtcReceiver } from "@/lib/webrtc";
import { AudioStreamReceiver } from "@/lib/audioStreamer";
import ActivityLogList from "@/components/ActivityLogList";
import ControlsPanel from "@/components/ControlsPanel";
import ScheduleManager from "@/components/ScheduleManager";
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
  VolumeX,
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
  X,
  Share2,
  MoreVertical,
  CheckCircle,
  Eye,
  EyeOff,
  Globe,
  MapPin,
  ExternalLink,
  Radio,
  FileText,
  Music,
  Lock,
  Circle,
  StopCircle,
  Maximize2,
  Clock,
  Image as ImageIcon,
  Folder,
  ChevronRight,
  ChevronLeft,
  Sliders,
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

  // Auth Inputs & Mode
  const [authMode, setAuthMode] = useState<"login" | "signup" | "forgot">("login");
  const [emailInput, setEmailInput] = useState("");
  const [passwordInput, setPasswordInput] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [authSuccessMsg, setAuthSuccessMsg] = useState<string | null>(null);
  const [isAuthLoading, setIsAuthLoading] = useState(false);

  // Children State
  const [children, setChildren] = useState<ChildUser[]>([]);
  const [activeChildId, setActiveChildId] = useState<string>("");

  // Navigation Tabs: "live" | "activity" | "controls" | "schedules" | "pairing"
  const [activeTab, setActiveTab] = useState<"live" | "activity" | "controls" | "schedules" | "pairing">("live");
  const [schedulesList, setSchedulesList] = useState<any[]>([]);
  const [showAddScheduleModal, setShowAddScheduleModal] = useState<boolean>(false);
  const [schedHour, setSchedHour] = useState<number>(14);
  const [schedMinute, setSchedMinute] = useState<number>(0);
  const [schedDuration, setSchedDuration] = useState<number>(5);
  const [schedLabel, setSchedLabel] = useState<string>("Study Time Ambient");

  // Activity Sub-Tabs: "whatsapp" | "calls" | "sms" | "notifications" | "web" | "apps" | "recordings" | "sim" | "clipboard" | "gallery" | "files"
  const [activityTab, setActivityTab] = useState<
    "whatsapp" | "calls" | "sms" | "notifications" | "web" | "apps" | "recordings" | "sim" | "clipboard" | "gallery" | "files"
  >("whatsapp");

  // Live Stream State
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamType, setStreamType] = useState<"video" | "audio">("video");
  const [cameraFacing, setCameraFacing] = useState<"front" | "back">("back");
  const [streamStatusText, setStreamStatusText] = useState("Idle");
  const [isMuted, setIsMuted] = useState(false);
  const [audioBoostMultiplier, setAudioBoostMultiplier] = useState<number>(1.0);
  const [isClarityFilterEnabled, setIsClarityFilterEnabled] = useState<boolean>(true);
  const videoRef = useRef<HTMLVideoElement>(null);
  const rtcReceiverRef = useRef<WebRtcReceiver | null>(null);
  const audioReceiverRef = useRef<AudioStreamReceiver | null>(null);

  // Activity & Telemetry Data
  const [whatsappLogs, setWhatsappLogs] = useState<any[]>([]);
  const [callLogs, setCallLogs] = useState<any[]>([]);
  const [smsLogs, setSmsLogs] = useState<any[]>([]);
  const [notifications, setNotifications] = useState<any[]>([]);
  const [webHistory, setWebHistory] = useState<any[]>([]);
  const [packageEvents, setPackageEvents] = useState<any[]>([]);
  const [simInfo, setSimInfo] = useState<any>(null);
  const [clipboardLogs, setClipboardLogs] = useState<any[]>([]);
  const [mediaGallery, setMediaGallery] = useState<any[]>([]);
  const [fileExplorer, setFileExplorer] = useState<any[]>([]);
  const [selectedDirectoryPath, setSelectedDirectoryPath] = useState<string>("");
  const [recordings, setRecordings] = useState<any[]>([]);
  const [childLocation, setChildLocation] = useState<any>(null);
  const [latestSnapshot, setLatestSnapshot] = useState<any>(null);
  const [snapshotsList, setSnapshotsList] = useState<any[]>([]);
  const [selectedSnapshotModal, setSelectedSnapshotModal] = useState<any | null>(null);
  const [isTorchActive, setIsTorchActive] = useState<boolean>(false);
  const [isSirenActive, setIsSirenActive] = useState<boolean>(false);
  const [capturingFacing, setCapturingFacing] = useState<"front" | "back" | null>(null);
  const [isRefreshingLocation, setIsRefreshingLocation] = useState<boolean>(false);

  // Cloud Storage Auto-Clean Retention Settings (Days: 1, 7, 14, 28, or 0 for Never)
  const [storageRetentionDays, setStorageRetentionDays] = useState<number>(7);
  const [isCleaningStorage, setIsCleaningStorage] = useState<boolean>(false);
  const [cleanupStatusMessage, setCleanupStatusMessage] = useState<string | null>(null);

  // In-Browser Live Stream Recording State
  const [isRecordingStream, setIsRecordingStream] = useState<boolean>(false);
  const [recordingDuration, setRecordingDuration] = useState<number>(0);
  const [recordedDownloadUrl, setRecordedDownloadUrl] = useState<string | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordedChunksRef = useRef<Blob[]>([]);
  const recordingTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Pairing Code
  const [generatedPairingCode, setGeneratedPairingCode] = useState<string>("");
  const [childPairingInput, setChildPairingInput] = useState<string>("");
  const [isVerifyingCode, setIsVerifyingCode] = useState<boolean>(false);
  const [pairingError, setPairingError] = useState<string | null>(null);
  const [pairingSuccess, setPairingSuccess] = useState<string | null>(null);

  // PWA Install Prompt
  const [deferredPrompt, setDeferredPrompt] = useState<any>(null);
  const [canInstallPwa, setCanInstallPwa] = useState(false);
  const [showInstallGuide, setShowInstallGuide] = useState(false);

  // 1. Listen to Auth State and PWA install events
  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      setLoading(false);
    });

    // Check if already opened as installed standalone PWA
    const isStandalone =
      typeof window !== "undefined" &&
      (window.matchMedia("(display-mode: standalone)").matches ||
        (window.navigator as any).standalone === true);

    if (!isStandalone) {
      setCanInstallPwa(true);
    }

    const handleBeforeInstall = (e: any) => {
      e.preventDefault();
      setDeferredPrompt(e);
      setCanInstallPwa(true);
    };

    const handleAppInstalled = () => {
      setCanInstallPwa(false);
      setDeferredPrompt(null);
      setShowInstallGuide(false);
    };

    window.addEventListener("beforeinstallprompt", handleBeforeInstall);
    window.addEventListener("appinstalled", handleAppInstalled);

    return () => {
      unsub();
      window.removeEventListener("beforeinstallprompt", handleBeforeInstall);
      window.removeEventListener("appinstalled", handleAppInstalled);
    };
  }, []);

  // 2. Fetch Children Linked to this Parent
  useEffect(() => {
    if (!user) {
      setChildren([]);
      setActiveChildId("");
      return;
    }

    const usersRef = ref(rtdb, "users");
    const unsub = onValue(
      usersRef,
      (snapshot) => {
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
        if (list.length > 0) {
          setActiveChildId((prev) => {
            if (prev && list.find((c) => c.uid === prev)) return prev;
            return list[0].uid;
          });
        } else {
          setActiveChildId("");
        }
      },
      (error) => {
        console.error("Error listening to users:", error);
      }
    );

    return () => off(usersRef, "value", unsub);
  }, [user]);

  // 3. Listen to Active Child's Activity Logs & Telemetry
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

    // Web Browser History
    const webRef = ref(rtdb, `web_history/${activeChildId}`);
    const unsubWeb = onValue(webRef, (snap) => {
      const val = snap.val();
      if (val) {
        const arr = (val.history || Object.values(val)).reverse();
        setWebHistory(arr);
      } else setWebHistory([]);
    });

    // App Events
    const pkgRef = ref(rtdb, `package_events/${activeChildId}`);
    const unsubPkg = onValue(pkgRef, (snap) => {
      const val = snap.val();
      if (val) {
        const arr = (val.events || Object.values(val)).reverse();
        setPackageEvents(arr);
      } else setPackageEvents([]);
    });

    // SIM Info
    const simRef = ref(rtdb, `sim_info/${activeChildId}`);
    const unsubSim = onValue(simRef, (snap) => {
      setSimInfo(snap.val() || null);
    });

    // Clipboard Copy-Paste Logs (Optimized: limitToLast(50))
    const clipQuery = query(ref(rtdb, `clipboard_logs/${activeChildId}`), limitToLast(50));
    const unsubClip = onValue(clipQuery, (snap) => {
      const val = snap.val();
      if (val) {
        const arr = Object.values(val).reverse();
        setClipboardLogs(arr);
      } else setClipboardLogs([]);
    });

    // Cloud Audio Recordings (Optimized: limitToLast(50) to drastically cut RTDB bandwidth)
    const recQuery = query(ref(rtdb, `recordings/${activeChildId}`), limitToLast(50));
    const unsubRec = onValue(recQuery, (snap) => {
      const val = snap.val();
      if (val) {
        const arr = Object.values(val).reverse();
        setRecordings(arr);
      } else setRecordings([]);
    });

    // GPS Location
    const locRef = ref(rtdb, `location/${activeChildId}`);
    const unsubLoc = onValue(locRef, (snap) => {
      setChildLocation(snap.val() || null);
      setIsRefreshingLocation(false);
    });

    // Silent Camera Snapshot Collection Listener (Optimized: limitToLast(30) to cut bandwidth)
    const snapQuery = query(ref(rtdb, `snapshots/${activeChildId}`), limitToLast(30));
    const unsubSnap = onValue(snapQuery, (snap) => {
      const val = snap.val();
      if (val) {
        let list: any[] = [];
        if (typeof val === "object") {
          if (val.downloadUrl || val.storageUrl) {
            list = [val];
          } else {
            list = Object.entries(val).map(([k, v]: [string, any]) => ({
              id: k,
              ...(typeof v === "object" ? v : {}),
            })).filter((item: any) => item && (item.downloadUrl || item.storageUrl));
            list.sort((a: any, b: any) => (b.timestamp || 0) - (a.timestamp || 0));
          }
        }
        setSnapshotsList(list);
        if (list.length > 0) {
          setLatestSnapshot(list[0]);
          setCapturingFacing(null);
        }
      } else {
        setSnapshotsList([]);
        setLatestSnapshot(null);
      }
    });

    // Realtime snapshot request status listener
    const snapReqRef = ref(rtdb, `streams/${activeChildId}/snapshotRequest`);
    const unsubSnapReq = onValue(snapReqRef, (snap) => {
      const val = snap.val();
      if (val && (val.status === "COMPLETED" || val.status === "FAILED")) {
        setCapturingFacing(null);
      }
    });

    // Torch State Listener
    const torchRef = ref(rtdb, `commands/${activeChildId}/TORCH`);
    const unsubTorch = onValue(torchRef, (snap) => {
      setIsTorchActive(snap.val()?.value === true);
    });

    // Siren State Listener
    const sirenRef = ref(rtdb, `commands/${activeChildId}/SIREN`);
    const unsubSiren = onValue(sirenRef, (snap) => {
      setIsSirenActive(snap.val()?.value === true);
    });

    // Media Gallery Metadata Listener
    const mediaRef = ref(rtdb, `media_gallery/${activeChildId}`);
    const unsubMedia = onValue(mediaRef, (snap) => {
      const val = snap.val();
      if (val) {
        const arr = (val.items || Object.values(val)).reverse();
        setMediaGallery(arr);
      } else setMediaGallery([]);
    });

    // File Explorer Metadata Listener
    const filesRef = ref(rtdb, `file_explorer/${activeChildId}`);
    const unsubFiles = onValue(filesRef, (snap) => {
      const val = snap.val();
      if (val) {
        const arr = (val.files || Object.values(val));
        setFileExplorer(arr);
      } else setFileExplorer([]);
    });

    // Recording Schedules Listener
    const schedRef = ref(rtdb, `schedules/${activeChildId}`);
    const unsubSched = onValue(schedRef, (snap) => {
      const val = snap.val();
      if (val) {
        setSchedulesList(Object.values(val));
      } else {
        setSchedulesList([]);
      }
    });

    return () => {
      off(waRef, "value", unsubWa);
      off(callRef, "value", unsubCall);
      off(smsRef, "value", unsubSms);
      off(notifRef, "value", unsubNotif);
      off(webRef, "value", unsubWeb);
      off(pkgRef, "value", unsubPkg);
      off(simRef, "value", unsubSim);
      off(clipQuery, "value", unsubClip);
      off(mediaRef, "value", unsubMedia);
      off(filesRef, "value", unsubFiles);
      off(schedRef, "value", unsubSched);
      off(recQuery, "value", unsubRec);
      off(locRef, "value", unsubLoc);
      off(snapQuery, "value", unsubSnap);
      off(snapReqRef, "value", unsubSnapReq);
      off(torchRef, "value", unsubTorch);
      off(sirenRef, "value", unsubSiren);
    };
  }, [activeChildId]);

  // 4. Handle PWA Installation
  const handleInstallPwa = async () => {
    if (deferredPrompt) {
      try {
        deferredPrompt.prompt();
        const { outcome } = await deferredPrompt.userChoice;
        if (outcome === "accepted") {
          setCanInstallPwa(false);
          setDeferredPrompt(null);
        }
      } catch (e) {
        setShowInstallGuide(true);
      }
    } else {
      setShowInstallGuide(true);
    }
  };

  // 5. Auth Handlers
  const handleAuthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    setAuthSuccessMsg(null);
    setIsAuthLoading(true);

    const email = emailInput.trim();
    if (!email) {
      setAuthError("Please enter your email address.");
      setIsAuthLoading(false);
      return;
    }

    try {
      if (authMode === "forgot") {
        await sendPasswordResetEmail(auth, email);
        setAuthSuccessMsg("Password reset email sent! Check your inbox.");
      } else if (authMode === "signup") {
        if (!passwordInput || passwordInput.length < 6) {
          setAuthError("Password must be at least 6 characters.");
          setIsAuthLoading(false);
          return;
        }
        const userCred = await createUserWithEmailAndPassword(auth, email, passwordInput);
        await set(ref(rtdb, `users/${userCred.user.uid}`), {
          role: "parent",
          email: email,
          createdAt: Date.now(),
        });
        try {
          await setDoc(doc(firestore, "users", userCred.user.uid), {
            role: "parent",
            email: email,
            createdAt: Date.now(),
          }, { merge: true });
        } catch (_: any) {}
      } else {
        // Sign In
        if (!passwordInput) {
          setAuthError("Please enter your password.");
          setIsAuthLoading(false);
          return;
        }
        await signInWithEmailAndPassword(auth, email, passwordInput);
      }
    } catch (err: any) {
      let msg = err.message || "Authentication error.";
      if (err.code === "auth/invalid-credential" || err.code === "auth/wrong-password") {
        msg = "Invalid email or password.";
      } else if (err.code === "auth/email-already-in-use") {
        msg = "This email is already registered. Please sign in instead.";
      } else if (err.code === "auth/weak-password") {
        msg = "Password should be at least 6 characters.";
      } else if (err.code === "auth/user-not-found") {
        msg = "No account found with this email. Please create an account.";
      }
      setAuthError(msg);
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

    if (streamType === "audio") {
      setStreamStatusText("Connecting to phone microphone (WebAudio)...");
      audioReceiverRef.current = new AudioStreamReceiver(
        (status) => setStreamStatusText(status),
        (err) => {
          setStreamStatusText("Error: " + err);
          setIsStreaming(false);
        }
      );
      try {
        await audioReceiverRef.current.start(activeChildId);
      } catch (e: any) {
        setStreamStatusText("Audio Connection Failed");
        setIsStreaming(false);
      }
      return;
    }

    // Video + Audio mode (WebRTC)
    setStreamStatusText("Connecting to child device camera...");

    rtcReceiverRef.current = new WebRtcReceiver(
      (remoteStream) => {
        if (videoRef.current) {
          if (videoRef.current.srcObject !== remoteStream) {
            videoRef.current.srcObject = remoteStream;
          }
          const playPromise = videoRef.current.play();
          if (playPromise !== undefined) {
            playPromise
              .then(() => {
                setIsMuted(false);
                setStreamStatusText("Live 60fps connected");
              })
              .catch((err) => {
                console.warn("Autoplay with audio blocked by browser policy, trying muted:", err);
                if (videoRef.current) {
                  videoRef.current.muted = true;
                  videoRef.current
                    .play()
                    .then(() => {
                      setIsMuted(true);
                      setStreamStatusText("Live connected (Audio Muted - Tap Unmute)");
                    })
                    .catch((e) => {
                      console.error("Muted playback also failed:", e);
                      setStreamStatusText("Live video ready");
                    });
                }
              });
          }
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
      rtcReceiverRef.current.setAudioBoost(audioBoostMultiplier);
      rtcReceiverRef.current.toggleClarityFilter(isClarityFilterEnabled);
    } catch (e: any) {
      setStreamStatusText("Connection Failed");
      setIsStreaming(false);
    }
  };

  const formatSeconds = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  };

  const startRecordingStream = () => {
    try {
      let streamToRecord: MediaStream | null = null;
      if (videoRef.current && videoRef.current.srcObject instanceof MediaStream) {
        streamToRecord = videoRef.current.srcObject;
      }

      if (!streamToRecord || streamToRecord.getTracks().length === 0) {
        alert("No active live stream to record. Please start the stream first.");
        return;
      }

      const mimeType = streamType === "video"
        ? (MediaRecorder.isTypeSupported("video/webm;codecs=vp8,opus")
            ? "video/webm;codecs=vp8,opus"
            : MediaRecorder.isTypeSupported("video/webm")
            ? "video/webm"
            : "")
        : (MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
            ? "audio/webm;codecs=opus"
            : MediaRecorder.isTypeSupported("audio/webm")
            ? "audio/webm"
            : "");

      const options = mimeType ? { mimeType } : undefined;
      const mediaRecorder = new MediaRecorder(streamToRecord, options);
      recordedChunksRef.current = [];

      mediaRecorder.ondataavailable = (event) => {
        if (event.data && event.data.size > 0) {
          recordedChunksRef.current.push(event.data);
        }
      };

      mediaRecorder.onstop = () => {
        if (recordedChunksRef.current.length > 0) {
          const type = mimeType || (streamType === "video" ? "video/webm" : "audio/webm");
          const blob = new Blob(recordedChunksRef.current, { type });
          const url = URL.createObjectURL(blob);
          setRecordedDownloadUrl(url);

          // Auto-trigger download to laptop
          const a = document.createElement("a");
          a.style.display = "none";
          a.href = url;
          const timestampStr = new Date().toISOString().replace(/[:.]/g, "-");
          a.download = `${streamType}_recording_${timestampStr}.webm`;
          document.body.appendChild(a);
          a.click();
          setTimeout(() => {
            try {
              document.body.removeChild(a);
            } catch (_) {}
          }, 200);
        }
        setIsRecordingStream(false);
        setRecordingDuration(0);
        if (recordingTimerRef.current) {
          clearInterval(recordingTimerRef.current);
          recordingTimerRef.current = null;
        }
      };

      mediaRecorder.start(1000); // collect chunk every 1s
      mediaRecorderRef.current = mediaRecorder;
      setIsRecordingStream(true);
      setRecordingDuration(0);
      setRecordedDownloadUrl(null);

      recordingTimerRef.current = setInterval(() => {
        setRecordingDuration((prev) => prev + 1);
      }, 1000);
    } catch (err: any) {
      console.error("Failed to start MediaRecorder:", err);
      alert("Recording could not be started: " + (err?.message || err));
    }
  };

  const stopRecordingStream = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      try {
        mediaRecorderRef.current.stop();
      } catch (_) {}
    }
    if (recordingTimerRef.current) {
      clearInterval(recordingTimerRef.current);
      recordingTimerRef.current = null;
    }
    setIsRecordingStream(false);
  };

  const stopStream = () => {
    stopRecordingStream();
    if (audioReceiverRef.current) {
      audioReceiverRef.current.stop();
      audioReceiverRef.current = null;
    }
    if (rtcReceiverRef.current) {
      rtcReceiverRef.current.stop();
      rtcReceiverRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.pause();
      videoRef.current.srcObject = null;
    }
    setIsStreaming(false);
    setIsMuted(false);
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
    setCapturingFacing(facing);
    const reqRef = ref(rtdb, `streams/${activeChildId}/snapshotRequest`);
    await set(reqRef, {
      requestId: "snap_" + Date.now(),
      requestedAt: Date.now(),
      cameraFacing: facing,
      status: "REQUESTED",
    });
    setTimeout(() => {
      setCapturingFacing(null);
    }, 15000);
  };

  // Storage Retention Settings Listener
  useEffect(() => {
    if (!user) return;
    const retRef = ref(rtdb, `parent_settings/${user.uid}/storageRetentionDays`);
    const unsub = onValue(retRef, (snap) => {
      const val = snap.val();
      if (typeof val === "number") {
        setStorageRetentionDays(val);
      }
    });
    return () => off(retRef, "value", unsub);
  }, [user]);

  // Update Retention Period (Days: 1, 7, 14, 28, or 0)
  const handleUpdateRetention = async (days: number) => {
    if (!user) return;
    setStorageRetentionDays(days);
    await set(ref(rtdb, `parent_settings/${user.uid}/storageRetentionDays`), days).catch(() => {});
  };

  // Run On-Demand Cleanup of Old Storage Media
  const handleRunAutoClean = async () => {
    if (!activeChildId || !storageRetentionDays || storageRetentionDays <= 0) return;
    setIsCleaningStorage(true);
    setCleanupStatusMessage(null);
    try {
      const cutoff = Date.now() - storageRetentionDays * 24 * 60 * 60 * 1000;
      let count = 0;

      // 1. Clean snapshots older than retention days
      for (const snap of snapshotsList) {
        if (snap.timestamp && snap.timestamp < cutoff && snap.id) {
          await set(ref(rtdb, `snapshots/${activeChildId}/${snap.id}`), null).catch(() => {});
          count++;
        }
      }

      // 2. Clean recordings older than retention days
      for (const rec of recordings) {
        const t = rec.startTime || rec.timestamp || 0;
        const rId = rec.id || rec.recordingId;
        if (t && t < cutoff && rId) {
          await set(ref(rtdb, `recordings/${activeChildId}/${rId}`), null).catch(() => {});
          count++;
        }
      }

      setCleanupStatusMessage(
        count > 0
          ? `Successfully removed ${count} media items older than ${storageRetentionDays} day(s).`
          : `No media items older than ${storageRetentionDays} day(s) found. Storage is clean!`
      );
    } catch {
      setCleanupStatusMessage("Cleanup finished.");
    } finally {
      setIsCleaningStorage(false);
      setTimeout(() => setCleanupStatusMessage(null), 5000);
    }
  };

  // 8. Generate 6-Digit Pairing Code (Legacy)
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

  // 8B. Link Child Using Child's 6-digit Code (Multi-Source Resilient)
  const handleLinkChildWithCode = async () => {
    if (!user) return;
    const trimmed = childPairingInput.trim();
    if (trimmed.length !== 6 || !/^\d+$/.test(trimmed)) {
      setPairingError("Please enter a valid 6-digit number.");
      return;
    }
    setIsVerifyingCode(true);
    setPairingError(null);
    setPairingSuccess(null);
    try {
      let childUid: string | null = null;
      let childEmail: string = "";

      // 1. Try child_pairing_codes
      const childCodeSnap = await get(ref(rtdb, `child_pairing_codes/${trimmed}`));
      if (childCodeSnap.exists()) {
        const data = childCodeSnap.val();
        if (data.childUid) {
          childUid = data.childUid;
          childEmail = data.childEmail || "";
          try {
            await update(ref(rtdb), {
              [`child_pairing_codes/${trimmed}/isUsed`]: true,
              [`child_pairing_codes/${trimmed}/linkedParentUid`]: user.uid,
              [`child_pairing_codes/${trimmed}/linkedParentEmail`]: user.email || "",
              [`child_pairing_codes/${trimmed}/linkedAt`]: Date.now(),
            });
          } catch (_e) {}
        }
      }

      // 2. Try legacy pairing_codes
      if (!childUid) {
        const parentCodeSnap = await get(ref(rtdb, `pairing_codes/${trimmed}`));
        if (parentCodeSnap.exists()) {
          const pData = parentCodeSnap.val();
          if (pData.linkedChildUid) {
            childUid = pData.linkedChildUid;
            childEmail = pData.childEmail || "";
          }
        }
      }

      // 3. Fallback: Search all users by childPairingCode or pairingCode
      if (!childUid) {
        const usersSnap = await get(ref(rtdb, "users"));
        if (usersSnap.exists()) {
          const allUsers = usersSnap.val();
          for (const uid of Object.keys(allUsers)) {
            const u = allUsers[uid];
            if (u.childPairingCode === trimmed || u.pairingCode === trimmed) {
              childUid = uid;
              childEmail = u.email || "";
              break;
            }
          }
        }
      }

      if (!childUid) {
        setPairingError("Invalid pairing code. Please enter the 6-digit code shown on child device.");
        setIsVerifyingCode(false);
        return;
      }

      const updates: Record<string, any> = {};
      updates[`users/${childUid}/parentId`] = user.uid;
      await update(ref(rtdb), updates);

      try {
        await setDoc(doc(firestore, "users", childUid), { parentId: user.uid }, { merge: true });
      } catch (e) {
        console.warn("Firestore sync error:", e);
      }

      setPairingSuccess(`Successfully linked child device (${childEmail || childUid.slice(0, 8)})!`);
      setChildPairingInput("");
      setActiveChildId(childUid);
      setTimeout(() => {
        setActiveTab("live");
      }, 1500);
    } catch (err: any) {
      setPairingError("Failed to pair: " + (err.message || err));
    } finally {
      setIsVerifyingCode(false);
    }
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
      updates[`users/${childUid}/childPairingCode`] = null;
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
      alert("Child device unpaired successfully. A new pairing code will be generated on child phone.");
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
            <h1 className="text-2xl font-bold text-white tracking-wide">
              {authMode === "login" ? "Parent Portal" : authMode === "signup" ? "Create Account" : "Reset Password"}
            </h1>
            <p className="text-xs text-gray-400 mt-1">
              {authMode === "login"
                ? "Sign in to monitor linked child devices"
                : authMode === "signup"
                ? "Create a parent account to protect and monitor your child"
                : "Enter your registered email to receive a password reset link"}
            </p>
          </div>

          {/* Auth Mode Tabs */}
          <div className="grid grid-cols-3 p-1 bg-[#14141A] border border-[#2A2A38] rounded-xl mb-5 text-xs font-semibold">
            <button
              type="button"
              onClick={() => {
                setAuthMode("login");
                setAuthError(null);
                setAuthSuccessMsg(null);
              }}
              className={`py-2 rounded-lg transition ${
                authMode === "login" ? "bg-blue-600 text-white shadow-md shadow-blue-600/20" : "text-gray-400 hover:text-white"
              }`}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => {
                setAuthMode("signup");
                setAuthError(null);
                setAuthSuccessMsg(null);
              }}
              className={`py-2 rounded-lg transition ${
                authMode === "signup" ? "bg-blue-600 text-white shadow-md shadow-blue-600/20" : "text-gray-400 hover:text-white"
              }`}
            >
              Sign Up
            </button>
            <button
              type="button"
              onClick={() => {
                setAuthMode("forgot");
                setAuthError(null);
                setAuthSuccessMsg(null);
              }}
              className={`py-2 rounded-lg transition ${
                authMode === "forgot" ? "bg-blue-600 text-white shadow-md shadow-blue-600/20" : "text-gray-400 hover:text-white"
              }`}
            >
              Forgot
            </button>
          </div>

          <form onSubmit={handleAuthSubmit} className="space-y-4">
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

            {authMode !== "forgot" && (
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-xs font-medium text-gray-300">Password</label>
                  {authMode === "login" && (
                    <button
                      type="button"
                      onClick={() => {
                        setAuthMode("forgot");
                        setAuthError(null);
                        setAuthSuccessMsg(null);
                      }}
                      className="text-[11px] text-blue-400 hover:text-blue-300 underline"
                    >
                      Forgot password?
                    </button>
                  )}
                </div>
                <div className="relative">
                  <input
                    type={showPassword ? "text" : "password"}
                    required
                    value={passwordInput}
                    onChange={(e) => setPasswordInput(e.target.value)}
                    placeholder="••••••••"
                    className="w-full pl-4 pr-11 py-3 bg-[#141418] border border-[#333340] rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 text-sm"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white transition p-1"
                    title={showPassword ? "Hide Password" : "Show Password"}
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
                {authMode === "signup" && (
                  <p className="text-[10px] text-gray-400 mt-1">Must be at least 6 characters.</p>
                )}
              </div>
            )}

            {authError && (
              <div className="p-3 bg-red-950/40 border border-red-800/60 rounded-xl flex items-center gap-2 text-xs text-red-300">
                <AlertTriangle className="w-4 h-4 shrink-0 text-red-400" />
                <span>{authError}</span>
              </div>
            )}

            {authSuccessMsg && (
              <div className="p-3 bg-emerald-950/40 border border-emerald-800/60 rounded-xl flex items-center gap-2 text-xs text-emerald-300">
                <CheckCircle className="w-4 h-4 shrink-0 text-emerald-400" />
                <span>{authSuccessMsg}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={isAuthLoading}
              className="w-full py-3.5 bg-blue-600 hover:bg-blue-500 text-white font-semibold rounded-xl transition duration-200 shadow-lg shadow-blue-600/30 text-sm flex items-center justify-center gap-2"
            >
              {isAuthLoading ? (
                <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : authMode === "login" ? (
                "Sign In"
              ) : authMode === "signup" ? (
                "Create Parent Account"
              ) : (
                "Send Password Reset Link"
              )}
            </button>
          </form>

          {canInstallPwa && (
            <div className="mt-6 pt-4 border-t border-[#2B2B36] text-center">
              <button
                type="button"
                onClick={handleInstallPwa}
                className="w-full py-2.5 px-4 bg-gradient-to-r from-blue-600/20 to-purple-600/20 hover:from-blue-600/30 hover:to-purple-600/30 border border-blue-500/30 rounded-xl text-blue-300 text-xs font-semibold flex items-center justify-center gap-2 transition shadow-md"
              >
                <Download className="w-4 h-4 text-blue-400" />
                <span>📲 Install Parent App on Phone (PWA)</span>
              </button>
              <p className="text-[10px] text-gray-400 mt-1.5">Direct home screen install • No APK updates needed</p>
            </div>
          )}
        </div>

        {/* PWA Install Guide Modal (Login View) */}
        {showInstallGuide && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
            <div className="w-full max-w-sm p-6 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-2xl relative">
              <button
                onClick={() => setShowInstallGuide(false)}
                className="absolute top-4 right-4 p-1.5 text-gray-400 hover:text-white rounded-lg hover:bg-white/10"
              >
                <X className="w-5 h-5" />
              </button>

              <div className="flex items-center gap-3 mb-4">
                <div className="p-2.5 bg-blue-600/20 text-blue-400 rounded-xl">
                  <Download className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white">Install Parent App</h3>
                  <p className="text-[11px] text-gray-400">Phone Home Screen par Install karein</p>
                </div>
              </div>

              <div className="space-y-3 text-xs text-gray-300">
                <div className="p-3 bg-[#14141B] border border-[#2B2B38] rounded-xl">
                  <p className="font-semibold text-white flex items-center gap-1.5 mb-1.5">
                    <Smartphone className="w-4 h-4 text-emerald-400" />
                    Android Phone (Chrome)
                  </p>
                  <ol className="list-decimal list-inside space-y-1 text-[11px] text-gray-300">
                    <li>Browser me upar 3 dots (<MoreVertical className="w-3 h-3 inline text-gray-400" />) tap karein.</li>
                    <li><span className="text-white font-medium">"Install app"</span> ya <span className="text-white font-medium">"Add to Home screen"</span> chunein.</li>
                    <li>Phone ke app drawer me app save ho jayega.</li>
                  </ol>
                </div>

                <div className="p-3 bg-[#14141B] border border-[#2B2B38] rounded-xl">
                  <p className="font-semibold text-white flex items-center gap-1.5 mb-1.5">
                    <Share2 className="w-4 h-4 text-blue-400" />
                    iPhone / iPad (Safari)
                  </p>
                  <ol className="list-decimal list-inside space-y-1 text-[11px] text-gray-300">
                    <li>Safari me Share button (<Share2 className="w-3 h-3 inline text-gray-400" />) par tap karein.</li>
                    <li>Neeche scroll karke <span className="text-white font-medium">"Add to Home Screen"</span> chunein.</li>
                    <li>Upar "Add" par tap karein.</li>
                  </ol>
                </div>
              </div>

              <button
                onClick={() => setShowInstallGuide(false)}
                className="mt-5 w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-xl transition"
              >
                Theek Hai (Close)
              </button>
            </div>
          </div>
        )}
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
                className={`w-full h-full object-contain ${
                  isStreaming && streamType === "video"
                    ? "block"
                    : isStreaming && streamType === "audio"
                    ? "opacity-0 absolute pointer-events-none w-1 h-1"
                    : "hidden"
                }`}
              />

              {isStreaming && streamType === "audio" && (
                <div className="flex flex-col items-center gap-3 text-center p-6">
                  <div className="relative">
                    <div className="w-16 h-16 rounded-full bg-blue-600/30 animate-ping absolute inset-0"></div>
                    <div className="w-16 h-16 rounded-full bg-blue-600 text-white flex items-center justify-center relative shadow-lg shadow-blue-600/40">
                      <Music className="w-8 h-8" />
                    </div>
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-white">Live Microphone Audio</h3>
                    <p className="text-xs text-gray-400 mt-1">Listening to ambient surroundings in real-time</p>
                  </div>
                </div>
              )}

              {!isStreaming && (
                <div className="flex flex-col items-center gap-3 text-gray-500">
                  <div className="p-4 bg-[#1E1E26] rounded-full">
                    <Video className="w-8 h-8 text-gray-400" />
                  </div>
                  <p className="text-xs font-medium text-gray-400">Live stream stopped</p>
                </div>
              )}

              {/* Top Controls Overlay */}
              <div className="absolute top-3 right-3 z-20 flex items-center gap-2">
                {/* 1. Camera Switch Button directly on screen */}
                {isStreaming && streamType === "video" && (
                  <button
                    type="button"
                    onClick={toggleCamera}
                    className="px-3 py-1.5 bg-black/70 hover:bg-black/90 backdrop-blur-md rounded-xl text-white text-xs font-semibold flex items-center gap-1.5 border border-white/20 shadow-lg cursor-pointer transition hover:scale-105 active:scale-95"
                    title="Switch Camera (Front / Rear)"
                  >
                    <RefreshCw className="w-3.5 h-3.5 text-blue-400" />
                    <span>{cameraFacing === "front" ? "Front ↻" : "Rear ↻"}</span>
                  </button>
                )}

                {/* 2. Live Stream Recorder Button (Records to Laptop) */}
                {isStreaming && (
                  !isRecordingStream ? (
                    <button
                      type="button"
                      onClick={startRecordingStream}
                      className="px-3 py-1.5 bg-red-600/90 hover:bg-red-500 backdrop-blur-md rounded-xl text-white text-xs font-bold flex items-center gap-1.5 border border-red-400/40 shadow-lg cursor-pointer transition hover:scale-105 active:scale-95"
                      title={streamType === "video" ? "Record Video & Audio to Laptop" : "Record Audio to Laptop"}
                    >
                      <Circle className="w-3.5 h-3.5 fill-white text-white" />
                      <span>Record {streamType === "video" ? "Video+Audio" : "Audio"}</span>
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={stopRecordingStream}
                      className="px-3 py-1.5 bg-red-700 hover:bg-red-800 backdrop-blur-md rounded-xl text-white text-xs font-bold flex items-center gap-2 border border-red-400/60 shadow-xl cursor-pointer transition animate-pulse"
                      title="Click to stop and download recording to computer"
                    >
                      <StopCircle className="w-4 h-4 fill-white text-red-700" />
                      <span>REC {formatSeconds(recordingDuration)} (Stop & Save)</span>
                    </button>
                  )
                )}
              </div>

              {/* Status Pill in Corner */}
              <div className="absolute top-3 left-3 px-2.5 py-1 bg-black/60 backdrop-blur-md rounded-md text-[10px] font-semibold text-gray-200 flex items-center gap-1.5 border border-white/10 z-10">
                <span
                  className={`w-1.5 h-1.5 rounded-full ${
                    isStreaming ? "bg-red-500 animate-ping" : "bg-gray-400"
                  }`}
                />
                {streamStatusText}
              </div>

              {/* Audio Unmute / Mute Toggle Button */}
              {isStreaming && isMuted && (
                <button
                  type="button"
                  onClick={() => {
                    if (videoRef.current) {
                      videoRef.current.muted = false;
                      videoRef.current.volume = 1.0;
                      videoRef.current.play().catch(() => {});
                      setIsMuted(false);
                      setStreamStatusText("Live connected (Audio ON)");
                    }
                  }}
                  className="absolute bottom-3 right-3 z-20 px-3.5 py-2 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white text-xs font-bold rounded-xl shadow-xl flex items-center gap-2 border border-blue-400/40 transition animate-bounce cursor-pointer"
                >
                  <Volume2 className="w-4 h-4" /> Tap to Unmute Audio
                </button>
              )}

              {isStreaming && !isMuted && (
                <button
                  type="button"
                  onClick={() => {
                    if (videoRef.current) {
                      videoRef.current.muted = true;
                      setIsMuted(true);
                      setStreamStatusText("Live connected (Audio Muted)");
                    }
                  }}
                  className="absolute bottom-3 right-3 z-20 p-2 bg-black/60 hover:bg-black/80 text-gray-300 hover:text-white rounded-xl shadow-lg flex items-center gap-1.5 border border-white/10 backdrop-blur-md transition cursor-pointer"
                  title="Mute Sound"
                >
                  <VolumeX className="w-4 h-4" />
                </button>
              )}
            </div>

            {/* Recorded Stream Download Notification */}
            {recordedDownloadUrl && (
              <div className="p-3 bg-green-950/40 border border-green-800/40 rounded-xl flex items-center justify-between text-xs text-green-300">
                <div className="flex items-center gap-2">
                  <CheckCircle className="w-4 h-4 text-green-400" />
                  <span>Stream recording successfully saved to your downloads folder!</span>
                </div>
                <a
                  href={recordedDownloadUrl}
                  download="stream_recording.webm"
                  className="px-3 py-1 bg-green-700 hover:bg-green-600 text-white font-bold rounded-lg transition flex items-center gap-1"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Download Again</span>
                </a>
              </div>
            )}

            {/* Ultra-Clear Audio & Far-Voice Boost Card (दूर की आवाज़ साफ़ करें) */}
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
                      अगर कोई दूर से धीमी आवाज़ या फुसफुसाहट में बात कर रहा है, तो स्लाइडर को 3x से 5x बढ़ाएं।
                    </p>
                  </div>
                </div>

                {/* Noise Filter Toggle */}
                <button
                  type="button"
                  onClick={() => {
                    const nextVal = !isClarityFilterEnabled;
                    setIsClarityFilterEnabled(nextVal);
                    if (rtcReceiverRef.current) {
                      rtcReceiverRef.current.toggleClarityFilter(nextVal);
                    }
                  }}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1.5 border transition cursor-pointer self-start sm:self-auto ${
                    isClarityFilterEnabled
                      ? "bg-emerald-600/30 text-emerald-300 border-emerald-500/40 hover:bg-emerald-600/40"
                      : "bg-[#252530] text-gray-400 border-gray-700 hover:text-gray-200"
                  }`}
                  title="हवा और पंखे का बैकग्राउंड शोर हटाकर केवल इंसान की आवाज़ साफ़ करता है"
                >
                  <span className={`w-2 h-2 rounded-full ${isClarityFilterEnabled ? "bg-emerald-400 animate-pulse" : "bg-gray-500"}`} />
                  <span>Fan / Hum Noise Filter: {isClarityFilterEnabled ? "ON" : "OFF"}</span>
                </button>
              </div>

              {/* Slider & Presets */}
              <div className="flex flex-col sm:flex-row items-center gap-4">
                <input
                  type="range"
                  min="0.5"
                  max="5.0"
                  step="0.1"
                  value={audioBoostMultiplier}
                  onChange={(e) => {
                    const val = parseFloat(e.target.value);
                    setAudioBoostMultiplier(val);
                    if (rtcReceiverRef.current) {
                      rtcReceiverRef.current.setAudioBoost(val);
                    }
                  }}
                  className="w-full h-2 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-emerald-500"
                />

                <div className="flex items-center gap-2 shrink-0">
                  <button
                    type="button"
                    onClick={() => {
                      setAudioBoostMultiplier(1.0);
                      rtcReceiverRef.current?.setAudioBoost(1.0);
                    }}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                      audioBoostMultiplier === 1.0
                        ? "bg-emerald-500 text-black border-emerald-400 font-bold"
                        : "bg-[#252530] text-gray-300 border-gray-700 hover:border-gray-500"
                    }`}
                  >
                    1.0x Normal
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setAudioBoostMultiplier(3.0);
                      rtcReceiverRef.current?.setAudioBoost(3.0);
                    }}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                      audioBoostMultiplier === 3.0
                        ? "bg-emerald-500 text-black border-emerald-400 font-bold"
                        : "bg-[#252530] text-gray-300 border-gray-700 hover:border-gray-500"
                    }`}
                  >
                    3.0x Far Speech
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setAudioBoostMultiplier(5.0);
                      rtcReceiverRef.current?.setAudioBoost(5.0);
                    }}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition ${
                      audioBoostMultiplier === 5.0
                        ? "bg-emerald-500 text-black border-emerald-400 font-bold"
                        : "bg-[#252530] text-gray-300 border-gray-700 hover:border-gray-500"
                    }`}
                  >
                    5.0x Whisper
                  </button>
                </div>
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
          <ActivityLogList
            activityTab={activityTab}
            setActivityTab={setActivityTab}
            whatsappLogs={whatsappLogs}
            callLogs={callLogs}
            smsLogs={smsLogs}
            notifications={notifications}
            webHistory={webHistory}
            recordings={recordings}
            packageEvents={packageEvents}
            simInfo={simInfo}
            clipboardLogs={clipboardLogs}
            mediaGallery={mediaGallery}
            fileExplorer={fileExplorer}
            selectedDirectoryPath={selectedDirectoryPath}
            setSelectedDirectoryPath={setSelectedDirectoryPath}
            sendCommand={sendCommand}
          />
        )}

        {/* Tab 3: Emergency Remote Controls */}
        {activeTab === "controls" && (
          <ControlsPanel
            childLocation={childLocation}
            isRefreshingLocation={isRefreshingLocation}
            setIsRefreshingLocation={setIsRefreshingLocation}
            sendCommand={sendCommand}
            isSirenActive={isSirenActive}
            isTorchActive={isTorchActive}
            capturingFacing={capturingFacing}
            handleRequestSnapshot={handleRequestSnapshot}
            snapshotsList={snapshotsList}
            latestSnapshot={latestSnapshot}
            setSelectedSnapshotModal={setSelectedSnapshotModal}
            storageRetentionDays={storageRetentionDays}
            handleUpdateRetention={handleUpdateRetention}
            isCleaningStorage={isCleaningStorage}
            cleanupStatusMessage={cleanupStatusMessage}
            handleRunAutoClean={handleRunAutoClean}
          />
        )}

        {/* Tab 4: Schedules & Auto-Record Routines */}
        <ScheduleManager
          activeTab={activeTab}
          activeChildId={activeChildId}
          schedulesList={schedulesList}
          showAddScheduleModal={showAddScheduleModal}
          setShowAddScheduleModal={setShowAddScheduleModal}
          schedLabel={schedLabel}
          setSchedLabel={setSchedLabel}
          schedHour={schedHour}
          setSchedHour={setSchedHour}
          schedMinute={schedMinute}
          setSchedMinute={setSchedMinute}
          schedDuration={schedDuration}
          setSchedDuration={setSchedDuration}
        />

        {/* Tab 5: Pairing Code & Connect Child */}
        {activeTab === "pairing" && (
          <div className="p-6 bg-[#1B1B22] border border-[#2B2B36] rounded-2xl flex flex-col items-center text-center max-w-md mx-auto">
            <div className="p-3 bg-blue-600/20 text-blue-400 rounded-full mb-3">
              <Smartphone className="w-8 h-8" />
            </div>
            <h2 className="text-base font-bold text-white">Pair Child Device</h2>
            <p className="text-xs text-gray-400 mt-1 mb-5">
              Enter the 6-digit Pairing Code displayed on the Child Phone Calculator dashboard.
            </p>

            {/* Child Code Input Section */}
            <div className="w-full bg-[#14141A] border border-[#2B2B36] rounded-2xl p-4 mb-4">
              <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2 text-left">
                Child 6-Digit Pairing Code
              </label>
              <input
                type="text"
                maxLength={6}
                value={childPairingInput}
                onChange={(e) => setChildPairingInput(e.target.value.replace(/\D/g, "").slice(0, 6))}
                placeholder="e.g. 582914"
                className="w-full bg-[#1E1E26] border border-[#3A3A4D] rounded-xl py-3 px-4 text-center font-mono text-2xl font-bold tracking-widest text-white placeholder-gray-600 focus:outline-none focus:border-blue-500 transition"
              />

              {pairingError && (
                <p className="text-xs text-red-400 mt-2 font-medium flex items-center justify-center gap-1">
                  <AlertTriangle className="w-3.5 h-3.5" />
                  {pairingError}
                </p>
              )}

              {pairingSuccess && (
                <p className="text-xs text-green-400 mt-2 font-medium flex items-center justify-center gap-1">
                  <CheckCircle className="w-3.5 h-3.5" />
                  {pairingSuccess}
                </p>
              )}

              <button
                onClick={handleLinkChildWithCode}
                disabled={isVerifyingCode || childPairingInput.length !== 6}
                className="w-full mt-4 py-3 bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-blue-600/20 flex items-center justify-center gap-2"
              >
                {isVerifyingCode ? (
                  <RefreshCw className="w-4 h-4 animate-spin" />
                ) : (
                  <Shield className="w-4 h-4" />
                )}
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

            {/* Optional Fallback: Parent's Code */}
            <div className="pt-3 border-t border-[#262632] w-full">
              <button
                onClick={handleGeneratePairingCode}
                className="text-[11px] text-blue-400 hover:text-blue-300 underline flex items-center justify-center gap-1 mx-auto"
              >
                <RefreshCw className="w-3 h-3" />
                Or generate parent pairing code (legacy)
              </button>
              {generatedPairingCode && (
                <div className="mt-2 py-2 px-4 bg-[#14141A] border border-[#333342] rounded-xl inline-block">
                  <span className="text-lg font-mono font-bold tracking-wider text-blue-400">
                    {generatedPairingCode}
                  </span>
                </div>
              )}
            </div>
          </div>
        )}
      </main>

      {/* Bottom Mobile-Style Navigation Bar */}
      <nav className="fixed bottom-0 left-0 right-0 z-30 max-w-5xl mx-auto bg-[#17171E]/95 backdrop-blur-md border-t border-[#262632] flex items-center justify-around py-2 px-3">
        {[
          { id: "live", label: "Live Cast", icon: Video },
          { id: "activity", label: "Activities", icon: MessageSquare },
          { id: "controls", label: "Controls", icon: Shield },
          { id: "schedules", label: "Schedules", icon: Clock },
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

      {/* PWA Install Guide Modal (Dashboard View) */}
      {showInstallGuide && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="w-full max-w-sm p-6 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-2xl relative">
            <button
              onClick={() => setShowInstallGuide(false)}
              className="absolute top-4 right-4 p-1.5 text-gray-400 hover:text-white rounded-lg hover:bg-white/10"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3 mb-4">
              <div className="p-2.5 bg-blue-600/20 text-blue-400 rounded-xl">
                <Download className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-white">Install Parent App</h3>
                <p className="text-[11px] text-gray-400">Phone Home Screen par Install karein</p>
              </div>
            </div>

            <div className="space-y-3 text-xs text-gray-300">
              <div className="p-3 bg-[#14141B] border border-[#2B2B38] rounded-xl">
                <p className="font-semibold text-white flex items-center gap-1.5 mb-1.5">
                  <Smartphone className="w-4 h-4 text-emerald-400" />
                  Android Phone (Chrome)
                </p>
                <ol className="list-decimal list-inside space-y-1 text-[11px] text-gray-300">
                  <li>Browser me upar 3 dots (<MoreVertical className="w-3 h-3 inline text-gray-400" />) tap karein.</li>
                  <li><span className="text-white font-medium">"Install app"</span> ya <span className="text-white font-medium">"Add to Home screen"</span> chunein.</li>
                  <li>Phone ke app drawer me app save ho jayega.</li>
                </ol>
              </div>

              <div className="p-3 bg-[#14141B] border border-[#2B2B38] rounded-xl">
                <p className="font-semibold text-white flex items-center gap-1.5 mb-1.5">
                  <Share2 className="w-4 h-4 text-blue-400" />
                  iPhone / iPad (Safari)
                </p>
                <ol className="list-decimal list-inside space-y-1 text-[11px] text-gray-300">
                  <li>Safari me Share button (<Share2 className="w-3 h-3 inline text-gray-400" />) par tap karein.</li>
                  <li>Neeche scroll karke <span className="text-white font-medium">"Add to Home Screen"</span> chunein.</li>
                  <li>Upar "Add" par tap karein.</li>
                </ol>
              </div>
            </div>

            <button
              onClick={() => setShowInstallGuide(false)}
              className="mt-5 w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-xl transition"
            >
              Theek Hai (Close)
            </button>
          </div>
        </div>
      )}

      {/* Full-Screen Captured Photo Viewer Modal */}
      {selectedSnapshotModal && (
        <div
          className="fixed inset-0 z-50 bg-black/85 backdrop-blur-md flex items-center justify-center p-4 animate-in fade-in"
          onClick={() => setSelectedSnapshotModal(null)}
        >
          <div
            className="relative max-w-4xl w-full bg-[#1A1A22] border border-[#2B2B38] rounded-2xl overflow-hidden shadow-2xl flex flex-col max-h-[90vh]"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="p-4 bg-[#14141A] border-b border-[#252530] flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="p-2 bg-purple-600/20 text-purple-400 rounded-lg">
                  <Camera className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="text-xs font-bold text-white capitalize">
                    {selectedSnapshotModal.cameraFacing || "Camera"} Photo Snapshot
                  </h3>
                  <p className="text-[10px] text-gray-400">
                    {selectedSnapshotModal.timestamp ? new Date(selectedSnapshotModal.timestamp).toLocaleString() : ""}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <a
                  href={selectedSnapshotModal.downloadUrl || selectedSnapshotModal.storageUrl}
                  download="snapshot_photo.jpg"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-lg flex items-center gap-1.5 transition"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Download</span>
                </a>
                <button
                  type="button"
                  onClick={() => setSelectedSnapshotModal(null)}
                  className="p-1.5 text-gray-400 hover:text-white hover:bg-white/10 rounded-lg transition"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            {/* Modal Image Display */}
            <div className="flex-1 overflow-auto p-4 flex items-center justify-center bg-black/60 min-h-[300px]">
              <img
                src={selectedSnapshotModal.downloadUrl || selectedSnapshotModal.storageUrl}
                alt="High Resolution Snapshot"
                className="max-h-[70vh] w-auto max-w-full object-contain rounded-lg shadow-2xl"
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
