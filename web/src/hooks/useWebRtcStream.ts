import { useState, useRef } from "react";
import { WebRtcReceiver } from "@/lib/webrtc";
import { AudioStreamReceiver } from "@/lib/audioStreamer";

export function useWebRtcStream() {
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamType, setStreamType] = useState<"video" | "audio">("video");
  const [cameraFacing, setCameraFacing] = useState<"front" | "back" | "ultrawide">("back");
  const [streamDuration, setStreamDuration] = useState<number>(0);
  const streamTimerRef = useRef<any>(null);
  const [streamStatusText, setStreamStatusText] = useState("Idle");
  const [isMuted, setIsMuted] = useState(false);
  const [audioBoostMultiplier, setAudioBoostMultiplier] = useState<number>(3.5);
  const [isClarityFilterEnabled, setIsClarityFilterEnabled] = useState<boolean>(true);

  const videoRef = useRef<HTMLVideoElement>(null);
  const rtcReceiverRef = useRef<WebRtcReceiver | null>(null);
  const audioReceiverRef = useRef<AudioStreamReceiver | null>(null);

  const [isRecordingStream, setIsRecordingStream] = useState<boolean>(false);
  const [recordingDuration, setRecordingDuration] = useState<number>(0);
  const [recordedDownloadUrl, setRecordedDownloadUrl] = useState<string | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordedChunksRef = useRef<Blob[]>([]);
  const recordingTimerRef = useRef<any>(null);

  const startStream = async (activeChildId: string) => {
    if (!activeChildId) return;
    setIsStreaming(true);
    if (streamTimerRef.current) clearInterval(streamTimerRef.current);
    setStreamDuration(0);
    streamTimerRef.current = setInterval(() => {
      setStreamDuration((prev) => prev + 1);
    }, 1000);

    const statusPrompt =
      streamType === "audio"
        ? "Connecting to phone microphone (Live Audio)..."
        : "Connecting to child device camera...";
    setStreamStatusText(statusPrompt);

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
                setStreamStatusText(
                  streamType === "audio"
                    ? "Live Ambient Audio Connected 🟢"
                    : "Live 60fps connected 🟢"
                );
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
                      console.error("Muted playback failed:", e);
                      setStreamStatusText(
                        streamType === "audio"
                          ? "Live Audio Active 🟢"
                          : "Live video ready 🟢"
                      );
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

  const stopStream = () => {
    stopRecordingStream();
    if (streamTimerRef.current) {
      clearInterval(streamTimerRef.current);
      streamTimerRef.current = null;
    }
    setStreamDuration(0);
    if (audioReceiverRef.current) {
      audioReceiverRef.current.stop();
      audioReceiverRef.current = null;
    }
    if (rtcReceiverRef.current) {
      rtcReceiverRef.current.stop();
      rtcReceiverRef.current = null;
    }
    if (videoRef.current) {
      if (videoRef.current.srcObject instanceof MediaStream) {
        try {
          videoRef.current.srcObject.getTracks().forEach((track) => {
            track.stop();
          });
        } catch (_e) {}
      }
      videoRef.current.pause();
      videoRef.current.srcObject = null;
    }
    setIsStreaming(false);
    setIsMuted(false);
    setStreamStatusText("Idle");
  };

  const toggleCamera = async () => {
    const nextFacing: "front" | "back" | "ultrawide" =
      cameraFacing === "back" ? "ultrawide" : cameraFacing === "ultrawide" ? "front" : "back";
    setCameraFacing(nextFacing);
    if (rtcReceiverRef.current) {
      await rtcReceiverRef.current.switchCamera(nextFacing);
    }
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

      const mediaRecorder = new MediaRecorder(streamToRecord, mimeType ? { mimeType } : undefined);
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

      mediaRecorder.start(1000);
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

  const setAudioBoost = (val: number) => {
    setAudioBoostMultiplier(val);
    if (rtcReceiverRef.current) {
      rtcReceiverRef.current.setAudioBoost(val);
    }
  };

  const toggleClarity = (enabled: boolean) => {
    setIsClarityFilterEnabled(enabled);
    if (rtcReceiverRef.current) {
      rtcReceiverRef.current.toggleClarityFilter(enabled);
    }
  };

  return {
    isStreaming,
    streamType,
    setStreamType,
    cameraFacing,
    streamDuration,
    streamStatusText,
    isMuted,
    setIsMuted,
    audioBoostMultiplier,
    setAudioBoost,
    isClarityFilterEnabled,
    toggleClarity,
    videoRef,
    startStream,
    stopStream,
    toggleCamera,
    isRecordingStream,
    recordingDuration,
    recordedDownloadUrl,
    startRecordingStream,
    stopRecordingStream,
  };
}
