package com.example.authapp.utils

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.MediaGalleryItem
import com.google.android.gms.tasks.Tasks
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

object MediaGalleryUtils {

    private const val HD_THUMBNAIL_SIZE = 400
    private const val JPEG_QUALITY = 75
    private const val MAX_IMAGES = 30
    private const val MAX_VIDEOS = 15

    @Volatile
    private var isStorageQuotaAvailable = true

    fun scanAndSyncMediaGallery(context: Context, childUid: String, force: Boolean = false) {
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
                while (cursor.moveToNext() && count < MAX_IMAGES) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "photo_${id}.jpg"
                    val path = if (pathColumn >= 0) cursor.getString(pathColumn) ?: "" else ""
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000L

                    // Generate HD 400x400 thumbnail & upload to Cloud Storage (with seamless fallback)
                    val thumbBytes = getHDImageThumbnailBytes(context, id, path)
                    val downloadUrl = if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                        uploadThumbnailToStorage(childUid, "img_${id}", thumbBytes)
                    } else ""

                    val base64Fallback = if (downloadUrl.isEmpty() && thumbBytes != null && thumbBytes.isNotEmpty()) {
                        android.util.Base64.encodeToString(thumbBytes, android.util.Base64.NO_WRAP)
                    } else ""

                    mediaList.add(
                        MediaGalleryItem(
                            id = id.toString(),
                            fileName = name,
                            filePath = path,
                            fileType = "IMAGE",
                            sizeBytes = size,
                            dateAdded = dateAdded,
                            downloadUrl = downloadUrl,
                            thumbnailBase64 = base64Fallback
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
                while (cursor.moveToNext() && count < MAX_VIDEOS) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "video_${id}.mp4"
                    val path = if (pathColumn >= 0) cursor.getString(pathColumn) ?: "" else ""
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000L

                    // Generate HD Video Thumbnail & upload to Cloud Storage (with seamless fallback)
                    val videoThumbBytes = getHDVideoThumbnailBytes(context, id, path)
                    val downloadUrl = if (videoThumbBytes != null && videoThumbBytes.isNotEmpty()) {
                        uploadThumbnailToStorage(childUid, "vid_${id}", videoThumbBytes)
                    } else ""

                    val videoBase64Fallback = if (downloadUrl.isEmpty() && videoThumbBytes != null && videoThumbBytes.isNotEmpty()) {
                        android.util.Base64.encodeToString(videoThumbBytes, android.util.Base64.NO_WRAP)
                    } else ""

                    mediaList.add(
                        MediaGalleryItem(
                            id = id.toString(),
                            fileName = name,
                            filePath = path,
                            fileType = "VIDEO",
                            sizeBytes = size,
                            dateAdded = dateAdded,
                            downloadUrl = downloadUrl,
                            thumbnailBase64 = videoBase64Fallback
                        )
                    )
                    count++
                }
            }

            if (mediaList.isNotEmpty()) {
                val prefs = context.getSharedPreferences("media_gallery_sync_prefs", Context.MODE_PRIVATE)
                val lastSyncTime = prefs.getLong("last_sync_time_$childUid", 0L)
                val now = System.currentTimeMillis()
                val currentFingerprint = "${mediaList.size}_${mediaList.firstOrNull()?.id}_${mediaList.firstOrNull()?.dateAdded}"

                // Throttling for automatic passes, bypassed if forced by parent
                if (!force && (now - lastSyncTime < 12 * 60 * 60 * 1000L)) {
                    android.util.Log.d("MediaGalleryUtils", "Throttled: Media gallery already synced recently.")
                    return
                }

                FirebaseRepository.syncMediaGallery(childUid, mediaList)
                prefs.edit()
                    .putString("last_fingerprint_$childUid", currentFingerprint)
                    .putLong("last_sync_time_$childUid", now)
                    .apply()
                android.util.Log.i("MediaGalleryUtils", "Synced ${mediaList.size} HD media items with Cloud Storage for $childUid")
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaGalleryUtils", "Failed to sync media gallery: ${e.message}", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun uploadThumbnailToStorage(childUid: String, fileId: String, bytes: ByteArray): String {
        if (!isStorageQuotaAvailable) return ""
        return try {
            val storageRef = FirebaseStorage.getInstance().reference.child("thumbnails/$childUid/$fileId.jpg")
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCacheControl("public, max-age=604800")
                .build()

            val uploadTask = storageRef.putBytes(bytes, metadata)
            Tasks.await(uploadTask, 2, TimeUnit.SECONDS)
            val downloadUrl = Tasks.await(storageRef.downloadUrl, 2, TimeUnit.SECONDS)
            downloadUrl.toString()
        } catch (e: Exception) {
            // If Storage fails (e.g. Spark 402 or timeout), immediately disable Storage for remaining batch
            isStorageQuotaAvailable = false
            android.util.Log.w("MediaGalleryUtils", "Storage upload note for $fileId: ${e.message} (switching to HD Base64 fallback)")
            ""
        }
    }

    fun getHDImageThumbnailBytes(context: Context, id: Long, path: String): ByteArray? {
        return try {
            var resolvedUri: android.net.Uri? = if (id > 0L) {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            } else null

            if (resolvedUri == null && path.isNotEmpty()) {
                val file = File(path)
                try {
                    val projection = arrayOf(MediaStore.Images.Media._ID)
                    val cursor = context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        "${MediaStore.Images.Media.DATA} = ? OR ${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                        arrayOf(path, file.name),
                        null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val realId = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                            resolvedUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, realId)
                        }
                    }
                } catch (_: Exception) {}
            }

            val bitmap: Bitmap? = if (resolvedUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(resolvedUri, Size(HD_THUMBNAIL_SIZE, HD_THUMBNAIL_SIZE), null)
                } catch (_: Exception) {
                    decodeSampledBitmap(path, HD_THUMBNAIL_SIZE, HD_THUMBNAIL_SIZE)
                }
            } else {
                decodeSampledBitmap(path, HD_THUMBNAIL_SIZE, HD_THUMBNAIL_SIZE)
            }

            if (bitmap != null) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                stream.toByteArray()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getHDVideoThumbnailBytes(context: Context, id: Long, path: String): ByteArray? {
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(HD_THUMBNAIL_SIZE, HD_THUMBNAIL_SIZE), null)
                } catch (_: Exception) {
                    decodeSampledBitmap(path, HD_THUMBNAIL_SIZE, HD_THUMBNAIL_SIZE)
                }
            } else {
                decodeSampledBitmap(path, HD_THUMBNAIL_SIZE, HD_THUMBNAIL_SIZE)
            }

            if (bitmap != null) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                stream.toByteArray()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // Deprecated backward-compatible signatures preserved
    fun getMicroThumbnailBase64(context: Context, id: Long, path: String): String = ""
    fun getVideoThumbnailBase64(context: Context, id: Long, path: String): String = ""

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (path.isEmpty()) return null
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
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
