import { rtdb } from "./firebase";
import { ref, set, onValue, push, off } from "firebase/database";

const ICE_SERVERS: RTCIceServer[] = [
  // 1. PRIMARY: Free Google & Twilio STUN Servers (Direct P2P First - ZERO Cost / 0 Bandwidth)
  { urls: "stun:stun.l.google.com:19302" },
  { urls: "stun:stun1.l.google.com:19302" },
  { urls: "stun:stun2.l.google.com:19302" },
  { urls: "stun:stun3.l.google.com:19302" },
  { urls: "stun:stun4.l.google.com:19302" },
  { urls: "stun:global.stun.twilio.com:3478" },

  // 2. Dedicated Google Cloud Compute Engine (GCE) Coturn Server (Always Free Tier - us-central1)
  { urls: "stun:34.46.137.244:3478" },
  {
    urls: "turn:34.46.137.244:3478",
    username: "apnasatthi",
    credential: "ApnaPass@2026",
  },
  {
    urls: "turn:34.46.137.244:3478?transport=tcp",
    username: "apnasatthi",
    credential: "ApnaPass@2026",
  },

  // 3. Fallback TURN Servers (FreeSTUN)
  {
    urls: "turn:freestun.net:3478",
    username: "free",
    credential: "free",
  },
];

export class WebRtcReceiver {
  private peerConnection: RTCPeerConnection | null = null;
  private sessionId: string = "";
  private childUid: string = "";
  private listeners: Array<() => void> = [];
  private pendingCandidates: RTCIceCandidateInit[] = [];
  private isRemoteDescriptionSet = false;
  private remoteStream: MediaStream | null = null;

  // Web Audio API DSP for Distant Voice Clarity & Super Boost
  private audioCtx: AudioContext | null = null;
  private audioSourceNode: MediaStreamAudioSourceNode | null = null;
  private gainNode: GainNode | null = null;
  private highpassFilter: BiquadFilterNode | null = null;
  private speechFilter: BiquadFilterNode | null = null;
  private currentGainMultiplier = 1.0;
  private isClarityFilterActive = true;

  constructor(
    private onRemoteStream: (stream: MediaStream) => void,
    private onConnectionStateChange: (state: RTCPeerConnectionState) => void,
    private onError: (err: string) => void
  ) {}

