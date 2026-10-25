import { rtdb } from "./firebase";
import { ref, set, onValue, off } from "firebase/database";

export class AudioStreamReceiver {
  private audioContext: AudioContext | null = null;
  private childUid: string = "";
  private isRunning: boolean = false;
  private nextPlayTime: number = 0;
  private lastProcessedSeq: number = 0;
  private unsubscribe: (() => void) | null = null;

  constructor(
    private onStateChange: (status: string) => void,
    private onError: (err: string) => void
  ) {}

  public async start(childUid: string) {
    this.stop(false);
    this.childUid = childUid;
    this.isRunning = true;
    this.nextPlayTime = 0;
    this.lastProcessedSeq = 0;

    try {
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      this.audioContext = new AudioCtx({ sampleRate: 16000 });

      if (this.audioContext.state === "suspended") {
        await this.audioContext.resume();
      }

      this.onStateChange("Connecting to phone microphone...");

      // 1. Tell Child to start audio stream
      const streamStatusRef = ref(rtdb, `streams/${this.childUid}/status`);
      await set(streamStatusRef, {
        status: "REQUESTED",
        streamType: "audio",
        sessionId: "audio_" + Date.now(),
      });

      // 2. Listen to real-time audio chunks from /audio_live/{childUid}/currentChunk
      const chunkRef = ref(rtdb, `audio_live/${this.childUid}/currentChunk`);
      const unsub = onValue(
        chunkRef,
        async (snapshot) => {
          if (!this.isRunning) return;
          const val = snapshot.val();
          if (!val || !val.data) return;

          const seq = Number(val.seq) || 0;
          if (seq <= this.lastProcessedSeq) return;
          this.lastProcessedSeq = seq;

          try {
            await this.playChunk(val.data);
            this.onStateChange("Live Ambient Audio Active");
          } catch (e: any) {
            console.warn("Error decoding audio chunk:", e);
          }
        },
        (err) => {
          this.onError("Audio stream error: " + err.message);
        }
      );

      this.unsubscribe = () => off(chunkRef, "value", unsub);
    } catch (e: any) {
      this.onError("Failed to start audio receiver: " + (e?.message || e));
    }
  }

  private async playChunk(base64Data: string) {
    if (!this.audioContext || !this.isRunning) return;

    // Convert Base64 to ArrayBuffer
    const binaryString = atob(base64Data);
    const len = binaryString.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }

    // Decode WAV audio data
    const audioBuffer = await this.audioContext.decodeAudioData(bytes.buffer.slice(0));

    // Schedule playback seamlessly
    const source = this.audioContext.createBufferSource();
    source.buffer = audioBuffer;

    const gainNode = this.audioContext.createGain();
    gainNode.gain.value = 1.0;

    source.connect(gainNode);
    gainNode.connect(this.audioContext.destination);

    const currentTime = this.audioContext.currentTime;
    const startTime = Math.max(currentTime, this.nextPlayTime);
    source.start(startTime);

    this.nextPlayTime = startTime + audioBuffer.duration;
  }

  public stop(notifyServer: boolean = true) {
    this.isRunning = false;

    if (this.unsubscribe) {
      this.unsubscribe();
      this.unsubscribe = null;
    }

    if (this.audioContext) {
      try {
        this.audioContext.close();
      } catch (_: any) {}
      this.audioContext = null;
    }

    if (notifyServer && this.childUid) {
      set(ref(rtdb, `streams/${this.childUid}/status`), {
        status: "STOPPED",
        streamType: "audio",
        sessionId: "",
      }).catch(() => {});

      set(ref(rtdb, `audio_live/${this.childUid}`), null).catch(() => {});
    }

    this.childUid = "";
    this.nextPlayTime = 0;
    this.lastProcessedSeq = 0;
    this.onStateChange("Idle");
  }
}
