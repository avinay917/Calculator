package com.example.authapp.service

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.example.authapp.utils.WebHistoryManager
import com.google.firebase.crashlytics.FirebaseCrashlytics

object ChildAccessibilityUrlExtractor {

    val BROWSER_PACKAGES = setOf(
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

    private var lastCapturedUrl = ""
    private var lastCapturedTime = 0L

    fun extractAndLogBrowserUrl(context: Context, rootNode: AccessibilityNodeInfo, packageName: String) {
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
                WebHistoryManager.logVisitedUrl(context, formattedUrl, packageName)
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

    fun isValidUrlCandidate(text: String): Boolean {
        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("www.")) {
            return true
        }
        return text.contains(".") && !text.contains(" ") && text.length > 3 &&
                (text.endsWith(".com") || text.endsWith(".org") || text.endsWith(".in") ||
                        text.endsWith(".net") || text.endsWith(".io") || text.endsWith(".co") ||
                        text.endsWith(".edu") || text.endsWith(".gov") || text.endsWith(".ai") ||
                        text.contains(".com/") || text.contains(".org/") || text.contains(".in/"))
    }

    fun formatUrl(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }
}
