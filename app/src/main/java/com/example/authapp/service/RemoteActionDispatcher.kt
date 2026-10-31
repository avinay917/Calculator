package com.example.authapp.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseControlRepository
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.utils.FileManagerUtils
import com.example.authapp.utils.MediaGalleryUtils
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Universal interface for modular remote control command execution.
 * To add any new remote control feature in the future:
 * 1. Implement this interface
 * 2. Register it in RemoteActionDispatcher.registerHandler()
 * That's it! Zero modifications to legacy service or background code needed.
 */
interface RemoteActionHandler {
    fun handle(
        context: Context,
        scope: CoroutineScope,
        effectiveUid: String,
        command: String,
        value: Any?,
        onSyncInstalledApps: ((String) -> Unit)? = null
    ): Boolean
}

object RemoteActionDispatcher {
    private val handlers = mutableMapOf<String, RemoteActionHandler>()

    init {
        registerBuiltInHandlers()
    }

    fun registerHandler(commandKey: String, handler: RemoteActionHandler) {
        handlers[commandKey.uppercase()] = handler
    }

    fun dispatch(
        context: Context,
        scope: CoroutineScope,
        uid: String,
        command: String,
        value: Any?,
        onSyncInstalledApps: ((String) -> Unit)? = null
    ): Boolean {
        val appContext = context.applicationContext
        val effectiveUid = uid.ifEmpty { AppHealthTelemetry.getEffectiveUserId(appContext) }
        val handler = handlers[command.uppercase()]
        return if (handler != null) {
            try {
                handler.handle(appContext, scope, effectiveUid, command, value, onSyncInstalledApps)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                false
            }
        } else {
            FirebaseCrashlytics.getInstance().log("[ActionDispatcher] Unhandled command: $command")
            false
        }
    }

    private fun registerBuiltInHandlers() {
        // Torch Handler
        val torchHandler = object : RemoteActionHandler {
            override fun handle(
                context: Context,
                scope: CoroutineScope,
                effectiveUid: String,
                command: String,
                value: Any?,
                onSyncInstalledApps: ((String) -> Unit)?
            ): Boolean {
                val enable = value == true || value == "true"
                RemoteActionsManager.setTorch(context, enable)
                return true
            }
        }
        registerHandler("TORCH", torchHandler)

        // Siren Handler
        val sirenHandler = object : RemoteActionHandler {
            override fun handle(
                context: Context,
                scope: CoroutineScope,
                effectiveUid: String,
                command: String,
                value: Any?,
                onSyncInstalledApps: ((String) -> Unit)?
            ): Boolean {
                val enable = value == true || value == "true"
                if (enable) {
                    RemoteActionsManager.playSiren(context, 30) {
                        try {
                            FirebaseControlRepository.sendRemoteCommand(effectiveUid, "SIREN", false)
                        } catch (_: Exception) {}
                    }
                } else {
                    RemoteActionsManager.stopSiren()
                }
                return true
            }
        }
        registerHandler("SIREN", sirenHandler)

        // Vibrate Phone Handler (New Modular Action)
        val vibrateHandler = object : RemoteActionHandler {
            override fun handle(
                context: Context,
                scope: CoroutineScope,
                effectiveUid: String,
                command: String,
                value: Any?,
                onSyncInstalledApps: ((String) -> Unit)?
            ): Boolean {
                try {
                    val durationMs = (value?.toString()?.toLongOrNull() ?: 3L) * 1000L
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                        vm?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(durationMs)
                    }
                    FirebaseControlRepository.sendRemoteCommand(effectiveUid, "VIBRATE", false)
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
                return true
            }
        }
        registerHandler("VIBRATE", vibrateHandler)

        // Lock Device Handler
        val lockHandler = object : RemoteActionHandler {
            override fun handle(
                context: Context,
                scope: CoroutineScope,
                effectiveUid: String,
                command: String,
                value: Any?,
                onSyncInstalledApps: ((String) -> Unit)?
            ): Boolean {
                val enable = value == true || value == "true"
                if (enable) {
                    RemoteActionsManager.lockDevice(context)
                    try {
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "LOCK", false)
                    } catch (_: Exception) {}
                }
                return true
            }
        }
        registerHandler("LOCK", lockHandler)

