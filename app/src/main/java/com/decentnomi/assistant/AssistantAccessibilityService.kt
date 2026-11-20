package com.decentnomi.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var pendingUntil: Long = 0L

        fun arm() {
            pendingUntil = System.currentTimeMillis() + 20000L
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (System.currentTimeMillis() > pendingUntil) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        var targets: List<AccessibilityNodeInfo>? = root.findAccessibilityNodeInfosByViewId(pkg + ":id/send")
        var viaText = false
        if (targets.isNullOrEmpty()) {
            targets = root.findAccessibilityNodeInfosByText("Send")
            viaText = true
        }
        for (node in targets.orEmpty()) {
            if (viaText && node.contentDescription?.toString().equals("Send", ignoreCase = true).not()) continue
            var n: AccessibilityNodeInfo? = node
            while (n != null && !n.isClickable) {
                n = n.parent
            }
            if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                pendingUntil = 0L
                return
            }
        }
    }

    override fun onInterrupt() {}
}
