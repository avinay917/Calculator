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
      FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
      FirebaseCrashlytics.getInstance().log("Calculator App Launched")
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

          DisposableEffect(Unit) {
            val updateListener = FirebaseRepository.listenToAppUpdate { info ->
              if (info != null && UpdateManager.isUpdateAvailable(currentVersionCode, info) && info.versionCode != dismissedUpdateCode) {
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
                  Toast.makeText(context, "Please allow 'Install unknown apps' to update", Toast.LENGTH_LONG).show()
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
                  }
                }
              },
              onDismiss = {
                dismissedUpdateCode = updateInfo.versionCode
                availableUpdate = null
              }
            )
          }
        }
      }
    }
  }
}
