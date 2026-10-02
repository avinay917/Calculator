package com.example.authapp.ui.components

import android.view.SurfaceHolder
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.authapp.analytics.AppHealthTelemetry
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.*

@Composable
fun SafeSurfaceViewRenderer(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    modifier: Modifier = Modifier,
    onRendererReleased: (() -> Unit)? = null
) {
    if (eglContext == null) return

    DisposableEffect(videoTrack) {
        var renderer: SurfaceViewRenderer? = null
        var currentTrack: VideoTrack? = videoTrack

        onDispose {
            try {
                currentTrack?.removeSink(renderer)
            } catch (_: Exception) {
            }
            try {
                renderer?.release()
            } catch (_: Exception) {
            }
            onRendererReleased?.invoke()
        }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                try {
                    setEnableHardwareScaler(true)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    setZOrderMediaOverlay(true)
                    init(eglContext, object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {
                            AppHealthTelemetry.logDiagnostic(
                                ctx,
                                "LIVE_VIDEO",
                                "SUCCESS",
                                "First frame rendered"
                            )
                            FirebaseCrashlytics.getInstance()
                                .log("[SurfaceViewRenderer] First frame rendered")
                        }

                        override fun onFrameResolutionChanged(
                            videoWidth: Int,
                            videoHeight: Int,
                            rotation: Int
                        ) {
                            FirebaseCrashlytics.getInstance()
                                .log("[SurfaceViewRenderer] Resolution: ${videoWidth}x${videoHeight}")
                        }
                    })
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        },
        modifier = modifier,
        update = { renderer ->
            try {
                if (videoTrack != null) {
                    videoTrack.addSink(renderer)
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    )
}
