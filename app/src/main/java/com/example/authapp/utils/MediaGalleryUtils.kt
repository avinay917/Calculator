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
                " DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                var count = 0
                while (cursor.moveToNext() && count < 50) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "photo_.jpg"
                    val path = cursor.getString(pathColumn) ?: ""
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateColumn) * 1000L

                    mediaList.add(
                        MediaGalleryItem(
                            id = id.toString(),
                            fileName = name,
                            filePath = path,
                            fileType = "IMAGE",
                            sizeBytes = size,
                            dateAdded = dateAdded
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
                " DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                var count = 0
                while (cursor.moveToNext() && count < 30) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "video_.mp4"
                    val path = cursor.getString(pathColumn) ?: ""
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
                FirebaseCrashlytics.getInstance().log("[MediaGalleryUtils] Synced  media items for ")
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
