import React from "react";
import { useApp } from "@/context/AppContext";
import {
  Video,
  Music,
  RefreshCw,
  Circle,
  StopCircle,
  Volume2,
  VolumeX,
  CheckCircle,
  Download,
  Play,
  Pause,
} from "lucide-react";
import { VoiceBoostCard } from "./VoiceBoostCard";

export function LiveMonitorSection() {
  const { stream, childData } = useApp();
  const {
    isStreaming, streamType, setStreamType, cameraFacing, streamDuration,
    streamStatusText, isMuted, setIsMuted, audioBoostMultiplier,
    setAudioBoost: setAudioBoostMultiplier, isClarityFilterEnabled,
    toggleClarity: toggleClarityFilter, videoRef, isRecordingStream,
    recordingDuration, recordedDownloadUrl, startRecordingStream: onStartRecording,
    stopRecordingStream: onStopRecording, startStream, stopStream: onStopStream,
    toggleCamera: onToggleCamera,
  } = stream;
  const { activeChildId } = childData;
  const onStartStream = () => startStream(activeChildId);
  const formatSeconds = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  };

  return (
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
              <div className="w-16 h-16 rounded-full bg-blue-600/30 animate-ping absolute inset-0" />
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
          {isStreaming && streamType === "video" && (
            <button
              type="button"
              onClick={onToggleCamera}
              className="px-3 py-1.5 bg-black/70 hover:bg-black/90 backdrop-blur-md rounded-xl text-white text-xs font-semibold flex items-center gap-1.5 border border-white/20 shadow-lg cursor-pointer transition hover:scale-105 active:scale-95"
              title="Switch Camera (1x Rear / 0.5x Ultra-Wide / Front)"
            >
              <RefreshCw className="w-3.5 h-3.5 text-blue-400" />
              <span>
                {cameraFacing === "ultrawide"
                  ? "0.5x Ultra-Wide ↻"
                  : cameraFacing === "front"
                  ? "Front ↻"
                  : "1x Rear ↻"}
              </span>
            </button>
          )}

          {isStreaming && (
            !isRecordingStream ? (
              <button
                type="button"
                onClick={onStartRecording}
                className="px-3 py-1.5 bg-red-600/90 hover:bg-red-500 backdrop-blur-md rounded-xl text-white text-xs font-bold flex items-center gap-1.5 border border-red-400/40 shadow-lg cursor-pointer transition hover:scale-105 active:scale-95"
                title={streamType === "video" ? "Record Video & Audio to Laptop" : "Record Audio to Laptop"}
              >
                <Circle className="w-3.5 h-3.5 fill-white text-white" />
                <span>Record {streamType === "video" ? "Video+Audio" : "Audio"}</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={onStopRecording}
                className="px-3 py-1.5 bg-red-700 hover:bg-red-800 backdrop-blur-md rounded-xl text-white text-xs font-bold flex items-center gap-2 border border-red-400/60 shadow-xl cursor-pointer transition animate-pulse"
                title="Click to stop and download recording to computer"
              >
                <StopCircle className="w-4 h-4 fill-white text-red-700" />
                <span>REC {formatSeconds(recordingDuration)} (Stop & Download)</span>
              </button>
            )
          )}
        </div>

        {/* Status & Live Elapsed Time */}
        <div className="absolute top-3 left-3 flex items-center gap-2 z-10">
          {isStreaming && (
            streamDuration > 0 ? (
              <div className="px-2.5 py-1 bg-red-600/90 backdrop-blur-md rounded-md text-[10px] font-black text-white flex items-center gap-1.5 shadow-md">
                <span className="w-2 h-2 rounded-full bg-white animate-pulse" />
                <span>LIVE {formatSeconds(streamDuration)}</span>
              </div>
            ) : (
              <div className="px-2.5 py-1 bg-amber-600/90 backdrop-blur-md rounded-md text-[10px] font-bold text-white flex items-center gap-1.5 shadow-md">
                <span className="w-2 h-2 rounded-full bg-amber-200 animate-ping" />
                <span>CONNECTING...</span>
              </div>
            )
          )}
          <div className="px-2.5 py-1 bg-black/60 backdrop-blur-md rounded-md text-[10px] font-semibold text-gray-200 flex items-center gap-1.5 border border-white/10">
            <span
              className={`w-1.5 h-1.5 rounded-full ${
                isStreaming && streamDuration > 0 ? "bg-green-400 animate-ping" : "bg-amber-400 animate-pulse"
              }`}
            />
            {streamStatusText}
          </div>
        </div>

        {/* Audio Mute/Unmute */}
        {isStreaming && isMuted && (
          <button
            type="button"
            onClick={() => {
              if (videoRef.current) {
                videoRef.current.muted = false;
                videoRef.current.volume = 1.0;
                videoRef.current.play().catch(() => {});
                setIsMuted(false);
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
              }
            }}
            className="absolute bottom-3 right-3 z-20 p-2 bg-black/60 hover:bg-black/80 text-gray-300 hover:text-white rounded-xl shadow-lg flex items-center gap-1.5 border border-white/10 backdrop-blur-md transition cursor-pointer"
            title="Mute Sound"
          >
            <VolumeX className="w-4 h-4" />
          </button>
        )}
      </div>

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

      {/* Far-Voice Boost Card */}
      <VoiceBoostCard
        audioBoostMultiplier={audioBoostMultiplier}
        setAudioBoostMultiplier={setAudioBoostMultiplier}
        isClarityFilterEnabled={isClarityFilterEnabled}
        toggleClarityFilter={toggleClarityFilter}
      />

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
          {!isStreaming ? (
            <button
              onClick={onStartStream}
              disabled={!activeChildId}
              className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-blue-600/30 flex items-center gap-2 cursor-pointer"
            >
              <Play className="w-4 h-4 fill-white" />
              <span>Start Live Stream</span>
            </button>
          ) : (
            <button
              onClick={onStopStream}
              className="px-5 py-2.5 bg-red-600 hover:bg-red-500 text-white text-xs font-bold rounded-xl transition shadow-lg shadow-red-600/30 flex items-center gap-2 cursor-pointer"
            >
              <Pause className="w-4 h-4 fill-white" />
              <span>Stop Stream</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
