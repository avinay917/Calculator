package com.example.authapp.webrtc

import android.content.Context
import android.content.Intent
import com.example.authapp.analytics.AppHealthTelemetry
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.*

object WebRtcScreenCapturerHelper {

    data class ScreenCaptureResult(
        val capturer: VideoCapturer,
        val surfaceTextureHelper: SurfaceTextureHelper,
        val videoSource: VideoSource,
        val isRunning: Boolean
    )

    fun startScreenCapture(
        appContext: Context,
        eglBaseContext: EglBase.Context,
        factory: PeerConnectionFactory?,
        mediaProjectionData: Intent
    ): ScreenCaptureResult? {
        return try {
            val screenCapturer = ScreenCapturerAndroid(
                mediaProjectionData,
                object : android.media.projection.MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        FirebaseCrashlytics.getInstance().log("[WebRTC] Screen capturer stopped by system")
                    }
                }
            )
            val surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBaseContext)
            val videoSource = factory?.createVideoSource(true) ?: return null
            screenCapturer.initialize(surfaceTextureHelper, appContext, videoSource.capturerObserver)

            val dm = appContext.resources.displayMetrics
            val capWidth = (dm.widthPixels / 2).coerceAtLeast(480)
            val capHeight = (dm.heightPixels / 2).coerceAtLeast(640)
            screenCapturer.startCapture(capWidth, capHeight, 15)

            AppHealthTelemetry.logDiagnostic(
                appContext, "LIVE_SCREEN", "SUCCESS", "Screen capturer active (${capWidth}x${capHeight}@15fps)"
            )
            ScreenCaptureResult(screenCapturer, surfaceTextureHelper, videoSource, true)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] Screen capture init failed: ${e.localizedMessage}")
            AppHealthTelemetry.logDiagnostic(appContext, "LIVE_SCREEN", "FAILED", "Screen capturer error: ${e.localizedMessage}")
            null
        }
    }
}
