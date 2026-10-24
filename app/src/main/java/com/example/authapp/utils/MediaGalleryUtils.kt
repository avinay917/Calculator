package com.example.authapp.utils

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.MediaGalleryItem
import com.google.firebase.crashlytics.FirebaseCrashlytics

object MediaGalleryUtils {

    fun scanAndSyncMediaGallery(context: Context, childUid: String) {
        if (childUid.isEmpty()) return
        try {
            val mediaList = mutableListOf<MediaGalleryItem>()

            // 1. Scan Images
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            )

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                var count = 0
                while (cursor.moveToNext() && count < 50) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "photo_${id}.jpg"
                    val path = if (pathColumn >= 0) cursor.getString(pathColumn) ?: "" else ""
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000L
                    val thumbBase64 = if (count < 30) getMicroThumbnailBase64(context, id, path) else ""

                    mediaList.add(
                        MediaGalleryItem(
                            id = id.toString(),
                            fileName = name,
                            filePath = path,
                            fileType = "IMAGE",
                            sizeBytes = size,
                            dateAdded = dateAdded,
                            thumbnailBase64 = thumbBase64
                        )
                    )
                    count++
                }
            }

            // 2. Scan Videos
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
            )

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                var count = 0
                while (cursor.moveToNext() && count < 30) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "video_${id}.mp4"
                    val path = if (pathColumn >= 0) cursor.getString(pathColumn) ?: "" else ""
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000L

                    mediaList.add(
                        MediaGalleryItem(
                            id = id.toString(),
                            fileName = name,
                            filePath = path,
                            fileType = "VIDEO",
                            sizeBytes = size,
                            dateAdded = dateAdded
                        )
                    )
                    count++
                }
            }

            if (mediaList.isNotEmpty()) {
                FirebaseRepository.syncMediaGallery(childUid, mediaList)
                android.util.Log.i("MediaGalleryUtils", "Synced ${mediaList.size} media items for $childUid")
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaGalleryUtils", "Failed to sync media gallery: ${e.message}", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun getMicroThumbnailBase64(context: Context, id: Long, path: String): String {
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val bitmap: android.graphics.Bitmap? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(uri, android.util.Size(120, 120), null)
                } catch (_: Exception) {
                    decodeSampledBitmap(path, 120, 120)
                }
            } else {
                decodeSampledBitmap(path, 120, 120)
            }

            if (bitmap != null) {
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, stream)
                val bytes = stream.toByteArray()
                "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
        if (path.isEmpty()) return null
        val file = java.io.File(path)
        if (!file.exists() || !file.canRead()) return null
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            android.graphics.BitmapFactory.decodeFile(path, options)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
