package com.example.authapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.utils.WebHistoryManager
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ChildAccessibilityService : AccessibilityService() {

    private var lastCapturedUrl = ""
    private var lastCapturedTime = 0L

    companion object {
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.canary",
            "com.chrome.dev",
            "org.chromium.chrome",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.sbrowser.beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.microsoft.emmx",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.focus",
            "com.duckduckgo.mobile.android",
            "com.google.android.apps.searchlite",
            "com.google.android.googlequicksearchbox"
        )

        private val URL_BAR_VIEW_IDS = listOf(
            "com.android.chrome:id/url_bar",
            "com.chrome.beta:id/url_bar",
            "com.brave.browser:id/url_bar",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.microsoft.emmx:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_edit_url_view",
            "org.mozilla.firefox:id/toolbar",
            "com.opera.browser:id/url_field"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_DEFAULT
                notificationTimeout = 100
            }
            serviceInfo = info
            FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Connected and configured")
            AppHealthTelemetry.syncDeviceHealth(applicationContext)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (!BROWSER_PACKAGES.contains(packageName)) return

        try {
            val rootNode = rootInActiveWindow ?: event.source ?: return
            extractAndLogBrowserUrl(rootNode, packageName)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Event parsing notice: ${e.localizedMessage}")
        }
    }

    private fun extractAndLogBrowserUrl(rootNode: AccessibilityNodeInfo, packageName: String) {
        var rawUrl: String? = null

        // 1. Try direct resource ID search
        for (viewId in URL_BAR_VIEW_IDS) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()?.trim()
                if (!text.isNullOrEmpty() && isValidUrlCandidate(text)) {
                    rawUrl = text
                    break
                }
            }
        }

        // 2. Recursive fallback search for address bar EditText
        if (rawUrl == null) {
            rawUrl = findUrlRecursively(rootNode)
        }

        if (!rawUrl.isNullOrEmpty()) {
            val formattedUrl = formatUrl(rawUrl)
            val now = System.currentTimeMillis()

            // Deduplicate: same URL within 3 seconds ignored
            if (formattedUrl != lastCapturedUrl || (now - lastCapturedTime) > 3000L) {
                lastCapturedUrl = formattedUrl
                lastCapturedTime = now
                FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Captured Browser URL: $formattedUrl (pkg=$packageName)")
                WebHistoryManager.logVisitedUrl(applicationContext, formattedUrl, packageName)
            }
        }
    }

    private fun findUrlRecursively(node: AccessibilityNodeInfo): String? {
        if (node.className == "android.widget.EditText" || node.className == "android.widget.TextView") {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && isValidUrlCandidate(text)) {
                return text
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findUrlRecursively(child)
            if (found != null) return found
        }
        return null
    }

    private fun isValidUrlCandidate(text: String): Boolean {
        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("www.")) {
            return true
        }
        return text.contains(".") && !text.contains(" ") && text.length > 3 &&
                (text.endsWith(".com") || text.endsWith(".org") || text.endsWith(".in") ||
                        text.endsWith(".net") || text.endsWith(".io") || text.endsWith(".co") ||
                        text.endsWith(".edu") || text.endsWith(".gov") || text.endsWith(".ai") ||
                        text.contains(".com/") || text.contains(".org/") || text.contains(".in/"))
    }

    private fun formatUrl(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }

    override fun onInterrupt() {
        FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Service interrupted")
    }
}
