package com.example.authapp.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeUtils {
    /**
     * Generates an Android Bitmap for a QR Code string.
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Helper to return Compose ImageBitmap directly.
     */
    fun generateQrImageBitmap(content: String, size: Int = 512): ImageBitmap? {
        return generateQrBitmap(content, size)?.asImageBitmap()
    }

    /**
     * Opens Camera QR Code Scanner via Google Play Services.
     * No manual camera permission dialog required from user.
     */
    fun startQrScanner(
        context: android.content.Context,
        onCodeScanned: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        try {
            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue?.trim().orEmpty()
                    val digits = raw.filter { it.isDigit() }
                    val finalCode = if (digits.length == 6) digits else if (raw.length == 6) raw else digits.take(6)
                    if (finalCode.isNotEmpty()) {
                        onCodeScanned(finalCode)
                    }
                }
                .addOnFailureListener { e ->
                    onError(e.localizedMessage ?: "Scanner unavailable")
                }
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Failed to launch camera scanner")
        }
    }
}
