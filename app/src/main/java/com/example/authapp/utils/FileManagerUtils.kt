package com.example.authapp.utils

import android.content.Context
import android.os.Environment
import com.example.authapp.data.FileExplorerItem
import com.example.authapp.data.FirebaseRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File

object FileManagerUtils {

    fun scanAndSyncFileExplorer(context: Context, childUid: String) {
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

            for (dir in targetDirs) {
                if (dir.exists()) {
                    fileList.add(
                        FileExplorerItem(
                            path = dir.absolutePath,
                            name = dir.name,
                            isDirectory = true,
                            sizeBytes = 0L,
                            lastModified = dir.lastModified(),
                            parentPath = dir.parent ?: ""
                        )
                    )

                    // Add top files inside directory
                    dir.listFiles()?.take(20)?.forEach { child ->
                        fileList.add(
                            FileExplorerItem(
                                path = child.absolutePath,
                                name = child.name,
                                isDirectory = child.isDirectory,
                                sizeBytes = if (child.isFile) child.length() else 0L,
                                lastModified = child.lastModified(),
                                parentPath = dir.absolutePath
                            )
                        )
                    }
                }
            }

            if (fileList.isNotEmpty()) {
                FirebaseRepository.syncFileExplorer(childUid, fileList)
                FirebaseCrashlytics.getInstance().log("[FileManagerUtils] Synced  file explorer items for ")
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
