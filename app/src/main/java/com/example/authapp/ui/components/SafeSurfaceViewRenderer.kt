package com.example.authapp.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.*

@Composable
fun SafeSurfaceViewRenderer(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Hold a stable reference to SurfaceViewRenderer tied to this composable lifecycle
    val renderer = remember(context) {
        SurfaceViewRenderer(context).apply {
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
    }

    // Attach / Detach videoTrack sink safely without triggering recomposition loops
    DisposableEffect(videoTrack, renderer) {
        if (videoTrack != null) {
            try {
                videoTrack.addSink(renderer)
                FirebaseCrashlytics.getInstance().log("[WebRTC UI] Remote videoTrack attached to SurfaceViewRenderer sink")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }

        onDispose {
            if (videoTrack != null) {
                try {
                    videoTrack.removeSink(renderer)
                    FirebaseCrashlytics.getInstance().log("[WebRTC UI] Remote videoTrack detached from SurfaceViewRenderer sink")
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
            try {
                renderer.clearImage()
                renderer.release()
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}
