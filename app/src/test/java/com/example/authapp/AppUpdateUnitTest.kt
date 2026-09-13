package com.example.authapp

import com.example.authapp.data.AppUpdateInfo
import com.example.authapp.updater.UpdateManager
import org.junit.Assert.*
import org.junit.Test

class AppUpdateUnitTest {

    @Test
    fun appUpdateInfo_modelIntegrity() {
        val info = AppUpdateInfo(
            versionCode = 12L,
            versionName = "v2.12",
            apkUrl = "https://github.com/avinay917/Calculator/releases/download/latest/Calculator-latest.apk",
            releaseNotes = "New In-App OTA Update System",
            isForceUpdate = false,
            updatedAt = 1710000000000L
        )

        assertEquals(12L, info.versionCode)
        assertEquals("v2.12", info.versionName)
        assertTrue(info.apkUrl.endsWith(".apk"))
        assertEquals("New In-App OTA Update System", info.releaseNotes)
        assertFalse(info.isForceUpdate)
        assertEquals(1710000000000L, info.updatedAt)
    }

    @Test
    fun updateManager_isUpdateAvailable_logicValidation() {
        val currentCode = 5L

        // Scenario 1: Remote is newer with valid APK URL -> true
        val newerUpdate = AppUpdateInfo(versionCode = 6L, apkUrl = "https://example.com/app.apk")
        assertTrue(UpdateManager.isUpdateAvailable(currentCode, newerUpdate))

        // Scenario 2: Remote is same version -> false
        val sameUpdate = AppUpdateInfo(versionCode = 5L, apkUrl = "https://example.com/app.apk")
        assertFalse(UpdateManager.isUpdateAvailable(currentCode, sameUpdate))

        // Scenario 3: Remote is older version -> false
        val olderUpdate = AppUpdateInfo(versionCode = 4L, apkUrl = "https://example.com/app.apk")
        assertFalse(UpdateManager.isUpdateAvailable(currentCode, olderUpdate))

        // Scenario 4: Remote is newer but APK URL is blank -> false
        val blankUrlUpdate = AppUpdateInfo(versionCode = 7L, apkUrl = "   ")
        assertFalse(UpdateManager.isUpdateAvailable(currentCode, blankUrlUpdate))

        // Scenario 5: Info is null -> false
        assertFalse(UpdateManager.isUpdateAvailable(currentCode, null))
    }

    @Test
    fun updateManager_forceUpdateFlag_isRespected() {
        val forceUpdate = AppUpdateInfo(versionCode = 10L, apkUrl = "https://example.com/app.apk", isForceUpdate = true)
        val optionalUpdate = AppUpdateInfo(versionCode = 10L, apkUrl = "https://example.com/app.apk", isForceUpdate = false)

        assertTrue(forceUpdate.isForceUpdate)
        assertFalse(optionalUpdate.isForceUpdate)
    }
}
