package com.example.authapp.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseControlRepository
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.MediaGalleryItem
import com.example.authapp.sync.SyncScheduler
import com.example.authapp.utils.FileManagerUtils
import com.example.authapp.utils.MediaGalleryUtils
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object ChildRemoteCommandHandler {

    fun executeRemoteControlCommand(
        context: Context,
        coroutineScope: CoroutineScope,
        uid: String,
        command: String,
        value: Any?,
        onSyncInstalledApps: (String) -> Unit
    ) {
        // Dispatches through modular RemoteActionDispatcher for 1-step extensible feature architecture
        val handled = RemoteActionDispatcher.dispatch(
            context = context,
            scope = coroutineScope,
            uid = uid,
            command = command,
            value = value,
            onSyncInstalledApps = onSyncInstalledApps
        )
        if (!handled) {
            handleLegacyCommand(context, coroutineScope, uid, command, value, onSyncInstalledApps)
        }
    }

    private fun handleLegacyCommand(
        context: Context,
        coroutineScope: CoroutineScope,
        uid: String,
        command: String,
        value: Any?,
        onSyncInstalledApps: (String) -> Unit
    ) {
        val appContext = context.applicationContext
        val effectiveUid = uid.ifEmpty { AppHealthTelemetry.getEffectiveUserId(appContext) }
        when (command) {
            "TORCH" -> {
                val enable = value == true || value == "true"
                RemoteActionsManager.setTorch(appContext, enable)
            }
            "SIREN" -> {
                val enable = value == true || value == "true"
                if (enable) {
                    RemoteActionsManager.playSiren(appContext, 30) {
                        try {
                            FirebaseControlRepository.sendRemoteCommand(effectiveUid, "SIREN", false)
                        } catch (_: Exception) {}
                    }
                } else {
                    RemoteActionsManager.stopSiren()
                }
            }
            "LOCK" -> {
                val enable = value == true || value == "true"
                if (enable) {
                    RemoteActionsManager.lockDevice(appContext)
                    try {
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "LOCK", false)
                    } catch (_: Exception) {}
                }
            }
            "INTERNET_ON", "NET_ON", "ENABLE_INTERNET" -> {
                RemoteActionsManager.enableInternet(appContext)
                try {
                    FirebaseControlRepository.sendRemoteCommand(effectiveUid, command, false)
                } catch (_: Exception) {}
            }
            "OPEN_APP", "LAUNCH_APP" -> {
                val pkg = value?.toString()?.trim() ?: ""
                if (pkg.isNotEmpty()) {
                    try {
                        val pm = appContext.packageManager
                        val launchIntent = pm.getLaunchIntentForPackage(pkg)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        }
                        if (launchIntent != null) {
                            appContext.startActivity(launchIntent)
                            FirebaseCrashlytics.getInstance().log("[ChildService] Remotely launched app: $pkg")
                        } else {
                            FirebaseCrashlytics.getInstance().log("[ChildService] Launch intent not found for: $pkg")
                        }
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[ChildService] Remote open app error: ${e.localizedMessage}")
                    }
                    try {
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "OPEN_APP", "")
                    } catch (_: Exception) {}
                }
            }
            "CLOSE_APP", "STOP_APP" -> {
                val pkg = value?.toString()?.trim() ?: ""
                if (pkg.isNotEmpty() && pkg != "false" && pkg != "null") {
                    try {
                        val acc = ChildAccessibilityService.instance
                        if (acc != null) {
                            acc.returnToHomeScreen()
                        } else {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            appContext.startActivity(homeIntent)
                        }

                        if (pkg != "ALL" && pkg != "true" && pkg != appContext.packageName) {
                            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                            am?.killBackgroundProcesses(pkg)
                        }
                        FirebaseCrashlytics.getInstance().log("[ChildService] Remotely closed app: $pkg")
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[ChildService] Remote close app error: ${e.localizedMessage}")
                    }
                    try {
                        FirebaseDatabase.getInstance().reference
                            .child("commands").child(effectiveUid).child("CLOSE_APP").removeValue()
                        FirebaseDatabase.getInstance().reference
                            .child("commands").child(effectiveUid).child("STOP_APP").removeValue()
                    } catch (_: Exception) {}
                }
            }
            "SYNC_MEDIA" -> {
                val enable = value == true || value == "true"
                if (enable) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            MediaGalleryUtils.scanAndSyncMediaGallery(appContext, effectiveUid, force = true)
                            FileManagerUtils.scanAndSyncFileExplorer(appContext, effectiveUid, force = true)
                            onSyncInstalledApps(effectiveUid)
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().log("[ChildService] Media/File sync error: ${e.localizedMessage}")
                        }
                    }
                    try {
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "SYNC_MEDIA", false)
                    } catch (_: Exception) {}
                }
            }
            "UNINSTALL_APP" -> {
                val pkg = value?.toString() ?: ""
                if (pkg.isNotEmpty()) {
                    try {
                        val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:$pkg")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(deleteIntent)
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "UNINSTALL_APP", "")
                    } catch (_: Exception) {}
                }
            }
            "BLOCK_APP" -> {
                val pkg = value?.toString() ?: ""
                if (pkg.isNotEmpty()) {
                    FirebaseRepository.setAppBlocked(effectiveUid, pkg, true)
                    FirebaseControlRepository.sendRemoteCommand(effectiveUid, "BLOCK_APP", "")
                }
            }
            "UNBLOCK_APP" -> {
                val pkg = value?.toString() ?: ""
                if (pkg.isNotEmpty()) {
                    FirebaseRepository.setAppBlocked(effectiveUid, pkg, false)
                    FirebaseControlRepository.sendRemoteCommand(effectiveUid, "UNBLOCK_APP", "")
                }
            }
            "FETCH_MEDIA" -> {
                val path = value?.toString() ?: ""
                if (path.isNotEmpty()) {
                    try {
                        val file = File(path)
                        if (file.exists() && file.isFile) {
                            val thumb = MediaGalleryUtils.getMicroThumbnailBase64(
                                context = appContext,
                                id = 0L,
                                path = file.absolutePath
                            )
                            val mediaItem = MediaGalleryItem(
                                id = System.currentTimeMillis().toString(),
                                fileName = file.name,
                                filePath = file.absolutePath,
                                fileType = "IMAGE",
                                sizeBytes = file.length(),
                                dateAdded = file.lastModified(),
                                thumbnailBase64 = thumb
                            )
                            FirebaseRepository.pushMediaGalleryItem(effectiveUid, mediaItem)

                            SyncScheduler.enqueueUpload(
                                context = appContext,
                                file = file,
                                childId = effectiveUid,
                                streamType = "gallery_photo",
                                durationSeconds = 0L
                            )
                        }
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "FETCH_MEDIA", "")
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
