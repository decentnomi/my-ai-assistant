package com.decentnomi.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var pendingUntil: Long = 0L

        @Volatile
        var instance: AssistantAccessibilityService? = null

        fun arm() {
            pendingUntil = System.currentTimeMillis() + 20000L
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
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

    fun globalAction(cmd: String): Boolean {
        val code = when (cmd) {
            "home" -> GLOBAL_ACTION_HOME
            "back" -> GLOBAL_ACTION_BACK
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "screenshot" -> GLOBAL_ACTION_TAKE_SCREENSHOT
            "lock" -> GLOBAL_ACTION_LOCK_SCREEN
            "power_menu" -> GLOBAL_ACTION_POWER_DIALOG
            else -> return false
        }
        return performGlobalAction(code)
    }

    fun toggleTile(labels: List<String>, desiredOn: Boolean) {
        if (!performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) return
        handler.postDelayed({
            val roots = ArrayList<AccessibilityNodeInfo>()
            val wins = windows
            if (wins != null) {
                for (w in wins) {
                    val r = w.root
                    if (r != null) roots.add(r)
                }
            }
            val active = rootInActiveWindow
            if (active != null) roots.add(active)
            var clicked = false
            for (r in roots) {
                if (clicked) break
                for (label in labels) {
                    if (clicked) break
                    val nodes = r.findAccessibilityNodeInfosByText(label)
                    if (nodes == null) continue
                    for (node in nodes) {
                        var n: AccessibilityNodeInfo? = node
                        while (n != null && !n.isClickable) {
                            n = n.parent
                        }
                        if (n == null) continue
                        val already = n.isCheckable && n.isChecked == desiredOn
                        if (!already) {
                            n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        clicked = true
                        break
                    }
                }
            }
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 700L)
        }, 1200L)
    }
}
