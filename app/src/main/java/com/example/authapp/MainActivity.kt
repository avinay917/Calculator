package com.example.authapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.authapp.data.AppUpdateInfo
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.theme.AuthAppTheme
import com.example.authapp.ui.components.InAppUpdateDialog
import com.example.authapp.updater.UpdateManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    try {
      val crashlytics = FirebaseCrashlytics.getInstance()
      crashlytics.setCrashlyticsCollectionEnabled(true)
      crashlytics.log("Calculator App Launched")

      // Check if previous session crashed fatally
      val prefs = getSharedPreferences("crash_logs", MODE_PRIVATE)
      val lastCrashTime = prefs.getLong("last_crash_time", 0L)
      if (lastCrashTime > 0L) {
        val thread = prefs.getString("last_crash_thread", "unknown")
        val msg = prefs.getString("last_crash_message", "")
        val stack = prefs.getString("last_crash_stacktrace", "")
        crashlytics.log("[PREV FATAL CRASH] Time: $lastCrashTime, Thread: $thread, Msg: $msg\nStack: $stack")
        crashlytics.recordException(Exception("PREV_FATAL_CRASH: $msg on $thread"))
        crashlytics.sendUnsentReports()
        // Clear after logging
        prefs.edit().remove("last_crash_time").apply()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    enableEdgeToEdge()
    setContent {
      AuthAppTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          val context = LocalContext.current
          val coroutineScope = rememberCoroutineScope()
          var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
          var isDownloading by remember { mutableStateOf(false) }
          var downloadProgress by remember { mutableStateOf(0f) }
          var downloadError by remember { mutableStateOf<String?>(null) }
          var dismissedUpdateCode by remember { mutableStateOf(0L) }
          val currentVersionCode = remember(context) { UpdateManager.getCurrentVersionCode(context) }
          val currentVersionName = remember(context) { UpdateManager.getCurrentVersionName(context) }
          var permissionRequestedForUpdate by remember { mutableStateOf(false) }
          val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

          DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
              if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val updateInfo = availableUpdate
                if (permissionRequestedForUpdate && updateInfo != null && UpdateManager.canInstallApk(context) && !isDownloading) {
                  permissionRequestedForUpdate = false
                  isDownloading = true
                  downloadError = null
                  downloadProgress = 0f
                  coroutineScope.launch {
                    UpdateManager.downloadAndInstallApk(
                      context = context,
                      apkUrl = updateInfo.apkUrl,
                      onProgress = { p -> downloadProgress = p },
                      onError = { err ->
                        isDownloading = false
                        downloadError = err
                      }
                    )
                    isDownloading = false
                  }
                }
              }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
              lifecycleOwner.lifecycle.removeObserver(observer)
            }
          }

          DisposableEffect(Unit) {
            val updateListener = FirebaseRepository.listenToAppUpdate { info ->
              if (info != null && UpdateManager.isUpdateAvailable(context, currentVersionCode, info) && info.versionCode != dismissedUpdateCode) {
                availableUpdate = info
              } else if (info == null || info.versionCode <= currentVersionCode) {
                availableUpdate = null
              }
            }
            onDispose {
              FirebaseRepository.removeValueListener("app_update", updateListener)
            }
          }

          MainNavigation()

          availableUpdate?.let { updateInfo ->
            InAppUpdateDialog(
              updateInfo = updateInfo,
              currentVersionName = currentVersionName,
              isDownloading = isDownloading,
              downloadProgress = downloadProgress,
              errorMessage = downloadError,
              onUpdateClick = {
                if (!UpdateManager.canInstallApk(context)) {
                  permissionRequestedForUpdate = true
                  Toast.makeText(context, "Please enable 'Allow from this source' to auto-update", Toast.LENGTH_LONG).show()
                  UpdateManager.openInstallPermissionSettings(context)
                } else {
                  isDownloading = true
                  downloadError = null
                  downloadProgress = 0f
                  coroutineScope.launch {
                    UpdateManager.downloadAndInstallApk(
                      context = context,
                      apkUrl = updateInfo.apkUrl,
                      onProgress = { p -> downloadProgress = p },
                      onError = { err ->
                        isDownloading = false
                        downloadError = err
                      }
                    )
                    isDownloading = false
                  }
                }
              },
              onDismiss = {
                dismissedUpdateCode = updateInfo.versionCode
                UpdateManager.markUpdateDismissed(context, updateInfo.versionCode)
                availableUpdate = null
              }
            )
          }
        }
      }
    }
  }
}
