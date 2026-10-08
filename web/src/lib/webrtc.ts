import { rtdb } from "./firebase";
import { ref, set, onValue, push, off } from "firebase/database";

const ICE_SERVERS: RTCIceServer[] = [
  { urls: "stun:stun.l.google.com:19302" },
  { urls: "stun:stun1.l.google.com:19302" },
  { urls: "stun:stun2.l.google.com:19302" },
  { urls: "stun:global.stun.twilio.com:3478" },
  {
    urls: "turn:openrelay.metered.ca:80",
    username: "openrelayproject",
    credential: "openrelayproject",
  },
  {
    urls: "turn:openrelay.metered.ca:443",
    username: "openrelayproject",
    credential: "openrelayproject",
  },
  {
    urls: "turn:openrelay.metered.ca:443?transport=tcp",
    username: "openrelayproject",
    credential: "openrelayproject",
  },
  {
    urls: "turns:openrelay.metered.ca:443?transport=tcp",
    username: "openrelayproject",
    credential: "openrelayproject",
  },
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

  constructor(
    private onRemoteStream: (stream: MediaStream) => void,
    private onConnectionStateChange: (state: RTCPeerConnectionState) => void,
    private onError: (err: string) => void
  ) {}

  public async startReceiver(childUid: string, streamType: "video" | "audio" = "video") {
    this.stop();
    this.childUid = childUid;
    this.sessionId = "session_" + Date.now();
    this.pendingCandidates = [];
    this.isRemoteDescriptionSet = false;

    try {
      this.peerConnection = new RTCPeerConnection({
        iceServers: ICE_SERVERS,
        iceCandidatePoolSize: 2,
      });

      this.peerConnection.ontrack = (event) => {
        if (event.streams && event.streams[0]) {
          this.onRemoteStream(event.streams[0]);
        }
      };

      this.peerConnection.onconnectionstatechange = () => {
        if (this.peerConnection) {
          this.onConnectionStateChange(this.peerConnection.connectionState);
        }
      };

      this.peerConnection.onicecandidate = (event) => {
        if (event.candidate && this.sessionId) {
          const candRef = push(ref(rtdb, `signaling/${this.sessionId}/candidates/parent`));
          set(candRef, {
            candidate: event.candidate.candidate,
            sdpMid: event.candidate.sdpMid,
            sdpMLineIndex: event.candidate.sdpMLineIndex,
          });
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
        if (sdp && this.peerConnection && !this.isRemoteDescriptionSet && this.peerConnection.signalingState === "stable") {
          try {
            await this.peerConnection.setRemoteDescription(new RTCSessionDescription({ type: "offer", sdp }));
            this.isRemoteDescriptionSet = true;

            // Drain queued ICE candidates
            for (const pending of this.pendingCandidates) {
              try {
                await this.peerConnection.addIceCandidate(new RTCIceCandidate(pending));
              } catch (_) {}
            }
            this.pendingCandidates = [];

            const answer = await this.peerConnection.createAnswer();
            await this.peerConnection.setLocalDescription(answer);

            // Send SDP Answer to child
            await set(ref(rtdb, `signaling/${this.sessionId}/sdpAnswer`), answer.sdp);
          } catch (e: any) {
            this.onError("SDP negotiation failed: " + (e?.message || e));
          }
        }
      });
      this.listeners.push(() => off(sdpOfferRef, "value", unsubscribeOffer));

      // 3. Listen to Child ICE Candidates
      const childCandidatesRef = ref(rtdb, `signaling/${this.sessionId}/candidates/child`);
      const unsubscribeCandidates = onValue(childCandidatesRef, (snapshot) => {
        const candidatesObj = snapshot.val();
        if (candidatesObj) {
          Object.values(candidatesObj).forEach(async (cand: any) => {
            if (cand && cand.candidate) {
              if (!this.isRemoteDescriptionSet || !this.peerConnection) {
                this.pendingCandidates.push(cand);
              } else {
                try {
                  await this.peerConnection.addIceCandidate(new RTCIceCandidate(cand));
                } catch (_) {}
              }
            }
          });
        }
      });
      this.listeners.push(() => off(childCandidatesRef, "value", unsubscribeCandidates));

      return this.sessionId;
    } catch (err: any) {
      this.onError("WebRTC initialization failed: " + (err?.message || err));
      throw err;
    }
  }

  public async switchCamera(facing: "front" | "back") {
    if (!this.sessionId) return;
    await set(ref(rtdb, `signaling/${this.sessionId}/cameraFacing`), facing);
  }

  public stop() {
    this.listeners.forEach((unsub) => unsub());
    this.listeners = [];
    this.pendingCandidates = [];
    this.isRemoteDescriptionSet = false;

    if (this.childUid) {
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

