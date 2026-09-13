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
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                setEnableHardwareScaler(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setMirror(false)
                setZOrderMediaOverlay(true)
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
                    } catch (t: Throwable) {
                        FirebaseCrashlytics.getInstance().recordException(t)
                    }
                }
            }
        },
        update = { renderer ->
            val oldTrack = renderer.tag as? VideoTrack
            if (oldTrack != videoTrack) {
                oldTrack?.let {
                    try {
                        it.removeSink(renderer)
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] Old videoTrack detached from sink")
                    } catch (t: Throwable) {
                        FirebaseCrashlytics.getInstance().recordException(t)
                    }
                }
                renderer.tag = videoTrack
                videoTrack?.let { newTrack ->
                    try {
                        newTrack.addSink(renderer)
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] New videoTrack attached to sink")
                    } catch (t: Throwable) {
                        FirebaseCrashlytics.getInstance().recordException(t)
                    }
                }
            }
        },
        onRelease = { renderer ->
            val attachedTrack = renderer.tag as? VideoTrack
            attachedTrack?.let {
                try {
                    it.removeSink(renderer)
                } catch (t: Throwable) {
                    FirebaseCrashlytics.getInstance().recordException(t)
                }
            }
            renderer.tag = null
            try {
                renderer.clearImage()
            } catch (_: Throwable) {}
            try {
                renderer.release()
                FirebaseCrashlytics.getInstance().log("[WebRTC UI] SurfaceViewRenderer released cleanly")
            } catch (t: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(t)
            }
        },
        modifier = modifier
    )
}
