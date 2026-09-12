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
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    DisposableEffect(videoTrack, rendererRef, isInitialized) {
        val renderer = rendererRef
        if (renderer != null && videoTrack != null && isInitialized) {
            try {
                videoTrack.addSink(renderer)
                FirebaseCrashlytics.getInstance().log("[WebRTC UI] Remote videoTrack attached to SurfaceViewRenderer sink")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
        onDispose {
            if (renderer != null && videoTrack != null && isInitialized) {
                try {
                    videoTrack.removeSink(renderer)
                    FirebaseCrashlytics.getInstance().log("[WebRTC UI] Remote videoTrack detached from SurfaceViewRenderer sink")
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                setZOrderMediaOverlay(true)
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
                        isInitialized = true
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
                rendererRef = this
            }
        },
        update = { renderer ->
            rendererRef = renderer
            if (eglContext != null && !isInitialized) {
                try {
                    renderer.init(eglContext, null)
                    isInitialized = true
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        },
        onRelease = { renderer ->
            try {
                renderer.clearImage()
                renderer.release()
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
            rendererRef = null
            isInitialized = false
        },
        modifier = modifier
    )
}
