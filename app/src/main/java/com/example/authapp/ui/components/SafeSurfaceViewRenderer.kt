package com.example.authapp.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.*

@Composable
fun SafeSurfaceViewRenderer(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    modifier: Modifier = Modifier
) {
    // Keep track of the currently attached sink to safely swap or detach without releasing the EGL renderer
    var currentTrack by remember { mutableStateOf<VideoTrack?>(null) }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                setEnableHardwareScaler(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setMirror(false)
                if (eglContext != null) {
                    try {
                        init(eglContext, object : RendererCommon.RendererEvents {
                            override fun onFirstFrameRendered() {
                                FirebaseCrashlytics.getInstance().log("[WebRTC UI] First video frame rendered on SurfaceViewRenderer")
                            }
                            override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                                FirebaseCrashlytics.getInstance().log("[WebRTC UI] Frame resolution changed: ${videoWidth}x${videoHeight}, rot=$rotation")
                            }
                        })
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
            }
        },
        update = { renderer ->
            if (currentTrack != videoTrack) {
                // Detach previous track if any
                currentTrack?.let { oldTrack ->
                    try {
                        oldTrack.removeSink(renderer)
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] Old videoTrack detached from sink")
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
                currentTrack = videoTrack
                // Attach new track safely
                videoTrack?.let { newTrack ->
                    try {
                        newTrack.addSink(renderer)
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] New videoTrack attached to sink")
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
            }
        },
        onRelease = { renderer ->
            try {
                currentTrack?.removeSink(renderer)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            try {
                renderer.clearImage()
                renderer.release()
                FirebaseCrashlytics.getInstance().log("[WebRTC UI] SurfaceViewRenderer released cleanly")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        },
        modifier = modifier
    )
}
