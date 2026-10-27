package com.example.authapp.webrtc

import android.content.Context
import com.example.authapp.analytics.AppHealthTelemetry
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.*

object WebRtcBroadcasterVideoHelper {

    data class CameraCaptureResult(
        val capturer: VideoCapturer,
        val surfaceTextureHelper: SurfaceTextureHelper,
        val videoSource: VideoSource,
        val isRunning: Boolean
    )

    fun startCameraCapture(
        appContext: Context,
        eglBaseContext: EglBase.Context,
        factory: PeerConnectionFactory?,
        cameraEventsHandler: CameraVideoCapturer.CameraEventsHandler
    ): CameraCaptureResult? {
        var capturer: VideoCapturer? = null
        for (attempt in 1..3) {
            capturer = WebRtcCameraHelper.createVideoCapturer(appContext, preferFront = true, cameraEventsHandler)
                ?: WebRtcCameraHelper.createVideoCapturer(appContext, preferFront = false, cameraEventsHandler)
            if (capturer != null) break
            try { Thread.sleep(250L) } catch (_: InterruptedException) {}
        }
        if (capturer == null) {
            AppHealthTelemetry.logDiagnostic(appContext, "LIVE_VIDEO", "FAILED", "No camera capturer could be created")
            return null
        }

        var surfaceTextureHelper: SurfaceTextureHelper? = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        val videoSource = factory?.createVideoSource(capturer.isScreencast) ?: return null
        capturer.initialize(surfaceTextureHelper, appContext, videoSource.capturerObserver)

        var started = false
        val resolutions = listOf(
            Triple(640, 480, 30),
            Triple(640, 480, 15),
            Triple(480, 360, 24),
            Triple(320, 240, 15)
        )
        for ((w, h, fps) in resolutions) {
            try {
                capturer.startCapture(w, h, fps)
                started = true
                break
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Camera ${w}x${h}@$fps failed: ${e.localizedMessage}")
            }
        }

        if (!started) {
            try { capturer.dispose() } catch (_: Exception) {}
            try { surfaceTextureHelper?.dispose() } catch (_: Exception) {}
            val fallbackCapturer = WebRtcCameraHelper.createVideoCapturer(appContext, preferFront = false, cameraEventsHandler)
            if (fallbackCapturer != null) {
                capturer = fallbackCapturer
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThreadFallback", eglBaseContext)
                fallbackCapturer.initialize(surfaceTextureHelper, appContext, videoSource.capturerObserver)
                for ((w, h, fps) in resolutions) {
                    try {
                        fallbackCapturer.startCapture(w, h, fps)
                        started = true
                        break
                    } catch (_: Exception) {}
                }
            }
        }

        if (!started || surfaceTextureHelper == null) return null
        return CameraCaptureResult(capturer, surfaceTextureHelper, videoSource, started)
    }

    fun configureAdaptiveBitrate(transceiver: RtpTransceiver?) {
        try {
            val sender = transceiver?.sender
            val parameters = sender?.parameters
            if (parameters != null && parameters.encodings.isNotEmpty()) {
                for (encoding in parameters.encodings) {
                    encoding.maxBitrateBps = 550 * 1000 // 550 Kbps: Crystal clear 720p/480p VP8 with 75% bandwidth reduction
                    encoding.minBitrateBps = 150 * 1000 // 150 Kbps
                    encoding.maxFramerate = 24 // 24 FPS: Smooth motion with 20% bandwidth savings
                }
                sender.parameters = parameters
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] Adaptive bitrate config notice: ${e.localizedMessage}")
        }
    }
}
