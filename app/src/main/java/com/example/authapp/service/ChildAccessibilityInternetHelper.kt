package com.example.authapp.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ChildAccessibilityInternetHelper(
    private val service: AccessibilityService,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {

    @Volatile
    var isAutoEnablingInternet = false
        private set

    fun triggerAutoEnableInternet() {
        isAutoEnablingInternet = true
        FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] triggerAutoEnableInternet initiated")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                service.startActivity(panelIntent)
            } catch (_: Exception) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            }
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }

        // Check active window after short delay
        mainHandler.postDelayed({
            tryAutoClickInternetSwitches()
        }, 500)

        // Reset safety flag after 5 seconds
        mainHandler.postDelayed({
            isAutoEnablingInternet = false
        }, 5000)
    }

    fun tryAutoClickInternetSwitches() {
        if (!isAutoEnablingInternet) return
        try {
            val root = service.rootInActiveWindow ?: return
            val clicked = scanAndClickInternetToggle(root)
            if (clicked) {
                isAutoEnablingInternet = false
                FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Internet toggle clicked successfully")
                mainHandler.postDelayed({
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }, 700)
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] tryAutoClick error: ${e.localizedMessage}")
        }
    }

    private fun scanAndClickInternetToggle(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val isTargetName = text.contains("wi-fi") || text.contains("wifi") || text.contains("mobile data") ||
                text.contains("cellular") || desc.contains("wi-fi") || desc.contains("wifi") ||
                desc.contains("mobile data") || desc.contains("turn on") || desc.contains("off")

        if (node.isClickable && (node.className == "android.widget.Switch" || isTargetName)) {
            val isChecked = node.isChecked
            if (!isChecked || isTargetName) {
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Auto-clicked internet toggle: $text / $desc")
                    return true
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scanAndClickInternetToggle(child)) return true
        }
        return false
    }
}