        // Enable Internet Handler
        val internetHandler = object : RemoteActionHandler {
            override fun handle(
                context: Context,
                scope: CoroutineScope,
                effectiveUid: String,
                command: String,
                value: Any?,
                onSyncInstalledApps: ((String) -> Unit)?
            ): Boolean {
                RemoteActionsManager.enableInternet(context)
                try {
                    FirebaseControlRepository.sendRemoteCommand(effectiveUid, command, false)
                } catch (_: Exception) {}
                return true
            }
        }
        registerHandler("INTERNET_ON", internetHandler)
        registerHandler("NET_ON", internetHandler)
        registerHandler("ENABLE_INTERNET", internetHandler)

        // Open App Handler
        val openAppHandler = object : RemoteActionHandler {
            override fun handle(
                context: Context,
                scope: CoroutineScope,
                effectiveUid: String,
                command: String,
                value: Any?,
                onSyncInstalledApps: ((String) -> Unit)?
            ): Boolean {
                val pkg = value?.toString()?.trim() ?: ""
                if (pkg.isNotEmpty()) {
                    try {
                        val pm = context.packageManager
                        val launchIntent = pm.getLaunchIntentForPackage(pkg)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        }
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                            FirebaseCrashlytics.getInstance().log("[ChildService] Remotely launched app: $pkg")
                        }
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[ChildService] Remote open app error: ${e.localizedMessage}")
                    }
                    try {
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "OPEN_APP", "")
                    } catch (_: Exception) {}
                }
                return true
            }
        }
        registerHandler("OPEN_APP", openAppHandler)
        registerHandler("LAUNCH_APP", openAppHandler)

        // Close App Handler
        val closeAppHandler = object : RemoteActionHandler {
            override fun handle(
                context: Context,
                scope: CoroutineScope,
                effectiveUid: String,
                command: String,
                value: Any?,
                onSyncInstalledApps: ((String) -> Unit)?
            ): Boolean {
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
                            context.startActivity(homeIntent)
                        }
                        if (pkg != "ALL" && pkg != "true" && pkg != context.packageName) {
                            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                            am?.killBackgroundProcesses(pkg)
                        }
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
                return true
            }
        }
        registerHandler("CLOSE_APP", closeAppHandler)
        registerHandler("STOP_APP", closeAppHandler)

        // Sync Media Handler
        val syncMediaHandler = object : RemoteActionHandler {
            override fun handle(
                context: Context,
                scope: CoroutineScope,
                effectiveUid: String,
                command: String,
                value: Any?,
                onSyncInstalledApps: ((String) -> Unit)?
            ): Boolean {
                val enable = value == true || value == "true"
                if (enable) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            MediaGalleryUtils.scanAndSyncMediaGallery(context, effectiveUid, force = true)
                            FileManagerUtils.scanAndSyncFileExplorer(context, effectiveUid, force = true)
                            onSyncInstalledApps?.invoke(effectiveUid)
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().log("[ChildService] Media/File sync error: ${e.localizedMessage}")
                        }
                    }
                    try {
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "SYNC_MEDIA", false)
                    } catch (_: Exception) {}
                }
                return true
            }
        }
        registerHandler("SYNC_MEDIA", syncMediaHandler)

        // Uninstall App Handler
        val uninstallAppHandler = object : RemoteActionHandler {
            override fun handle(
                context: Context,
                scope: CoroutineScope,
                effectiveUid: String,
                command: String,
                value: Any?,
                onSyncInstalledApps: ((String) -> Unit)?
            ): Boolean {
                val pkg = value?.toString() ?: ""
                if (pkg.isNotEmpty()) {
                    try {
                        val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:$pkg")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(deleteIntent)
                        FirebaseControlRepository.sendRemoteCommand(effectiveUid, "UNINSTALL_APP", "")
                    } catch (_: Exception) {}
                }
                return true
            }
        }
        registerHandler("UNINSTALL_APP", uninstallAppHandler)

        // Block / Unblock App Handlers
        val blockHandler = object : RemoteActionHandler {
            override fun handle(
                context: Context,
                scope: CoroutineScope,
                effectiveUid: String,
                command: String,
                value: Any?,
                onSyncInstalledApps: ((String) -> Unit)?
            ): Boolean {
                val pkg = value?.toString() ?: ""
                if (pkg.isNotEmpty()) {
                    val isBlocked = command.uppercase() == "BLOCK_APP"
                    FirebaseRepository.setAppBlocked(effectiveUid, pkg, isBlocked)
                    FirebaseControlRepository.sendRemoteCommand(effectiveUid, command, "")
                }
                return true
            }
        }
        registerHandler("BLOCK_APP", blockHandler)
        registerHandler("UNBLOCK_APP", blockHandler)
    }
}

