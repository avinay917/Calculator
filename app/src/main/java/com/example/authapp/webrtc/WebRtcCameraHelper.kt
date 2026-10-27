package com.example.authapp.webrtc

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.VideoCapturer

object WebRtcCameraHelper {

    fun createVideoCapturer(
        appContext: Context,
        preferFront: Boolean,
        cameraEventsHandler: CameraVideoCapturer.CameraEventsHandler
    ): VideoCapturer? {
        try {
            val enumerator = Camera2Enumerator(appContext)
            val capturer = findCapturer(enumerator, preferFront, cameraEventsHandler)
            if (capturer != null) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Created Camera2Capturer successfully (preferFront=$preferFront)")
                return capturer
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] Camera2Enumerator failed: ${e.localizedMessage}")
        }

        try {
            val enumerator = Camera1Enumerator(false)
            val capturer = findCapturer(enumerator, preferFront, cameraEventsHandler)
            if (capturer != null) {
                FirebaseCrashlytics.getInstance().log("[WebRTC] Created Camera1Capturer fallback successfully (preferFront=$preferFront)")
                return capturer
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] Camera1Enumerator failed: ${e.localizedMessage}")
        }

        FirebaseCrashlytics.getInstance().log("[WebRTC] ERROR: No camera capturer could be created!")
        return null
    }

    private fun findCapturer(
        enumerator: CameraEnumerator,
        preferFront: Boolean,
        handler: CameraVideoCapturer.CameraEventsHandler
    ): VideoCapturer? {
        val deviceNames = enumerator.deviceNames ?: return null
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName) == preferFront) {
                try {
                    val capturer = enumerator.createCapturer(deviceName, handler)
                    if (capturer != null) return capturer
                } catch (_: Throwable) {}
            }
        }
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName) != preferFront) {
                try {
                    val capturer = enumerator.createCapturer(deviceName, handler)
                    if (capturer != null) return capturer
                } catch (_: Throwable) {}
            }
        }
        for (deviceName in deviceNames) {
            try {
                val capturer = enumerator.createCapturer(deviceName, handler)
                if (capturer != null) return capturer
            } catch (_: Throwable) {}
        }
        return null
    }

    fun getCameraDeviceName(appContext: Context, targetMode: String): String? {
        try {
            val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
            val enumerator = Camera2Enumerator(appContext)
            val deviceNames = enumerator.deviceNames ?: return null

            var frontDevice: String? = null
            var standardBackDevice: String? = null
            var ultrawideDevice: String? = null
            var minFocalLength = Float.MAX_VALUE

            for (deviceName in deviceNames) {
                if (enumerator.isFrontFacing(deviceName)) {
                    if (frontDevice == null) frontDevice = deviceName
                } else {
                    try {
                        val chars = cameraManager.getCameraCharacteristics(deviceName)
                        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        val focalLength = focalLengths?.minOrNull() ?: 5.0f
                        if (focalLength < 2.8f) ultrawideDevice = deviceName
                        if (focalLength < minFocalLength) minFocalLength = focalLength
                        if (standardBackDevice == null || focalLength >= 2.8f) standardBackDevice = deviceName
                    } catch (_: Exception) {
                        if (standardBackDevice == null) standardBackDevice = deviceName
                    }
                }
            }

            if (ultrawideDevice == null) {
                val backDevices = deviceNames.filter { !enumerator.isFrontFacing(it) }
                if (backDevices.size > 1) {
                    ultrawideDevice = backDevices.minByOrNull { dev ->
                        try {
                            val chars = cameraManager.getCameraCharacteristics(dev)
                            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: 5.0f
                        } catch (_: Exception) { 5.0f }
                    }
                }
            }

            return when (targetMode.lowercase()) {
                "front" -> frontDevice ?: deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                "ultrawide", "ultra_wide", "wide", "0.5x" -> ultrawideDevice ?: standardBackDevice ?: deviceNames.firstOrNull { !enumerator.isFrontFacing(it) }
                else -> standardBackDevice ?: deviceNames.firstOrNull { !enumerator.isFrontFacing(it) }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[WebRTC] getCameraDeviceName error: ${e.localizedMessage}")
            return null
        }
    }

    fun switchCamera(
        videoCapturer: VideoCapturer?,
        isCameraRunning: Boolean,
        onSwitched: ((Boolean) -> Unit)? = null
    ) {
        val capturer = videoCapturer as? CameraVideoCapturer ?: return
        if (!isCameraRunning) return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) { onSwitched?.invoke(isFrontCamera) }
            override fun onCameraSwitchError(errorDescription: String?) {}
        })
    }

    fun switchCameraMode(
        appContext: Context,
        videoCapturer: VideoCapturer?,
        isCameraRunning: Boolean,
        targetMode: String,
        onSwitched: ((String) -> Unit)? = null
    ) {
        val capturer = videoCapturer as? CameraVideoCapturer ?: return
        if (!isCameraRunning) return
        val targetDeviceName = getCameraDeviceName(appContext, targetMode)
        if (targetDeviceName != null) {
            try {
                capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFrontCamera: Boolean) { onSwitched?.invoke(targetMode) }
                    override fun onCameraSwitchError(errorDescription: String?) {
                        switchCamera(videoCapturer, isCameraRunning) { isFront -> onSwitched?.invoke(if (isFront) "front" else "back") }
                    }
                }, targetDeviceName)
                return
            } catch (_: Throwable) {}
        }
        switchCamera(videoCapturer, isCameraRunning) { isFront -> onSwitched?.invoke(if (isFront) "front" else "back") }
    }
}
