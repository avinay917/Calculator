package com.example.authapp.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.authapp.analytics.AppHealthTelemetry
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ChildOverlayWindowManager(private val context: Context, private val mainHandler: Handler) {

    private var overlayView: View? = null
    private var studyModeOverlayView: View? = null

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun ensureOverlayWindow() {
        mainHandler.post {
            if (overlayView != null) return@post
            if (!hasOverlayPermission()) {
                FirebaseCrashlytics.getInstance().log("[ChildService] ensureOverlayWindow: SYSTEM_ALERT_WINDOW permission missing, cannot add overlay")
                AppHealthTelemetry.logDiagnostic(
                    context.applicationContext,
                    "OVERLAY_WINDOW",
                    "PERMISSION_DENIED",
                    "SYSTEM_ALERT_WINDOW not granted; foreground service alone may fail to capture camera on Android 11+"
                )
                return@post
            }
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val params = WindowManager.LayoutParams(
                    1, 1,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSPARENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                }
                val view = View(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                }
                wm.addView(view, params)
                overlayView = view
                FirebaseCrashlytics.getInstance().log("[ChildService] 1x1 transparent overlay window attached for camera compliance")
                AppHealthTelemetry.logDiagnostic(
                    context.applicationContext,
                    "OVERLAY_WINDOW",
                    "SUCCESS",
                    "1x1 overlay window attached successfully"
                )
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                AppHealthTelemetry.logDiagnostic(
                    context.applicationContext,
                    "OVERLAY_WINDOW",
                    "FAILED",
                    "Failed to attach overlay window: ${e.localizedMessage}",
                    e.message
                )
            }
        }
    }

    fun removeOverlayWindow() {
        mainHandler.post {
            overlayView?.let { view ->
                try {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    wm?.removeView(view)
                    FirebaseCrashlytics.getInstance().log("[ChildService] 1x1 overlay window removed")
                } catch (_: Exception) {}
                overlayView = null
            }
        }
    }

    fun updateStudyModeOverlay(isActive: Boolean, message: String) {
        mainHandler.post {
            if (isActive) {
                if (studyModeOverlayView == null && hasOverlayPermission()) {
                    try {
                        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        } else {
                            @Suppress("DEPRECATION")
                            WindowManager.LayoutParams.TYPE_PHONE
                        }
                        val params = WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            layoutType,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT
                        )
                        val layout = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setBackgroundColor(Color.parseColor("#E6000000"))
                            setPadding(48, 48, 48, 48)

                            val icon = TextView(context).apply {
                                text = "🔒"
                                textSize = 48f
                                gravity = Gravity.CENTER
                            }
                            val title = TextView(context).apply {
                                text = "Study Mode Active"
                                textSize = 24f
                                setTextColor(Color.WHITE)
                                setTypeface(null, Typeface.BOLD)
                                gravity = Gravity.CENTER
                                setPadding(0, 24, 0, 12)
                            }
                            val subtitle = TextView(context).apply {
                                text = message.ifEmpty { "Focus on your studies! This device is locked by your parent." }
                                textSize = 16f
                                setTextColor(Color.LTGRAY)
                                gravity = Gravity.CENTER
                            }
                            addView(icon)
                            addView(title)
                            addView(subtitle)
                        }
                        wm.addView(layout, params)
                        studyModeOverlayView = layout
                        FirebaseCrashlytics.getInstance().log("[ChildService] Study Mode Overlay displayed")
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[ChildService] Study Mode Overlay Error: ${e.localizedMessage}")
                    }
                }
            } else {
                studyModeOverlayView?.let { view ->
                    try {
                        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                        wm?.removeView(view)
                        FirebaseCrashlytics.getInstance().log("[ChildService] Study Mode Overlay removed")
                    } catch (_: Exception) {}
                    studyModeOverlayView = null
                }
            }
        }
    }

    fun removeAllOverlays() {
        removeOverlayWindow()
        updateStudyModeOverlay(false, "")
    }
}
