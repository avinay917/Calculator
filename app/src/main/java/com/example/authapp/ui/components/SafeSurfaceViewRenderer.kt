package com.example.authapp.ui.components

import android.view.SurfaceHolder
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
    if (eglContext == null) return

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                setEnableHardwareScaler(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setMirror(false)

                try {
                    init(eglContext, object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {
                            FirebaseCrashlytics.getInstance().log("[WebRTC UI] First video frame rendered on SurfaceViewRenderer")
                        }
                        override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
                            FirebaseCrashlytics.getInstance().log("[WebRTC UI] Frame resolution changed: ${videoWidth}x${videoHeight}, rot=$rotation")
                        }
                    })
                    tag = "INITIALIZED"
                } catch (t: Throwable) {
                    FirebaseCrashlytics.getInstance().log("[WebRTC UI] SurfaceViewRenderer init failed: ${t.localizedMessage}")
                    FirebaseCrashlytics.getInstance().recordException(t)
                }

                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] SurfaceView surfaceCreated (valid=${holder.surface?.isValid})")
                        val pending = (getTag(android.R.id.custom) as? VideoTrack)
                        if (pending != null && (tag == "INITIALIZED" || tag is VideoTrack)) {
                            try {
                                pending.addSink(this@apply)
                                tag = pending
                                setTag(android.R.id.custom, null)
                                FirebaseCrashlytics.getInstance().log("[WebRTC UI] Pending track attached to sink on surfaceCreated")
                            } catch (t: Throwable) {
                                FirebaseCrashlytics.getInstance().recordException(t)
                            }
                        }
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] SurfaceView surfaceDestroyed (detaching sink)")
                        val cur = tag as? VideoTrack
                        cur?.let {
                            try {
                                it.removeSink(this@apply)
                                FirebaseCrashlytics.getInstance().log("[WebRTC UI] Sink detached upon surfaceDestroyed")
                            } catch (t: Throwable) {
                                FirebaseCrashlytics.getInstance().recordException(t)
                            }
                        }
                        tag = "INITIALIZED"
                    }
                })
            }
        },
        update = { renderer ->
            val isInit = renderer.tag == "INITIALIZED" || renderer.tag is VideoTrack
            if (!isInit) return@AndroidView

            val currentAttached = renderer.tag as? VideoTrack
            if (currentAttached != videoTrack) {
                currentAttached?.let { old ->
                    try {
                        old.removeSink(renderer)
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] Detached old videoTrack")
                    } catch (t: Throwable) {
                        FirebaseCrashlytics.getInstance().recordException(t)
                    }
                }

                renderer.tag = "INITIALIZED"

                if (videoTrack != null) {
                    val surface = try { renderer.holder?.surface } catch (_: Throwable) { null }
                    if (surface?.isValid == true) {
                        try {
                            videoTrack.addSink(renderer)
                            renderer.tag = videoTrack
                            renderer.setTag(android.R.id.custom, null)
                            FirebaseCrashlytics.getInstance().log("[WebRTC UI] Attached new videoTrack directly (surface isValid)")
                        } catch (t: Throwable) {
                            FirebaseCrashlytics.getInstance().recordException(t)
                        }
                    } else {
                        // Queue track to be attached when surfaceCreated is called
                        renderer.setTag(android.R.id.custom, videoTrack)
                        FirebaseCrashlytics.getInstance().log("[WebRTC UI] Queued videoTrack (waiting for surfaceCreated)")
                    }
                } else {
                    renderer.setTag(android.R.id.custom, null)
                }
            }
        },
        onRelease = { renderer ->
            try {
                renderer.setTag(android.R.id.custom, null)
                val attached = renderer.tag as? VideoTrack
                attached?.let {
                    try {
                        it.removeSink(renderer)
                    } catch (t: Throwable) {
                        FirebaseCrashlytics.getInstance().recordException(t)
                    }
                }
                renderer.tag = null
                renderer.release()
                FirebaseCrashlytics.getInstance().log("[WebRTC UI] SurfaceViewRenderer released cleanly")
            } catch (t: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(t)
            }
        },
        modifier = modifier
    )
}