  public async startReceiver(childUid: string, streamType: "video" | "audio" = "video") {
    this.stop(false);
    this.childUid = childUid;
    this.sessionId = "session_" + Date.now();
    this.pendingCandidates = [];
    this.isRemoteDescriptionSet = false;
    this.remoteStream = new MediaStream();

    try {
      this.peerConnection = new RTCPeerConnection({
        iceServers: ICE_SERVERS,
        iceCandidatePoolSize: 2,
      });

      // Explicitly request audio and video reception
      this.peerConnection.addTransceiver("audio", { direction: "recvonly" });
      this.peerConnection.addTransceiver("video", { direction: "recvonly" });

      this.peerConnection.ontrack = (event) => {
        console.log("WebRTC ontrack received:", event.track.kind, event.track.id);
        if (this.remoteStream) {
          if (!this.remoteStream.getTracks().some((t) => t.id === event.track.id)) {
            this.remoteStream.addTrack(event.track);
          }
          this.onRemoteStream(this.remoteStream);
          if (event.track.kind === "audio") {
            this.setupAudioProcessing(this.remoteStream);
          }
        }
        event.track.onunmute = () => {
          console.log("WebRTC track onunmute:", event.track.kind);
          if (this.remoteStream) {
            this.onRemoteStream(this.remoteStream);
            if (event.track.kind === "audio") {
              this.setupAudioProcessing(this.remoteStream);
            }
          }
        };
      };

      this.peerConnection.onconnectionstatechange = () => {
        if (this.peerConnection) {
          const state = this.peerConnection.connectionState;
          console.log("WebRTC peerConnection state:", state);
          this.onConnectionStateChange(state);
        }
      };

      this.peerConnection.oniceconnectionstatechange = () => {
        console.log("WebRTC iceConnectionState:", this.peerConnection?.iceConnectionState);
      };

      this.peerConnection.onicecandidate = (event) => {
        if (event.candidate && event.candidate.candidate && this.sessionId) {
          const candPayload = {
            sdpMid: event.candidate.sdpMid || "0",
            sdpMLineIndex: typeof event.candidate.sdpMLineIndex === "number" ? event.candidate.sdpMLineIndex : 0,
            sdp: event.candidate.candidate,
            candidate: event.candidate.candidate,
          };

          // 1. Send to child's primary listener node: parentCandidates
          push(ref(rtdb, `signaling/${this.sessionId}/parentCandidates`), candPayload);

          // 2. Also send to candidates/parent for compatibility
          push(ref(rtdb, `signaling/${this.sessionId}/candidates/parent`), candPayload);
        }
      };

      // 1. Tell Child to start streaming
      const streamStatusRef = ref(rtdb, `streams/${this.childUid}/status`);
      await set(streamStatusRef, {
        status: "REQUESTED",
        streamType: streamType,
        sessionId: this.sessionId,
      });

      // 2. Listen to Child SDP Offer
      const sdpOfferRef = ref(rtdb, `signaling/${this.sessionId}/sdpOffer`);
      const unsubscribeOffer = onValue(sdpOfferRef, async (snapshot) => {
        const sdp = snapshot.val();
        if (sdp && this.peerConnection && !this.isRemoteDescriptionSet) {
          try {
            console.log("Setting remote SDP offer from child...");
            await this.peerConnection.setRemoteDescription(new RTCSessionDescription({ type: "offer", sdp }));
            this.isRemoteDescriptionSet = true;

            // Drain queued ICE candidates
            for (const pending of this.pendingCandidates) {
              try {
                await this.peerConnection.addIceCandidate(new RTCIceCandidate(pending));
              } catch (e) {
                console.warn("Error adding queued ICE candidate:", e);
              }
            }
            this.pendingCandidates = [];

            const answer = await this.peerConnection.createAnswer();
            await this.peerConnection.setLocalDescription(answer);

            console.log("Sending SDP answer to child...");
            // Send SDP Answer to child
            await set(ref(rtdb, `signaling/${this.sessionId}/sdpAnswer`), answer.sdp);
          } catch (e: any) {
            console.error("SDP negotiation failed:", e);
            this.onError("SDP negotiation failed: " + (e?.message || e));
          }
        }
      });
      this.listeners.push(() => off(sdpOfferRef, "value", unsubscribeOffer));

      // 3. Listen to Child ICE Candidates from both childCandidates and candidates/child
      const processedCandidates = new Set<string>();
      const handleCandidate = async (cand: any) => {
        if (!cand) return;
        const sdpString = cand.sdp || cand.candidate;
        if (!sdpString || processedCandidates.has(sdpString)) return;
        processedCandidates.add(sdpString);

        const candInit: RTCIceCandidateInit = {
          candidate: sdpString,
          sdpMid: cand.sdpMid || "0",
          sdpMLineIndex: typeof cand.sdpMLineIndex === "number" ? cand.sdpMLineIndex : 0,
        };

        if (!this.isRemoteDescriptionSet || !this.peerConnection) {
          this.pendingCandidates.push(candInit);
        } else {
          try {
            await this.peerConnection.addIceCandidate(new RTCIceCandidate(candInit));
          } catch (e) {
            console.warn("Failed to add candidate:", e);
          }
        }
      };

      const childCandidatesRef = ref(rtdb, `signaling/${this.sessionId}/childCandidates`);
      const unsubCandidates = onValue(childCandidatesRef, (snapshot) => {
        const candidatesObj = snapshot.val();
        if (candidatesObj) {
          Object.values(candidatesObj).forEach(handleCandidate);
        }
      });
      this.listeners.push(() => off(childCandidatesRef, "value", unsubCandidates));

      const legacyChildCandidatesRef = ref(rtdb, `signaling/${this.sessionId}/candidates/child`);
      const unsubLegacyCandidates = onValue(legacyChildCandidatesRef, (snapshot) => {
        const candidatesObj = snapshot.val();
        if (candidatesObj) {
          Object.values(candidatesObj).forEach(handleCandidate);
        }
      });
      this.listeners.push(() => off(legacyChildCandidatesRef, "value", unsubLegacyCandidates));

      return this.sessionId;
    } catch (err: any) {
      this.onError("WebRTC initialization failed: " + (err?.message || err));
      throw err;
    }
  }

