package com.example.authapp.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.authapp.data.AppUpdateInfo
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    fun isUpdateAvailable(context: Context, currentVersionCode: Long, updateInfo: AppUpdateInfo?): Boolean {
        if (updateInfo == null) return false
        val prefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
        val dismissedCode = prefs.getLong("dismissed_version_code", -1L)
        if (updateInfo.versionCode <= dismissedCode && !updateInfo.isForceUpdate) {
            return false
        }
        return updateInfo.versionCode > currentVersionCode && updateInfo.apkUrl.isNotBlank()
    }

    fun isUpdateAvailable(currentVersionCode: Long, updateInfo: AppUpdateInfo?): Boolean {
        if (updateInfo == null) return false
        return updateInfo.versionCode > currentVersionCode && updateInfo.apkUrl.isNotBlank()
    }

    fun markUpdateDismissed(context: Context, versionCode: Long) {
        try {
            val prefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("dismissed_version_code", versionCode).apply()
        } catch (_: Exception) {}
    }

    fun canInstallApk(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            var activeConn: HttpURLConnection? = null
            try {
                fun isTrustedUrl(urlStr: String): Boolean {
                    if (!urlStr.startsWith("https://", ignoreCase = true)) return false
                    val host = Uri.parse(urlStr).host?.lowercase() ?: return false
                    return host.endsWith("github.com") ||
                        host.endsWith("githubusercontent.com") ||
                        host.endsWith("googleapis.com")
                }

                if (!isTrustedUrl(apkUrl)) {
                    throw SecurityException("Untrusted or non-HTTPS APK download URL: $apkUrl")
                }

                val updatesDir = File(context.cacheDir, "updates").apply {
                    if (!exists()) mkdirs()
                }
                val apkFile = File(updatesDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                var currentUrl = apkUrl
                var redirects = 0
                val maxRedirects = 5

                while (redirects < maxRedirects) {
                    val url = URL(currentUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = false // Manual redirect handling
                    }
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (!location.isNullOrBlank()) {
                            // Validate redirect destination
                            val redirectTarget = if (location.startsWith("/")) {
                                val baseUri = Uri.parse(currentUrl)
                                "${baseUri.scheme}://${baseUri.host}$location"
                            } else location

                            if (!isTrustedUrl(redirectTarget)) {
                                throw SecurityException("Untrusted redirect host: $redirectTarget")
                            }
                            currentUrl = redirectTarget
                            redirects++
                            continue
                        }
                    }
                    activeConn = conn
                    break
                }

                val conn = activeConn ?: throw IllegalStateException("Failed to establish connection")
                if (conn.responseCode !in 200..299) {
                    withContext(Dispatchers.Main) {
                        onError("Server returned HTTP ${conn.responseCode}")
                    }
                    return@withContext
                }

                val totalBytes = conn.contentLength.toFloat()
                conn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = downloadedBytes / totalBytes
                                withContext(Dispatchers.Main) {
                                    onProgress(progress.coerceIn(0f, 1f))
                                }
                            }
                        }
                        output.flush()
                    }
                }

                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                withContext(Dispatchers.Main) {
                    onError("Download failed: ${e.localizedMessage}")
                }
            } finally {
                activeConn?.disconnect()
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() <= 0) {
                return
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
