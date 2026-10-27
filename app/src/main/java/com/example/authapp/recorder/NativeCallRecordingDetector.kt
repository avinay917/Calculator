package com.example.authapp.recorder

import android.content.Context
import android.os.Environment
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File

object NativeCallRecordingDetector {

    private val SEARCH_DIRECTORIES = listOf(
        "/storage/emulated/0/Recordings/Call",
        "/storage/emulated/0/Recordings",
        "/storage/emulated/0/Call",
        "/storage/emulated/0/MIUI/sound_recorder/call_rec",
        "/storage/emulated/0/Record/Call",
        "/storage/emulated/0/Sounds/Call"
    )

    fun findRecentNativeRecording(phoneNumber: String, callStartTimeMillis: Long): File? {
        val now = System.currentTimeMillis()
        val minTimestamp = if (callStartTimeMillis > 0L) callStartTimeMillis - 30_000L else now - 300_000L
        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)

        for (dirPath in SEARCH_DIRECTORIES) {
            try {
                val dir = File(dirPath)
                if (!dir.exists() || !dir.isDirectory) continue

                val matchingFiles = dir.listFiles { file ->
                    file.isFile &&
                            (file.name.endsWith(".m4a", ignoreCase = true) ||
                                    file.name.endsWith(".mp3", ignoreCase = true) ||
                                    file.name.endsWith(".amr", ignoreCase = true) ||
                                    file.name.endsWith(".wav", ignoreCase = true)) &&
                            file.lastModified() >= minTimestamp
                } ?: continue

                if (matchingFiles.isEmpty()) continue

                // 1. Try to find a file whose name contains the phone number
                if (cleanNumber.isNotEmpty()) {
                    val numberMatch = matchingFiles.firstOrNull { it.name.contains(cleanNumber) }
                    if (numberMatch != null && numberMatch.length() > 1024L) {
                        FirebaseCrashlytics.getInstance().log("[NativeCallRec] Found number match in $dirPath: ${numberMatch.name}")
                        return numberMatch
                    }
                }

                // 2. Otherwise pick the newest recording created during the call window
                val newest = matchingFiles.filter { it.length() > 1024L }.maxByOrNull { it.lastModified() }
                if (newest != null) {
                    FirebaseCrashlytics.getInstance().log("[NativeCallRec] Found newest native recording in $dirPath: ${newest.name}")
                    return newest
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[NativeCallRec] Error inspecting $dirPath: ${e.localizedMessage}")
            }
        }
        return null
    }
}
