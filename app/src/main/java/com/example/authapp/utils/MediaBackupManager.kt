package com.example.authapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.MediaItemInfo
import com.google.firebase.crashlytics.FirebaseCrashlytics

object MediaBackupManager {

    fun getRecentPhotos(context: Context, limit: Int = 15): List<MediaItemInfo> {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val list = mutableListOf<MediaItemInfo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(MediaStore.Images.Media._ID)
                val nameIdx = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeIdx = it.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val sizeIdx = it.getColumnIndex(MediaStore.Images.Media.SIZE)
                val dateIdx = it.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

                while (it.moveToNext() && list.size < limit) {
                    val id = if (idIdx >= 0) it.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "Photo" else "Photo"
                    val mime = if (mimeIdx >= 0) it.getString(mimeIdx) ?: "image/jpeg" else "image/jpeg"
                    val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0L
                    val dateSec = if (dateIdx >= 0) it.getLong(dateIdx) else 0L

                    list.add(
                        MediaItemInfo(
                            id = id,
                            displayName = name,
                            mimeType = mime,
                            sizeBytes = size,
                            timestamp = dateSec * 1000L,
                            folderName = "Camera / Gallery"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[MediaBackup] getRecentPhotos error: ${e.localizedMessage}")
        }
        return list
    }

    fun syncRecentMedia(context: Context, childId: String) {
        if (childId.isEmpty()) return
        val photos = getRecentPhotos(context, 15)
        if (photos.isNotEmpty()) {
            FirebaseRepository.syncMediaItems(childId, photos)
        }
    }
}
