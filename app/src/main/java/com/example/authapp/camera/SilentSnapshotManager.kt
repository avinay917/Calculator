package com.example.authapp.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.io.FileOutputStream

class SilentSnapshotManager(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun captureSnapshot(
        isFront: Boolean = false,
        onCaptured: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onError("Camera permission not granted")
            return
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            onError("CameraManager not available")
            return
        }

        try {
            val targetFacing = if (isFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            var targetCameraId: String? = null

            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == targetFacing) {
                    targetCameraId = id
                    break
                }
            }

            if (targetCameraId == null) {
                targetCameraId = cameraManager.cameraIdList.firstOrNull()
            }

            if (targetCameraId == null) {
                onError("No available camera found")
                return
            }

            val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2)
            val bgHandler = Handler(Looper.getMainLooper())
            var isHandled = false

            cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    // Watchdog: release camera if capture doesn't complete within 6 seconds
                    val timeoutRunnable = Runnable {
                        if (!isHandled) {
                            isHandled = true
                            try { camera.close() } catch (_: Exception) {}
                            try { imageReader.close() } catch (_: Exception) {}
                            onError("Snapshot timed out")
                        }
                    }
                    bgHandler.postDelayed(timeoutRunnable, 6000L)

                    try {
                        imageReader.setOnImageAvailableListener({ reader ->
                            if (isHandled) return@setOnImageAvailableListener
                            isHandled = true
                            bgHandler.removeCallbacks(timeoutRunnable)
                            val image = reader.acquireLatestImage()
                            if (image != null) {
                                try {
                                    val planes = image.planes
                                    val buffer = planes[0].buffer
                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)

                                    val outFile = File(context.cacheDir, "snapshot_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(outFile).use { fos ->
                                        fos.write(bytes)
                                    }
                                    onCaptured(outFile)
                                } catch (e: Exception) {
                                    onError(e.localizedMessage ?: "Image write error")
                                } finally {
                                    image.close()
                                    reader.close()
                                    camera.close()
                                }
                            }
                        }, bgHandler)

                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(imageReader.surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }

                        camera.createCaptureSession(listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    session.capture(captureBuilder.build(), null, bgHandler)
                                } catch (e: Exception) {
                                    onError("Capture failed: ${e.localizedMessage}")
                                    camera.close()
                                    imageReader.close()
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                onError("Camera session config failed")
                                camera.close()
                                imageReader.close()
                            }
                        }, bgHandler)

                    } catch (e: Exception) {
                        onError("Session setup error: ${e.localizedMessage}")
                        camera.close()
                        imageReader.close()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    imageReader.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    imageReader.close()
                    onError("CameraDevice error code: $error")
                }
            }, bgHandler)

        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            onError(e.localizedMessage ?: "Unknown Camera2 error")
        }
    }
}
