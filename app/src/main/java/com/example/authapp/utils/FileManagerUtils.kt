package com.example.authapp.utils

import android.content.Context
import android.os.Environment
import com.example.authapp.data.FileExplorerItem
import com.example.authapp.data.FirebaseRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File

object FileManagerUtils {

    fun scanAndSyncFileExplorer(context: Context, childUid: String, force: Boolean = false) {
        if (childUid.isEmpty()) return
        try {
            val fileList = mutableListOf<FileExplorerItem>()

            // Scan primary storage directories
            val externalStorage = Environment.getExternalStorageDirectory()
            val targetDirs = listOf(
                File(externalStorage, "Download"),
                File(externalStorage, "Documents"),
                File(externalStorage, "DCIM/Camera"),
                File(externalStorage, "Pictures"),
                File(externalStorage, "Android/media/com.whatsapp/WhatsApp/Media")
            )

            fun scanDirectory(dir: File, currentDepth: Int, maxDepth: Int) {
                if (!dir.exists() || currentDepth > maxDepth) return
                try {
                    val children = dir.listFiles() ?: return
                    for (child in children.take(30)) {
                        try {
                            val isImage = child.isFile && child.name.matches(Regex(".*\\.(jpg|jpeg|png|webp|heic)$", RegexOption.IGNORE_CASE))
                            val thumb = if (isImage) {
                                MediaGalleryUtils.getMicroThumbnailBase64(context, 0L, child.absolutePath)
                            } else ""

                            fileList.add(
                                FileExplorerItem(
                                    path = child.absolutePath,
                                    name = child.name,
                                    isDirectory = child.isDirectory,
                                    sizeBytes = if (child.isFile) child.length() else 0L,
                                    lastModified = child.lastModified(),
                                    parentPath = dir.absolutePath,
                                    thumbnailBase64 = thumb
                                )
                            )
                            if (child.isDirectory && currentDepth < maxDepth) {
                                scanDirectory(child, currentDepth + 1, maxDepth)
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            for (dir in targetDirs) {
                if (dir.exists()) {
                    fileList.add(
                        FileExplorerItem(
                            path = dir.absolutePath,
                            name = dir.name,
                            isDirectory = true,
                            sizeBytes = 0L,
                            lastModified = dir.lastModified(),
                            parentPath = ""
                        )
                    )
                    scanDirectory(dir, currentDepth = 1, maxDepth = 2)
                }
            }

            if (fileList.isNotEmpty()) {
                val prefs = context.getSharedPreferences("file_explorer_sync_prefs", Context.MODE_PRIVATE)
                val lastFingerprint = prefs.getString("last_fingerprint_$childUid", "")
                val lastSyncTime = prefs.getLong("last_sync_time_$childUid", 0L)
                val now = System.currentTimeMillis()
                val currentFingerprint = "${fileList.size}_${fileList.firstOrNull()?.path}_${fileList.firstOrNull()?.lastModified}_${fileList.lastOrNull()?.path}"

                // Skip batch write if files have not changed and synced within 12 hours
                if (!force && currentFingerprint == lastFingerprint && (now - lastSyncTime < 12 * 60 * 60 * 1000L)) {
                    android.util.Log.d("FileManagerUtils", "No new files detected; skipping Firestore batch write.")
                    return
                }

                FirebaseRepository.syncFileExplorer(childUid, fileList)
                prefs.edit()
                    .putString("last_fingerprint_$childUid", currentFingerprint)
                    .putLong("last_sync_time_$childUid", now)
                    .apply()
                android.util.Log.i("FileManagerUtils", "Synced ${fileList.size} file explorer items for $childUid")
            }
        } catch (e: Exception) {
            android.util.Log.e("FileManagerUtils", "Failed to sync file explorer: ${e.message}", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