  public setupAudioProcessing(stream: MediaStream) {
    try {
      if (this.audioCtx) {
        if (this.audioCtx.state === "suspended") {
          this.audioCtx.resume().catch(() => {});
        }
        return;
      }
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioCtxClass) return;
      this.audioCtx = new AudioCtxClass();
      this.audioSourceNode = this.audioCtx.createMediaStreamSource(stream);

      // 1. Highpass filter: cuts fan / AC / desk vibration hum below 180Hz
      this.highpassFilter = this.audioCtx.createBiquadFilter();
      this.highpassFilter.type = "highpass";
      this.highpassFilter.frequency.value = this.isClarityFilterActive ? 180 : 20;

      // 2. Peaking filter: enhances speech formant presence around 2.5kHz
      this.speechFilter = this.audioCtx.createBiquadFilter();
      this.speechFilter.type = "peaking";
      this.speechFilter.frequency.value = 2500;
      this.speechFilter.Q.value = 1.2;
      this.speechFilter.gain.value = this.isClarityFilterActive ? 4.0 : 0.0;

      // 3. Audio Gain Booster (1.0x to 5.0x / 500% volume amplification)
      this.gainNode = this.audioCtx.createGain();
      this.gainNode.gain.value = this.currentGainMultiplier;

      // Connect DSP chain
      this.audioSourceNode.connect(this.highpassFilter);
      this.highpassFilter.connect(this.speechFilter);
      this.speechFilter.connect(this.gainNode);
      this.gainNode.connect(this.audioCtx.destination);
    } catch (e) {
      console.warn("Web Audio API setup notice:", e);
    }
  }

  public setAudioBoost(multiplier: number) {
    this.currentGainMultiplier = Math.max(0.5, Math.min(5.0, multiplier));
    if (this.gainNode && this.audioCtx) {
      this.gainNode.gain.setValueAtTime(this.currentGainMultiplier, this.audioCtx.currentTime);
    }
  }

  public toggleClarityFilter(enable: boolean) {
    this.isClarityFilterActive = enable;
    if (this.highpassFilter && this.speechFilter && this.audioCtx) {
      this.highpassFilter.frequency.setValueAtTime(enable ? 180 : 20, this.audioCtx.currentTime);
      this.speechFilter.gain.setValueAtTime(enable ? 4.0 : 0.0, this.audioCtx.currentTime);
    }
  }

  public async switchCamera(facing: "front" | "back" | "ultrawide" | string) {
    if (!this.sessionId) return;
    await set(ref(rtdb, `signaling/${this.sessionId}/cameraFacing`), facing);
  }

  public stop(notifyServer: boolean = true) {
    this.listeners.forEach((unsub) => unsub());
    this.listeners = [];
    this.pendingCandidates = [];
    this.isRemoteDescriptionSet = false;
    this.remoteStream = null;

    if (this.audioSourceNode) {
      try { this.audioSourceNode.disconnect(); } catch (_) {}
      this.audioSourceNode = null;
    }
    if (this.gainNode) {
      try { this.gainNode.disconnect(); } catch (_) {}
      this.gainNode = null;
    }
    if (this.highpassFilter) {
      try { this.highpassFilter.disconnect(); } catch (_) {}
      this.highpassFilter = null;
    }
    if (this.speechFilter) {
      try { this.speechFilter.disconnect(); } catch (_) {}
      this.speechFilter = null;
    }
    if (this.audioCtx) {
      try { this.audioCtx.close(); } catch (_) {}
      this.audioCtx = null;
    }

    if (notifyServer && this.childUid) {
      set(ref(rtdb, `streams/${this.childUid}/status`), {
        status: "STOPPED",
        streamType: "video",
        sessionId: "",
      }).catch(() => {});
    }

    if (this.sessionId) {
      set(ref(rtdb, `signaling/${this.sessionId}`), null).catch(() => {});
    }

    if (this.peerConnection) {
      this.peerConnection.close();
      this.peerConnection = null;
    }
    this.sessionId = "";
    this.childUid = "";
  }
}

