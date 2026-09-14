package com.example.authapp.ui.components

import android.view.SurfaceHolder
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.authapp.analytics.AppHealthTelemetry
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.*

private class SurfaceRendererState(
    var isInitialized: Boolean = false,
    var attachedTrack: VideoTrack? = null,
    var pendingTrack: VideoTrack? = null
)

@Composable
fun SafeSurfaceViewRenderer(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    modifier: Modifier = Modifier
) {
    if (eglContext == null) return

    AndroidView(
        factory = { ctx ->
            val state = SurfaceRendererState()
            SurfaceViewRenderer(ctx).apply {
                tag = state
                setEnableHardwareScaler(false)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setMirror(false)
                setZOrderMediaOverlay(true)

                try {
                    init(eglContext, object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {
                            AppHealthTelemetry.logDiagnostic(ctx, "LIVE_VIDEO", "SUCCESS", "Parent SurfaceView rendered first video frame!")
                            FirebaseCrashlytics.getInstance().log("[WebRTC UI] First video frame rendered on SurfaceViewRenderer")
                        }
                        override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                            FirebaseCrashlytics.getInstance().log("[WebRTC UI] Frame resolution changed: ${videoWidth}x${videoHeight}, rot=$rotation")
                        }
                    })
                    state.isInitialized = true
                } catch (t: Throwable) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC UI] SurfaceViewRenderer init failed: ${t.localizedMessage}")
                    FirebaseCrashlytics.getInstance().recordException(t)
                }

                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] SurfaceView surfaceCreated (valid=${holder.surface?.isValid})")
                        val currentPending = state.pendingTrack
                        if (currentPending != null && state.isInitialized) {
                            try {
                                currentPending.addSink(this@apply)
                                state.attachedTrack = currentPending
                                state.pendingTrack = null
                                FirebaseCrashlytics.getInstance().log("[WebRTC UI] Pending track attached to sink on surfaceCreated")
                            } catch (t: Throwable) {
                                FirebaseCrashlytics.getInstance().recordException(t)
                            }
                        }
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] SurfaceView surfaceDestroyed (detaching sink)")
                        state.attachedTrack?.let {
                            try {
                                it.removeSink(this@apply)
                                FirebaseCrashlytics.getInstance().log("[WebRTC UI] Sink detached upon surfaceDestroyed")
                            } catch (t: Throwable) {
                                FirebaseCrashlytics.getInstance().recordException(t)
                            }
                        }
                        state.pendingTrack = state.attachedTrack ?: state.pendingTrack
                        state.attachedTrack = null
                    }
                })
            }
        },
        update = { renderer ->
            val state = renderer.tag as? SurfaceRendererState ?: return@AndroidView
            if (!state.isInitialized) return@AndroidView

            if (state.attachedTrack != videoTrack) {
                state.attachedTrack?.let { old ->
                    try {
                        old.removeSink(renderer)
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] Detached old videoTrack")
                    } catch (t: Throwable) {
                        FirebaseCrashlytics.getInstance().recordException(t)
                    }
                }
                state.attachedTrack = null

                if (videoTrack != null) {
                    val surface = try { renderer.holder?.surface } catch (_: Throwable) { null }
                    if (surface?.isValid == true) {
                        try {
                            videoTrack.addSink(renderer)
                            state.attachedTrack = videoTrack
                            state.pendingTrack = null
                            FirebaseCrashlytics.getInstance().log("[WebRTC UI] Attached new videoTrack directly (surface isValid)")
                        } catch (t: Throwable) {
                            FirebaseCrashlytics.getInstance().recordException(t)
                        }
                    } else {
                        // Queue track to be attached when surfaceCreated is called
                        state.pendingTrack = videoTrack
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] Queued videoTrack (waiting for surfaceCreated)")
                    }
                } else {
                    state.pendingTrack = null
                }
            }
        },
        onRelease = { renderer ->
            val state = renderer.tag as? SurfaceRendererState
            state?.pendingTrack = null
            state?.attachedTrack?.let {
                try {
                    it.removeSink(renderer)
                } catch (t: Throwable) {
                    FirebaseCrashlytics.getInstance().recordException(t)
                }
            }
            state?.attachedTrack = null
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
