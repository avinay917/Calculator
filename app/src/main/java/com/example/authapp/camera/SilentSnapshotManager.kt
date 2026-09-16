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

            val handlerThread = android.os.HandlerThread("SilentSnapshotThread").apply { start() }
            val bgHandler = Handler(handlerThread.looper)
            val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2)
            var isHandled = false

            fun cleanup() {
                try { handlerThread.quitSafely() } catch (_: Exception) {}
            }

            cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    // Watchdog: release camera if capture doesn't complete within 7 seconds
                    val timeoutRunnable = Runnable {
                        if (!isHandled) {
                            isHandled = true
                            try { camera.close() } catch (_: Exception) {}
                            try { imageReader.close() } catch (_: Exception) {}
                            cleanup()
                            onError("Snapshot timed out")
                        }
                    }
                    bgHandler.postDelayed(timeoutRunnable, 7000L)

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
                                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    if (bitmap != null) {
                                        // Downscale to max 1280x720 while maintaining aspect ratio
                                        val maxDim = 1280
                                        val width = bitmap.width
                                        val height = bitmap.height
                                        val (newWidth, newHeight) = if (width > maxDim || height > maxDim) {
                                            if (width > height) {
                                                Pair(maxDim, (maxDim * height.toFloat() / width).toInt())
                                            } else {
                                                Pair((maxDim * width.toFloat() / height).toInt(), maxDim)
                                            }
                                        } else {
                                            Pair(width, height)
                                        }
                                        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                                        FileOutputStream(outFile).use { fos ->
                                            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, fos)
                                        }
                                        if (scaledBitmap != bitmap) {
                                            scaledBitmap.recycle()
                                        }
                                        bitmap.recycle()
                                    } else {
                                        FileOutputStream(outFile).use { fos ->
                                            fos.write(bytes)
                                        }
                                    }
                                    onCaptured(outFile)
                                } catch (e: Exception) {
                                    onError(e.localizedMessage ?: "Image write error")
                                } finally {
                                    try { image.close() } catch (_: Exception) {}
                                    try { reader.close() } catch (_: Exception) {}
                                    try { camera.close() } catch (_: Exception) {}
                                    cleanup()
                                }
                            } else {
                                try { reader.close() } catch (_: Exception) {}
                                try { camera.close() } catch (_: Exception) {}
                                cleanup()
                                onError("Acquired image frame was null")
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
                                    try { camera.close() } catch (_: Exception) {}
                                    try { imageReader.close() } catch (_: Exception) {}
                                    cleanup()
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                onError("Camera session config failed")
                                try { camera.close() } catch (_: Exception) {}
                                try { imageReader.close() } catch (_: Exception) {}
                                cleanup()
                            }
                        }, bgHandler)

                    } catch (e: Exception) {
                        onError("Session setup error: ${e.localizedMessage}")
                        try { camera.close() } catch (_: Exception) {}
                        try { imageReader.close() } catch (_: Exception) {}
                        cleanup()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    try { camera.close() } catch (_: Exception) {}
                    try { imageReader.close() } catch (_: Exception) {}
                    cleanup()
                    onError("Camera disconnected by system")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    try { camera.close() } catch (_: Exception) {}
                    try { imageReader.close() } catch (_: Exception) {}
                    cleanup()
                    onError("CameraDevice error code: $error")
                }
            }, bgHandler)

        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            onError(e.localizedMessage ?: "Unknown Camera2 error")
        }
    }
}
